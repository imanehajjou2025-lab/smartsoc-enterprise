package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'aggrégat impose la forme V1 (racine AND de conditions plates) sans que
 * {@link HuntGroup}/{@link HuntNode} eux-mêmes ne le sachent — c'est ce qui
 * garantit que débloquer l'imbrication plus tard ne touche que cette
 * classe.
 */
class HuntQueryTest {

    private static HuntCondition severityCritical() {
        return new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "CRITICAL");
    }

    private static HuntQuery.DeclareCommand.DeclareCommandBuilder command() {
        return HuntQuery.DeclareCommand.builder()
                .name("Suspicious PowerShell")
                .description("Recherche d'exécution PowerShell suspecte")
                .criteria(new HuntGroup(HuntLogicalOperator.AND, List.of(severityCritical())));
    }

    @Test
    void declareStartsWithPrivateVisibilityAndNoExecutionYet() {
        HuntQuery hunt = HuntQuery.declare(command().build());

        assertThat(hunt.getId()).isNotNull();
        assertThat(hunt.getName()).isEqualTo("Suspicious PowerShell");
        assertThat(hunt.getVisibility()).isEqualTo(HuntVisibility.PRIVATE);
        assertThat(hunt.getLastExecutedAt()).isNull();
    }

    @Test
    void declareRequiresANameAndCriteria() {
        assertThatThrownBy(() -> HuntQuery.declare(command().name(" ").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_HUNT_QUERY");
        assertThatThrownBy(() -> HuntQuery.declare(command().criteria(null).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_HUNT_QUERY");
    }

    @Test
    void rejectsAnOrOrNotRootForV1() {
        HuntGroup orRoot = new HuntGroup(HuntLogicalOperator.OR, List.of(severityCritical(), severityCritical()));
        assertThatThrownBy(() -> HuntQuery.declare(command().criteria(orRoot).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_LOGICAL_OPERATOR_UNSUPPORTED");

        HuntGroup notRoot = new HuntGroup(HuntLogicalOperator.NOT, List.of(severityCritical()));
        assertThatThrownBy(() -> HuntQuery.declare(command().criteria(notRoot).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_LOGICAL_OPERATOR_UNSUPPORTED");
    }

    @Test
    void rejectsANestedGroupForV1() {
        HuntGroup nested = new HuntGroup(HuntLogicalOperator.AND, List.of(
                new HuntGroup(HuntLogicalOperator.OR, List.of(severityCritical(), severityCritical()))));
        assertThatThrownBy(() -> HuntQuery.declare(command().criteria(nested).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_NESTED_GROUPS_UNSUPPORTED");
    }

    @Test
    void updateReplacesTheDefinitionButKeepsIdentityAndLastExecutedAt() {
        HuntQuery hunt = HuntQuery.declare(command().build());
        hunt.markExecuted(Instant.parse("2026-07-25T10:00:00Z"));

        hunt.update(command().name("Renamed hunt").visibility(HuntVisibility.TEAM).build());

        assertThat(hunt.getName()).isEqualTo("Renamed hunt");
        assertThat(hunt.getVisibility()).isEqualTo(HuntVisibility.TEAM);
        // Le repère d'exécution récente n'est pas un contenu de la
        // définition : une mise à jour de la requête ne l'efface pas.
        assertThat(hunt.getLastExecutedAt()).isEqualTo(Instant.parse("2026-07-25T10:00:00Z"));
    }

    @Test
    void updateAlsoEnforcesTheV1Shape() {
        HuntQuery hunt = HuntQuery.declare(command().build());
        HuntGroup orRoot = new HuntGroup(HuntLogicalOperator.OR, List.of(severityCritical(), severityCritical()));

        assertThatThrownBy(() -> hunt.update(command().criteria(orRoot).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_LOGICAL_OPERATOR_UNSUPPORTED");
    }

    @Test
    void markExecutedRequiresANonNullInstant() {
        HuntQuery hunt = HuntQuery.declare(command().build());
        assertThatThrownBy(() -> hunt.markExecuted(null)).isInstanceOf(NullPointerException.class);
    }
}
