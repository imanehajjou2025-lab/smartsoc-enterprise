package com.smartsoc.application.reputation;

import com.smartsoc.application.connectors.ObservableReputationPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.connectors.VirusTotalCapabilityPort;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ObservableReputation;
import com.smartsoc.domain.reputation.ObservableReputationRepository;
import com.smartsoc.domain.reputation.ReputationVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservableReputationServiceTest {

    @Mock
    private ObservableReputationPort port;

    @Mock
    private ObservableReputationRepository repository;

    @Mock
    private SocConnectorRepository connectorRepository;

    @Mock
    private VirusTotalCapabilityPort capabilityPort;

    private ObservableReputationService service;

    private static ObservableReputation cachedReputation(Instant checkedAt) {
        return ObservableReputation.record(ObservableReputation.Lookup.builder()
                .source("virustotal").type(IndicatorType.IPV4).value("8.8.8.8")
                .maliciousCount(0).suspiciousCount(0).harmlessCount(53).undetectedCount(38)
                .checkedAt(checkedAt).build());
    }

    @BeforeEach
    void createService() {
        service = new ObservableReputationService(port, repository, connectorRepository, capabilityPort, 24L);
    }

    @Test
    void emailIsRejectedWithoutEverCallingThePort() {
        assertThatThrownBy(() -> service.getReputation(IndicatorType.EMAIL, "attacker@evil.test"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("email");

        verify(port, never()).lookup(any(), any());
    }

    @Test
    void freshCacheIsServedWithoutCallingThePort() {
        ObservableReputation fresh = cachedReputation(Instant.now().minus(1, ChronoUnit.HOURS));
        when(repository.findByIdentity("virustotal", IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(Optional.of(fresh));

        ObservableReputation result = service.getReputation(IndicatorType.IPV4, "8.8.8.8");

        assertThat(result).isSameAs(fresh);
        verify(port, never()).lookup(any(), any());
    }

    @Test
    void missingCacheCallsThePortAndCreatesANewEntry() {
        when(repository.findByIdentity("virustotal", IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(Optional.empty());
        when(port.lookup(IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(new ObservableReputationPort.Lookup(1, 0, 40, 10));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(connectorRepository.findByType(ConnectorType.VIRUSTOTAL))
                .thenReturn(Optional.of(SocConnector.notConfigured(ConnectorType.VIRUSTOTAL)));

        ObservableReputation result = service.getReputation(IndicatorType.IPV4, "8.8.8.8");

        assertThat(result.getVerdict()).isEqualTo(ReputationVerdict.MALICIOUS);
        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void staleCacheTriggersARefreshRatherThanANewEntry() {
        ObservableReputation stale = cachedReputation(Instant.now().minus(48, ChronoUnit.HOURS));
        when(repository.findByIdentity("virustotal", IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(Optional.of(stale));
        when(port.lookup(IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(new ObservableReputationPort.Lookup(0, 0, 60, 5));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(connectorRepository.findByType(ConnectorType.VIRUSTOTAL))
                .thenReturn(Optional.of(SocConnector.notConfigured(ConnectorType.VIRUSTOTAL)));

        ObservableReputation result = service.getReputation(IndicatorType.IPV4, "8.8.8.8");

        assertThat(result.getId()).isEqualTo(stale.getId());
        assertThat(result.getHarmlessCount()).isEqualTo(60);
    }

    @Test
    void portFailureWithNoCacheIsPropagatedAndRecordsFailure() {
        when(repository.findByIdentity("virustotal", IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(Optional.empty());
        when(port.lookup(IndicatorType.IPV4, "8.8.8.8"))
                .thenThrow(new SocConnectorException("VirusTotal quota exceeded"));
        SocConnector previouslyConnected = SocConnector.notConfigured(ConnectorType.VIRUSTOTAL);
        previouslyConnected.recordSuccess(Instant.now().minusSeconds(300),
                com.smartsoc.domain.connectors.ConnectorDescriptor.unknown());
        when(connectorRepository.findByType(ConnectorType.VIRUSTOTAL))
                .thenReturn(Optional.of(previouslyConnected));

        assertThatThrownBy(() -> service.getReputation(IndicatorType.IPV4, "8.8.8.8"))
                .isInstanceOf(SocConnectorException.class);

        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED);
    }

    @Test
    void portFailureWithStaleCacheDegradesGracefullyByServingTheStaleValue() {
        ObservableReputation stale = cachedReputation(Instant.now().minus(48, ChronoUnit.HOURS));
        when(repository.findByIdentity("virustotal", IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(Optional.of(stale));
        when(port.lookup(IndicatorType.IPV4, "8.8.8.8"))
                .thenThrow(new SocConnectorException("VirusTotal quota exceeded"));
        SocConnector previouslyConnected = SocConnector.notConfigured(ConnectorType.VIRUSTOTAL);
        previouslyConnected.recordSuccess(Instant.now().minusSeconds(300),
                com.smartsoc.domain.connectors.ConnectorDescriptor.unknown());
        when(connectorRepository.findByType(ConnectorType.VIRUSTOTAL))
                .thenReturn(Optional.of(previouslyConnected));

        ObservableReputation result = service.getReputation(IndicatorType.IPV4, "8.8.8.8");

        assertThat(result).isSameAs(stale);
        verify(repository, never()).save(any());
    }

    @Test
    void appliesTheCapabilityDescriptorOnSuccess() {
        when(repository.findByIdentity("virustotal", IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(Optional.empty());
        when(port.lookup(IndicatorType.IPV4, "8.8.8.8"))
                .thenReturn(new ObservableReputationPort.Lookup(1, 0, 40, 10));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SocConnector connector = SocConnector.notConfigured(ConnectorType.VIRUSTOTAL);
        when(connectorRepository.findByType(ConnectorType.VIRUSTOTAL)).thenReturn(Optional.of(connector));
        ConnectorDescriptor descriptor = new ConnectorDescriptor("Service cloud — pas de version applicable",
                java.util.Set.of(com.smartsoc.domain.connectors.ConnectorCapability.OBSERVABLE_REPUTATION),
                Instant.now());
        when(capabilityPort.detect(connector.getDescriptor())).thenReturn(descriptor);

        service.getReputation(IndicatorType.IPV4, "8.8.8.8");

        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getDescriptor()).isEqualTo(descriptor);
    }
}
