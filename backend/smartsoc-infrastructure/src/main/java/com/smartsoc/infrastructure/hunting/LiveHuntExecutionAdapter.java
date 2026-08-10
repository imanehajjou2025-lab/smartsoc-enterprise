package com.smartsoc.infrastructure.hunting;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntExecutionPort;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntExecutionSummary;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntOperator;
import com.smartsoc.domain.hunting.HuntStatistics;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertClient;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertMapper;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertQuery;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertSearchResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptateur {@code live} du port de chasse (ADR-014 phase 4) : traduit un
 * {@link HuntGroup} en requête réelle contre {@code wazuh-alerts-*}, via le
 * MÊME client OpenSearch que le flux vulnérabilités (phase 1.3). Schéma
 * confirmé en réel avant tout code, voir {@code docs/integration/fixtures/
 * opensearch/alerts-*-sample.json}.
 *
 * <p>Racine V1 garantie {@code AND} de {@link HuntCondition} plates par le
 * domaine ({@code HuntQuery}) — même hypothèse que l'adaptateur simulation.
 *
 * <p>{@code STATUS}/{@code SOURCE} n'existent PAS côté document brut Wazuh :
 * chaque résultat est nécessairement {@code AlertStatus.NEW} (jamais
 * triagé — non persisté) et {@code source="wazuh"} (seul index interrogé).
 * Une condition qui exige une AUTRE valeur ne peut donc JAMAIS correspondre
 * — court-circuitée en résultat vide plutôt que traduite en filtre
 * OpenSearch inexistant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.open-search.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveHuntExecutionAdapter implements HuntExecutionPort {

    static final String CIRCUIT_BREAKER = "openSearchHuntExecution";
    private static final String DEFAULT_INDEX_PATTERN = "wazuh-alerts-*";

    private final OpenSearchAlertClient client;
    private final OpenSearchAlertMapper mapper;
    private final ConnectorProperties properties;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "executionUnavailable")
    public HuntExecutionResult execute(HuntGroup criteria, PageQuery page) {
        Instant executedAt = Instant.now();
        long startNanos = System.nanoTime();

        List<HuntCondition> conditions = criteria.children().stream()
                .map(HuntCondition.class::cast).toList();
        if (neverMatchesLiveAlerts(conditions)) {
            return emptyResult(executedAt, System.nanoTime() - startNanos, page);
        }

        OpenSearchAlertQuery query = buildQuery(conditions, page);
        OpenSearchAlertSearchResponse response = client.search(indexPatternOrDefault(), query);

        List<Alert> matches = new ArrayList<>();
        for (OpenSearchAlertSearchResponse.Hit hit : response.hits().hits()) {
            Alert alert = mapper.toAlert(hit);
            if (alert != null) {
                matches.add(alert);
            }
        }

        long totalElements = response.hits().total().value();
        long tookMillis = (System.nanoTime() - startNanos) / 1_000_000;
        boolean truncated = !"eq".equals(response.hits().total().relation());

        HuntStatistics statistics = new HuntStatistics(
                mapper.toSeverityBreakdown(response.aggregations()),
                singleBucket(AlertStatus.NEW, totalElements),
                Map.of(OpenSearchAlertMapper.SOURCE, totalElements));

        HuntExecutionSummary summary = new HuntExecutionSummary(
                null, executedAt, tookMillis, totalElements, truncated);
        PageResult<Alert> matchPage = new PageResult<>(matches, totalElements, page.page(), page.size());

        return new HuntExecutionResult(summary, statistics, matchPage);
    }

    /**
     * {@code true} si une condition exige une valeur de {@code STATUS} ou
     * {@code SOURCE} qu'un document brut Wazuh ne peut jamais porter (voir
     * javadoc de classe) — inutile d'appeler OpenSearch dans ce cas.
     */
    private static boolean neverMatchesLiveAlerts(List<HuntCondition> conditions) {
        for (HuntCondition condition : conditions) {
            if (condition.field() == HuntField.STATUS
                    && condition.operator() == HuntOperator.EQUALS
                    && !AlertStatus.NEW.name().equals(condition.value())) {
                return true;
            }
            if (condition.field() == HuntField.SOURCE
                    && condition.operator() == HuntOperator.EQUALS
                    && !OpenSearchAlertMapper.SOURCE.equalsIgnoreCase(condition.value())) {
                return true;
            }
        }
        return false;
    }

    private static HuntExecutionResult emptyResult(Instant executedAt, long elapsedNanos, PageQuery page) {
        long tookMillis = elapsedNanos / 1_000_000;
        HuntStatistics statistics = new HuntStatistics(Map.of(), Map.of(), Map.of());
        HuntExecutionSummary summary = new HuntExecutionSummary(null, executedAt, tookMillis, 0, false);
        return new HuntExecutionResult(summary, statistics, new PageResult<>(List.of(), 0, page.page(), page.size()));
    }

    private static Map<AlertStatus, Long> singleBucket(AlertStatus status, long count) {
        Map<AlertStatus, Long> map = new EnumMap<>(AlertStatus.class);
        if (count > 0) {
            map.put(status, count);
        }
        return map;
    }

    private static OpenSearchAlertQuery buildQuery(List<HuntCondition> conditions, PageQuery page) {
        List<Map<String, Object>> filters = new ArrayList<>();
        for (HuntCondition condition : conditions) {
            toFilter(condition).ifPresent(filters::add);
        }
        Map<String, Object> query = filters.isEmpty() ? OpenSearchAlertQuery.matchAll()
                : OpenSearchAlertQuery.boolFilter(filters);

        return new OpenSearchAlertQuery(
                query,
                page.page() * page.size(),
                page.size(),
                List.of(Map.of("@timestamp", Map.of("order", "desc"))),
                true,
                severityBandsAggregation());
    }

    /**
     * {@code STATUS}/{@code SOURCE} déjà tranchées par {@link #neverMatchesLiveAlerts}
     * (soit court-circuit vide, soit — cas {@code EQUALS} sur la seule
     * valeur possible — aucun filtre nécessaire, tout document correspond) :
     * jamais traduites en clause ici.
     */
    private static Optional<Map<String, Object>> toFilter(HuntCondition condition) {
        return switch (condition.field()) {
            case STATUS, SOURCE -> Optional.empty();
            case SEVERITY -> Optional.of(severityRangeFilter(condition.value()));
            case HOSTNAME -> Optional.of(textFilter("agent.name", condition));
            case RULE_ID -> Optional.of(textFilter("rule.id", condition));
            case MITRE_TECHNIQUE -> Optional.of(OpenSearchAlertQuery.term("rule.mitre.id", condition.value()));
            case DETECTED_AT -> Optional.of(detectedAtFilter(condition));
            case RAW_PAYLOAD_TEXT -> Optional.of(OpenSearchAlertQuery.queryStringAnyField(condition.value()));
        };
    }

    private static Map<String, Object> textFilter(String field, HuntCondition condition) {
        return switch (condition.operator()) {
            case EQUALS -> OpenSearchAlertQuery.term(field, condition.value());
            case CONTAINS -> OpenSearchAlertQuery.wildcardContains(field, condition.value());
            default -> throw new IllegalStateException(
                    "Unreachable: HuntField already restricts operators for " + field);
        };
    }

    private static Map<String, Object> detectedAtFilter(HuntCondition condition) {
        String comparator = condition.operator() == HuntOperator.GREATER_THAN ? "gt" : "lt";
        return OpenSearchAlertQuery.range("@timestamp", comparator, condition.value());
    }

    private static Map<String, Object> severityRangeFilter(String severityName) {
        for (Map.Entry<String, int[]> band : OpenSearchAlertMapper.SEVERITY_BANDS) {
            if (band.getKey().equals(severityName)) {
                return Map.of("range", Map.of("rule.level",
                        Map.of("gte", band.getValue()[0], "lt", band.getValue()[1])));
            }
        }
        throw new IllegalStateException("Unreachable: unknown Severity " + severityName);
    }

    private static Map<String, Object> severityBandsAggregation() {
        List<Map<String, Object>> ranges = OpenSearchAlertMapper.SEVERITY_BANDS.stream()
                .map(band -> Map.<String, Object>of(
                        "key", band.getKey(), "from", band.getValue()[0], "to", band.getValue()[1]))
                .toList();
        return Map.of("by_severity", Map.of("range", Map.of("field", "rule.level", "ranges", ranges)));
    }

    private String indexPatternOrDefault() {
        ConnectorProperties.OpenSearch openSearch = properties.openSearch();
        String configured = openSearch == null ? null : openSearch.alertIndexPattern();
        return (configured == null || configured.isBlank()) ? DEFAULT_INDEX_PATTERN : configured;
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private HuntExecutionResult executionUnavailable(HuntGroup criteria, PageQuery page, Throwable cause) {
        log.warn("OpenSearch hunt execution unavailable: {}", cause.getMessage());
        throw new SocConnectorException("OpenSearch hunt execution unavailable: " + cause.getMessage(), cause);
    }
}
