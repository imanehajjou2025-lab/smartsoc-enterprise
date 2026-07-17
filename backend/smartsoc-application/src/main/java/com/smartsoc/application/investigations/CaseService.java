package com.smartsoc.application.investigations;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.investigations.Case;
import com.smartsoc.domain.investigations.CaseEventType;
import com.smartsoc.domain.investigations.CaseQuery;
import com.smartsoc.domain.investigations.CaseReferenceGenerator;
import com.smartsoc.domain.investigations.CaseRepository;
import com.smartsoc.domain.investigations.CaseStatus;
import com.smartsoc.domain.investigations.CaseTask;
import com.smartsoc.domain.investigations.CaseTimelineEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage des investigations. Chaque action métier enregistre une
 * entrée de timeline avec l'analyste à l'origine comme auteur — l'audit
 * de l'enquête globale. Les règles de cycle de vie appartiennent au
 * domaine ; ce service orchestre, et étend la garde d'immutabilité du
 * cas clôturé aux opérations satellites (liaisons, tâches, notes) qui
 * ne passent pas par l'entité Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseRepository caseRepository;
    private final CaseReferenceGenerator referenceGenerator;
    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;

    public record CreateCaseCommand(String title, String description, Severity priority) {
    }

    public record UpdateTaskCommand(String title, CaseTask.Status status, String assignee) {
    }

    @Transactional
    public Case createCase(CreateCaseCommand command, String author) {
        Case investigation = Case.open(referenceGenerator.nextReference(),
                command.title(), command.description(), command.priority());
        Case saved = caseRepository.save(investigation);
        record(saved.getId(), CaseEventType.CREATED,
                "Cas %s ouvert".formatted(saved.getReference()), author);
        log.info("Case {} created by {}", saved.getReference(), author);
        return saved;
    }

    /** Ouvre un cas d'enquête à partir d'un incident et l'y lie. */
    @Transactional
    public Case openFromIncident(UUID incidentId, String author) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        Case investigation = Case.open(referenceGenerator.nextReference(),
                incident.getTitle(), incident.getDescription(), incident.getSeverity());
        Case saved = caseRepository.save(investigation);
        record(saved.getId(), CaseEventType.CREATED,
                "Cas %s ouvert depuis l'incident %s"
                        .formatted(saved.getReference(), incident.getReference()), author);

        caseRepository.linkIncident(saved.getId(), incidentId);
        record(saved.getId(), CaseEventType.INCIDENT_LINKED,
                "Incident lié : %s".formatted(incident.getReference()), author);
        return saved;
    }

    /**
     * Reprise d'une enquête clôturée : nouveau cas référençant l'origine
     * (le domaine exige une origine CLOSED). Le cas d'origine reste
     * immuable — seule sa timeline reçoit la trace du suivi.
     */
    @Transactional
    public Case openFollowUp(UUID originCaseId, CreateCaseCommand command, String author) {
        Case origin = requireCase(originCaseId);
        Case followUp = Case.openFollowUp(origin, referenceGenerator.nextReference(),
                command.title(), command.description(), command.priority());
        Case saved = caseRepository.save(followUp);
        record(saved.getId(), CaseEventType.CREATED,
                "Cas %s ouvert en suivi du cas %s"
                        .formatted(saved.getReference(), origin.getReference()), author);
        record(originCaseId, CaseEventType.FOLLOW_UP_OPENED,
                "Cas de suivi ouvert : %s".formatted(saved.getReference()), author);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Case> search(CaseQuery query) {
        return caseRepository.search(query);
    }

    @Transactional(readOnly = true)
    public Case getCase(UUID id) {
        return requireCase(id);
    }

    @Transactional(readOnly = true)
    public List<CaseTimelineEntry> getTimeline(UUID id) {
        requireCase(id);
        return caseRepository.findTimeline(id);
    }

    @Transactional(readOnly = true)
    public List<CaseTask> getTasks(UUID id) {
        requireCase(id);
        return caseRepository.findTasks(id);
    }

    @Transactional(readOnly = true)
    public List<Case> getFollowUps(UUID id) {
        requireCase(id);
        return caseRepository.findFollowUps(id);
    }

    @Transactional(readOnly = true)
    public List<Incident> getLinkedIncidents(UUID id) {
        requireCase(id);
        return caseRepository.findLinkedIncidentIds(id).stream()
                .map(incidentRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Alert> getLinkedAlerts(UUID id) {
        requireCase(id);
        return caseRepository.findLinkedAlertIds(id).stream()
                .map(alertRepository::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional
    public Case changeStatus(UUID id, CaseStatus newStatus, String author) {
        Case investigation = requireCase(id);
        CaseStatus previous = investigation.getStatus();
        investigation.transitionTo(newStatus);
        Case saved = caseRepository.save(investigation);
        record(id, CaseEventType.STATUS_CHANGED,
                "Statut : %s → %s".formatted(previous, newStatus), author);
        return saved;
    }

    @Transactional
    public Case close(UUID id, String conclusion, String author) {
        Case investigation = requireCase(id);
        investigation.close(conclusion);
        Case saved = caseRepository.save(investigation);
        record(id, CaseEventType.CLOSED,
                "Cas clôturé : %s".formatted(saved.getConclusion()), author);
        log.info("Case {} closed by {}", saved.getReference(), author);
        return saved;
    }

    @Transactional
    public Case assign(UUID id, String assignee, String author) {
        Case investigation = requireCase(id);
        investigation.assignTo(assignee);
        Case saved = caseRepository.save(investigation);
        record(id, CaseEventType.ASSIGNED,
                "Assigné à %s".formatted(saved.getAssigneeUsername()), author);
        return saved;
    }

    @Transactional
    public Case unassign(UUID id, String author) {
        Case investigation = requireCase(id);
        investigation.unassign();
        Case saved = caseRepository.save(investigation);
        record(id, CaseEventType.UNASSIGNED, "Désassigné", author);
        return saved;
    }

    @Transactional
    public void addNote(UUID id, String message, String author) {
        requireOpenCase(id);
        record(id, CaseEventType.NOTE_ADDED, message, author);
    }

    @Transactional
    public void linkIncident(UUID id, UUID incidentId, String author) {
        requireOpenCase(id);
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
        caseRepository.linkIncident(id, incidentId);
        record(id, CaseEventType.INCIDENT_LINKED,
                "Incident lié : %s".formatted(incident.getReference()), author);
    }

    @Transactional
    public void unlinkIncident(UUID id, UUID incidentId, String author) {
        requireOpenCase(id);
        caseRepository.unlinkIncident(id, incidentId);
        record(id, CaseEventType.INCIDENT_UNLINKED,
                "Incident délié : %s".formatted(incidentId), author);
    }

    @Transactional
    public void linkAlert(UUID id, UUID alertId, String author) {
        requireOpenCase(id);
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));
        caseRepository.linkAlert(id, alertId);
        record(id, CaseEventType.ALERT_LINKED,
                "Alerte liée : %s / %s".formatted(alert.getSource(), alert.getExternalId()), author);
    }

    @Transactional
    public void unlinkAlert(UUID id, UUID alertId, String author) {
        requireOpenCase(id);
        caseRepository.unlinkAlert(id, alertId);
        record(id, CaseEventType.ALERT_UNLINKED,
                "Alerte déliée : %s".formatted(alertId), author);
    }

    @Transactional
    public CaseTask addTask(UUID id, String title, String author) {
        requireOpenCase(id);
        CaseTask saved = caseRepository.saveTask(CaseTask.create(id, title));
        record(id, CaseEventType.TASK_ADDED, "Tâche ajoutée : %s".formatted(saved.getTitle()), author);
        return saved;
    }

    @Transactional
    public CaseTask updateTask(UUID id, UUID taskId, UpdateTaskCommand command, String author) {
        requireOpenCase(id);
        CaseTask task = caseRepository.findTaskById(taskId)
                .filter(t -> t.getCaseId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("CaseTask", taskId));

        if (command.title() != null) {
            task.rename(command.title());
        }
        if (command.assignee() != null) {
            if (command.assignee().isBlank()) {
                task.unassign();
            } else {
                task.assignTo(command.assignee());
            }
        }
        boolean completed = false;
        if (command.status() != null && command.status() != task.getStatus()) {
            task.updateStatus(command.status());
            completed = command.status() == CaseTask.Status.DONE;
        }
        CaseTask saved = caseRepository.saveTask(task);
        record(id, completed ? CaseEventType.TASK_COMPLETED : CaseEventType.TASK_UPDATED,
                "Tâche %s : %s".formatted(completed ? "terminée" : "mise à jour",
                        saved.getTitle()), author);
        return saved;
    }

    private void record(UUID caseId, CaseEventType type, String message, String author) {
        caseRepository.addTimelineEntry(CaseTimelineEntry.of(caseId, type, message, author));
    }

    private Case requireCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case", id));
    }

    /** Garde d'immutabilité pour les opérations qui ne passent pas par Case. */
    private Case requireOpenCase(UUID id) {
        Case investigation = requireCase(id);
        if (investigation.getStatus() == CaseStatus.CLOSED) {
            throw new BusinessRuleViolationException("CASE_CLOSED",
                    "A closed case is immutable; open a follow-up case instead");
        }
        return investigation;
    }
}
