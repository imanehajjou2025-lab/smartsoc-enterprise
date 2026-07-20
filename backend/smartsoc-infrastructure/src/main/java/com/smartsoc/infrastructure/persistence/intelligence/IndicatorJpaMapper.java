package com.smartsoc.infrastructure.persistence.intelligence;

import com.smartsoc.domain.intelligence.Indicator;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Les tags traversent la frontière {@code Set} (domaine, dédoublonné) ↔
 * {@code List} (JSONB, ordonné) : MapStruct fait la conversion, et le
 * domaine re-normalise de toute façon à la lecture comme à l'écriture.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IndicatorJpaMapper {

    IndicatorJpaEntity toJpa(Indicator indicator);

    Indicator toDomain(IndicatorJpaEntity entity);
}
