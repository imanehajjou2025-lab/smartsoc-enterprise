package com.smartsoc.api.identity.user;

import com.smartsoc.api.identity.user.dto.UserDtos.CreateUserRequest;
import com.smartsoc.api.identity.user.dto.UserDtos.UpdateUserRequest;
import com.smartsoc.api.identity.user.dto.UserDtos.UserResponse;
import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.identity.UserManagementService;
import com.smartsoc.application.identity.UserManagementService.CreateUserCommand;
import com.smartsoc.application.identity.UserManagementService.UpdateUserCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** User administration — restricted to the ADMIN role (RBAC). */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;
    private final UserApiMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request,
                                @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        return mapper.toResponse(userManagementService.createUser(new CreateUserCommand(
                request.username(),
                request.email(),
                request.password(),
                request.fullName(),
                request.role()), actorOf(jwt, httpRequest)));
    }

    @GetMapping
    public List<UserResponse> list() {
        return mapper.toResponses(userManagementService.listUsers());
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return mapper.toResponse(userManagementService.getUser(id));
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                                @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        return mapper.toResponse(userManagementService.updateUser(id, new UpdateUserCommand(
                request.fullName(),
                request.role(),
                request.enabled()), actorOf(jwt, httpRequest)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                        HttpServletRequest httpRequest) {
        userManagementService.deleteUser(id, actorOf(jwt, httpRequest));
    }

    private static ActorContext actorOf(Jwt jwt, HttpServletRequest httpRequest) {
        return new ActorContext(jwt.getSubject(),
                UUID.fromString(jwt.getClaimAsString("userId")), httpRequest.getRemoteAddr());
    }
}
