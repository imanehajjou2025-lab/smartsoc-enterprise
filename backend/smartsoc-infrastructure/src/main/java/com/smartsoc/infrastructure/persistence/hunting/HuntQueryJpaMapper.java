package com.smartsoc.infrastructure.persistence.hunting;

import com.smartsoc.domain.hunting.HuntQuery;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@code criteria} traverse la frontière {@code HuntGroup} ↔ {@code String}
 * (JSON) : MapStruct sélectionne automatiquement
 * {@link HuntCriteriaJsonCodec#toJson}/{@code fromJson} via {@code uses}.
 */
@Mapper(componentModel = "spring", uses = HuntCriteriaJsonCodec.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HuntQueryJpaMapper {

    HuntQueryJpaEntity toJpa(HuntQuery domain);

    HuntQuery toDomain(HuntQueryJpaEntity entity);
}
