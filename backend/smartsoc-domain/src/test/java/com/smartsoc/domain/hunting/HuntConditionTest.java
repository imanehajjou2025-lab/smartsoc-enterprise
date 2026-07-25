package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuntConditionTest {

    @Test
    void storesTheNormalizedValueNotTheRawOne() {
        HuntCondition condition = new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "critical");
        assertThat(condition.value()).isEqualTo("CRITICAL");
    }

    @Test
    void rejectsAnOperatorTheFieldDoesNotSupport() {
        assertThatThrownBy(() -> new HuntCondition(HuntField.SEVERITY, HuntOperator.CONTAINS, "CRITICAL"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_INCOMPATIBLE_OPERATOR");
    }

    @Test
    void requiresFieldAndOperator() {
        assertThatThrownBy(() -> new HuntCondition(null, HuntOperator.EQUALS, "x"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_HUNT_CONDITION");
        assertThatThrownBy(() -> new HuntCondition(HuntField.SOURCE, null, "x"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_HUNT_CONDITION");
    }

    @Test
    void propagatesFieldSpecificValueValidation() {
        assertThatThrownBy(() -> new HuntCondition(HuntField.DETECTED_AT, HuntOperator.GREATER_THAN, "not-a-date"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_INVALID_VALUE");
    }
}
