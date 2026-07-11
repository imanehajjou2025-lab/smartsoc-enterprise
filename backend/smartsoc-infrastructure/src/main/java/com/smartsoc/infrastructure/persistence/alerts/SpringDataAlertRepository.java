package com.smartsoc.infrastructure.persistence.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataAlertRepository
        extends JpaRepository<AlertJpaEntity, UUID>, JpaSpecificationExecutor<AlertJpaEntity> {

    Optional<AlertJpaEntity> findBySourceAndExternalId(String source, String externalId);

    @Query("select a.severity, count(a) from AlertJpaEntity a group by a.severity")
    List<Object[]> countGroupedBySeverity();

    @Query("select a.status, count(a) from AlertJpaEntity a group by a.status")
    List<Object[]> countGroupedByStatus();

    @Query("select a.source, count(a) from AlertJpaEntity a group by a.source order by count(a) desc")
    List<Object[]> countGroupedBySource();

    @Query(value = """
            select cast(date_trunc('day', detected_at) as date) as day, count(*)
            from alerts where detected_at >= :from
            group by day order by day
            """, nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("from") Instant from);
}
