package com.smartsoc.domain.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primitives de normalisation des clés de corrélation. Deux garanties y
 * sont figées : l'indépendance à la locale de la JVM (sans quoi la clé
 * Java divergerait du {@code lower()} SQL), et la distinction entre la
 * variante tolérante au {@code null} et celle qui l'exclut.
 */
class TextNormalizationTest {

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void lowerTrimIsLocaleIndependent() {
        // En locale turque, "I".toLowerCase() rend 'ı' (i sans point) : la
        // clé Java ne correspondrait plus au lower() de PostgreSQL.
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(TextNormalization.lowerTrim("  SRV-WEB-01.CORP  ")).isEqualTo("srv-web-01.corp");
        assertThat(TextNormalization.lowerTrimRequired("  INVOICE@EVIL.COM "))
                .isEqualTo("invoice@evil.com");
    }

    @Test
    void lowerTrimToleratesNullBecauseAbsenceIsSometimesLegitimate() {
        // Cas métier réel : un tag vide qu'on écarte ensuite.
        assertThat(TextNormalization.lowerTrim(null)).isNull();
        assertThat(TextNormalization.blankToNull("   ")).isNull();
        assertThat(TextNormalization.blankToNull("  c2 ")).isEqualTo("c2");
    }

    @Test
    void lowerTrimRequiredFailsFastInsteadOfLeakingANullDownstream() {
        // La variante « required » rend la précondition STRUCTURELLE : au
        // lieu de propager un null qui explosera plus loin (au fond d'une
        // expression régulière, par exemple), elle échoue immédiatement et
        // nommément. C'est ce qui ferme les S2259 sur IndicatorType.
        assertThatThrownBy(() -> TextNormalization.lowerTrimRequired(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value");
    }
}
