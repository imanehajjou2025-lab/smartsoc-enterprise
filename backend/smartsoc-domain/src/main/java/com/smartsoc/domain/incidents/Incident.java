package com.smartsoc.domain.incidents;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Incident SOC : regroupe une ou plusieurs alertes et porte le travail de
 * l'analyste (cycle de vie, affectation). Entité de domaine pure : les
 * invariants et les transitions sont gardés ici ; la timeline et les liens
 * d'alertes sont persistés séparément (voir IncidentRepository).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Incident {

    private final UUID id;
    private final String reference;
    private String title;
    private String description;
    private final Severity severity;
    private IncidentStatus status;
    private String assigneeUsername;
    private final Instant openedAt;
    private Instant closedAt;

    /** Ouverture d'un incident ; la référence lisible est fournie par l'appelant. */
    public static Incident open(String reference, String title, String description,
                                Severity severity) {
        requireNonBlank(reference, "reference");
        requireNonBlank(title, "title");
        if (severity == null) {
            throw new BusinessRuleViolationException("INVALID_INCIDENT",
                    "An incident must have a severity");
        }
        return Incident.builder()
                .id(UUID.randomUUID())
                .reference(reference)
                .title(title.trim())
                .description(description)
                .severity(severity)
                .status(IncidentStatus.OPEN)
                .openedAt(Instant.now())
                .build();
    }

    public void transitionTo(IncidentStatus newStatus) {
        if (newStatus == null || !status.allowedTransitions().contains(newStatus)) {
            throw new BusinessRuleViolationException("INVALID_INCIDENT_TRANSITION",
                    "Cannot transition incident from %s to %s".formatted(status, newStatus));
        }
        this.status = newStatus;
        if (newStatus == IncidentStatus.CLOSED) {
            this.closedAt = Instant.now();
        }
    }

    public void assignTo(String username) {
        requireNonBlank(username, "assignee");
        this.assigneeUsername = username.trim().toLowerCase();
    }

    public void unassign() {
        this.assigneeUsername = null;
    }

    public void rename(String newTitle) {
        requireNonBlank(newTitle, "title");
        this.title = newTitle.trim();
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_INCIDENT",
                    "Field '%s' must not be blank".formatted(field));
        }
    }
}
