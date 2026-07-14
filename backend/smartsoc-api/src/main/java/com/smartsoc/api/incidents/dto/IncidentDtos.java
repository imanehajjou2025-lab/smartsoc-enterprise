package com.smartsoc.api.incidents.dto;

import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.incidents.IncidentEventType;
import com.smartsoc.domain.incidents.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats REST du contexte incidents. */
public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record CreateIncidentRequest(
            @NotBlank @Size(max = 500) String title,
            String description,
            @NotNull Severity severity) {
    }

    public record UpdateStatusRequest(@NotNull IncidentStatus status) {
    }

    public record AssigneeRequest(@NotBlank @Size(max = 50) String username) {
    }

    public record NoteRequest(@NotBlank String message) {
    }

    public record IncidentResponse(
            UUID id,
            String reference,
            String title,
            String description,
            Severity severity,
            IncidentStatus status,
            String assigneeUsername,
            Instant openedAt) {
    }

    public record TimelineEntryResponse(
            IncidentEventType type,
            String message,
            String author,
            Instant occurredAt) {
    }

    public record IncidentDetailResponse(
            IncidentResponse incident,
            List<AlertResponse> linkedAlerts,
            List<TimelineEntryResponse> timeline) {
    }
}
