package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.soar.Playbook;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlaybookJpaMapper {

    PlaybookJpaEntity toJpa(Playbook domain);

    Playbook toDomain(PlaybookJpaEntity entity);
}
