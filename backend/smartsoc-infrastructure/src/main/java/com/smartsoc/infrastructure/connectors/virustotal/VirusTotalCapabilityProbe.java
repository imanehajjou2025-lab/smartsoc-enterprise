package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.VirusTotalCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;

/**
 * VirusTotal est un service SaaS public sans endpoint de version exposé
 * (contrat v3 vérifié) : aucune sonde réseau à faire, la capacité est
 * structurellement acquise dès que le connecteur est configuré en live.
 * Le descripteur le dit explicitement plutôt que de laisser un champ
 * vide muet — voir {@link VirusTotalCapabilityPort}.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class VirusTotalCapabilityProbe implements VirusTotalCapabilityPort {

    static final String NO_VERSION_CONCEPT = "Service cloud — pas de version applicable";

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return new ConnectorDescriptor(NO_VERSION_CONCEPT,
                EnumSet.of(ConnectorCapability.OBSERVABLE_REPUTATION), Instant.now());
    }
}
