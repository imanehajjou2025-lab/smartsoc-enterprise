package com.smartsoc.api.alerts;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistance des alertes sur un vrai PostgreSQL : mapping JSONB,
 * déduplication par index unique, recherche filtrée et paginée.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class AlertPersistenceIntegrationTest {

    @Autowired
    private AlertRepository alertRepository;

    private static Alert alert(String externalId, Severity severity) {
        return Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId(externalId)
                .title("Brute force on ssh")
                .description("10 failures")
                .severity(severity)
                .detectedAt(Instant.now())
                .hostname("srv-01")
                .ruleId("5710")
                .mitreTechniques(List.of("T1110", "T1078"))
                .rawPayload("{\"agent\":{\"name\":\"srv-01\"},\"rule\":{\"id\":\"5710\"}}")
                .build());
    }

    @Test
    void savesAndReloadsAnAlertWithJsonbFields() {
        Alert saved = alertRepository.save(alert("evt-" + UUID.randomUUID(), Severity.HIGH));

        Alert reloaded = alertRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSource()).isEqualTo("wazuh");
        assertThat(reloaded.getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(reloaded.getMitreTechniques()).containsExactly("T1110", "T1078");
        assertThat(reloaded.getRawPayload()).contains("\"5710\"");
    }

    @Test
    void uniqueIndexEnforcesIngestionDeduplication() {
        String externalId = "evt-dup-" + UUID.randomUUID();
        alertRepository.save(alert(externalId, Severity.MEDIUM));

        // Doublon construit hors lambda : seule save() peut lever (S5778).
        Alert duplicate = alert(externalId, Severity.MEDIUM);
        assertThatThrownBy(() -> alertRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(alertRepository.findBySourceAndExternalId("wazuh", externalId)).isPresent();
    }

    @Test
    void searchFiltersBySeverityAndPaginates() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            alertRepository.save(alert("evt-crit-" + marker + "-" + i, Severity.CRITICAL));
        }
        alertRepository.save(alert("evt-low-" + marker, Severity.LOW));

        PageResult<Alert> page = alertRepository.search(
                new AlertQuery(null, Severity.CRITICAL, "wazuh", PageQuery.of(0, 2)));

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.items()).allSatisfy(a ->
                assertThat(a.getSeverity()).isEqualTo(Severity.CRITICAL));
        assertThat(page.totalPages()).isGreaterThanOrEqualTo(2);
    }
}
