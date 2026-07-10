package com.smartsoc.api.identity.auth;

import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.identity.InvalidRefreshTokenException;
import com.smartsoc.domain.identity.RefreshToken;
import com.smartsoc.domain.identity.RefreshTokenRepository;
import com.smartsoc.domain.identity.User;
import com.smartsoc.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Authentication flows: login, refresh (with rotation and reuse detection)
 * and logout. Every path presenting an invalid refresh token gets the same
 * opaque 401 — no oracle for attackers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;

    @Transactional
    public TokenResponse login(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        String rawRefreshToken = tokenService.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.issueNewFamily(
                user.getId(), tokenService.hash(rawRefreshToken), tokenService.refreshTokenExpiry()));

        return buildResponse(user, rawRefreshToken);
    }

    /**
     * noRollbackFor is essential: when token reuse is detected we revoke the
     * whole family AND reply 401. Without it, throwing the exception would
     * roll the revocation back — leaving the stolen session alive.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if (current.isRevoked()) {
            // Reuse of a rotated token: someone (user or attacker) replays an
            // old token. Kill the whole family so the stolen session dies.
            log.warn("Refresh token reuse detected for user {} (family {}); revoking the family",
                    current.getUserId(), current.getFamilyId());
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(current.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::new);

        // Rotation: the presented token is consumed, a successor replaces it.
        current.revoke(now);
        refreshTokenRepository.save(current);

        String newRawToken = tokenService.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.issueInFamily(
                user.getId(), tokenService.hash(newRawToken), current.getFamilyId(),
                tokenService.refreshTokenExpiry()));

        return buildResponse(user, newRawToken);
    }

    /** Idempotent: logging out with an unknown token is not an error. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .ifPresent(token ->
                        refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private TokenResponse buildResponse(User user, String rawRefreshToken) {
        return new TokenResponse(
                tokenService.generateAccessToken(user),
                rawRefreshToken,
                "Bearer",
                tokenService.accessTokenTtlSeconds());
    }
}
