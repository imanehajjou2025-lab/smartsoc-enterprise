package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookStepTemplateTest {

    @Test
    void tripsAndDropsABlankDescription() {
        PlaybookStepTemplate step = new PlaybookStepTemplate(0, "  Isoler l'hôte  ", "   ");
        assertThat(step.title()).isEqualTo("Isoler l'hôte");
        assertThat(step.description()).isNull();
    }

    @Test
    void rejectsANegativeOrder() {
        assertThatThrownBy(() -> new PlaybookStepTemplate(-1, "x", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYBOOK_STEP");
    }

    @Test
    void rejectsABlankOrOversizedTitle() {
        assertThatThrownBy(() -> new PlaybookStepTemplate(0, " ", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYBOOK_STEP");
        assertThatThrownBy(() -> new PlaybookStepTemplate(0, null, null))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> new PlaybookStepTemplate(0, "x".repeat(501), null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYBOOK_STEP");
    }
}
