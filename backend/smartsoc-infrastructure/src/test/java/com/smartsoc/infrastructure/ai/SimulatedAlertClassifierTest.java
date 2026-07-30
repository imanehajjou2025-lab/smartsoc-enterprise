package com.smartsoc.infrastructure.ai;

import com.smartsoc.application.ai.AlertClassification;
import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.AiZone;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le stub de simulation doit être crédible ET déterministe : mêmes entrées,
 * même verdict — condition pour des démos et des tests reproductibles.
 */
class SimulatedAlertClassifierTest {

    private final SimulatedAlertClassifier classifier = new SimulatedAlertClassifier();

    @ParameterizedTest
    @EnumSource(Severity.class)
    void scoreStaysWithinBoundsForEverySeverity(Severity severity) {
        AlertClassification result = classifier.classify(alertOf(severity)).orElseThrow();

        assertThat(result.score()).isBetween(0.02, 0.98);
        assertThat(result.modelVersion()).isEqualTo(SimulatedAlertClassifier.MODEL_VERSION);
        assertThat(result.classifiedAt()).isNotNull();
    }

    @Test
    void sameAlertAlwaysGetsTheSameScoreAndVerdict() {
        Alert alert = alertOf(Severity.MEDIUM);

        AlertClassification first = classifier.classify(alert).orElseThrow();
        AlertClassification second = classifier.classify(alert).orElseThrow();

        assertThat(first.score()).isEqualTo(second.score());
        assertThat(first.verdict()).isEqualTo(second.verdict());
    }

    @Test
    void verdictIsConsistentWithTheScoreThreshold() {
        for (Severity severity : Severity.values()) {
            AlertClassification result = classifier.classify(alertOf(severity)).orElseThrow();
            AiVerdict expected = result.score() >= 0.5
                    ? AiVerdict.TRUE_POSITIVE : AiVerdict.FALSE_POSITIVE;
            assertThat(result.verdict()).isEqualTo(expected);
        }
    }

    @Test
    void severityDrivesTheVerdictTendency() {
        // Le bruit est de ±0.08 : CRITICAL (base 0.90) est toujours TP,
        // INFO (base 0.12) toujours FP, quel que soit l'id.
        assertThat(classifier.classify(alertOf(Severity.CRITICAL)).orElseThrow().verdict())
                .isEqualTo(AiVerdict.TRUE_POSITIVE);
        assertThat(classifier.classify(alertOf(Severity.INFO)).orElseThrow().verdict())
                .isEqualTo(AiVerdict.FALSE_POSITIVE);
    }

    @Test
    void zoneFollowsTheSameThresholdsAsTheRealClassifierAndNeverForcesAnOverride() {
        for (Severity severity : Severity.values()) {
            AlertClassification result = classifier.classify(alertOf(severity)).orElseThrow();
            AiZone expected = result.score() >= 0.75 ? AiZone.SOAR_ESCALATION
                    : result.score() >= 0.50 ? AiZone.ANALYST_REVIEW : AiZone.ARCHIVE;
            assertThat(result.zone()).isEqualTo(expected);
            // Aucune preuve réelle (IOC, historique) en simulation : jamais de dérogation forcée.
            assertThat(result.hardOverride()).isFalse();
            assertThat(result.justifications()).isNotEmpty();
        }
    }

    private static Alert alertOf(Severity severity) {
        return Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-sim")
                .title("Simulation fixture")
                .severity(severity)
                .detectedAt(Instant.parse("2026-07-17T08:00:00Z"))
                .build());
    }
}
