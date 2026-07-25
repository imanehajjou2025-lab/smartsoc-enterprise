package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;

/**
 * Une étape d'un gabarit de playbook — le "quoi faire" que l'analyste
 * suivra. {@code order} est TOUJOURS dérivé de la position dans la liste
 * par {@link Playbook} (jamais fait confiance à une valeur fournie par
 * l'appelant) : un gabarit n'a pas de trous ni de doublons d'ordre par
 * construction, pas par validation.
 */
public record PlaybookStepTemplate(int order, String title, String description) {

    private static final String INVALID = "INVALID_PLAYBOOK_STEP";
    private static final int MAX_TITLE_LENGTH = 500;

    public PlaybookStepTemplate {
        if (order < 0) {
            throw new BusinessRuleViolationException(INVALID, "A step order must not be negative");
        }
        title = requireTitle(title);
        description = TextNormalization.blankToNull(description);
    }

    private static String requireTitle(String title) {
        String cleaned = TextNormalization.blankToNull(title);
        if (cleaned == null) {
            throw new BusinessRuleViolationException(INVALID, "A step must have a title");
        }
        if (cleaned.length() > MAX_TITLE_LENGTH) {
            throw new BusinessRuleViolationException(INVALID,
                    "A step title must not exceed %d characters".formatted(MAX_TITLE_LENGTH));
        }
        return cleaned;
    }
}
