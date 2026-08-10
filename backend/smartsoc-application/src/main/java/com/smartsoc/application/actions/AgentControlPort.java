package com.smartsoc.application.actions;

/**
 * Port d'action RÉELLE sur un agent Wazuh (ADR-014 phase 5) — SÉPARÉ des
 * ports de lecture ({@code AgentInventoryPort}) : identifiants dédiés
 * (jamais le compte lecture seule), aucun planificateur, aucun retry
 * automatique (rejouer un redémarrage, c'est agir deux fois).
 *
 * <p>Contrat : lève {@link com.smartsoc.application.connectors.SocConnectorException}
 * si l'appel échoue — jamais avalé, jamais retenté silencieusement.
 */
public interface AgentControlPort {

    void restart(String wazuhAgentId);

    /**
     * Active-response {@code firewall-drop} — seule commande réellement
     * configurée sur le manager SOC (confirmé en réel, ADR-014 phase 5) :
     * bloque {@code ipAddress} sur le pare-feu local de l'agent ciblé.
     */
    void blockIp(String wazuhAgentId, String ipAddress);
}
