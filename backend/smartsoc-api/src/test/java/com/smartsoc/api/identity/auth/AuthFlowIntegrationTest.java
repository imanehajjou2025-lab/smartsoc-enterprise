package com.smartsoc.api.identity.auth;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
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
 * End-to-end authentication flow against a real PostgreSQL: login, RBAC
 * identity probe, refresh rotation, stolen-token (reuse) detection, logout.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String ME = "/api/v1/auth/me";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginWithValidCredentialsReturnsTokenPair() {
        ResponseEntity<TokenResponse> response = login("admin", "IntegrationTest123!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(900);
    }

    @Test
    void loginWithWrongPasswordReturnsOpaque401Problem() {
        ResponseEntity<String> response = rest.postForEntity(
                LOGIN, Map.of("username", "admin", "password", "wrong"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("AUTHENTICATION_FAILED");
        assertThat(response.getBody()).doesNotContain("wrong");
    }

    @Test
    void protectedEndpointRequiresAuthentication() {
        ResponseEntity<String> anonymous = rest.getForEntity(ME, String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).contains("AUTHENTICATION_REQUIRED");

        TokenResponse tokens = login("admin", "IntegrationTest123!").getBody();
        ResponseEntity<String> me = getWithBearer(ME, tokens.accessToken());
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("\"username\":\"admin\"").contains("\"role\":\"ADMIN\"")
                .contains("\"fullName\":\"Platform Administrator\"");
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() {
        TokenResponse initial = login("admin", "IntegrationTest123!").getBody();

        // Legitimate rotation.
        ResponseEntity<TokenResponse> rotated = refresh(initial.refreshToken());
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String successor = rotated.getBody().refreshToken();
        assertThat(successor).isNotEqualTo(initial.refreshToken());

        // Replaying the consumed token = theft signal -> 401...
        ResponseEntity<String> reuse = rest.postForEntity(
                REFRESH, Map.of("refreshToken", initial.refreshToken()), String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // ...and the whole family dies, successor included.
        ResponseEntity<String> afterReuse = rest.postForEntity(
                REFRESH, Map.of("refreshToken", successor), String.class);
        assertThat(afterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheSession() {
        TokenResponse tokens = login("admin", "IntegrationTest123!").getBody();

        ResponseEntity<Void> logout = rest.postForEntity(
                LOGOUT, Map.of("refreshToken", tokens.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> refreshAfterLogout = rest.postForEntity(
                REFRESH, Map.of("refreshToken", tokens.refreshToken()), String.class);
        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<TokenResponse> login(String username, String password) {
        return rest.postForEntity(LOGIN,
                Map.of("username", username, "password", password), TokenResponse.class);
    }

    private ResponseEntity<TokenResponse> refresh(String refreshToken) {
        return rest.postForEntity(REFRESH,
                Map.of("refreshToken", refreshToken), TokenResponse.class);
    }

    private ResponseEntity<String> getWithBearer(String url, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
