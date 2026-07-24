package com.smartsoc.infrastructure.persistence.investigations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCaseRepository
        extends JpaRepository<CaseJpaEntity, UUID>, JpaSpecificationExecutor<CaseJpaEntity> {

    Optional<CaseJpaEntity> findByReference(String reference);

    List<CaseJpaEntity> findByOriginCaseIdOrderByOpenedAt(UUID originCaseId);

    /**
     * Liaisons en SQL natif (tables d'association sans entité). La clause
     * {@code on conflict do nothing} de chaque requête rend le rejeu de la
     * liaison idempotent.
     */
    @Modifying
    @Query(value = """
            insert into case_incidents (case_id, incident_id)
            values (:caseId, :incidentId)
            on conflict do nothing
            """, nativeQuery = true)
    void linkIncident(@Param("caseId") UUID caseId, @Param("incidentId") UUID incidentId);

    @Modifying
    @Query(value = "delete from case_incidents where case_id = :caseId and incident_id = :incidentId",
            nativeQuery = true)
    void unlinkIncident(@Param("caseId") UUID caseId, @Param("incidentId") UUID incidentId);

    @Query(value = """
            select incident_id from case_incidents
            where case_id = :caseId order by linked_at
            """, nativeQuery = true)
    List<UUID> findLinkedIncidentIds(@Param("caseId") UUID caseId);

    @Modifying
    @Query(value = """
            insert into case_alerts (case_id, alert_id)
            values (:caseId, :alertId)
            on conflict do nothing
            """, nativeQuery = true)
    void linkAlert(@Param("caseId") UUID caseId, @Param("alertId") UUID alertId);

    @Modifying
    @Query(value = "delete from case_alerts where case_id = :caseId and alert_id = :alertId",
            nativeQuery = true)
    void unlinkAlert(@Param("caseId") UUID caseId, @Param("alertId") UUID alertId);

    @Query(value = """
            select alert_id from case_alerts
            where case_id = :caseId order by linked_at
            """, nativeQuery = true)
    List<UUID> findLinkedAlertIds(@Param("caseId") UUID caseId);
}
