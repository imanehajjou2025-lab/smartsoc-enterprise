package com.smartsoc.application.settings;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationTestServiceTest {

    @Mock
    private NotificationTestPort testPort;

    @Test
    void sendTestEmailRejectsWhenNoLivePortIsWired() {
        NotificationTestService service = new NotificationTestService(Optional.empty());

        assertThatThrownBy(() -> service.sendTestEmail("admin@smartsoc.local"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "NOTIFICATIONS_MODE_SIMULATION");
    }

    @Test
    void sendTestEmailDelegatesToTheLivePortWhenAvailable() {
        NotificationTestService service = new NotificationTestService(Optional.of(testPort));

        assertThatCode(() -> service.sendTestEmail("admin@smartsoc.local")).doesNotThrowAnyException();
        verify(testPort).sendTestEmail("admin@smartsoc.local");
    }
}
