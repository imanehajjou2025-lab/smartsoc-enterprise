package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.alerts.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AlertJpaMapper {

    AlertJpaEntity toJpa(Alert alert);

    Alert toDomain(AlertJpaEntity entity);
}
