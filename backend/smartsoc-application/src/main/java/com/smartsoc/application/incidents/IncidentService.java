package com.smartsoc.application.incidents;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentEventType;
import com.smartsoc.domain.incidents.IncidentQuery;
import com.smartsoc.domain.incidents.IncidentReferenceGenerator;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.incidents.IncidentStatus;
import com.smartsoc.domain.incidents.IncidentTimelineEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des incidents. Chaque action métier enregistre une entrée de
 * timeline (trace d'investigation) avec l'analyste à l'origine comme auteur.
 * Les règles de transition appartiennent au domaine ; ce service orchestre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentReferenceGenerator referenceGenerator;
    private final AlertRepository alertRepository;

    public record CreateIncidentCommand(String title, String description, Severity severity) {
    }

    @Transactional
    public Incident createIncident(CreateIncidentCommand command, String author) {
        Incident incident = Incident.open(
                referenceGenerator.nextReference(),
                command.title(), command.description(), command.severity());
        Incident saved = incidentRepository.save(incident);
        recordEvent(saved.getId(), IncidentEventType.CREATED,
                "Incident %s ouvert".formatted(saved.getReference()), author);
        log.info("Incident {} created by {}", saved.getReference(), author);
        return saved;
    }

    /** Escalade : crée un incident à partir d'une alerte et l'y lie. */
    @Transactional
    public Incident escalateFromAlert(UUID alertId, String author) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));

        Incident incident = Incident.open(
                referenceGenerator.nextReference(),
                alert.getTitle(), alert.getDescription(), alert.getSeverity());
        Incident saved = incidentRepository.save(incident);
        recordEvent(saved.getId(), IncidentEventType.CREATED,
                "Incident %s ouvert depuis l'alerte %s".formatted(saved.getReference(), alert.getSource()),
                author);

        incidentRepository.linkAlert(saved.getId(), alertId);
        recordEvent(saved.getId(), IncidentEventType.ALERT_LINKED,
                "Alerte liée : %s / %s".formatted(alert.getSource(), alert.getExternalId()), author);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Incident> search(IncidentQuery query) {
        return incidentRepository.search(query);
    }

    @Transactional(readOnly = true)
    public Incident getIncident(UUID id) {
        return requireIncident(id);
    }

    @Transactional(readOnly = true)
    public List<IncidentTimelineEntry> getTimeline(UUID id) {
        requireIncident(id);
        return incidentRepository.findTimeline(id);
    }

    @Transactional(readOnly = true)
    public List<Alert> getLinkedAlerts(UUID id) {
        requireIncident(id);
        return incidentRepository.findLinkedAlertIds(id).stream()
                .map(alertRepository::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Transactional
    public Incident changeStatus(UUID id, IncidentStatus newStatus, String author) {
        Incident incident = requireIncident(id);
        IncidentStatus previous = incident.getStatus();
        incident.transitionTo(newStatus);
        Incident saved = incidentRepository.save(incident);
        recordEvent(id, IncidentEventType.STATUS_CHANGED,
                "Statut : %s → %s".formatted(previous, newStatus), author);
        return saved;
    }

    @Transactional
    public Incident assign(UUID id, String assignee, String author) {
        Incident incident = requireIncident(id);
        incident.assignTo(assignee);
        Incident saved = incidentRepository.save(incident);
        recordEvent(id, IncidentEventType.ASSIGNED,
                "Assigné à %s".formatted(saved.getAssigneeUsername()), author);
        return saved;
    }

    @Transactional
    public Incident unassign(UUID id, String author) {
        Incident incident = requireIncident(id);
        incident.unassign();
        Incident saved = incidentRepository.save(incident);
        recordEvent(id, IncidentEventType.UNASSIGNED, "Désassigné", author);
        return saved;
    }

    @Transactional
    public void addNote(UUID id, String message, String author) {
        requireIncident(id);
        recordEvent(id, IncidentEventType.NOTE, message, author);
    }

    @Transactional
    public void linkAlert(UUID id, UUID alertId, String author) {
        requireIncident(id);
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));
        incidentRepository.linkAlert(id, alertId);
        recordEvent(id, IncidentEventType.ALERT_LINKED,
                "Alerte liée : %s / %s".formatted(alert.getSource(), alert.getExternalId()), author);
    }

    @Transactional
    public void unlinkAlert(UUID id, UUID alertId, String author) {
        requireIncident(id);
        incidentRepository.unlinkAlert(id, alertId);
        recordEvent(id, IncidentEventType.ALERT_UNLINKED, "Alerte déliée : %s".formatted(alertId), author);
    }

    private void recordEvent(UUID incidentId, IncidentEventType type, String message, String author) {
        incidentRepository.addTimelineEntry(
                IncidentTimelineEntry.of(incidentId, type, message, author));
    }

    private Incident requireIncident(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }
}
