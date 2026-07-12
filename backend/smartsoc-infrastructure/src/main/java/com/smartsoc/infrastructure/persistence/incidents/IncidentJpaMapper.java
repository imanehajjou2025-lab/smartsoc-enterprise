package com.smartsoc.infrastructure.persistence.incidents;

import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentTimelineEntry;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IncidentJpaMapper {

    IncidentJpaEntity toJpa(Incident incident);

    Incident toDomain(IncidentJpaEntity entity);

    IncidentTimelineJpaEntity toJpa(IncidentTimelineEntry entry);

    IncidentTimelineEntry toDomain(IncidentTimelineJpaEntity entity);
}
