package com.smartsoc.domain.common;

import java.util.Locale;
import java.util.Objects;

/**
 * Primitives partagées de normalisation des CLÉS DE CORRÉLATION.
 *
 * Une clé de corrélation (hostname d'actif, valeur d'IOC) doit être
 * normalisée EXACTEMENT de la même façon partout : deux écritures de la
 * même réalité qui ne se normalisent pas pareil ne se rejoignent jamais.
 * L'échec est silencieux — ni erreur, ni trace, juste « 0 résultat ».
 * Ces primitives existent pour qu'aucun contexte ne re-dérive sa propre
 * version de la règle.
 *
 * {@code Locale.ROOT} est obligatoire et non négociable :
 * {@code toLowerCase()} sans locale dépend de la JVM — en locale turque,
 * 'I' devient 'ı' (i sans point) et non 'i' — et divergerait alors du
 * {@code lower()} de PostgreSQL utilisé par les jointures de corrélation
 * et les contraintes CHECK qui gravent la normalisation en base.
 */
public final class TextNormalization {

    private TextNormalization() {
    }

    /**
     * Minuscules + espaces de bord retirés : le {@code lower(trim(...))} de
     * SQL. Tolère {@code null} et le laisse passer — utile quand l'absence
     * de valeur est un cas métier légitime (ex. un tag vide qu'on écarte
     * ensuite).
     */
    public static String lowerTrim(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Comme {@link #lowerTrim(String)}, mais pour une valeur dont l'absence
     * a DÉJÀ été exclue en amont — et le garantit structurellement.
     *
     * <p>Sans cette variante, un appelant qui sait sa valeur non nulle
     * déréférence quand même le résultat d'une méthode déclarée nullable :
     * l'analyse statique y voit un déréférencement de {@code null}
     * (java:S2259), et surtout la sûreté ne repose que sur la discipline de
     * l'appelant. Un futur appel direct à la méthode privée rendrait le
     * risque réel.
     *
     * <p>Ici la précondition est explicite et VÉRIFIÉE : si elle est
     * violée, l'échec est immédiat et nommé, plutôt qu'un
     * {@code NullPointerException} obscur au fond d'une expression
     * régulière.
     */
    public static String lowerTrimRequired(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    }

    /** {@code null} si la valeur est absente ou vide, sinon la valeur sans espaces de bord. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
