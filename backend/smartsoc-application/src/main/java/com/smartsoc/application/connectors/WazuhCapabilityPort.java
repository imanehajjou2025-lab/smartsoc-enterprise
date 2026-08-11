package com.smartsoc.application.connectors;

import com.smartsoc.domain.connectors.ConnectorDescriptor;

/**
 * Détecte la version réelle de l'API Wazuh et les capacités qui en
 * découlent (ADR-014 §6.5, {@code CapabilityProbe} de
 * {@code CONNECTORS-REFERENCE.md} §1). Jamais de valeur supposée.
 */
public interface WazuhCapabilityPort {

    /**
     * @param previous descripteur actuel du connecteur — renvoyé tel quel si la
     *                 sonde échoue ou si la dernière détection est encore récente
     */
    ConnectorDescriptor detect(ConnectorDescriptor previous);
}
