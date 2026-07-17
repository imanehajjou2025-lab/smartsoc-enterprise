package com.smartsoc.domain.investigations;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tâche d'analyse d'un cas d'investigation (élément de checklist).
 * Les transitions de statut sont volontairement libres — une checklist
 * se coche et se décoche — mais completedAt trace fidèlement la fin.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CaseTask {

    /** Avancement d'une tâche de checklist. */
    public enum Status {
        TODO,
        IN_PROGRESS,
        DONE
    }

    private final UUID id;
    private final UUID caseId;
    private String title;
    private Status status;
    private String assigneeUsername;
    private final Instant createdAt;
    private Instant completedAt;

    public static CaseTask create(UUID caseId, String title) {
        if (caseId == null) {
            throw new BusinessRuleViolationException("INVALID_CASE_TASK",
                    "A task must belong to a case");
        }
        requireNonBlank(title);
        return CaseTask.builder()
                .id(UUID.randomUUID())
                .caseId(caseId)
                .title(title.trim())
                .status(Status.TODO)
                .createdAt(Instant.now())
                .build();
    }

    public void updateStatus(Status newStatus) {
        if (newStatus == null) {
            throw new BusinessRuleViolationException("INVALID_CASE_TASK",
                    "A task must have a status");
        }
        this.status = newStatus;
        this.completedAt = newStatus == Status.DONE ? Instant.now() : null;
    }

    public void rename(String newTitle) {
        requireNonBlank(newTitle);
        this.title = newTitle.trim();
    }

    public void assignTo(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_CASE_TASK",
                    "Field 'assignee' must not be blank");
        }
        this.assigneeUsername = username.trim().toLowerCase();
    }

    public void unassign() {
        this.assigneeUsername = null;
    }

    private static void requireNonBlank(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_CASE_TASK",
                    "Field 'title' must not be blank");
        }
    }
}
