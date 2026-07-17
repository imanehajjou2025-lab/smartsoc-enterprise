package com.smartsoc.domain.investigations;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Cas d'investigation (module Investigations) : le dossier d'enquête
 * au-dessus des incidents — il regroupe incidents et alertes, porte une
 * checklist de tâches d'analyse et se conclut formellement. Entité de
 * domaine pure : invariants et transitions gardés ici ; timeline, tâches
 * et liaisons sont persistées séparément (voir CaseRepository).
 *
 * Un cas CLOSED est immuable ; la reprise d'enquête passe par un cas de
 * suivi (openFollowUp) référençant le cas d'origine via originCaseId.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Case {

    private final UUID id;
    private final String reference;
    private String title;
    private String description;
    private final Severity priority;
    private CaseStatus status;
    private String assigneeUsername;
    private String conclusion;
    private final UUID originCaseId;
    private final Instant openedAt;
    private Instant closedAt;

    /** Ouverture d'un cas ; la référence lisible est fournie par l'appelant. */
    public static Case open(String reference, String title, String description,
                            Severity priority) {
        return newCase(reference, title, description, priority, null);
    }

    /**
     * Ouverture d'un cas de suivi : SEULE voie de reprise d'une enquête
     * clôturée — le cas d'origine doit être CLOSED et n'est jamais modifié.
     */
    public static Case openFollowUp(Case origin, String reference, String title,
                                    String description, Severity priority) {
        if (origin == null || origin.getStatus() != CaseStatus.CLOSED) {
            throw new BusinessRuleViolationException("FOLLOW_UP_REQUIRES_CLOSED_CASE",
                    "A follow-up case can only be opened from a CLOSED case");
        }
        return newCase(reference, title, description, priority, origin.getId());
    }

    /**
     * Transition de cycle de vie hors clôture. La clôture passe
     * exclusivement par close(conclusion) : fermer sans conclure est
     * interdit par conception.
     */
    public void transitionTo(CaseStatus newStatus) {
        if (newStatus == CaseStatus.CLOSED) {
            throw new BusinessRuleViolationException("CASE_CONCLUSION_REQUIRED",
                    "Closing a case requires a conclusion");
        }
        if (newStatus == null || !status.allowedTransitions().contains(newStatus)) {
            throw new BusinessRuleViolationException("INVALID_CASE_TRANSITION",
                    "Cannot transition case from %s to %s".formatted(status, newStatus));
        }
        this.status = newStatus;
    }

    /** Clôture formelle : conclusion obligatoire, CLOSED définitif. */
    public void close(String conclusion) {
        if (conclusion == null || conclusion.isBlank()) {
            throw new BusinessRuleViolationException("CASE_CONCLUSION_REQUIRED",
                    "Closing a case requires a conclusion");
        }
        if (!status.allowedTransitions().contains(CaseStatus.CLOSED)) {
            throw new BusinessRuleViolationException("INVALID_CASE_TRANSITION",
                    "Cannot transition case from %s to %s".formatted(status, CaseStatus.CLOSED));
        }
        this.status = CaseStatus.CLOSED;
        this.conclusion = conclusion.trim();
        this.closedAt = Instant.now();
    }

    public void assignTo(String username) {
        requireOpen();
        requireNonBlank(username, "assignee");
        this.assigneeUsername = username.trim().toLowerCase();
    }

    public void unassign() {
        requireOpen();
        this.assigneeUsername = null;
    }

    public void rename(String newTitle) {
        requireOpen();
        requireNonBlank(newTitle, "title");
        this.title = newTitle.trim();
    }

    public void updateDescription(String newDescription) {
        requireOpen();
        this.description = newDescription;
    }

    private static Case newCase(String reference, String title, String description,
                                Severity priority, UUID originCaseId) {
        requireNonBlank(reference, "reference");
        requireNonBlank(title, "title");
        if (priority == null) {
            throw new BusinessRuleViolationException("INVALID_CASE",
                    "A case must have a priority");
        }
        return Case.builder()
                .id(UUID.randomUUID())
                .reference(reference)
                .title(title.trim())
                .description(description)
                .priority(priority)
                .status(CaseStatus.OPEN)
                .originCaseId(originCaseId)
                .openedAt(Instant.now())
                .build();
    }

    /** Un dossier clôturé est immuable (pièce d'audit). */
    private void requireOpen() {
        if (status == CaseStatus.CLOSED) {
            throw new BusinessRuleViolationException("CASE_CLOSED",
                    "A closed case is immutable; open a follow-up case instead");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_CASE",
                    "Field '%s' must not be blank".formatted(field));
        }
    }
}
