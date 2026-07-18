package com.smartsoc.domain.assets;

/**
 * Statut d'inventaire. Pas de suppression physique : un actif
 * décommissionné garde son historique de corrélation d'alertes.
 * La réactivation est autorisée (un actif peut revenir en service —
 * contrairement à un cas d'enquête clôturé, définitif).
 */
public enum AssetStatus {
    ACTIVE,
    DECOMMISSIONED
}
