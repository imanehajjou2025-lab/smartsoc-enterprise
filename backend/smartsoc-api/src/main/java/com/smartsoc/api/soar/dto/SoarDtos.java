package com.smartsoc.api.soar.dto;

import com.smartsoc.domain.soar.ExecutionStatus;
import com.smartsoc.domain.soar.StepStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats REST du contexte SOAR (playbooks de réponse). */
public final class SoarDtos {

    private SoarDtos() {
    }

    /**
     * Une étape, en entrée comme en sortie. {@code order} est ignoré en
     * entrée (le domaine le redérive de la position dans la liste, voir
     * {@code Playbook}) mais accepté pour ne pas forcer un DTO distinct.
     */
    public record PlaybookStepDto(int order, @NotBlank @Size(max = 500) String title, String description) {
    }

    public record DeclarePlaybookRequest(
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotEmpty List<@Valid @NotNull PlaybookStepDto> steps) {
    }

    public record PlaybookResponse(
            UUID id,
            String name,
            String description,
            int version,
            List<PlaybookStepDto> steps,
            boolean archived) {
    }

    public record StartExecutionRequest(@NotNull UUID playbookId) {
    }

    public record UpdateStepRequest(@NotNull StepStatus status, String note) {
    }

    public record PlaybookExecutionStepResponse(
            UUID id, int order, String title, StepStatus status, String note, Instant completedAt) {
    }

    public record PlaybookExecutionResponse(
            UUID id,
            UUID playbookId,
            int playbookVersion,
            String playbookName,
            UUID incidentId,
            ExecutionStatus status,
            Instant startedAt,
            Instant completedAt,
            List<PlaybookExecutionStepResponse> steps) {
    }
}
