package com.smartsoc.application.investigations;

import com.smartsoc.application.investigations.CaseService.CreateCaseCommand;
import com.smartsoc.application.investigations.CaseService.UpdateTaskCommand;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.investigations.Case;
import com.smartsoc.domain.investigations.CaseEventType;
import com.smartsoc.domain.investigations.CaseReferenceGenerator;
import com.smartsoc.domain.investigations.CaseRepository;
import com.smartsoc.domain.investigations.CaseTask;
import com.smartsoc.domain.investigations.CaseTimelineEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration des cas : chaque action inscrit sa trace de timeline avec
 * le bon auteur et le bon type ; la garde d'immutabilité du cas clôturé
 * couvre aussi les opérations satellites (liaisons, tâches, notes).
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private CaseReferenceGenerator referenceGenerator;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AlertRepository alertRepository;

    private CaseService service;

    @BeforeEach
    void setUp() {
        service = new CaseService(caseRepository, referenceGenerator,
                incidentRepository, alertRepository);
    }

    private Case openCase() {
        return Case.open("CASE-2026-0001", "Campagne de phishing", null, Severity.HIGH);
    }

    private Case closedCase() {
        Case investigation = openCase();
        investigation.close("Vrai positif confirmé");
        return investigation;
    }

    private CaseTimelineEntry lastTimelineEntry() {
        ArgumentCaptor<CaseTimelineEntry> captor =
                ArgumentCaptor.forClass(CaseTimelineEntry.class);
        verify(caseRepository).addTimelineEntry(captor.capture());
        return captor.getValue();
    }

    @Test
    void createCaseRecordsCreationWithAuthor() {
        when(referenceGenerator.nextReference()).thenReturn("CASE-2026-0001");
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Case created = service.createCase(
                new CreateCaseCommand("Campagne de phishing", null, Severity.HIGH), "analyst01");

        assertThat(created.getReference()).isEqualTo("CASE-2026-0001");
        CaseTimelineEntry entry = lastTimelineEntry();
        assertThat(entry.type()).isEqualTo(CaseEventType.CREATED);
        assertThat(entry.author()).isEqualTo("analyst01");
    }

    @Test
    void openFromIncidentInheritsAndLinks() {
        Incident incident = Incident.open("INC-2026-0007", "Brute force", "desc", Severity.CRITICAL);
        when(incidentRepository.findById(any())).thenReturn(Optional.of(incident));
        when(referenceGenerator.nextReference()).thenReturn("CASE-2026-0002");
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Case created = service.openFromIncident(incident.getId(), "analyst01");

        assertThat(created.getTitle()).isEqualTo("Brute force");
        assertThat(created.getPriority()).isEqualTo(Severity.CRITICAL);
        verify(caseRepository).linkIncident(created.getId(), incident.getId());
        ArgumentCaptor<CaseTimelineEntry> captor =
                ArgumentCaptor.forClass(CaseTimelineEntry.class);
        verify(caseRepository, org.mockito.Mockito.times(2)).addTimelineEntry(captor.capture());
        assertThat(captor.getAllValues()).extracting(CaseTimelineEntry::type)
                .containsExactly(CaseEventType.CREATED, CaseEventType.INCIDENT_LINKED);
    }

    @Test
    void followUpTracesBothCases() {
        Case origin = closedCase();
        when(caseRepository.findById(origin.getId())).thenReturn(Optional.of(origin));
        when(referenceGenerator.nextReference()).thenReturn("CASE-2026-0003");
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Case followUp = service.openFollowUp(origin.getId(),
                new CreateCaseCommand("Reprise", null, Severity.HIGH), "analyst01");

        assertThat(followUp.getOriginCaseId()).isEqualTo(origin.getId());
        ArgumentCaptor<CaseTimelineEntry> captor =
                ArgumentCaptor.forClass(CaseTimelineEntry.class);
        verify(caseRepository, org.mockito.Mockito.times(2)).addTimelineEntry(captor.capture());
        assertThat(captor.getAllValues()).extracting(CaseTimelineEntry::caseId)
                .containsExactly(followUp.getId(), origin.getId());
        assertThat(captor.getAllValues().get(1).type())
                .isEqualTo(CaseEventType.FOLLOW_UP_OPENED);
    }

    @Test
    void closeRecordsConclusion() {
        Case investigation = openCase();
        when(caseRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Case closed = service.close(investigation.getId(), "Faux positif", "analyst01");

        assertThat(closed.getConclusion()).isEqualTo("Faux positif");
        CaseTimelineEntry entry = lastTimelineEntry();
        assertThat(entry.type()).isEqualTo(CaseEventType.CLOSED);
        assertThat(entry.message()).contains("Faux positif");
    }

    @Test
    void satelliteOperationsAreRejectedOnAClosedCase() {
        Case investigation = closedCase();
        when(caseRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        UUID caseId = investigation.getId();
        UUID any = UUID.randomUUID();

        assertThatThrownBy(() -> service.addNote(caseId, "note", "a"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> service.linkIncident(caseId, any, "a"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.linkAlert(caseId, any, "a"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.addTask(caseId, "titre", "a"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.updateTask(caseId, any,
                new UpdateTaskCommand(null, CaseTask.Status.DONE, null), "a"))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(caseRepository, never()).addTimelineEntry(any());
        verify(caseRepository, never()).saveTask(any());
    }

    @Test
    void completingATaskRecordsTaskCompleted() {
        Case investigation = openCase();
        CaseTask task = CaseTask.create(investigation.getId(), "Analyser les entêtes");
        when(caseRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(caseRepository.findTaskById(task.getId())).thenReturn(Optional.of(task));
        when(caseRepository.saveTask(any())).thenAnswer(inv -> inv.getArgument(0));

        CaseTask updated = service.updateTask(investigation.getId(), task.getId(),
                new UpdateTaskCommand(null, CaseTask.Status.DONE, null), "analyst01");

        assertThat(updated.getStatus()).isEqualTo(CaseTask.Status.DONE);
        assertThat(lastTimelineEntry().type()).isEqualTo(CaseEventType.TASK_COMPLETED);
    }

    @Test
    void updatingATaskFromAnotherCaseIs404() {
        Case investigation = openCase();
        CaseTask foreignTask = CaseTask.create(UUID.randomUUID(), "Autre cas");
        when(caseRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(caseRepository.findTaskById(foreignTask.getId()))
                .thenReturn(Optional.of(foreignTask));

        assertThatThrownBy(() -> service.updateTask(investigation.getId(), foreignTask.getId(),
                new UpdateTaskCommand("nouveau titre", null, null), "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unknownCaseIs404() {
        UUID unknown = UUID.randomUUID();
        when(caseRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCase(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
