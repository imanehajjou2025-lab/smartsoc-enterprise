package com.smartsoc.domain.identity;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void createNormalizesIdentifiersAndEnablesTheAccount() {
        User user = User.create("  Analyst01 ", "Analyst@SmartSOC.io", "hash", "Jane Doe", Role.SOC_ANALYST);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("analyst01");
        assertThat(user.getEmail()).isEqualTo("analyst@smartsoc.io");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.SOC_ANALYST);
    }

    @Test
    void createRejectsBlankMandatoryFields() {
        assertThatThrownBy(() -> User.create(" ", "a@b.io", "hash", "Jane", Role.VIEWER))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("username");
    }

    @Test
    void createRejectsMissingRole() {
        assertThatThrownBy(() -> User.create("jane", "a@b.io", "hash", "Jane", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("role");
    }

    @Test
    void disableAndEnableToggleTheAccount() {
        User user = User.create("jane", "a@b.io", "hash", "Jane", Role.VIEWER);

        user.disable();
        assertThat(user.isEnabled()).isFalse();

        user.enable();
        assertThat(user.isEnabled()).isTrue();
    }
}
