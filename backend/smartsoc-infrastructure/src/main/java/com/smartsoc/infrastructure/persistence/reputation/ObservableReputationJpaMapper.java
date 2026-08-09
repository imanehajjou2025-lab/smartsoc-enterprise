package com.smartsoc.infrastructure.persistence.reputation;

import com.smartsoc.domain.reputation.ObservableReputation;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ObservableReputationJpaMapper {

    ObservableReputationJpaEntity toJpa(ObservableReputation reputation);

    ObservableReputation toDomain(ObservableReputationJpaEntity entity);
}
