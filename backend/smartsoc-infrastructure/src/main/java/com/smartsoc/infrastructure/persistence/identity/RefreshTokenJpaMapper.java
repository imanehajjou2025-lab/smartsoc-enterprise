package com.smartsoc.infrastructure.persistence.identity;

import com.smartsoc.domain.identity.RefreshToken;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RefreshTokenJpaMapper {

    RefreshTokenJpaEntity toJpa(RefreshToken token);

    RefreshToken toDomain(RefreshTokenJpaEntity entity);
}
