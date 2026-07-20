package com.smartsoc.api.intelligence;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API CTI de bout en bout sur PostgreSQL réel (V7 + ddl validate) :
 * tolérance par élément du webhook de flux, upsert sur l'identité,
 * indicateurs révoqués/expirés toujours consultables mais hors des
 * recherches d'actifs, conflit manuel en 409, et RBAC.
 *
 * <p>Chaque test s'isole par un tag unique — ce qui exerce du même coup
 * le filtre de containment JSONB à travers la vraie requête HTTP.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class IocApiIntegrationTest {

    private static final String IOCS = "/api/v1/iocs";
    private static final String INGEST_IOCS = "/api/v1/ingest/iocs";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void feedBatchKeepsGoingAfterABadItemAndNamesEachRejection() {
        String run = suffix();
        String admin = adminToken();

        String domaine = "evil-" + run + ".com";
        String hashInvalide = "ceci-n-est-pas-un-hash";
        // URL défangée ET à chemin capitalisé : elle doit être refangée
        // sans que la casse du chemin ne bouge.
        String urlDefangee = "hxxps://phishing-" + run + "[.]com/Compte/Connexion";
        String urlAttendue = "https://phishing-" + run + ".com/Compte/Connexion";

        // Un lot réaliste : deux indicateurs valides encadrant une entrée
        // malformée. Le troisième est le vrai enjeu — il prouve que le
        // traitement ne s'arrête pas au premier rejet.
        Map<String, Object> report = pushFeed("misp", List.of(
                ioc("DOMAIN", domaine, 80, run),
                ioc("SHA256", hashInvalide, 50, run),
                ioc("URL", urlDefangee, 70, run)));

        assertThat(report)
                .containsEntry("received", 3)
                .containsEntry("created", 2)
                .containsEntry("updated", 0)
                .containsEntry("rejected", 1);

        // Le rejet est nommé : position, valeur fautive et code stable.
        var errors = (List<Map<String, Object>>) report.get("errors");
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst())
                .containsEntry("index", 1)
                .containsEntry("value", hashInvalide)
                .containsEntry("code", "INVALID_INDICATOR");
        assertThat((String) errors.getFirst().get("message")).contains("SHA256");

        // Les deux valides sont bien dans le référentiel, et le troisième
        // y est entré REFANGÉ, chemin intact : la normalisation traverse
        // tout le chemin, de la requête HTTP jusqu'à la base.
        Map<String, Object> page = searchByTag(admin, run);
        assertThat(((Number) page.get("totalElements")).longValue()).isEqualTo(2);
        assertThat(valuesOf(page)).containsExactlyInAnyOrder(domaine, urlAttendue);
    }

    @Test
    void secondPushOfSameIdentityUpdatesMetadataWithoutCreatingASecondIoc() {
        String run = suffix();
        String admin = adminToken();
        String value = "c2-" + run + ".net";
        Instant firstSeen = Instant.now().minus(5, ChronoUnit.DAYS);

        Map<String, Object> first = pushFeed("misp", List.of(
                iocAt("DOMAIN", value, 40, run, firstSeen)));
        assertThat(first).containsEntry("created", 1).containsEntry("updated", 0);

        Map<String, Object> before = onlyItem(searchByTag(admin, run));
        String id = (String) before.get("id");

        // Même identité, écrite en majuscules et défangée : c'est le même
        // IOC. Métadonnées différentes, source différente.
        Instant lastSeen = Instant.now();
        Map<String, Object> second = pushFeed("otx", List.of(
                iocAt("DOMAIN", "C2-" + run.toUpperCase() + "[.]NET", 95, run, lastSeen)));
        assertThat(second)
                .containsEntry("created", 0)
                .containsEntry("updated", 1)
                .containsEntry("rejected", 0);

        // AUCUN second enregistrement : toujours un seul IOC pour ce tag.
        Map<String, Object> page = searchByTag(admin, run);
        assertThat(((Number) page.get("totalElements")).longValue()).isEqualTo(1);

        Map<String, Object> after = onlyItem(page);
        assertThat(after)
                .containsEntry("id", id)
                .containsEntry("value", value)
                .containsEntry("confidence", 95)
                .containsEntry("feedSource", "otx");
        // La première observation est conservée, la dernière avance.
        assertThat(Instant.parse((String) after.get("firstSeen")))
                .isCloseTo(firstSeen, within(1000));
        assertThat(Instant.parse((String) after.get("lastSeen")))
                .isCloseTo(lastSeen, within(1000));
    }

    @Test
    void revokedAndExpiredStayReadableButLeaveActiveSearches() {
        String run = suffix();
        String admin = adminToken();

        // Un expiré, un révoqué, un actif — tous sous le même tag.
        pushFeed("misp", List.of(
                iocExpiring("DOMAIN", "perime-" + run + ".com", run,
                        Instant.now().minus(1, ChronoUnit.HOURS)),
                ioc("DOMAIN", "faux-positif-" + run + ".com", 60, run),
                ioc("DOMAIN", "actif-" + run + ".com", 90, run)));

        Map<String, Object> all = searchByTag(admin, run);
        assertThat(((Number) all.get("totalElements")).longValue()).isEqualTo(3);
        String revokedId = idOfValue(all, "faux-positif-" + run + ".com");
        String expiredId = idOfValue(all, "perime-" + run + ".com");

        exchange(HttpMethod.POST, IOCS + "/" + revokedId + "/revoke", admin,
                Map.of("reason", "Domaine de test interne — faux positif"), Map.class);

        // Recherche des indicateurs ACTIFS : seul le troisième répond.
        Map<String, Object> active = exchange(HttpMethod.GET,
                IOCS + "?tag=" + run + "&status=ACTIVE", admin, null, Map.class).getBody();
        assertThat(((Number) active.get("totalElements")).longValue()).isEqualTo(1);
        assertThat(valuesOf(active)).containsExactly("actif-" + run + ".com");

        // …mais les deux autres restent parfaitement consultables, avec
        // leur statut DÉDUIT et, pour le révoqué, sa justification.
        Map<String, Object> expired = exchange(HttpMethod.GET,
                IOCS + "/" + expiredId, admin, null, Map.class).getBody();
        assertThat(expired).containsEntry("status", "EXPIRED").containsEntry("revoked", false);

        Map<String, Object> revoked = exchange(HttpMethod.GET,
                IOCS + "/" + revokedId, admin, null, Map.class).getBody();
        assertThat(revoked).containsEntry("status", "REVOKED").containsEntry("revoked", true);
        assertThat((String) revoked.get("revocationReason")).contains("faux positif");
        assertThat(revoked.get("revokedAt")).isNotNull();

        // Et les filtres dédiés les retrouvent explicitement.
        assertThat(totalOf(exchange(HttpMethod.GET,
                IOCS + "?tag=" + run + "&status=EXPIRED", admin, null, Map.class).getBody()))
                .isEqualTo(1);
        assertThat(totalOf(exchange(HttpMethod.GET,
                IOCS + "?tag=" + run + "&status=REVOKED", admin, null, Map.class).getBody()))
                .isEqualTo(1);
    }

    @Test
    void revokedIndicatorIsNotResurrectedByTheFeedThatKeepsPushingIt() {
        String run = suffix();
        String admin = adminToken();
        String value = "zombie-" + run + ".com";

        pushFeed("misp", List.of(ioc("DOMAIN", value, 50, run)));
        String id = (String) onlyItem(searchByTag(admin, run)).get("id");
        exchange(HttpMethod.POST, IOCS + "/" + id + "/revoke", admin,
                Map.of("reason", "Faux positif confirmé"), Map.class);

        // Le flux repousse l'indicateur avec une confiance maximale.
        Map<String, Object> report = pushFeed("misp", List.of(ioc("DOMAIN", value, 100, run)));
        assertThat(report).containsEntry("updated", 1);

        // La décision d'analyste prime : toujours révoqué, toujours hors
        // des recherches d'indicateurs actifs.
        Map<String, Object> after = exchange(HttpMethod.GET,
                IOCS + "/" + id, admin, null, Map.class).getBody();
        assertThat(after).containsEntry("status", "REVOKED");
        assertThat(totalOf(exchange(HttpMethod.GET,
                IOCS + "?tag=" + run + "&status=ACTIVE", admin, null, Map.class).getBody()))
                .isZero();
    }

    @Test
    void manualDuplicateIs409EvenWhenWrittenDifferently() {
        String run = suffix();
        String admin = adminToken();
        String value = "manuel-" + run + ".com";

        ResponseEntity<Map> created = exchange(HttpMethod.POST, IOCS, admin,
                declarePayload(value, run), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((Map<String, Object>) created.getBody()).containsEntry("feedSource", "manual");

        // Même identité en majuscules + défangée + espaces.
        ResponseEntity<String> duplicate = exchange(HttpMethod.POST, IOCS, admin,
                declarePayload("  " + value.toUpperCase().replace(".", "[.]") + " ", run),
                String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody())
                .contains("INDICATOR_ALREADY_EXISTS")
                .contains(value);
    }

    @Test
    void ingestWebhookRequiresItsApiKey() {
        HttpHeaders noKey = new HttpHeaders();
        noKey.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> refused = rest.postForEntity(INGEST_IOCS,
                new HttpEntity<>(Map.of("feedSource", "misp",
                        "indicators", List.of(ioc("DOMAIN", "sans-cle.com", 50, "x"))), noKey),
                String.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void viewerCanReadButCannotDeclareOrRevoke() {
        String admin = adminToken();
        String viewer = "viewer." + suffix();
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);

        assertThat(exchange(HttpMethod.GET, IOCS, viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, IOCS, viewerToken,
                declarePayload("interdit-" + suffix() + ".com", "x"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static org.assertj.core.data.TemporalUnitOffset within(long millis) {
        return org.assertj.core.api.Assertions.within(millis, ChronoUnit.MILLIS);
    }

    private static Map<String, Object> ioc(String type, String value, int confidence, String tag) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("value", value);
        item.put("confidence", confidence);
        item.put("tags", List.of(tag));
        return item;
    }

    private static Map<String, Object> iocAt(String type, String value, int confidence,
                                             String tag, Instant observedAt) {
        Map<String, Object> item = ioc(type, value, confidence, tag);
        item.put("observedAt", observedAt.toString());
        return item;
    }

    private static Map<String, Object> iocExpiring(String type, String value,
                                                   String tag, Instant validUntil) {
        Map<String, Object> item = ioc(type, value, 70, tag);
        item.put("validUntil", validUntil.toString());
        return item;
    }

    private static Map<String, Object> declarePayload(String value, String tag) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "DOMAIN");
        body.put("value", value);
        body.put("confidence", 75);
        body.put("tlp", "AMBER");
        body.put("tags", List.of(tag));
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pushFeed(String feedSource, List<Map<String, Object>> indicators) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        return rest.postForEntity(INGEST_IOCS,
                new HttpEntity<>(Map.of("feedSource", feedSource, "indicators", indicators),
                        headers), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> searchByTag(String token, String tag) {
        return exchange(HttpMethod.GET, IOCS + "?tag=" + tag, token, null, Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> itemsOf(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    private static List<String> valuesOf(Map<String, Object> page) {
        return itemsOf(page).stream().map(i -> (String) i.get("value")).toList();
    }

    private static long totalOf(Map<String, Object> page) {
        return ((Number) page.get("totalElements")).longValue();
    }

    private static Map<String, Object> onlyItem(Map<String, Object> page) {
        assertThat(itemsOf(page)).hasSize(1);
        return itemsOf(page).getFirst();
    }

    private static String idOfValue(Map<String, Object> page, String value) {
        return itemsOf(page).stream()
                .filter(i -> value.equals(i.get("value")))
                .map(i -> (String) i.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("IOC absent de la page : " + value));
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
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
