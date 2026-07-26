package com.smartsoc.infrastructure.persistence.reporting;

import com.smartsoc.domain.reporting.Report;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportJpaMapper {

    ReportJpaEntity toJpa(Report domain);

    Report toDomain(ReportJpaEntity entity);
}
