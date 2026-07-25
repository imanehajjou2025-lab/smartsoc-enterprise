package com.smartsoc.application.hunting;

import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntExecutionPort;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntExecutionSummary;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntLogicalOperator;
import com.smartsoc.domain.hunting.HuntOperator;
import com.smartsoc.domain.hunting.HuntQuery;
import com.smartsoc.domain.hunting.HuntQueryRepository;
import com.smartsoc.domain.hunting.HuntStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuntExecutionServiceTest {

    @Mock
    private HuntQueryRepository huntQueryRepository;

    @Mock
    private HuntExecutionPort executionPort;

    @InjectMocks
    private HuntExecutionService service;

    private static HuntGroup criteria() {
        return new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "CRITICAL")));
    }

    private static HuntExecutionResult portResult() {
        return new HuntExecutionResult(
                new HuntExecutionSummary(null, Instant.parse("2026-07-25T10:00:00Z"), 5, 3, false),
                new HuntStatistics(Map.of(), Map.of(), Map.of()),
                new PageResult<>(List.of(), 3, 0, 25));
    }

    @Test
    void executeMarksTheHuntExecutedAndAttributesTheHuntId() {
        HuntQuery hunt = HuntQuery.declare(HuntQuery.DeclareCommand.builder()
                .name("Suspicious PowerShell").criteria(criteria()).build());
        when(huntQueryRepository.findById(hunt.getId())).thenReturn(Optional.of(hunt));
        when(executionPort.execute(criteria(), PageQuery.of(0, 25))).thenReturn(portResult());
        when(huntQueryRepository.save(any(HuntQuery.class))).thenAnswer(call -> call.getArgument(0));

        HuntExecutionResult result = service.execute(hunt.getId(), PageQuery.of(0, 25));

        assertThat(result.summary().huntId()).isEqualTo(hunt.getId());
        assertThat(hunt.getLastExecutedAt()).isEqualTo(Instant.parse("2026-07-25T10:00:00Z"));
        verify(huntQueryRepository).save(hunt);
    }

    @Test
    void executeRaises404WhenTheHuntIsMissing() {
        when(huntQueryRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), PageQuery.of(0, 25)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void executeAdHocNeverPersistsAnything() {
        when(executionPort.execute(criteria(), PageQuery.of(0, 25))).thenReturn(portResult());

        HuntExecutionResult result = service.executeAdHoc(criteria(), PageQuery.of(0, 25));

        assertThat(result.summary().huntId()).isNull();
        verify(huntQueryRepository, never()).save(any());
        verify(huntQueryRepository, never()).findById(any());
    }
}
