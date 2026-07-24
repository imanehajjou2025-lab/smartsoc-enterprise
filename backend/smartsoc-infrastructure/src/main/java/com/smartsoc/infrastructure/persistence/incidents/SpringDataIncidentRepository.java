package com.smartsoc.infrastructure.persistence.incidents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
