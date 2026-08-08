package com.smartsoc.domain.connectors;

/**
 * Les cinq outils SOC intégrés (ADR-014). Un seul {@code SocConnector}
 * existe par type — ce n'est pas une instance par appel, mais l'état
 * courant de la relation de la plateforme à cet outil.
 */
public enum ConnectorType {
    WAZUH,
    OPENSEARCH,
    MISP,
    VIRUSTOTAL,
    SHUFFLE
}
