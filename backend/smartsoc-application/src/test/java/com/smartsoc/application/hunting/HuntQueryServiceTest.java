package com.smartsoc.application.hunting;

import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntLogicalOperator;
import com.smartsoc.domain.hunting.HuntOperator;
import com.smartsoc.domain.hunting.HuntQuery;
import com.smartsoc.domain.hunting.HuntQuery.DeclareCommand;
import com.smartsoc.domain.hunting.HuntQueryFilter;
import com.smartsoc.domain.hunting.HuntQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuntQueryServiceTest {

    @Mock
    private HuntQueryRepository huntQueryRepository;

    @InjectMocks
    private HuntQueryService service;

    private static DeclareCommand command() {
        return HuntQuery.DeclareCommand.builder()
                .name("Suspicious PowerShell")
                .criteria(new HuntGroup(HuntLogicalOperator.AND,
                        List.of(new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "CRITICAL"))))
                .build();
    }

    @Test
    void declareSavesANewHuntQuery() {
        when(huntQueryRepository.save(any(HuntQuery.class))).thenAnswer(call -> call.getArgument(0));

        HuntQuery declared = service.declare(command());

        assertThat(declared.getName()).isEqualTo("Suspicious PowerShell");
        verify(huntQueryRepository).save(any(HuntQuery.class));
    }

    @Test
    void updateMutatesTheExistingAggregateInPlace() {
        HuntQuery existing = HuntQuery.declare(command());
        when(huntQueryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(huntQueryRepository.save(any(HuntQuery.class))).thenAnswer(call -> call.getArgument(0));

        HuntQuery updated = service.update(existing.getId(), HuntQuery.DeclareCommand.builder()
                .name("Renamed").criteria(command().criteria()).build());

        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getName()).isEqualTo("Renamed");
    }

    @Test
    void updateRaises404WhenMissing() {
        when(huntQueryRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), command()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRequiresExistenceBeforeRemoving() {
        UUID id = UUID.randomUUID();
        HuntQuery existing = HuntQuery.declare(command());
        when(huntQueryRepository.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(huntQueryRepository).deleteById(captor.capture());
        assertThat(captor.getValue()).isEqualTo(id);
    }

    @Test
    void deleteRaises404WhenMissing() {
        when(huntQueryRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRaises404WhenMissing() {
        when(huntQueryRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchDelegatesToTheRepository() {
        PageResult<HuntQuery> page = new PageResult<>(List.of(), 0, 0, 25);
        HuntQueryFilter filter = new HuntQueryFilter("powershell", PageQuery.of(0, 25));
        when(huntQueryRepository.search(filter)).thenReturn(page);

        assertThat(service.search(filter)).isSameAs(page);
    }
}
