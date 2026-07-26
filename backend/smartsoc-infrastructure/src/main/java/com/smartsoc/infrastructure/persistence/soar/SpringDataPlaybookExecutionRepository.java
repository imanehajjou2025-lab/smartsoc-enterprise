package com.smartsoc.infrastructure.persistence.soar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataPlaybookExecutionRepository
        extends JpaRepository<PlaybookExecutionJpaEntity, UUID>,
        JpaSpecificationExecutor<PlaybookExecutionJpaEntity> {

    @Query("select e.status, count(e) from PlaybookExecutionJpaEntity e "
            + "where e.startedAt >= :from and e.startedAt < :to group by e.status")
    List<Object[]> countGroupedByStatusInPeriod(@Param("from") Instant from, @Param("to") Instant to);
}
