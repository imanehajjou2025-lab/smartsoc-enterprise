package com.smartsoc.domain.common;

import java.util.Locale;

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

    /** Minuscules + espaces de bord retirés : le {@code lower(trim(...))} de SQL. */
    public static String lowerTrim(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /** {@code null} si la valeur est absente ou vide, sinon la valeur sans espaces de bord. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
