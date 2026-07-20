package com.smartsoc.infrastructure.persistence.alerts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            select cast(date_trunc('day', detected_at AT TIME ZONE 'UTC') as date) as day, count(*)
            from alerts where detected_at >= :from
            group by day order by day
            """, nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("from") Instant from);

    // Corrélation actifs <-> alertes. Le prédicat lower(trim(hostname))
    // est EXACTEMENT celui de l'index fonctionnel V6
    // ix_alerts_hostname_normalized (sinon l'index est mort) ; la
    // countQuery de pagination reprend le même prédicat — le total de la
    // page est le compteur de corrélation, aucune requête séparée.
    @Query(value = """
            select * from alerts
            where lower(trim(hostname)) = :hostname
            order by detected_at desc
            """,
            countQuery = "select count(*) from alerts where lower(trim(hostname)) = :hostname",
            nativeQuery = true)
    Page<AlertJpaEntity> findByNormalizedHostname(@Param("hostname") String hostname,
                                                  Pageable pageable);

    // Corrélation IOC -> alertes (retro-hunt). La jointure porte sur le
    // COUPLE (type, value), qui est exactement la clé de
    // ix_alert_observables_identity : la clé primaire de la table
    // commence par alert_id et ne servirait pas ce sens de lecture.
    // La countQuery reprend le MÊME prédicat et la même jointure — le
    // total de la page est le compteur de corrélation, il ne peut pas
    // diverger de la liste.
    @Query(value = """
            select a.* from alerts a
            join alert_observables o on o.alert_id = a.id
            where o.type = :type and o.value = :value
            order by a.detected_at desc
            """,
            countQuery = """
            select count(*) from alerts a
            join alert_observables o on o.alert_id = a.id
            where o.type = :type and o.value = :value
            """,
            nativeQuery = true)
    Page<AlertJpaEntity> findByObservable(@Param("type") String type,
                                          @Param("value") String value,
                                          Pageable pageable);
}
