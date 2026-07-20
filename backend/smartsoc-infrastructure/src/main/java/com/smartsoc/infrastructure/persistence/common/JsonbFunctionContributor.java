package com.smartsoc.infrastructure.persistence.common;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Rend l'opérateur de containment JSONB {@code @>} accessible depuis
 * l'API Criteria.
 *
 * <p><b>Pourquoi c'est nécessaire.</b> Un index GIN n'est jamais
 * emprunté par un appel de FONCTION : PostgreSQL ne fait correspondre un
 * index qu'à une expression d'OPÉRATEUR. Écrire
 * {@code jsonb_exists(tags, 'c2')} — le contournement que documente le
 * driver JDBC, l'opérateur natif {@code ?} étant inutilisable puisque le
 * caractère entre en conflit avec les paramètres liés — donne donc un
 * résultat exact mais un Seq Scan systématique. Mesuré sur 20 002
 * indicateurs : 4,053 ms contre 0,118 ms pour la forme {@code @>}, et
 * l'écart croît linéairement avec la taille du référentiel CTI (un MISP
 * abonné aux flux ouverts dépasse rapidement le million d'IOC).
 *
 * <p><b>Pourquoi ça marche.</b> Contrairement à {@code cb.function(...)}
 * qui produit toujours une syntaxe d'appel {@code nom(args)}, une
 * fonction enregistrée par MOTIF est recopiée telle quelle dans le SQL
 * émis : le moteur voit une vraie expression d'opérateur
 * {@code tags @> cast(? as jsonb)}, et l'index GIN redevient éligible.
 * L'opérateur {@code @>}, lui, ne contient aucun caractère qui gênerait
 * JDBC.
 *
 * <p>Découvert par la classe {@code FunctionContributor} via le fichier
 * de service {@code META-INF/services}, donc appliqué à toute unité de
 * persistance sans configuration Spring.
 */
public class JsonbFunctionContributor implements FunctionContributor {

    /** Nom exposé aux Specifications — voir IndicatorRepositoryAdapter. */
    public static final String JSONB_ARRAY_CONTAINS = "jsonb_array_contains";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
                JSONB_ARRAY_CONTAINS,
                "(?1 @> cast(?2 as jsonb))",
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.BOOLEAN));
    }
}
