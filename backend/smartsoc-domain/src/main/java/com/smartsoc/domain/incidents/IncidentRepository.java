package com.smartsoc.domain.incidents;

import com.smartsoc.domain.common.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for incident persistence (aggregate + links + timeline). */
public interface IncidentRepository {

    Incident save(Incident incident);

    Optional<Incident> findById(UUID id);

    Optional<Incident> findByReference(String reference);

    PageResult<Incident> search(IncidentQuery query);

    /** Lie une alerte à l'incident (idempotent : un lien déjà présent est ignoré). */
    void linkAlert(UUID incidentId, UUID alertId);

    void unlinkAlert(UUID incidentId, UUID alertId);

    List<UUID> findLinkedAlertIds(UUID incidentId);

    void addTimelineEntry(IncidentTimelineEntry entry);

    List<IncidentTimelineEntry> findTimeline(UUID incidentId);

    /**
     * Activité bornée à {@code [from, to)} — brique « incidents » d'un
     * rapport (module reporting). {@code opened} compte sur
     * {@code openedAt} dans la période, {@code closed} et
     * {@code avgResolutionHours} sur {@code closedAt} dans la période
     * (un incident ouvert avant la période mais clôturé pendant compte
     * dans {@code closed}, pas dans {@code opened}).
     */
    IncidentPeriodMetrics periodMetrics(Instant from, Instant to);
}
