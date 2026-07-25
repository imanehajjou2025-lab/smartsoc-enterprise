package com.smartsoc.domain.hunting;

/**
 * Visibilité déclarée d'une requête de chasse sauvegardée.
 *
 * <p><b>Non appliquée en V1.</b> Le champ est stocké et restituable, mais
 * AUCUN filtrage par visibilité n'existe encore : une requête {@code
 * PRIVATE} reste aujourd'hui lisible par tout utilisateur authentifié,
 * exactement comme le reste de la plateforme ne cloisonne les données par
 * utilisateur nulle part ailleurs. Le champ existe pour que l'évolution
 * future (restreindre {@code PRIVATE} à son auteur via {@code createdBy},
 * déjà porté par chaque entité) n'exige aucune migration de schéma.
 */
public enum HuntVisibility {
    PRIVATE,
    TEAM
}
