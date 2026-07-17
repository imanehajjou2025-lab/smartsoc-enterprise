package com.smartsoc.domain.investigations;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'un cas d'investigation (validé en conception) :
 * OPEN → IN_PROGRESS → CLOSED.
 * CLOSED est STRICTEMENT terminal : un dossier clôturé ne se rouvre
 * jamais — si l'enquête doit reprendre, on ouvre un nouveau cas lié au
 * cas d'origine (Case.openFollowUp), pour la traçabilité d'audit.
 * La clôture exige une conclusion (voir Case.close).
 */
public enum CaseStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED;

    public Set<CaseStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CLOSED);
            case IN_PROGRESS -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(CaseStatus.class);
        };
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
