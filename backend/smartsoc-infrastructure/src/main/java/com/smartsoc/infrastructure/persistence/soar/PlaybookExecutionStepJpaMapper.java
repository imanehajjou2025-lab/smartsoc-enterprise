package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.soar.PlaybookExecutionStep;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** {@code order} (domaine) ↔ {@code stepOrder} ("order" est un mot réservé SQL, colonne renommée). */
@Mapper(componentModel = "spring")
public interface PlaybookExecutionStepJpaMapper {

    @Mapping(target = "stepOrder", source = "order")
    PlaybookExecutionStepJpaEntity toJpa(PlaybookExecutionStep domain);

    @Mapping(target = "order", source = "stepOrder")
    PlaybookExecutionStep toDomain(PlaybookExecutionStepJpaEntity entity);
}
