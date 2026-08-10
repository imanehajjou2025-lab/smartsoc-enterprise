package com.smartsoc.infrastructure.connectors.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertSearchResponse.Aggregation;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertSearchResponse.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Anti-Corruption Layer de l'index {@code wazuh-alerts-*} (ADR-014 phase 4)
 * — seul point du système qui connaît le modèle brut des alertes Wazuh
 * côté Indexer. Chaque règle ci-dessous répond à un cas RÉELLEMENT observé
 * dans des échantillons capturés (voir {@code docs/integration/fixtures/
 * opensearch/alerts-*-sample.json}), jamais une anticipation théorique.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchAlertMapper {

    public static final String SOURCE = "wazuh";
    private static final String UNTITLED = "Wazuh alert";

    /**
     * Bandes confirmées avec l'utilisateur (convention Wazuh officielle des
     * niveaux de règle) — PARTAGÉES avec l'agrégation {@code by_severity}
     * construite côté {@code LiveHuntExecutionAdapter} : une seule source de
     * vérité pour ces bornes.
     */
    public static final List<Map.Entry<String, int[]>> SEVERITY_BANDS = List.of(
            Map.entry("INFO", new int[] {0, 4}),
            Map.entry("LOW", new int[] {4, 7}),
            Map.entry("MEDIUM", new int[] {7, 10}),
            Map.entry("HIGH", new int[] {10, 14}),
            Map.entry("CRITICAL", new int[] {14, 16}));

    private final ObjectMapper objectMapper;

    /**
     * {@code null} si le document ne peut pas être traduit en {@link Alert}
     * valide (titre absent, timestamp illisible…) — DÉGRADATION SILENCIEUSE
     * loggée : un document individuellement mal formé n'est jamais un échec
     * de toute la page de résultats, même doctrine que {@code AgentSnapshot}.
     */
    public Alert toAlert(Hit hit) {
        try {
            JsonNode source = hit.source();
            OpenSearchAlertDocument doc = objectMapper.treeToValue(source, OpenSearchAlertDocument.class);
            OpenSearchAlertDocument.Rule rule = doc.rule();
            String title = (rule == null || rule.description() == null || rule.description().isBlank())
                    ? UNTITLED : rule.description();

            return Alert.ingest(Alert.IngestionData.builder()
                    .source(SOURCE)
                    .externalId(hit.id())
                    .title(title)
                    .description(null)
                    .severity(deriveSeverity(rule == null ? null : rule.level()))
                    .detectedAt(doc.timestamp())
                    .hostname(doc.agent() == null ? null : doc.agent().name())
                    .ruleId(rule == null ? null : rule.id())
                    .mitreTechniques(mitreIds(rule))
                    .observables(List.of())
                    // Document COMPLET (pas la vue partielle OpenSearchAlertDocument) :
                    // l'investigation a besoin de tous les champs, pas seulement ceux modélisés.
                    .rawPayload(source.toString())
                    .build());
        } catch (Exception e) {
            log.warn("Skipped unmappable alert document {}: {}", hit.id(), e.getMessage());
            return null;
        }
    }

    private static List<String> mitreIds(OpenSearchAlertDocument.Rule rule) {
        if (rule == null || rule.mitre() == null || rule.mitre().id() == null) {
            return List.of();
        }
        return rule.mitre().id();
    }

    /**
     * {@code null} (pas de {@code rule.level}) dégrade à {@code INFO} plutôt
     * que d'échouer la traduction : {@code Alert.ingest} exige une sévérité
     * non nulle, et un document sans niveau reste un événement réel à
     * afficher, pas une raison de le perdre.
     */
    static Severity deriveSeverity(Integer level) {
        if (level == null) {
            return Severity.INFO;
        }
        if (level >= 14) {
            return Severity.CRITICAL;
        }
        if (level >= 10) {
            return Severity.HIGH;
        }
        if (level >= 7) {
            return Severity.MEDIUM;
        }
        if (level >= 4) {
            return Severity.LOW;
        }
        return Severity.INFO;
    }

    /** Traduit l'agrégation {@code by_severity} (bandes {@link #SEVERITY_BANDS}) en répartition du domaine. */
    public Map<Severity, Long> toSeverityBreakdown(Map<String, Aggregation> aggregations) {
        Map<Severity, Long> breakdown = new EnumMap<>(Severity.class);
        Aggregation bySeverity = aggregations == null ? null : aggregations.get("by_severity");
        if (bySeverity == null) {
            return breakdown;
        }
        for (OpenSearchAlertSearchResponse.Bucket bucket : bySeverity.buckets()) {
            if (bucket.docCount() > 0) {
                breakdown.put(Severity.valueOf(bucket.key()), bucket.docCount());
            }
        }
        return breakdown;
    }
}
