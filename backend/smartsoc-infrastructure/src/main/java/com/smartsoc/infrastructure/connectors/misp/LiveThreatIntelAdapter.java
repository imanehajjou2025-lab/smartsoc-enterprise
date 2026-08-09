package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.connectors.ThreatIntelPort;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptateur live du port CTI (ADR-014 phase 2) : appelle la vraie API
 * MISP via Feign, protégé par circuit breaker — même patron que
 * {@code LiveAgentInventoryAdapter}/{@code LiveVulnerabilityFeedAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.misp.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveThreatIntelAdapter implements ThreatIntelPort {

    static final String CIRCUIT_BREAKER = "mispThreatIntel";
    private static final int RESULT_LIMIT = 1000;

    private final MispClient client;
    private final MispIndicatorMapper mapper;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "indicatorsUnavailable")
    public List<FeedObservation> listIndicators() {
        MispAttributesSearchResponse response = client.restSearch(
                new MispClient.RestSearchRequest(RESULT_LIMIT, true));
        return mapper.toObservations(response);
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private List<FeedObservation> indicatorsUnavailable(Throwable cause) {
        log.warn("MISP threat intel feed unavailable: {}", cause.getMessage());
        throw new SocConnectorException("MISP threat intel feed unavailable: " + cause.getMessage(), cause);
    }
}
