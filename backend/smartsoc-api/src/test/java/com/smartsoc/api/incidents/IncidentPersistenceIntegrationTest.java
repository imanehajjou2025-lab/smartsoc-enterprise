package com.smartsoc.api.incidents;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentEventType;
import com.smartsoc.domain.incidents.IncidentQuery;
import com.smartsoc.domain.incidents.IncidentReferenceGenerator;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.incidents.IncidentStatus;
import com.smartsoc.domain.incidents.IncidentTimelineEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance des incidents sur un vrai PostgreSQL : référence générée par
 * séquence, aller-retour, liaison d'alertes (FK + idempotence), timeline,
 * recherche filtrée et paginée.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class IncidentPersistenceIntegrationTest {

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private IncidentReferenceGenerator referenceGenerator;
    @Autowired
    private AlertRepository alertRepository;

    private Incident newIncident(Severity severity) {
        return Incident.open(referenceGenerator.nextReference(),
                "Brute force sur srv-web-01", "10 échecs SSH", severity);
    }

    @Test
    void generatesMonotonicReferencesInTheExpectedFormat() {
        String first = referenceGenerator.nextReference();
        String second = referenceGenerator.nextReference();

        assertThat(first).matches("INC-\\d{4}-\\d{4}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void savesAndReloadsAnIncident() {
        Incident saved = incidentRepository.save(newIncident(Severity.HIGH));

        Incident reloaded = incidentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReference()).isEqualTo(saved.getReference());
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(reloaded.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(incidentRepository.findByReference(saved.getReference())).isPresent();
    }

    @Test
    void linksAlertsIdempotentlyAndUnlinks() {
        Incident incident = incidentRepository.save(newIncident(Severity.MEDIUM));
        UUID alertId = ingestAlert();

        incidentRepository.linkAlert(incident.getId(), alertId);
        incidentRepository.linkAlert(incident.getId(), alertId); // rejoué : idempotent
        assertThat(incidentRepository.findLinkedAlertIds(incident.getId())).containsExactly(alertId);

        incidentRepository.unlinkAlert(incident.getId(), alertId);
        assertThat(incidentRepository.findLinkedAlertIds(incident.getId())).isEmpty();
    }

    @Test
    void recordsAndReadsTheTimeline() {
        Incident incident = incidentRepository.save(newIncident(Severity.LOW));

        incidentRepository.addTimelineEntry(IncidentTimelineEntry.of(
                incident.getId(), IncidentEventType.CREATED, "Incident ouvert", "admin"));
        incidentRepository.addTimelineEntry(IncidentTimelineEntry.of(
                incident.getId(), IncidentEventType.NOTE, "Analyse en cours", "analyst01"));

        List<IncidentTimelineEntry> timeline = incidentRepository.findTimeline(incident.getId());
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).type()).isEqualTo(IncidentEventType.CREATED);
        assertThat(timeline.get(1).message()).isEqualTo("Analyse en cours");
    }

    @Test
    void searchFiltersBySeverityAndPaginates() {
        for (int i = 0; i < 3; i++) {
            incidentRepository.save(newIncident(Severity.CRITICAL));
        }
        incidentRepository.save(newIncident(Severity.LOW));

        PageResult<Incident> page = incidentRepository.search(
                new IncidentQuery(null, Severity.CRITICAL, null, PageQuery.of(0, 2)));

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.items()).allSatisfy(i ->
                assertThat(i.getSeverity()).isEqualTo(Severity.CRITICAL));
    }

    private UUID ingestAlert() {
        Alert alert = alertRepository.save(Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-inc-" + UUID.randomUUID())
                .title("alerte liée à un incident")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .mitreTechniques(List.of("T1110"))
                .rawPayload("{}")
                .build()));
        return alert.getId();
    }
}
