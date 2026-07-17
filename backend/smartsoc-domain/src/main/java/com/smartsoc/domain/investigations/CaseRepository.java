package com.smartsoc.domain.investigations;

import com.smartsoc.domain.common.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for case persistence (aggregate + links + tasks + timeline).
 */
public interface CaseRepository {

    Case save(Case investigationCase);

    Optional<Case> findById(UUID id);

    Optional<Case> findByReference(String reference);

    PageResult<Case> search(CaseQuery query);

    /** Cas de suivi ouverts depuis ce cas (chaîne origine → suivis). */
    List<Case> findFollowUps(UUID originCaseId);

    /** Lie un incident au cas (idempotent : un lien déjà présent est ignoré). */
    void linkIncident(UUID caseId, UUID incidentId);

    void unlinkIncident(UUID caseId, UUID incidentId);

    List<UUID> findLinkedIncidentIds(UUID caseId);

    /** Lie une alerte au cas (idempotent : un lien déjà présent est ignoré). */
    void linkAlert(UUID caseId, UUID alertId);

    void unlinkAlert(UUID caseId, UUID alertId);

    List<UUID> findLinkedAlertIds(UUID caseId);

    CaseTask saveTask(CaseTask task);

    Optional<CaseTask> findTaskById(UUID taskId);

    /** Tâches du cas, plus ancienne d'abord (ordre de la checklist). */
    List<CaseTask> findTasks(UUID caseId);

    void addTimelineEntry(CaseTimelineEntry entry);

    List<CaseTimelineEntry> findTimeline(UUID caseId);
}
