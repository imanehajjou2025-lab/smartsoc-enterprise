package com.smartsoc.api.mitre;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API MITRE ATT&CK de bout en bout sur PostgreSQL réel : la matrice est
 * consultable par tout utilisateur authentifié (le semis embarqué est
 * présent), les identifiants inconnus et malformés sont distingués, les
 * techniques dépréciées sont cachées par défaut, et l'import d'un bundle est
 * réservé aux administrateurs et tolérant par élément.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class MitreApiIntegrationTest {

    private static final String MITRE = "/api/v1/mitre";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void matrixIsReadableAndFilteredByTacticShortName() {
        String admin = adminToken();

        List<Map<String, Object>> tactics =
                exchange(HttpMethod.GET, MITRE + "/tactics", admin, null, List.class).getBody();
        assertThat(tactics).hasSize(14);
        assertThat(tactics.getFirst()).containsEntry("shortName", "reconnaissance");

        // Le semis est là : la tactique execution contient au moins T1059.
        Map<String, Object> page = exchange(HttpMethod.GET,
                MITRE + "/techniques?tactic=execution&size=200", admin, null, Map.class).getBody();
        assertThat(attackIdsOf(page)).contains("T1059");

        Map<String, Object> powershell = exchange(HttpMethod.GET,
                MITRE + "/techniques/T1059.001", admin, null, Map.class).getBody();
        assertThat(powershell)
                .containsEntry("name", "PowerShell")
                .containsEntry("subTechnique", true)
                .containsEntry("parentId", "T1059");
        assertThat((List<String>) powershell.get("tactics")).contains("execution");
    }

    @Test
    void unknownAndMalformedIdentifiersAreDistinguished() {
        String admin = adminToken();

        // Bien formé mais absent -> 404.
        assertThat(exchange(HttpMethod.GET, MITRE + "/techniques/T9999", admin, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Hors format -> 422 (le domaine refuse l'identifiant).
        assertThat(exchange(HttpMethod.GET, MITRE + "/techniques/not-an-id", admin, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // Tactique inconnue -> 422 nommé, jamais un filtre silencieusement ignoré.
        ResponseEntity<String> badTactic = exchange(HttpMethod.GET,
                MITRE + "/techniques?tactic=nope", admin, null, String.class);
        assertThat(badTactic.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(badTactic.getBody()).contains("UNKNOWN_TACTIC");
    }

    @Test
    void deprecatedTechniquesAreHiddenByDefaultButAvailableOnDemand() {
        String admin = adminToken();

        Map<String, Object> byDefault = exchange(HttpMethod.GET,
                MITRE + "/techniques?tactic=defense-evasion&size=200",
                admin, null, Map.class).getBody();
        assertThat(attackIdsOf(byDefault)).doesNotContain("T1064");

        Map<String, Object> withDeprecated = exchange(HttpMethod.GET,
                MITRE + "/techniques?tactic=defense-evasion&includeDeprecated=true&size=200",
                admin, null, Map.class).getBody();
        assertThat(attackIdsOf(withDeprecated)).contains("T1064");
    }

    @Test
    @SuppressWarnings("unchecked")
    void importIsAdminOnlyAndTolerantPerItem() {
        String admin = adminToken();
        Map<String, Object> bundle = Map.of("attackVersion", "16.1", "techniques", List.of(
                technique("T1204", "User Execution", "execution"),                    // nouveau -> created
                technique("T1059", "Command and Scripting Interpreter", "execution"), // existe -> updated
                technique("bad-id", "Broken", "execution")));                         // rejeté

        // Un VIEWER et un SOC_ANALYST n'importent pas : c'est un acte d'admin,
        // plus strict qu'une écriture ordinaire.
        assertThat(exchange(HttpMethod.POST, MITRE + "/import", tokenForRole("VIEWER"), bundle,
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, MITRE + "/import", tokenForRole("SOC_ANALYST"), bundle,
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> report = exchange(HttpMethod.POST, MITRE + "/import", admin, bundle,
                Map.class).getBody();
        assertThat(report)
                .containsEntry("received", 3)
                .containsEntry("created", 1)
                .containsEntry("updated", 1)
                .containsEntry("rejected", 1);
        var errors = (List<Map<String, Object>>) report.get("errors");
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst())
                .containsEntry("index", 2)
                .containsEntry("code", "INVALID_ATTACK_ID");
    }

    // --- helpers ---

    private static Map<String, Object> technique(String attackId, String name, String tactic) {
        return Map.of("attackId", attackId, "name", name,
                "tactics", List.of(tactic), "deprecated", false);
    }

    @SuppressWarnings("unchecked")
    private static List<String> attackIdsOf(Map<String, Object> page) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        return items.stream().map(item -> (String) item.get("attackId")).toList();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String tokenForRole(String role) {
        String username = role.toLowerCase() + "." + suffix();
        exchange(HttpMethod.POST, "/api/v1/users", adminToken(), Map.of(
                "username", username, "email", username + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Role " + role, "role", role), String.class);
        return login(username, STRONG_PWD);
    }

    private String login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String token,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
