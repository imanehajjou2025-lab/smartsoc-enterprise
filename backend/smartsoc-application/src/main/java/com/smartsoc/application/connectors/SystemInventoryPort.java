package com.smartsoc.application.connectors;

import java.util.Optional;

/**
 * Port vers le détail système d'UN agent (syscollector Wazuh, ADR-014) —
 * « décris cette machine », distinct de {@link AgentInventoryPort}
 * (« quels sont les agents ? »). Un appel par agent, séparé de la liste
 * de base : un agent jamais scanné n'a légitimement aucune donnée
 * ({@link Optional#empty()}), ce n'est pas une erreur.
 *
 * <p>Lève {@link SocConnectorException} uniquement sur un échec de
 * connexion réel — jamais pour une absence de données.
 */
public interface SystemInventoryPort {

    Optional<SystemDetails> describe(String agentExternalId);

    /**
     * @param operatingSystemDetail description enrichie (build, édition) — plus
     *                              précise que celle de la liste d'agents de base
     * @param hardwareSummary       résumé matériel lisible (CPU, RAM), {@code null}
     *                              si l'endpoint matériel n'a rien renvoyé
     */
    record SystemDetails(String operatingSystemDetail, String hardwareSummary) {
    }
}
