package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.PageQuery;

import java.time.Instant;

/**
 * Critères de recherche du référentiel IOC. Tout filtre null est ignoré ;
 * `search` cherche dans la valeur et la description. Tri fixe : dernière
 * observation d'abord (un IOC frais vaut mieux qu'un IOC ancien).
 *
 * <p><b>{@code evaluatedAt} n'est pas un filtre, c'est l'INSTANT DE
 * RÉFÉRENCE du statut.</b> Comme EXPIRED se déduit de {@code validUntil}
 * plutôt que d'être stocké (voir {@link IndicatorStatus}), filtrer sur un
 * statut suppose de fixer l'instant auquel on l'évalue. L'expliciter ici
 * garantit que la liste ET son total s'évaluent au MÊME instant : deux
 * appels à {@code now()} à quelques millisecondes d'écart de part et
 * d'autre d'une date de péremption suffiraient à faire diverger le
 * compteur de la liste — la leçon du compteur de corrélation des actifs.
 */
public record IndicatorQuery(
        IndicatorType type,
        IndicatorStatus status,
        String feedSource,
        String tag,
        Integer minConfidence,
        String search,
        Instant evaluatedAt,
        PageQuery page) {

    public IndicatorQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }
}
