package com.smartsoc.infrastructure.persistence.assets;

import com.smartsoc.domain.assets.Asset;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AssetJpaMapper {

    AssetJpaEntity toJpa(Asset asset);

    Asset toDomain(AssetJpaEntity entity);
}
