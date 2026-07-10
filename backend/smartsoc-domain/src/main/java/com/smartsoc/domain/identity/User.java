package com.smartsoc.domain.identity;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Platform user (SOC analyst, manager, admin or viewer).
 * Pure domain entity: invariants are enforced here, persistence lives in
 * the infrastructure layer, password hashing is done by the caller (the
 * domain only ever sees a hash, never a raw password).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    private final UUID id;
    private final String username;
    private final String email;
    private String passwordHash;
    private String fullName;
    private Role role;
    private boolean enabled;

    public static User create(String username, String email, String passwordHash,
                              String fullName, Role role) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(passwordHash, "passwordHash");
        requireNonBlank(fullName, "fullName");
        if (role == null) {
            throw new BusinessRuleViolationException("INVALID_USER", "A user must have a role");
        }
        return User.builder()
                .id(UUID.randomUUID())
                .username(username.trim().toLowerCase())
                .email(email.trim().toLowerCase())
                .passwordHash(passwordHash)
                .fullName(fullName.trim())
                .role(role)
                .enabled(true)
                .build();
    }

    public void changeRole(Role newRole) {
        if (newRole == null) {
            throw new BusinessRuleViolationException("INVALID_USER", "A user must have a role");
        }
        this.role = newRole;
    }

    public void changePasswordHash(String newPasswordHash) {
        requireNonBlank(newPasswordHash, "passwordHash");
        this.passwordHash = newPasswordHash;
    }

    public void rename(String newFullName) {
        requireNonBlank(newFullName, "fullName");
        this.fullName = newFullName.trim();
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_USER",
                    "Field '%s' must not be blank".formatted(field));
        }
    }
}
