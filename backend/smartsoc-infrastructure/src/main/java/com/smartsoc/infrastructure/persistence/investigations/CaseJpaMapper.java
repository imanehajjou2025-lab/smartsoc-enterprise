package com.smartsoc.infrastructure.persistence.investigations;

import com.smartsoc.domain.investigations.Case;
import com.smartsoc.domain.investigations.CaseTask;
import com.smartsoc.domain.investigations.CaseTimelineEntry;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CaseJpaMapper {

    CaseJpaEntity toJpa(Case investigationCase);

    Case toDomain(CaseJpaEntity entity);

    CaseTaskJpaEntity toJpa(CaseTask task);

    CaseTask toDomain(CaseTaskJpaEntity entity);

    CaseTimelineJpaEntity toJpa(CaseTimelineEntry entry);

    CaseTimelineEntry toDomain(CaseTimelineJpaEntity entity);
}
