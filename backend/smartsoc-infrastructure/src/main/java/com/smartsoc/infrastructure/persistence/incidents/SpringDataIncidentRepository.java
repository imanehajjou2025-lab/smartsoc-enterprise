package com.smartsoc.infrastructure.persistence.incidents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataIncidentRepository
        extends JpaRepository<IncidentJpaEntity, UUID>, JpaSpecificationExecutor<IncidentJpaEntity> {

    Optional<IncidentJpaEntity> findByReference(String reference);

    /**
     * Liaison d'une alerte, en SQL natif (table d'association sans entité).
     * La clause {@code on conflict do nothing} de la requête rend le rejeu
     * de la liaison idempotent.
     */
    @Modifying
    @Query(value = """
            insert into incident_alerts (incident_id, alert_id)
            values (:incidentId, :alertId)
            on conflict do nothing
            """, nativeQuery = true)
    void linkAlert(@Param("incidentId") UUID incidentId, @Param("alertId") UUID alertId);

    @Modifying
    @Query(value = "delete from incident_alerts where incident_id = :incidentId and alert_id = :alertId",
            nativeQuery = true)
    void unlinkAlert(@Param("incidentId") UUID incidentId, @Param("alertId") UUID alertId);

    @Query(value = """
            select alert_id from incident_alerts
            where incident_id = :incidentId order by linked_at
            """, nativeQuery = true)
    List<UUID> findLinkedAlertIds(@Param("incidentId") UUID incidentId);

    @Query(value = "select count(*) from incidents where opened_at >= :from and opened_at < :to",
            nativeQuery = true)
    long countOpenedInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    // avg() sur un ensemble vide rend NULL, mappe en null cote Java —
    // distingue "aucun incident cloture" d'une resolution instantanee.
    @Query(value = """
            select count(*), avg(extract(epoch from (closed_at - opened_at)) / 3600.0)
            from incidents
            where closed_at is not null and closed_at >= :from and closed_at < :to
            """, nativeQuery = true)
    List<Object[]> closedPeriodStats(@Param("from") Instant from, @Param("to") Instant to);
}
