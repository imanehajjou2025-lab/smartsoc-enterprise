package com.smartsoc.application.identity;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.identity.PasswordHasher;
import com.smartsoc.domain.identity.RefreshTokenRepository;
import com.smartsoc.domain.identity.Role;
import com.smartsoc.domain.identity.User;
import com.smartsoc.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User administration use cases (ADMIN only, enforced at the API layer).
 * Security rule applied throughout: any operation that removes a user's
 * access (disable, delete) also revokes every active session immediately.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;

    public record CreateUserCommand(String username, String email, String rawPassword,
                                    String fullName, Role role) {
    }

    /** Null fields mean "no change" (PATCH semantics). */
    public record UpdateUserCommand(String fullName, Role role, Boolean enabled) {
    }

    @Transactional
    public User createUser(CreateUserCommand command) {
        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessRuleViolationException("USERNAME_ALREADY_TAKEN",
                    "Username '%s' is already taken".formatted(command.username()));
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new BusinessRuleViolationException("EMAIL_ALREADY_TAKEN",
                    "Email '%s' is already registered".formatted(command.email()));
        }
        User user = User.create(
                command.username(),
                command.email(),
                passwordHasher.hash(command.rawPassword()),
                command.fullName(),
                command.role());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        return requireUser(id);
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateUser(UUID id, UpdateUserCommand command) {
        User user = requireUser(id);

        if (command.fullName() != null) {
            user.rename(command.fullName());
        }
        if (command.role() != null) {
            user.changeRole(command.role());
        }
        if (command.enabled() != null) {
            if (command.enabled()) {
                user.enable();
            } else {
                user.disable();
                refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            }
        }
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = requireUser(id);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        userRepository.deleteById(user.getId());
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
