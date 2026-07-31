package com.smartsoc.application.identity;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.domain.audit.AuditAction;
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
 * Every mutation is traced in the audit log (console Paramètres).
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final AuditRecorder auditRecorder;

    public record CreateUserCommand(String username, String email, String rawPassword,
                                    String fullName, Role role) {
    }

    /** Null fields mean "no change" (PATCH semantics). */
    public record UpdateUserCommand(String fullName, Role role, Boolean enabled) {
    }

    @Transactional
    public User createUser(CreateUserCommand command, ActorContext actor) {
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
        User saved = userRepository.save(user);
        auditRecorder.record(AuditAction.USER_CREATED, actor.username(), actor.userId(),
                "User", saved.getId().toString(), "Compte '%s' créé (rôle %s)"
                        .formatted(saved.getUsername(), saved.getRole()), actor.ipAddress());
        return saved;
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
    public User updateUser(UUID id, UpdateUserCommand command, ActorContext actor) {
        User user = requireUser(id);
        Role previousRole = user.getRole();

        if (command.fullName() != null) {
            user.rename(command.fullName());
            auditRecorder.record(AuditAction.USER_UPDATED, actor.username(), actor.userId(),
                    "User", user.getId().toString(), "Nom complet modifié", actor.ipAddress());
        }
        if (command.role() != null && command.role() != previousRole) {
            user.changeRole(command.role());
            auditRecorder.record(AuditAction.USER_ROLE_CHANGED, actor.username(), actor.userId(),
                    "User", user.getId().toString(), "Rôle %s -> %s".formatted(previousRole, command.role()),
                    actor.ipAddress());
        }
        if (command.enabled() != null) {
            if (Boolean.TRUE.equals(command.enabled())) {
                user.enable();
                auditRecorder.record(AuditAction.USER_ENABLED, actor.username(), actor.userId(),
                        "User", user.getId().toString(), null, actor.ipAddress());
            } else {
                user.disable();
                refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
                auditRecorder.record(AuditAction.USER_DISABLED, actor.username(), actor.userId(),
                        "User", user.getId().toString(), null, actor.ipAddress());
            }
        }
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id, ActorContext actor) {
        User user = requireUser(id);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        userRepository.deleteById(user.getId());
        auditRecorder.record(AuditAction.USER_DELETED, actor.username(), actor.userId(),
                "User", id.toString(), "Compte '%s' supprimé".formatted(user.getUsername()),
                actor.ipAddress());
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
