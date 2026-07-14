package com.smartsoc.domain.incidents;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'un incident (validé en conception) :
 * OPEN → INVESTIGATING → CONTAINED → RESOLVED → CLOSED.
 * Un incident RESOLVED peut être rouvert (retour en INVESTIGATING) tant
 * qu'il n'est pas CLOSED, qui est le seul état terminal.
 */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    CONTAINED,
    RESOLVED,
    CLOSED;

    public Set<IncidentStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> EnumSet.of(INVESTIGATING, CLOSED);
            case INVESTIGATING -> EnumSet.of(CONTAINED, RESOLVED);
            case CONTAINED -> EnumSet.of(RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED, INVESTIGATING);
            case CLOSED -> EnumSet.noneOf(IncidentStatus.class);
        };
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
