package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;

import java.util.Collections;
import java.util.List;

/**
 * Un groupe de l'arbre de critères : combine récursivement d'autres
 * {@link HuntNode} par {@link HuntLogicalOperator}. Invariants STRUCTURELS
 * uniquement (valables pour tout groupe, à toute profondeur, y compris les
 * évolutions futures) — la restriction « racine = AND de conditions
 * plates » propre à la V1 est portée par {@link HuntQuery}, pas ici.
 */
public record HuntGroup(HuntLogicalOperator operator, List<HuntNode> children) implements HuntNode {

    private static final String INVALID = "INVALID_HUNT_GROUP";

    public HuntGroup {
        if (operator == null) {
            throw new BusinessRuleViolationException(INVALID, "A hunt group requires a logical operator");
        }
        if (children == null || children.isEmpty()) {
            throw new BusinessRuleViolationException(INVALID, "A hunt group requires at least one child");
        }
        if (operator == HuntLogicalOperator.NOT && children.size() != 1) {
            throw new BusinessRuleViolationException(INVALID, "A NOT group must have exactly one child");
        }
        children = List.copyOf(children);
    }

    /**
     * Accesseur écrit à la main plutôt que généré par le record : le
     * constructeur compact affecte déjà {@code List.copyOf(children)}, mais
     * CodeQL (java/internal-representation-exposure) ne fait pas confiance
     * à cette garantie prise en amont — il veut voir l'enveloppement dans
     * l'accesseur lui-même. Même correctif que {@code Alert.getObservables}.
     */
    @Override
    public List<HuntNode> children() {
        return Collections.unmodifiableList(children);
    }
}
