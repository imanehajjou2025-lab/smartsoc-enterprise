package com.smartsoc.infrastructure.connectors.misp;

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
class MispCapabilityProbeTest {

    @Mock
    private MispClient client;

    private MispCapabilityProbe probe;

    @BeforeEach
    void createProbe() {
        probe = new MispCapabilityProbe(client);
    }

    @Test
    void detectsTheRealVersionAndThreatIntelCapability() {
        when(client.version()).thenReturn(new MispVersionResponse("2.5.44"));

        ConnectorDescriptor descriptor = probe.detect(ConnectorDescriptor.unknown());

        assertThat(descriptor.detectedVersion()).isEqualTo("2.5.44");
        assertThat(descriptor.capabilities()).containsExactly(ConnectorCapability.THREAT_INTEL);
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeIsStillFresh() {
        ConnectorDescriptor recent = new ConnectorDescriptor("2.5.44",
                java.util.Set.of(ConnectorCapability.THREAT_INTEL), Instant.now().minusSeconds(60));

        ConnectorDescriptor result = probe.detect(recent);

        assertThat(result).isEqualTo(recent);
        verify(client, never()).version();
    }

    @Test
    void keepsThePreviousDescriptorWhenTheProbeFails() {
        ConnectorDescriptor stale = new ConnectorDescriptor("2.5.43",
                java.util.Set.of(ConnectorCapability.THREAT_INTEL), Instant.now().minusSeconds(7200));
        when(client.version()).thenThrow(new RuntimeException("timeout"));

        ConnectorDescriptor result = probe.detect(stale);

        assertThat(result).isEqualTo(stale);
    }
}
