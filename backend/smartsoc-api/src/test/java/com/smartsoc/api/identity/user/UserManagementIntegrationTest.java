package com.smartsoc.api.identity.user;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.api.identity.user.dto.UserDtos.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User administration over a real PostgreSQL: CRUD, uniqueness rules,
 * RBAC enforcement (analyst gets 403) and session revocation on
 * disable/delete.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class UserManagementIntegrationTest {

    private static final String USERS = "/api/v1/users";
    private static final String STRONG_PASSWORD = "Str0ng-Passw0rd!";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void adminManagesFullUserLifecycle() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        // Create
        ResponseEntity<UserResponse> created = exchange(HttpMethod.POST, USERS, adminToken,
                createUserBody("analyst.one", "analyst.one@smartsoc.io"), UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse analyst = created.getBody();
        assertThat(analyst.username()).isEqualTo("analyst.one");
        assertThat(analyst.enabled()).isTrue();

        // Read (single + list)
        ResponseEntity<UserResponse> fetched = exchange(HttpMethod.GET,
                USERS + "/" + analyst.id(), adminToken, null, UserResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<UserResponse[]> list = exchange(HttpMethod.GET, USERS, adminToken,
                null, UserResponse[].class);
        assertThat(list.getBody()).extracting(UserResponse::username).contains("admin", "analyst.one");

        // Update (PATCH semantics)
        ResponseEntity<UserResponse> updated = exchange(HttpMethod.PATCH,
                USERS + "/" + analyst.id(), adminToken,
                Map.of("fullName", "Analyst Renamed"), UserResponse.class);
        assertThat(updated.getBody().fullName()).isEqualTo("Analyst Renamed");
        assertThat(updated.getBody().role().name()).isEqualTo("SOC_ANALYST");

        // Soft delete, then 404
        ResponseEntity<Void> deleted = exchange(HttpMethod.DELETE,
                USERS + "/" + analyst.id(), adminToken, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> gone = exchange(HttpMethod.GET,
                USERS + "/" + analyst.id(), adminToken, null, String.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicateUsernameIsRejectedWith422() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        exchange(HttpMethod.POST, USERS, adminToken,
                createUserBody("analyst.dup", "analyst.dup@smartsoc.io"), UserResponse.class);

        ResponseEntity<String> duplicate = exchange(HttpMethod.POST, USERS, adminToken,
                createUserBody("Analyst.DUP", "other@smartsoc.io"), String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(duplicate.getBody()).contains("USERNAME_ALREADY_TAKEN");
    }

    @Test
    void weakPasswordIsRejectedByValidation() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.POST, USERS, adminToken,
                Map.of("username", "analyst.weak", "email", "weak@smartsoc.io",
                        "password", "short", "fullName", "Weak", "role", "SOC_ANALYST"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED").contains("password");
    }

    @Test
    void nonAdminIsForbiddenByRbac() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        exchange(HttpMethod.POST, USERS, adminToken,
                createUserBody("analyst.rbac", "analyst.rbac@smartsoc.io"), UserResponse.class);

        String analystToken = loginToken("analyst.rbac", STRONG_PASSWORD);
        ResponseEntity<String> forbidden = exchange(HttpMethod.GET, USERS, analystToken,
                null, String.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).contains("ACCESS_DENIED");
    }

    @Test
    void disablingAUserRevokesItsSessionsAndBlocksLogin() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        UserResponse analyst = exchange(HttpMethod.POST, USERS, adminToken,
                createUserBody("analyst.off", "analyst.off@smartsoc.io"), UserResponse.class)
                .getBody();

        TokenResponse analystTokens = login("analyst.off", STRONG_PASSWORD).getBody();

        exchange(HttpMethod.PATCH, USERS + "/" + analyst.id(), adminToken,
                Map.of("enabled", false), UserResponse.class);

        // Refresh session is dead and re-login is refused.
        ResponseEntity<String> refreshAfterDisable = rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", analystTokens.refreshToken()), String.class);
        assertThat(refreshAfterDisable.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("analyst.off", STRONG_PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userMutationsAreTracedInTheAuditLog() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        UserResponse created = exchange(HttpMethod.POST, USERS, adminToken,
                createUserBody("analyst.trace", "analyst.trace@smartsoc.io"), UserResponse.class).getBody();
        exchange(HttpMethod.PATCH, USERS + "/" + created.id(), adminToken,
                Map.of("role", "SOC_MANAGER"), UserResponse.class);
        exchange(HttpMethod.PATCH, USERS + "/" + created.id(), adminToken,
                Map.of("enabled", false), UserResponse.class);
        exchange(HttpMethod.DELETE, USERS + "/" + created.id(), adminToken, null, Void.class);

        ResponseEntity<String> auditLog = exchange(HttpMethod.GET,
                "/api/v1/audit-logs?actorUsername=admin&size=50", adminToken, null, String.class);
        String body = auditLog.getBody();
        assertThat(body).contains("\"action\":\"USER_CREATED\"")
                .contains("\"action\":\"USER_ROLE_CHANGED\"")
                .contains("\"action\":\"USER_DISABLED\"")
                .contains("\"action\":\"USER_DELETED\"")
                .contains(created.id().toString());
    }

    @Test
    void swaggerContractIsPubliclyAvailable() {
        ResponseEntity<String> apiDocs = rest.getForEntity("/api-docs", String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody()).contains("SmartSOC Enterprise API").contains("bearerAuth");
    }

    private Map<String, String> createUserBody(String username, String email) {
        return Map.of("username", username, "email", email, "password", STRONG_PASSWORD,
                "fullName", "Test Analyst", "role", "SOC_ANALYST");
    }

    private ResponseEntity<TokenResponse> login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), TokenResponse.class);
    }

    private String loginToken(String username, String password) {
        return login(username, password).getBody().accessToken();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String bearer,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
