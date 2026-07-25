package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuntFieldTest {

    @Test
    void eachFieldDeclaresItsCompatibleOperators() {
        assertThat(HuntField.SEVERITY.allowedOperators()).containsExactly(HuntOperator.EQUALS);
        assertThat(HuntField.STATUS.allowedOperators()).containsExactly(HuntOperator.EQUALS);
        assertThat(HuntField.DETECTED_AT.allowedOperators())
                .containsExactlyInAnyOrder(HuntOperator.GREATER_THAN, HuntOperator.LESS_THAN);
        assertThat(HuntField.MITRE_TECHNIQUE.allowedOperators()).containsExactly(HuntOperator.CONTAINS);
        assertThat(HuntField.RAW_PAYLOAD_TEXT.allowedOperators()).containsExactly(HuntOperator.CONTAINS);
        assertThat(HuntField.SOURCE.supports(HuntOperator.CONTAINS)).isTrue();
        assertThat(HuntField.SEVERITY.supports(HuntOperator.CONTAINS)).isFalse();
    }

    @Test
    void normalizesSeverityAndStatusCaseInsensitively() {
        assertThat(HuntField.SEVERITY.normalize("critical")).isEqualTo("CRITICAL");
        assertThat(HuntField.STATUS.normalize("  new ")).isEqualTo("NEW");
    }

    @Test
    void rejectsUnknownEnumValue() {
        assertThatThrownBy(() -> HuntField.SEVERITY.normalize("not-a-severity"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_INVALID_VALUE");
    }

    @Test
    void normalizesInstantToIsoForm() {
        assertThat(HuntField.DETECTED_AT.normalize("2026-07-25T10:00:00Z"))
                .isEqualTo("2026-07-25T10:00:00Z");
        assertThatThrownBy(() -> HuntField.DETECTED_AT.normalize("not-a-date"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_INVALID_VALUE");
    }

    @Test
    void mitreTechniqueReusesTheCatalogNormalization() {
        // Même clé de corrélation que le catalogue MITRE : une chasse
        // "T1059" doit correspondre exactement à la même forme canonique.
        assertThat(HuntField.MITRE_TECHNIQUE.normalize("  t1059 ")).isEqualTo("T1059");
        assertThatThrownBy(() -> HuntField.MITRE_TECHNIQUE.normalize("not-an-id"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ATTACK_ID");
    }

    @Test
    void rejectsBlankOrOversizedValues() {
        assertThatThrownBy(() -> HuntField.HOSTNAME.normalize(" "))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_INVALID_VALUE");
        assertThatThrownBy(() -> HuntField.HOSTNAME.normalize(null))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> HuntField.RAW_PAYLOAD_TEXT.normalize("x".repeat(2049)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "HUNT_INVALID_VALUE");
    }
}
