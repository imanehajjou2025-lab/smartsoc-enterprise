package com.smartsoc.domain.incidents;

/** Nature d'une entrée de la timeline d'investigation d'un incident. */
public enum IncidentEventType {
    CREATED,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    NOTE,
    ALERT_LINKED,
    ALERT_UNLINKED
}
