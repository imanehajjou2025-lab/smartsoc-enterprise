package com.smartsoc.infrastructure.hunting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntLogicalOperator;
import com.smartsoc.domain.hunting.HuntOperator;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertClient;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertMapper;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertQuery;
import com.smartsoc.infrastructure.connectors.opensearch.OpenSearchAlertSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveHuntExecutionAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OpenSearchAlertMapper mapper = new OpenSearchAlertMapper(objectMapper);

    @Mock
    private OpenSearchAlertClient client;

    private LiveHuntExecutionAdapter adapter;

    private void withProperties(String alertIndexPattern) {
        ConnectorProperties.OpenSearch openSearch = new ConnectorProperties.OpenSearch(
                "live", "https://10.100.0.1:9200", "smartsoc-reader", "secret", null, alertIndexPattern);
        ConnectorProperties properties = new ConnectorProperties(null, openSearch, null, null, null);
        adapter = new LiveHuntExecutionAdapter(client, mapper, properties);
    }

    private ObjectNode sourceNode(String hostname, int level, String description) {
        ObjectNode source = objectMapper.createObjectNode();
        source.putObject("agent").put("name", hostname);
        var rule = source.putObject("rule");
        rule.put("level", level);
        rule.put("description", description);
        rule.put("id", "5710");
        source.put("@timestamp", "2026-08-10T00:00:00Z");
        return source;
    }

    private OpenSearchAlertSearchResponse responseWith(String relation, long total,
                                                        List<OpenSearchAlertSearchResponse.Hit> hits) {
        return new OpenSearchAlertSearchResponse(
                new OpenSearchAlertSearchResponse.Hits(
                        new OpenSearchAlertSearchResponse.Total(total, relation), hits),
                Map.of());
    }

    @Test
    void translatesHostnameEqualsIntoATermFilterAndMapsRealResults() {
        withProperties(null);
        OpenSearchAlertSearchResponse.Hit hit = new OpenSearchAlertSearchResponse.Hit(
                "doc-1", sourceNode("WIN10-CLIENT", 5, "Suspicious login"));
        when(client.search(eq("wazuh-alerts-*"), any())).thenReturn(responseWith("eq", 1, List.of(hit)));

        HuntGroup criteria = new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.HOSTNAME, HuntOperator.EQUALS, "WIN10-CLIENT")));

        HuntExecutionResult result = adapter.execute(criteria, PageQuery.of(0, 25));

        ArgumentCaptor<OpenSearchAlertQuery> queryCaptor = ArgumentCaptor.forClass(OpenSearchAlertQuery.class);
        verify(client).search(eq("wazuh-alerts-*"), queryCaptor.capture());
        assertThat(queryCaptor.getValue().query()).isEqualTo(
                OpenSearchAlertQuery.boolFilter(List.of(OpenSearchAlertQuery.term("agent.name", "WIN10-CLIENT"))));

        assertThat(result.summary().matchedCount()).isEqualTo(1);
        assertThat(result.summary().truncated()).isFalse();
        assertThat(result.matches().items()).hasSize(1);
        Alert alert = result.matches().items().get(0);
        assertThat(alert.getHostname()).isEqualTo("WIN10-CLIENT");
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(alert.getSeverity()).isEqualTo(Severity.LOW);
    }

    @Test
    void usesTheConfiguredIndexPatternWhenSet() {
        withProperties("custom-wazuh-alerts-*");
        when(client.search(eq("custom-wazuh-alerts-*"), any())).thenReturn(responseWith("eq", 0, List.of()));

        adapter.execute(new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.RULE_ID, HuntOperator.EQUALS, "5710"))), PageQuery.of(0, 25));

        verify(client).search(eq("custom-wazuh-alerts-*"), any());
    }

    @Test
    void statusOtherThanNewNeverMatchesAndSkipsTheOpenSearchCallEntirely() {
        withProperties(null);

        HuntGroup criteria = new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.STATUS, HuntOperator.EQUALS, "RESOLVED")));

        HuntExecutionResult result = adapter.execute(criteria, PageQuery.of(0, 25));

        assertThat(result.summary().matchedCount()).isZero();
        assertThat(result.matches().items()).isEmpty();
        verify(client, never()).search(any(), any());
    }

    @Test
    void sourceOtherThanWazuhNeverMatchesAndSkipsTheOpenSearchCallEntirely() {
        withProperties(null);

        HuntGroup criteria = new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.SOURCE, HuntOperator.EQUALS, "misp")));

        HuntExecutionResult result = adapter.execute(criteria, PageQuery.of(0, 25));

        assertThat(result.summary().matchedCount()).isZero();
        verify(client, never()).search(any(), any());
    }

    @Test
    void statusEqualsNewIsANoOpFilterAndStillQueriesOpenSearch() {
        withProperties(null);
        when(client.search(any(), any())).thenReturn(responseWith("eq", 0, List.of()));

        HuntGroup criteria = new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.STATUS, HuntOperator.EQUALS, "NEW")));

        adapter.execute(criteria, PageQuery.of(0, 25));

        ArgumentCaptor<OpenSearchAlertQuery> queryCaptor = ArgumentCaptor.forClass(OpenSearchAlertQuery.class);
        verify(client).search(any(), queryCaptor.capture());
        // STATUS=NEW matche tout : aucun filtre ne doit en decouler, match_all.
        assertThat(queryCaptor.getValue().query()).isEqualTo(OpenSearchAlertQuery.matchAll());
    }

    @Test
    void aCappedOpenSearchCountIsReportedAsTruncated() {
        withProperties(null);
        when(client.search(any(), any())).thenReturn(responseWith("gte", 10_000, List.of()));

        HuntExecutionResult result = adapter.execute(new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.RULE_ID, HuntOperator.CONTAINS, "571"))), PageQuery.of(0, 25));

        assertThat(result.summary().truncated()).isTrue();
    }

    @Test
    void severityConditionTranslatesToTheConfirmedRuleLevelBand() {
        withProperties(null);
        when(client.search(any(), any())).thenReturn(responseWith("eq", 0, List.of()));

        adapter.execute(new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "HIGH"))), PageQuery.of(0, 25));

        ArgumentCaptor<OpenSearchAlertQuery> queryCaptor = ArgumentCaptor.forClass(OpenSearchAlertQuery.class);
        verify(client).search(any(), queryCaptor.capture());
        assertThat(queryCaptor.getValue().query()).isEqualTo(
                OpenSearchAlertQuery.boolFilter(List.of(
                        Map.of("range", Map.of("rule.level", Map.of("gte", 10, "lt", 14))))));
    }

    @Test
    void connectorFailurePropagatesRatherThanBeingSwallowed() {
        // Le disjoncteur (@CircuitBreaker) est un aspect Spring : hors
        // contexte, l'appel direct au client remonte tel quel. La
        // traduction en SocConnectorException (fallbackMethod) est
        // verifiee par le test d'integration live (WireMock).
        withProperties(null);
        when(client.search(any(), any())).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> adapter.execute(new HuntGroup(HuntLogicalOperator.AND,
                        List.of(new HuntCondition(HuntField.RULE_ID, HuntOperator.EQUALS, "5710"))),
                PageQuery.of(0, 25)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection refused");
    }
}
