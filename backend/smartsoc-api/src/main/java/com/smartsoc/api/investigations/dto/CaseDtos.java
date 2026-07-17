package com.smartsoc.api.investigations.dto;

import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.IncidentResponse;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.investigations.CaseEventType;
import com.smartsoc.domain.investigations.CaseStatus;
import com.smartsoc.domain.investigations.CaseTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats REST du contexte investigations (entité métier : Case). */
public final class CaseDtos {

    private CaseDtos() {
    }

    public record CreateCaseRequest(
            @NotBlank @Size(max = 500) String title,
            String description,
            @NotNull Severity priority) {
    }

    public record UpdateStatusRequest(@NotNull CaseStatus status) {
    }

    /** La clôture est un acte formel : la conclusion est obligatoire. */
    public record CloseCaseRequest(@NotBlank String conclusion) {
    }

    public record AssigneeRequest(@NotBlank @Size(max = 50) String username) {
    }

    public record NoteRequest(@NotBlank String message) {
    }

    public record CreateTaskRequest(@NotBlank @Size(max = 500) String title) {
    }

    /** Mise à jour partielle d'une tâche : tout champ null est ignoré. */
    public record UpdateTaskRequest(
            @Size(max = 500) String title,
            CaseTask.Status status,
            @Size(max = 50) String assignee) {
    }

    public record CaseResponse(
            UUID id,
            String reference,
            String title,
            String description,
            Severity priority,
            CaseStatus status,
            String assigneeUsername,
            String conclusion,
            UUID originCaseId,
            Instant openedAt,
            Instant closedAt) {
    }

    public record TaskResponse(
            UUID id,
            String title,
            CaseTask.Status status,
            String assigneeUsername,
            Instant createdAt,
            Instant completedAt) {
    }

    public record TimelineEntryResponse(
            CaseEventType type,
            String message,
            String author,
            Instant occurredAt) {
    }

    public record CaseDetailResponse(
            CaseResponse investigation,
            List<IncidentResponse> linkedIncidents,
            List<AlertResponse> linkedAlerts,
            List<TaskResponse> tasks,
            List<TimelineEntryResponse> timeline,
            List<CaseResponse> followUps) {
    }
}
