package com.smartsoc.api.hunting.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntLogicalOperator;
import com.smartsoc.domain.hunting.HuntOperator;
import com.smartsoc.domain.hunting.HuntVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contrats REST du contexte threat hunting. Les enums du domaine
 * ({@link HuntField}, {@link HuntOperator}, {@link HuntLogicalOperator},
 * {@link HuntVisibility}) sont réutilisés directement — même convention
 * que {@code Severity}/{@code IndicatorType} dans les autres contrats.
 */
public final class HuntDtos {

    private HuntDtos() {
    }

    /**
     * Un nœud de l'arbre de critères, tel qu'échangé par l'API — même
     * forme récursive que le domaine ({@code HuntNode}), avec un
     * discriminant JSON explicite {@code kind}. La V1 restreint la racine
     * à un {@code AND} de conditions plates (422 sinon, voir
     * {@code HuntQuery}), mais le CONTRAT accepte déjà l'imbrication et
     * {@code OR}/{@code NOT} : débloquer l'évolution ne change aucun DTO.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = HuntConditionDto.class, name = "CONDITION"),
            @JsonSubTypes.Type(value = HuntGroupDto.class, name = "GROUP")
    })
    public sealed interface HuntNodeDto permits HuntConditionDto, HuntGroupDto {
    }

    public record HuntConditionDto(
            @NotNull HuntField field,
            @NotNull HuntOperator operator,
            @NotBlank @Size(max = 2048) String value) implements HuntNodeDto {
    }

    public record HuntGroupDto(
            @NotNull HuntLogicalOperator operator,
            @NotEmpty List<@Valid @NotNull HuntNodeDto> children) implements HuntNodeDto {
    }

    public record DeclareHuntRequest(
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotNull @Valid HuntGroupDto criteria,
            HuntVisibility visibility) {
    }

    public record ExecuteAdHocRequest(@NotNull @Valid HuntGroupDto criteria) {
    }

    public record HuntResponse(
            UUID id,
            String name,
            String description,
            HuntGroupDto criteria,
            HuntVisibility visibility,
            Instant lastExecutedAt) {
    }

    /** Métadonnées d'une exécution, sans le contenu — voir {@code HuntExecutionSummary}. */
    public record HuntExecutionSummaryResponse(
            UUID huntId,
            Instant executedAt,
            long tookMillis,
            long matchedCount,
            boolean truncated) {
    }

    public record HuntStatisticsResponse(
            Map<Severity, Long> bySeverity,
            Map<AlertStatus, Long> byStatus,
            Map<String, Long> bySource) {
    }

    public record HuntExecutionResponse(
            HuntExecutionSummaryResponse summary,
            HuntStatisticsResponse statistics,
            PageResponse<AlertResponse> matches) {
    }

    /** Catalogue des champs chassables et de leurs opérateurs compatibles — pilote le constructeur du frontend. */
    public record HuntFieldDescriptor(HuntField field, Set<HuntOperator> allowedOperators) {
    }
}
