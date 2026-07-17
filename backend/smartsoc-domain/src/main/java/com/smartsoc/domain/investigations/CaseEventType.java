package com.smartsoc.domain.investigations;

/**
 * Nature d'une entrée de la timeline d'un cas d'investigation. Chaque
 * action d'API inscrit son événement : matière des audits, des rapports
 * et, plus tard, des tools de l'Assistant IA.
 */
public enum CaseEventType {
    CREATED,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    INCIDENT_LINKED,
    INCIDENT_UNLINKED,
    ALERT_LINKED,
    ALERT_UNLINKED,
    TASK_ADDED,
    TASK_UPDATED,
    TASK_COMPLETED,
    NOTE_ADDED,
    CLOSED,
    FOLLOW_UP_OPENED
}
