package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.SocConnectorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledThreatIntelAdapterTest {

    @Test
    void alwaysThrowsRatherThanReturningSilentlyEmptyData() {
        DisabledThreatIntelAdapter adapter = new DisabledThreatIntelAdapter();

        assertThatThrownBy(adapter::listIndicators)
                .isInstanceOf(SocConnectorException.class)
                .hasMessageContaining("disabled");
    }
}
