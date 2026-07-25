package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;

/**
 * Une condition atomique de chasse : {@code field OPERATOR value}. La
 * valeur est validée et NORMALISÉE à la construction selon
 * {@link HuntField#normalize(String)} — c'est cette forme, et elle seule,
 * qui traverse jusqu'à l'adaptateur d'exécution ; celui-ci n'a plus à
 * revalider ni reparser.
 */
public record HuntCondition(HuntField field, HuntOperator operator, String value) implements HuntNode {

    private static final String INVALID = "INVALID_HUNT_CONDITION";
    private static final String INCOMPATIBLE_OPERATOR = "HUNT_INCOMPATIBLE_OPERATOR";

    public HuntCondition {
        if (field == null) {
            throw new BusinessRuleViolationException(INVALID, "A hunt condition requires a field");
        }
        if (operator == null) {
            throw new BusinessRuleViolationException(INVALID, "A hunt condition requires an operator");
        }
        if (!field.supports(operator)) {
            throw new BusinessRuleViolationException(INCOMPATIBLE_OPERATOR,
                    "Field %s does not support operator %s (allowed: %s)"
                            .formatted(field, operator, field.allowedOperators()));
        }
        value = field.normalize(value);
    }
}
