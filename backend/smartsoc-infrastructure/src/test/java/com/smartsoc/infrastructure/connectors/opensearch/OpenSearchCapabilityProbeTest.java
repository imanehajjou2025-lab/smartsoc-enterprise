package com.smartsoc.infrastructure.connectors.opensearch;

import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import org.junit.jupiter.api.BeforeEach;
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
class OpenSearchCapabilityProbeTest {

    @Mock
    private OpenSearchVulnerabilityClient client;

    private OpenSearchCapabilityProbe probe;

    @BeforeEach
    void createProbe() {
        probe = new OpenSearchCapabilityProbe(client);
    }

    @Test
    void detectsTheRealVersionAndCapabilities() {
        when(client.version()).thenReturn(
                new OpenSearchVersionResponse("wazuh-cluster", new OpenSearchVersionResponse.Version("7.10.2")));

        ConnectorDescriptor descriptor = probe.detect(ConnectorDescriptor.unknown());

        assertThat(descriptor.detectedVersion()).isEqualTo("7.10.2");
        assertThat(descriptor.capabilities()).containsExactlyInAnyOrder(
                ConnectorCapability.EVENT_SEARCH, ConnectorCapability.VULNERABILITY_FEED);
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeIsStillFresh() {
        ConnectorDescriptor recent = new ConnectorDescriptor("7.10.2",
                java.util.Set.of(ConnectorCapability.EVENT_SEARCH), Instant.now().minusSeconds(60));

        ConnectorDescriptor result = probe.detect(recent);

        assertThat(result).isEqualTo(recent);
        verify(client, never()).version();
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeFails() {
        ConnectorDescriptor stale = new ConnectorDescriptor("7.10.2",
                java.util.Set.of(ConnectorCapability.EVENT_SEARCH), Instant.now().minusSeconds(7200));
        when(client.version()).thenThrow(new RuntimeException("timeout"));

        ConnectorDescriptor result = probe.detect(stale);

        assertThat(result).isEqualTo(stale);
    }
}
