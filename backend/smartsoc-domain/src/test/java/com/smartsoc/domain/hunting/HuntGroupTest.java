package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuntGroupTest {

    private static HuntCondition severityCritical() {
        return new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "CRITICAL");
    }

    @Test
    void acceptsNestedGroupsStructurally() {
        // La structure (hors V1, portée par HuntQuery) accepte déjà
        // l'imbrication : un groupe peut contenir un autre groupe.
        HuntGroup inner = new HuntGroup(HuntLogicalOperator.OR, List.of(severityCritical(), severityCritical()));
        HuntGroup outer = new HuntGroup(HuntLogicalOperator.AND, List.of(inner, severityCritical()));

        assertThat(outer.children()).hasSize(2);
        assertThat(outer.children().getFirst()).isInstanceOf(HuntGroup.class);
    }

    @Test
    void requiresAtLeastOneChild() {
        assertThatThrownBy(() -> new HuntGroup(HuntLogicalOperator.AND, List.of()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_HUNT_GROUP");
        assertThatThrownBy(() -> new HuntGroup(HuntLogicalOperator.AND, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void notGroupRequiresExactlyOneChild() {
        assertThatThrownBy(() -> new HuntGroup(HuntLogicalOperator.NOT,
                List.of(severityCritical(), severityCritical())))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_HUNT_GROUP");

        HuntGroup valid = new HuntGroup(HuntLogicalOperator.NOT, List.of(severityCritical()));
        assertThat(valid.children()).hasSize(1);
    }

    @Test
    void childrenAreDefensivelyCopied() {
        List<HuntNode> mutable = new java.util.ArrayList<>(List.of(severityCritical()));
        HuntGroup group = new HuntGroup(HuntLogicalOperator.AND, mutable);
        mutable.add(severityCritical());

        assertThat(group.children()).hasSize(1);
        assertThatThrownBy(() -> group.children().add(severityCritical()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
