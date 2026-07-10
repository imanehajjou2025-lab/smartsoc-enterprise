package com.smartsoc.api.identity.user.dto;

import com.smartsoc.domain.identity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request/response contracts of the user administration endpoints. */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                    message = "only letters, digits, dots, underscores and dashes")
            String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 128,
                    message = "password must be between 12 and 128 characters")
            String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role) {
    }

    /** All fields optional: PATCH semantics, null means "no change". */
    public record UpdateUserRequest(
            @Size(max = 150) String fullName,
            Role role,
            Boolean enabled) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            String fullName,
            Role role,
            boolean enabled) {
    }
}
