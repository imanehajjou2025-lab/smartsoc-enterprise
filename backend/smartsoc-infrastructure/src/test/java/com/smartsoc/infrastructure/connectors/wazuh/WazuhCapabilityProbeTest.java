package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WazuhCapabilityProbeTest {

    @Mock
    private WazuhAgentApiClient client;

    @Test
    void detectsTheRealVersionAndBaseCapabilities() {
        var properties = new ConnectorProperties(
                new ConnectorProperties.Wazuh("live", "https://wazuh", "u", "p",
                        new ConnectorProperties.Wazuh.Actions("simulation", null, null)),
                null, null, null, null);
        var probe = new WazuhCapabilityProbe(client, properties);
        when(client.version()).thenReturn(new WazuhVersionResponse(new WazuhVersionResponse.Data("4.12.0")));

        ConnectorDescriptor descriptor = probe.detect(ConnectorDescriptor.unknown());

        assertThat(descriptor.detectedVersion()).isEqualTo("4.12.0");
        assertThat(descriptor.capabilities()).containsExactlyInAnyOrder(
                ConnectorCapability.AGENT_INVENTORY,
                ConnectorCapability.SYSTEM_INVENTORY,
                ConnectorCapability.MANAGER_STATS);
    }

    @Test
    void announcesAgentControlOnlyWhenTheActionsSubContextIsAlsoLive() {
        var properties = new ConnectorProperties(
                new ConnectorProperties.Wazuh("live", "https://wazuh", "u", "p",
                        new ConnectorProperties.Wazuh.Actions("live", "smartsoc-actuator", "pw")),
                null, null, null, null);
        var probe = new WazuhCapabilityProbe(client, properties);
        when(client.version()).thenReturn(new WazuhVersionResponse(new WazuhVersionResponse.Data("4.12.0")));

        ConnectorDescriptor descriptor = probe.detect(ConnectorDescriptor.unknown());

        assertThat(descriptor.capabilities()).contains(ConnectorCapability.AGENT_CONTROL);
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeIsStillFresh() {
        var properties = new ConnectorProperties(
                new ConnectorProperties.Wazuh("live", "https://wazuh", "u", "p",
                        new ConnectorProperties.Wazuh.Actions("simulation", null, null)),
                null, null, null, null);
        var probe = new WazuhCapabilityProbe(client, properties);
        ConnectorDescriptor recent = new ConnectorDescriptor("4.12.0",
                java.util.Set.of(ConnectorCapability.AGENT_INVENTORY), Instant.now().minusSeconds(60));

        ConnectorDescriptor result = probe.detect(recent);

        assertThat(result).isEqualTo(recent);
        verify(client, never()).version();
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeFails() {
        var properties = new ConnectorProperties(
                new ConnectorProperties.Wazuh("live", "https://wazuh", "u", "p",
                        new ConnectorProperties.Wazuh.Actions("simulation", null, null)),
                null, null, null, null);
        var probe = new WazuhCapabilityProbe(client, properties);
        ConnectorDescriptor stale = new ConnectorDescriptor("4.11.0",
                java.util.Set.of(ConnectorCapability.AGENT_INVENTORY), Instant.now().minusSeconds(7200));
        when(client.version()).thenThrow(new RuntimeException("timeout"));

        ConnectorDescriptor result = probe.detect(stale);

        assertThat(result).isEqualTo(stale);
    }
}
