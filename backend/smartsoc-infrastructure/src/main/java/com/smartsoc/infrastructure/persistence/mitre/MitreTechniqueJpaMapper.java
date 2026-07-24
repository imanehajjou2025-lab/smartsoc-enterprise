package com.smartsoc.infrastructure.persistence.mitre;

import com.smartsoc.domain.mitre.MitreTechnique;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Les tactiques traversent la frontière {@code Set} (domaine, dédoublonné)
 * ↔ {@code List} (JSONB, ordonné) : MapStruct fait la conversion. Le
 * domaine reste l'autorité sur la forme — {@code fromCatalog} re-normalise
 * en {@code EnumSet} non-modifiable, la relecture reconstruit via le
 * builder Lombok.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MitreTechniqueJpaMapper {

    MitreTechniqueJpaEntity toJpa(MitreTechnique technique);

    MitreTechnique toDomain(MitreTechniqueJpaEntity entity);
}
