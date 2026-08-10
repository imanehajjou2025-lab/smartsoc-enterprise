package com.smartsoc.domain.assets;

/**
 * Statut de connexion RAPPORTÉ par un connecteur d'agents (Wazuh) — distinct
 * du {@link AssetStatus} (cycle de vie de l'actif dans l'inventaire). Un
 * actif peut rester {@code ACTIVE} en inventaire tout en étant
 * {@code DISCONNECTED} côté agent : deux notions différentes, jamais
 * confondues. {@code null} sur {@link Asset} pour un actif enregistré à la
 * main, jamais rapporté par un connecteur.
 */
public enum AgentConnectionStatus {
    ACTIVE,
    DISCONNECTED,
    NEVER_CONNECTED
}
