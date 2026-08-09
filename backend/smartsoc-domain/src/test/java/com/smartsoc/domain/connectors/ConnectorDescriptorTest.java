package com.smartsoc.domain.connectors;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorDescriptorTest {

    @Test
    void unknownHasNoCapabilities() {
        ConnectorDescriptor descriptor = ConnectorDescriptor.unknown();

        assertThat(descriptor.detectedVersion()).isNull();
        assertThat(descriptor.capabilities()).isEmpty();
        assertThat(descriptor.supports(ConnectorCapability.AGENT_INVENTORY)).isFalse();
    }

    @Test
    void supportsReflectsDeclaredCapabilitiesOnly() {
        ConnectorDescriptor descriptor = new ConnectorDescriptor(
                "Wazuh v4.12.0",
                Set.of(ConnectorCapability.AGENT_INVENTORY, ConnectorCapability.SYSTEM_INVENTORY),
                Instant.now());

        assertThat(descriptor.supports(ConnectorCapability.AGENT_INVENTORY)).isTrue();
        assertThat(descriptor.supports(ConnectorCapability.SYSTEM_INVENTORY)).isTrue();
        // Une capacité non listée est INDISPONIBLE — jamais supposée par défaut.
        assertThat(descriptor.supports(ConnectorCapability.VULNERABILITY_FEED)).isFalse();
    }

    @Test
    void nullCapabilitiesBecomeEmptySetNeverNull() {
        ConnectorDescriptor descriptor = new ConnectorDescriptor("v1", null, null);

        assertThat(descriptor.capabilities()).isNotNull().isEmpty();
    }
}
