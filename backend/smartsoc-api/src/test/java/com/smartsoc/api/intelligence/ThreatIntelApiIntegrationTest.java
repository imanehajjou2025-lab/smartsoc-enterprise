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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enrichissement CTI de bout en bout sur PostgreSQL réel : entrée des
 * observables (tolérance, normalisation, compte rendu), corrélation dans
 * les deux sens, retro-hunt sans rattrapage, et non-régression stricte
 * des producteurs qui ne déclarent aucun observable.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class ThreatIntelApiIntegrationTest {

    private static final String INGEST_ALERTS = "/api/v1/ingest/alerts";
    private static final String API_KEY = "test-ingest-key-0123456789abcdef";

    @Autowired
    private TestRestTemplate rest;

    // ------------------------------------------------------------------
    // Entrée des observables
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void invalidObservablesAreReportedIndividuallyAndNeverCostTheAlert() {
        String run = suffix();
        Map<String, Object> payload = alertPayload(run);
        payload.put("observables", List.of(
                Map.of("type", "DOMAIN", "value", "  EVIL-" + run + "[.]COM. "),
                Map.of("type", "SHA256", "value", "pas-un-hash"),
                Map.of("type", "IPV4", "value", "45.83.12[.]7")));

        ResponseEntity<Map> reponse = ingest(payload);

        // L'alerte est CRÉÉE malgré l'entrée fautive : une détection ne se
        // perd pas pour un champ annexe.
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> corps = reponse.getBody();

        // Les observables renvoyés sont les valeurs NORMALISÉES — le
        // producteur voit ce qui servira réellement à la corrélation.
        assertThat(valuesOf((List<Map<String, Object>>) corps.get("observables")))
                .containsExactly("evil-" + run + ".com", "45.83.12.7");

        var rapport = (Map<String, Object>) corps.get("observableReport");
        assertThat(rapport).containsEntry("accepted", 2).containsEntry("rejected", 1);

        // Le CODE est la partie contractuelle ; le message n'est vérifié
        // que comme non vide, il n'engage rien.
        var erreurs = (List<Map<String, Object>>) rapport.get("errors");
        assertThat(erreurs).hasSize(1);
        assertThat(erreurs.getFirst())
                .containsEntry("index", 1)
                .containsEntry("value", "pas-un-hash")
                .containsEntry("code", "INVALID_SHA256");
        assertThat((String) erreurs.getFirst().get("message")).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void replayingTheSameEventStillSurfacesTheMappingError() {
        Map<String, Object> payload = alertPayload(suffix());
        payload.put("observables", List.of(Map.of("type", "MD5", "value", "trop-court")));

        assertThat(ingest(payload).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> rejeu = ingest(payload);

        // Idempotence préservée, et le producteur revoit son erreur : le
        // compte rendu décrit ce qui a été COMPRIS du payload, pas ce qui
        // a été écrit en base.
        assertThat(rejeu.getStatusCode()).isEqualTo(HttpStatus.OK);
        var rapport = (Map<String, Object>) rejeu.getBody().get("observableReport");
        assertThat(rapport).containsEntry("accepted", 0).containsEntry("rejected", 1);
        assertThat(((List<Map<String, Object>>) rapport.get("errors")).getFirst())
                .containsEntry("code", "INVALID_MD5");
    }

    // ------------------------------------------------------------------
    // NON-RÉGRESSION : un producteur d'avant CTI-2 est intact
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void anAlertWithoutObservablesBehavesExactlyAsBeforeAndIsSimplyNotEnriched() {
        String admin = adminToken();

        // Payload strictement identique à celui d'avant l'introduction du
        // champ : aucun observable déclaré.
        ResponseEntity<Map> reponse = ingest(alertPayload(suffix()));
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) reponse.getBody().get("id");
        assertThat(reponse.getBody().get("observables")).isEqualTo(List.of());

        // Consultation ordinaire : rien n'a changé pour le lecteur, et le
        // compte rendu d'ingestion est ABSENT du JSON (pas présent à null).
        Map<String, Object> relu = get(admin, "/api/v1/alerts/" + id);
        assertThat(relu).containsEntry("id", id).containsEntry("severity", "HIGH");
        assertThat(relu.get("observables")).isEqualTo(List.of());
        assertThat(relu).doesNotContainKey("observableReport");

        // Enrichissement : 200 avec deux listes vides. Une alerte sans
        // observable est normale, elle n'est simplement pas enrichie.
        Map<String, Object> ti = get(admin, "/api/v1/alerts/" + id + "/threat-intel");
        assertThat((List<Object>) ti.get("observables")).isEmpty();
        assertThat((List<Object>) ti.get("matches")).isEmpty();
        assertThat(ti.get("evaluatedAt")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Corrélation alerte → IOC
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void onlyActiveIndicatorsMatchAndUnmatchedObservablesStayVisible() {
        String run = suffix();
        String admin = adminToken();
        String actif = "actif-" + run + ".evil.com";
        String perime = "perime-" + run + ".evil.com";
        String revoque = "revoque-" + run + ".evil.com";
        String inconnu = "inconnu-" + run + ".example.com";

        declareIoc(admin, actif, null);
        declareIoc(admin, perime, Instant.now().minus(1, ChronoUnit.HOURS));
        String revoqueId = declareIoc(admin, revoque, null);
        exchange(HttpMethod.POST, "/api/v1/iocs/" + revoqueId + "/revoke", admin,
                Map.of("reason", "Faux positif confirmé"), Map.class);

        String alerteId = ingestWithObservables(run, List.of(actif, perime, revoque, inconnu));

        Map<String, Object> ti = get(admin, "/api/v1/alerts/" + alerteId + "/threat-intel");

        // Les QUATRE observables sont rendus, y compris celui qu'aucun IOC
        // ne connaît : « 4 observables, 1 connu » est une information que
        // le seul décompte des correspondances ne donnerait pas.
        assertThat(valuesOf((List<Map<String, Object>>) ti.get("observables")))
                .containsExactlyInAnyOrder(actif, perime, revoque, inconnu);

        // Une seule correspondance : périmé et révoqué correspondent
        // pourtant parfaitement sur (type, valeur) — ils sont écartés par
        // le filtre d'activité, qui vit dans le SQL.
        var matches = (List<Map<String, Object>>) ti.get("matches");
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst())
                .containsEntry("value", actif)
                .containsEntry("status", "ACTIVE");
    }

    /**
     * Champs comparés pour établir qu'un enrichissement n'écrit rien.
     *
     * <p>{@code aiScore} et {@code aiVerdict} en sont volontairement
     * exclus : le classifieur TP/FP écrit sur l'alerte de façon
     * ASYNCHRONE après l'ingestion (jalon IA). Les inclure rendrait ce
     * test intermittent tout en ne prouvant rien sur l'enrichissement,
     * qui n'a aucun moyen de les toucher.
     */
    private static final List<String> CHAMPS_STABLES = List.of(
            "id", "source", "externalId", "title", "severity", "status",
            "detectedAt", "receivedAt", "hostname", "observables");

    @Test
    void enrichmentIsPureReadAndRepeatableWithoutSideEffect() {
        String run = suffix();
        String admin = adminToken();
        declareIoc(admin, "stable-" + run + ".evil.com", null);
        String alerteId = ingestWithObservables(run, List.of("stable-" + run + ".evil.com"));

        Map<String, Object> avant = get(admin, "/api/v1/alerts/" + alerteId);
        Map<String, Object> premier = get(admin, "/api/v1/alerts/" + alerteId + "/threat-intel");
        Map<String, Object> second = get(admin, "/api/v1/alerts/" + alerteId + "/threat-intel");
        Map<String, Object> apres = get(admin, "/api/v1/alerts/" + alerteId);

        // Deux appels d'affilée rendent la même corrélation : le calcul à
        // la lecture est reproductible.
        assertThat(second.get("matches")).isEqualTo(premier.get("matches"));

        // Et l'alerte n'a pas bougé : ni statut, ni date, ni observable.
        // Il n'existe d'ailleurs aucune colonne d'enrichissement à faire
        // bouger — c'est l'absence d'état qui rend l'effet de bord
        // impossible, pas seulement interdit.
        for (String champ : CHAMPS_STABLES) {
            assertThat(apres.get(champ)).as("champ %s", champ).isEqualTo(avant.get(champ));
        }
    }

    // ------------------------------------------------------------------
    // Retro-hunt : IOC → alertes
    // ------------------------------------------------------------------

    @Test
    void anIndicatorDeclaredAfterTheAlertsStillFindsThemWithNoCatchUpJob() {
        String run = suffix();
        String admin = adminToken();
        String domaine = "retro-" + run + ".evil.com";

        // TROIS alertes citent le domaine, AVANT que l'IOC n'existe.
        String premiere = ingestWithObservables(run, List.of(domaine));
        ingestWithObservables(run, List.of(domaine));
        ingestWithObservables(run, List.of(domaine));

        // À ce stade, rien à corréler : la plateforme n'invente pas d'IOC.
        assertThat(matchesOf(get(admin, "/api/v1/alerts/" + premiere + "/threat-intel"))).isEmpty();

        // L'IOC arrive MAINTENANT — aucun job, aucun recalcul.
        String iocId = declareIoc(admin, domaine, null);

        // Les trois alertes d'avant remontent immédiatement…
        Map<String, Object> page = get(admin, "/api/v1/iocs/" + iocId + "/alerts");
        assertThat(((Number) page.get("totalElements")).longValue()).isEqualTo(3);

        // …et dans l'autre sens, l'alerte voit désormais l'indicateur.
        assertThat(matchesOf(get(admin, "/api/v1/alerts/" + premiere + "/threat-intel")))
                .hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retroHuntPaginatesAndRemainsAvailableOnARevokedIndicator() {
        String run = suffix();
        String admin = adminToken();
        String domaine = "hist-" + run + ".evil.com";
        for (int i = 0; i < 3; i++) {
            ingestWithObservables(run, List.of(domaine));
        }
        String iocId = declareIoc(admin, domaine, null);

        Map<String, Object> page = get(admin, "/api/v1/iocs/" + iocId + "/alerts?page=0&size=2");
        assertThat((List<Object>) page.get("items")).hasSize(2);
        assertThat(((Number) page.get("totalElements")).longValue()).isEqualTo(3);

        // Après révocation, l'historique reste consultable : l'indicateur
        // est l'ENTRÉE de la requête, pas un résultat — c'est même ce qui
        // permet de justifier la révocation.
        exchange(HttpMethod.POST, "/api/v1/iocs/" + iocId + "/revoke", admin,
                Map.of("reason", "Domaine repris par un tiers légitime"), Map.class);
        assertThat(((Number) get(admin, "/api/v1/iocs/" + iocId + "/alerts")
                .get("totalElements")).longValue()).isEqualTo(3);
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> alertPayload(String run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "wazuh");
        payload.put("externalId", "evt-ti-" + run + "-" + UUID.randomUUID());
        payload.put("title", "Contact vers une infrastructure suspecte");
        payload.put("severity", "HIGH");
        payload.put("detectedAt", Instant.now().toString());
        payload.put("hostname", "srv-web-01");
        return payload;
    }

    private static List<String> valuesOf(List<Map<String, Object>> observables) {
        return observables.stream().map(o -> (String) o.get("value")).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matchesOf(Map<String, Object> threatIntel) {
        return (List<Map<String, Object>>) threatIntel.get("matches");
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> ingest(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", API_KEY);
        return rest.postForEntity(INGEST_ALERTS, new HttpEntity<>(payload, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String ingestWithObservables(String run, List<String> domaines) {
        Map<String, Object> payload = alertPayload(run);
        payload.put("observables",
                domaines.stream().map(d -> Map.of("type", "DOMAIN", "value", d)).toList());
        ResponseEntity<Map> reponse = ingest(payload);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) reponse.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String declareIoc(String token, String valeur, Instant validUntil) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DOMAIN");
        body.put("value", valeur);
        body.put("confidence", 90);
        body.put("tlp", "AMBER");
        if (validUntil != null) {
            body.put("validUntil", validUntil.toString());
        }
        ResponseEntity<Map> reponse = exchange(HttpMethod.POST, "/api/v1/iocs", token, body, Map.class);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) reponse.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String token, String url) {
        ResponseEntity<Map> reponse = exchange(HttpMethod.GET, url, token, null, Map.class);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return reponse.getBody();
    }

    private String adminToken() {
        return rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "IntegrationTest123!"),
                TokenResponse.class).getBody().accessToken();
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
