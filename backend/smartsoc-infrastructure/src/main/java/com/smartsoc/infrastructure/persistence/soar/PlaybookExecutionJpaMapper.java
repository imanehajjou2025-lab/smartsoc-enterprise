package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.soar.PlaybookExecution;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlaybookExecutionJpaMapper {

    PlaybookExecutionJpaEntity toJpa(PlaybookExecution domain);

    PlaybookExecution toDomain(PlaybookExecutionJpaEntity entity);
}
