package com.smartsoc.api.hunting;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntConditionDto;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntExecutionResponse;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntExecutionSummaryResponse;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntFieldDescriptor;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntGroupDto;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntNodeDto;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntResponse;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntStatisticsResponse;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntNode;
import com.smartsoc.domain.hunting.HuntQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Traductions REST du contexte hunting. Manuel plutôt que MapStruct :
 * {@link HuntNodeDto}/{@link HuntNode} sont des arbres récursifs à
 * hiérarchies scellées DIFFÉRENTES (API vs domaine) — même raisonnement que
 * le codec de persistance {@code HuntCriteriaJsonCodec}, explicite et
 * testable plutôt que de la magie de mapping générique.
 */
@Component
@RequiredArgsConstructor
public class HuntApiMapper {

    private final AlertApiMapper alertMapper;

    public HuntGroup toDomain(HuntGroupDto dto) {
        return new HuntGroup(dto.operator(), dto.children().stream().map(this::toDomainNode).toList());
    }

    private HuntNode toDomainNode(HuntNodeDto dto) {
        return switch (dto) {
            case HuntConditionDto c -> new HuntCondition(c.field(), c.operator(), c.value());
            case HuntGroupDto g -> toDomain(g);
        };
    }

    public HuntGroupDto toDto(HuntGroup group) {
        return new HuntGroupDto(group.operator(), group.children().stream().map(this::toDtoNode).toList());
    }

    private HuntNodeDto toDtoNode(HuntNode node) {
        return switch (node) {
            case HuntCondition c -> new HuntConditionDto(c.field(), c.operator(), c.value());
            case HuntGroup g -> toDto(g);
        };
    }

    public HuntResponse toResponse(HuntQuery query) {
        return new HuntResponse(
                query.getId(), query.getName(), query.getDescription(),
                toDto(query.getCriteria()), query.getVisibility(), query.getLastExecutedAt());
    }

    public HuntExecutionResponse toResponse(HuntExecutionResult result) {
        HuntExecutionSummaryResponse summary = new HuntExecutionSummaryResponse(
                result.summary().huntId(), result.summary().executedAt(),
                result.summary().tookMillis(), result.summary().matchedCount(), result.summary().truncated());
        HuntStatisticsResponse statistics = new HuntStatisticsResponse(
                result.statistics().bySeverity(), result.statistics().byStatus(), result.statistics().bySource());
        PageResponse<AlertResponse> matches = PageResponse.of(result.matches(), alertMapper::toResponse);
        return new HuntExecutionResponse(summary, statistics, matches);
    }

    public List<HuntFieldDescriptor> fieldDescriptors() {
        return Arrays.stream(HuntField.values())
                .map(field -> new HuntFieldDescriptor(field, field.allowedOperators()))
                .toList();
    }
}
