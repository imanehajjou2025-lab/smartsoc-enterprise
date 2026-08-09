package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.ObservableReputationPort;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Stub du connecteur VirusTotal (mode simulation, ADR-014) : la
 * plateforme se démontre de bout en bout sans le SOC réel. Verdict
 * plausible dérivé de la valeur elle-même (déterministe, pas aléatoire)
 * pour qu'une même valeur redemandée en démo réponde toujours pareil.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedObservableReputationAdapter implements ObservableReputationPort {

    @Override
    public Lookup lookup(IndicatorType type, String normalizedValue) {
        String lower = normalizedValue.toLowerCase(Locale.ROOT);
        boolean looksMalicious = lower.contains("evil") || lower.contains("malicious") || lower.contains("phishing");
        if (looksMalicious) {
            return new Lookup(8, 3, 2, 15);
        }
        return new Lookup(0, 0, 55, 15);
    }
}
