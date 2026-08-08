package com.smartsoc.domain.connectors;

/**
 * Une capacité que peut exposer un connecteur, une fois sa version réelle
 * détectée (ADR-014 §6.5). Une capacité absente est déclarée comme telle
 * dans la console — jamais silencieusement masquée.
 */
public enum ConnectorCapability {
    AGENT_INVENTORY,
    SYSTEM_INVENTORY,
    MANAGER_STATS,
    VULNERABILITY_FEED,
    AGENT_CONTROL,
    EVENT_SEARCH,
    THREAT_INTEL,
    OBSERVABLE_REPUTATION,
    WORKFLOW_TRIGGER,
    WORKFLOW_STATUS
}
