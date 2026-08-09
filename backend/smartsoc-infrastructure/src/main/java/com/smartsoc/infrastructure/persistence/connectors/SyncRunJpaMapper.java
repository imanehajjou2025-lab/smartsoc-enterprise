package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.SyncRun;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SyncRunJpaMapper {

    SyncRunJpaEntity toJpa(SyncRun run);

    SyncRun toDomain(SyncRunJpaEntity entity);
}
