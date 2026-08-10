package com.smartsoc.application.connectors;

import com.smartsoc.domain.assets.AgentConnectionStatus;

import java.time.Instant;
import java.util.List;

/**
 * Port vers l'inventaire d'agents d'un outil SOC (ADR-014) — nommé
 * d'après le BESOIN de la plateforme, pas d'après Wazuh : un autre
 * fournisseur d'inventaire pourra implémenter ce même port demain sans
 * changer une ligne de {@code AgentSyncService}.
 *
 * <p>Contrat : lève {@link SocConnectorException} si l'outil est
 * injoignable. Un agent individuellement mal formé n'est jamais un
 * échec de tout l'appel — c'est {@code AgentSnapshot} qui absorbe les
 * champs absents en les représentant honnêtement à {@code null}.
 */
public interface AgentInventoryPort {

    List<AgentSnapshot> listAgents();

    /**
     * Ce que la plateforme retient d'un agent, déjà traduit par l'ACL —
     * jamais le modèle brut de l'outil. Les champs {@code null} sont des
     * FAITS (ex. un agent jamais connecté n'a pas d'IP ni de système
     * observable), pas des erreurs de lecture.
     *
     * @param externalId    identifiant chez l'outil source — clé de réconciliation
     * @param hostname      nom déclaré par l'outil, PAS encore normalisé (voir Asset)
     * @param ipAddress     {@code null} si absente ou non significative (ex. "any")
     * @param operatingSystem description lisible, {@code null} si jamais observée
     * @param lastSeenAt    dernier contact connu, {@code null} si jamais connecté
     *                      ou si l'outil renvoie une valeur sentinelle non exploitable
     * @param connectionStatus état de connexion rapporté par l'outil, {@code null}
     *                      si l'outil ne fournit pas cette notion ou renvoie une
     *                      valeur non reconnue (dégradation silencieuse)
     */
    record AgentSnapshot(
            String externalId,
            String hostname,
            String ipAddress,
            String operatingSystem,
            Instant lastSeenAt,
            AgentConnectionStatus connectionStatus) {
    }
}
