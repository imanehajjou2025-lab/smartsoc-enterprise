package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertTest {

    private static Alert sampleAlert() {
        return Alert.ingest("Wazuh", "evt-42", "Brute force detected",
                "10 failed SSH logins", Severity.HIGH, Instant.parse("2026-07-11T08:00:00Z"),
                "srv-web-01", "5710", List.of("T1110"), "{\"full\":\"payload\"}");
    }

    @Test
    void ingestNormalizesSourceAndStartsInNewStatus() {
        Alert alert = sampleAlert();

        assertThat(alert.getId()).isNotNull();
        assertThat(alert.getSource()).isEqualTo("wazuh");
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(alert.getReceivedAt()).isNotNull();
        assertThat(alert.getMitreTechniques()).containsExactly("T1110");
        assertThat(alert.getAiScore()).isNull();
        assertThat(alert.getAiVerdict()).isNull();
    }

    @Test
    void ingestRejectsMissingMandatoryFields() {
        assertThatThrownBy(() -> Alert.ingest("wazuh", " ", "title", null,
                Severity.LOW, Instant.now(), null, null, null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("externalId");

        assertThatThrownBy(() -> Alert.ingest("wazuh", "evt-1", "title", null,
                null, Instant.now(), null, null, null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void lifecycleAllowsTheNominalTriageFlow() {
        Alert alert = sampleAlert();

        alert.transitionTo(AlertStatus.ACKNOWLEDGED);
        alert.transitionTo(AlertStatus.IN_PROGRESS);
        alert.transitionTo(AlertStatus.RESOLVED);

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getStatus().isTerminal()).isTrue();
    }

    @Test
    void lifecycleRejectsIllegalTransitions() {
        Alert alert = sampleAlert();

        // NEW -> RESOLVED interdit : une alerte doit être prise en charge d'abord.
        assertThatThrownBy(() -> alert.transitionTo(AlertStatus.RESOLVED))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("NEW")
                .hasMessageContaining("RESOLVED");

        alert.transitionTo(AlertStatus.FALSE_POSITIVE);
        // Statut terminal : plus aucune transition.
        assertThatThrownBy(() -> alert.transitionTo(AlertStatus.ACKNOWLEDGED))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aiAssessmentIsBoundedAndOptional() {
        Alert alert = sampleAlert();

        alert.applyAiAssessment(0.93, AiVerdict.TRUE_POSITIVE);
        assertThat(alert.getAiScore()).isEqualTo(0.93);
        assertThat(alert.getAiVerdict()).isEqualTo(AiVerdict.TRUE_POSITIVE);

        assertThatThrownBy(() -> alert.applyAiAssessment(1.2, AiVerdict.FALSE_POSITIVE))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
