package com.smartsoc.infrastructure.persistence.audit;

import com.smartsoc.domain.audit.AuditLogEntry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogJpaMapper {

    AuditLogJpaEntity toJpa(AuditLogEntry domain);

    AuditLogEntry toDomain(AuditLogJpaEntity entity);
}
