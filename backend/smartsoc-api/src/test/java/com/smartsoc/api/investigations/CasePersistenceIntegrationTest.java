package com.smartsoc.api.investigations;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentReferenceGenerator;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.investigations.Case;
import com.smartsoc.domain.investigations.CaseEventType;
import com.smartsoc.domain.investigations.CaseQuery;
import com.smartsoc.domain.investigations.CaseReferenceGenerator;
import com.smartsoc.domain.investigations.CaseRepository;
import com.smartsoc.domain.investigations.CaseStatus;
import com.smartsoc.domain.investigations.CaseTask;
import com.smartsoc.domain.investigations.CaseTimelineEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance des cas d'investigation sur un vrai PostgreSQL : migration V5
 * appliquée + mapping validé par Hibernate (ddl-auto: validate), référence
 * par séquence, aller-retour (clôture incluse — contrainte ck_cases_closure),
 * chaîne de suivi, liaisons incidents/alertes idempotentes, checklist,
 * timeline, recherche filtrée et paginée.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class CasePersistenceIntegrationTest {

    @Autowired
    private CaseRepository caseRepository;
    @Autowired
    private CaseReferenceGenerator referenceGenerator;
    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private IncidentReferenceGenerator incidentReferenceGenerator;
    @Autowired
    private AlertRepository alertRepository;

    private Case newCase(Severity priority) {
        return Case.open(referenceGenerator.nextReference(),
                "Campagne de phishing ciblée", "Incidents corrélés", priority);
    }

    @Test
    void generatesMonotonicReferencesInTheExpectedFormat() {
        String first = referenceGenerator.nextReference();
        String second = referenceGenerator.nextReference();

        assertThat(first).matches("CASE-\\d{4}-\\d{4}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void savesAndReloadsAnOpenCase() {
        Case saved = caseRepository.save(newCase(Severity.HIGH));

        Case reloaded = caseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReference()).isEqualTo(saved.getReference());
        assertThat(reloaded.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(reloaded.getPriority()).isEqualTo(Severity.HIGH);
        assertThat(reloaded.getOriginCaseId()).isNull();
        assertThat(reloaded.getConclusion()).isNull();
        assertThat(caseRepository.findByReference(saved.getReference())).isPresent();
    }

    @Test
    void persistsClosureWithConclusionAndFollowUpChain() {
        Case origin = newCase(Severity.MEDIUM);
        origin.close("Faux positif : scanner interne autorisé");
        // La contrainte ck_cases_closure exige conclusion + closed_at : OK.
        caseRepository.save(origin);

        Case reloadedOrigin = caseRepository.findById(origin.getId()).orElseThrow();
        assertThat(reloadedOrigin.getStatus()).isEqualTo(CaseStatus.CLOSED);
        assertThat(reloadedOrigin.getConclusion()).contains("Faux positif");
        assertThat(reloadedOrigin.getClosedAt()).isNotNull();

        Case followUp = caseRepository.save(Case.openFollowUp(reloadedOrigin,
                referenceGenerator.nextReference(), "Reprise : nouvelle vague",
                null, Severity.MEDIUM));

        List<Case> followUps = caseRepository.findFollowUps(origin.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getId()).isEqualTo(followUp.getId());
        assertThat(followUps.get(0).getOriginCaseId()).isEqualTo(origin.getId());
    }

    @Test
    void linksIncidentsAndAlertsIdempotentlyAndUnlinks() {
        Case investigation = caseRepository.save(newCase(Severity.HIGH));
        UUID incidentId = persistIncident();
        UUID alertId = persistAlert();

        caseRepository.linkIncident(investigation.getId(), incidentId);
        caseRepository.linkIncident(investigation.getId(), incidentId); // rejoué : idempotent
        caseRepository.linkAlert(investigation.getId(), alertId);
        caseRepository.linkAlert(investigation.getId(), alertId); // rejoué : idempotent

        assertThat(caseRepository.findLinkedIncidentIds(investigation.getId()))
                .containsExactly(incidentId);
        assertThat(caseRepository.findLinkedAlertIds(investigation.getId()))
                .containsExactly(alertId);

        caseRepository.unlinkIncident(investigation.getId(), incidentId);
        caseRepository.unlinkAlert(investigation.getId(), alertId);
        assertThat(caseRepository.findLinkedIncidentIds(investigation.getId())).isEmpty();
        assertThat(caseRepository.findLinkedAlertIds(investigation.getId())).isEmpty();
    }

    @Test
    void savesTasksInChecklistOrderAndTracksCompletion() {
        Case investigation = caseRepository.save(newCase(Severity.LOW));

        CaseTask first = caseRepository.saveTask(
                CaseTask.create(investigation.getId(), "Analyser les entêtes"));
        CaseTask second = caseRepository.saveTask(
                CaseTask.create(investigation.getId(), "Vérifier les IOC"));

        second.updateStatus(CaseTask.Status.DONE);
        caseRepository.saveTask(second);

        List<CaseTask> tasks = caseRepository.findTasks(investigation.getId());
        assertThat(tasks).extracting(CaseTask::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(tasks.get(1).getStatus()).isEqualTo(CaseTask.Status.DONE);
        assertThat(tasks.get(1).getCompletedAt()).isNotNull();
        assertThat(caseRepository.findTaskById(first.getId())).isPresent();
    }

    @Test
    void recordsAndReadsTheTimeline() {
        Case investigation = caseRepository.save(newCase(Severity.LOW));

        caseRepository.addTimelineEntry(CaseTimelineEntry.of(
                investigation.getId(), CaseEventType.CREATED, "Cas ouvert", "admin"));
        caseRepository.addTimelineEntry(CaseTimelineEntry.of(
                investigation.getId(), CaseEventType.NOTE_ADDED, "Analyse en cours", "analyst01"));

        List<CaseTimelineEntry> timeline = caseRepository.findTimeline(investigation.getId());
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).type()).isEqualTo(CaseEventType.CREATED);
        assertThat(timeline.get(1).message()).isEqualTo("Analyse en cours");
    }

    @Test
    void searchFiltersByPriorityStatusAssigneeAndPaginates() {
        for (int i = 0; i < 3; i++) {
            caseRepository.save(newCase(Severity.CRITICAL));
        }
        Case assigned = newCase(Severity.HIGH);
        assigned.transitionTo(CaseStatus.IN_PROGRESS);
        assigned.assignTo("analyst-" + UUID.randomUUID().toString().substring(0, 8));
        String assignee = assigned.getAssigneeUsername();
        caseRepository.save(assigned);

        PageResult<Case> byPriority = caseRepository.search(
                new CaseQuery(null, Severity.CRITICAL, null, PageQuery.of(0, 2)));
        assertThat(byPriority.items()).hasSize(2);
        assertThat(byPriority.totalElements()).isGreaterThanOrEqualTo(3);
        assertThat(byPriority.items()).allSatisfy(c ->
                assertThat(c.getPriority()).isEqualTo(Severity.CRITICAL));

        PageResult<Case> byAssignee = caseRepository.search(
                new CaseQuery(CaseStatus.IN_PROGRESS, null, assignee, PageQuery.of(0, 10)));
        assertThat(byAssignee.items()).hasSize(1);
        assertThat(byAssignee.items().get(0).getAssigneeUsername()).isEqualTo(assignee);
    }

    private UUID persistIncident() {
        Incident incident = incidentRepository.save(Incident.open(
                incidentReferenceGenerator.nextReference(),
                "Incident lié à un cas", null, Severity.HIGH));
        return incident.getId();
    }

    private UUID persistAlert() {
        Alert alert = alertRepository.save(Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-case-" + UUID.randomUUID())
                .title("alerte liée à un cas")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .mitreTechniques(List.of("T1566"))
                .rawPayload("{}")
                .build()));
        return alert.getId();
    }
}
