package com.smartsoc.domain.intelligence;

/**
 * État d'un indicateur AU MOMENT OÙ ON LE REGARDE.
 *
 * <p><b>EXPIRED n'est jamais un fait stocké</b> : il se déduit de
 * {@code validUntil}. Une colonne de statut à maintenir exigerait un
 * batch de péremption ; le jour où ce batch prendrait du retard ou
 * tomberait, des IOC périmés continueraient d'enrichir les alertes en se
 * déclarant ACTIVE — faux en silence, et dans le sens le plus coûteux
 * pour un SOC (du bruit sur des indicateurs qui ne valent plus rien).
 * Déduire l'expiration supprime le batch ET la classe de bug.
 *
 * <p>Seule la RÉVOCATION est un fait stocké : c'est une décision
 * d'analyste (« cet IOC est un faux positif »), pas une conséquence du
 * temps qui passe. Elle survit aux ré-observations du flux qui continue
 * de pousser l'indicateur.
 *
 * <p>Un indicateur n'enrichit une alerte que s'il est ACTIVE.
 */
public enum IndicatorStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
