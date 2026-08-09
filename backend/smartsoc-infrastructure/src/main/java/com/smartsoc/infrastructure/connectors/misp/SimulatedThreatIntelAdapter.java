package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.ThreatIntelPort;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Stub du connecteur MISP (mode simulation, ADR-014) : la plateforme se
 * démontre de bout en bout sans le SOC réel. Deux indicateurs plausibles,
 * un par type courant (IP, domaine), avec confiance et TLP explicites —
 * même doctrine que {@code SimulatedVulnerabilityFeedAdapter}.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.misp.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedThreatIntelAdapter implements ThreatIntelPort {

    @Override
    public List<FeedObservation> listIndicators() {
        Instant now = Instant.now();
        return List.of(
                new FeedObservation(IndicatorType.IPV4, "203.0.113.42", 65, TlpMarking.AMBER,
                        "sim-misp-ioc-1", "Événement MISP (simulation) : Scan de reconnaissance",
                        Set.of(), now.minusSeconds(3600), null),
                new FeedObservation(IndicatorType.DOMAIN, "phishing-simule.test", 90, TlpMarking.AMBER,
                        "sim-misp-ioc-2", "Événement MISP (simulation) : Campagne de phishing",
                        Set.of(), now.minusSeconds(7200), null));
    }
}
