package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.domain.connectors.ConnectorDescriptor;

/**
 * Détecte les capacités réelles de Shuffle (ADR-014 §6.5). Interne au
 * paquet — {@link ShuffleConnectorStatusRecorder} est lui-même
 * infra-only, aucun besoin de traverser la frontière applicative.
 */
interface ShuffleCapabilityProbe {

    /**
     * @param previous descripteur actuel du connecteur — renvoyé tel quel si la
     *                 sonde échoue ou si la dernière détection est encore récente
     */
    ConnectorDescriptor detect(ConnectorDescriptor previous);
}
