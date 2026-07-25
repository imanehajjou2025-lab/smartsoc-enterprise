package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;

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
}
