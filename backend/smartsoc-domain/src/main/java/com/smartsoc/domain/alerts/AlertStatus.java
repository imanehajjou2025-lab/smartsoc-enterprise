package com.smartsoc.domain.alerts;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une alerte (validé en conception) :
 * NEW → ACKNOWLEDGED → IN_PROGRESS → RESOLVED, avec sortie FALSE_POSITIVE
 * possible à toute étape non terminale. RESOLVED et FALSE_POSITIVE sont
 * terminaux : une alerte close ne se rouvre pas (on en ingère une nouvelle).
 */
public enum AlertStatus {
    NEW,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    FALSE_POSITIVE;

    public Set<AlertStatus> allowedTransitions() {
        return switch (this) {
            case NEW -> EnumSet.of(ACKNOWLEDGED, FALSE_POSITIVE);
            case ACKNOWLEDGED -> EnumSet.of(IN_PROGRESS, RESOLVED, FALSE_POSITIVE);
            case IN_PROGRESS -> EnumSet.of(RESOLVED, FALSE_POSITIVE);
            case RESOLVED, FALSE_POSITIVE -> EnumSet.noneOf(AlertStatus.class);
        };
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
