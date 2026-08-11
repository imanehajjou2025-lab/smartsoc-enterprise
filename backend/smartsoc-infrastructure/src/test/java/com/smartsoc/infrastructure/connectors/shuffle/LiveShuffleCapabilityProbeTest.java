package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveShuffleCapabilityProbeTest {

    @Mock
    private ShuffleClient client;

    private LiveShuffleCapabilityProbe probe;

    @BeforeEach
    void createProbe() {
        probe = new LiveShuffleCapabilityProbe(client);
    }

    @Test
    void detectsTheRealEnvironmentAndWorkflowCapabilities() {
        when(client.environments()).thenReturn(
                List.of(new ShuffleEnvironmentResponse("Shuffle", "onprem", "docker")));

        ConnectorDescriptor descriptor = probe.detect(ConnectorDescriptor.unknown());

        assertThat(descriptor.detectedVersion()).isEqualTo("Shuffle (onprem/docker)");
        assertThat(descriptor.capabilities()).containsExactlyInAnyOrder(
                ConnectorCapability.WORKFLOW_TRIGGER, ConnectorCapability.WORKFLOW_STATUS);
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeIsStillFresh() {
        ConnectorDescriptor recent = new ConnectorDescriptor("Shuffle (onprem/docker)",
                java.util.Set.of(ConnectorCapability.WORKFLOW_TRIGGER), Instant.now().minusSeconds(60));

        ConnectorDescriptor result = probe.detect(recent);

        assertThat(result).isEqualTo(recent);
        verify(client, never()).environments();
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeFails() {
        ConnectorDescriptor stale = new ConnectorDescriptor("Shuffle (onprem/docker)",
                java.util.Set.of(ConnectorCapability.WORKFLOW_TRIGGER), Instant.now().minusSeconds(7200));
        when(client.environments()).thenThrow(new RuntimeException("timeout"));

        ConnectorDescriptor result = probe.detect(stale);

        assertThat(result).isEqualTo(stale);
    }
}
