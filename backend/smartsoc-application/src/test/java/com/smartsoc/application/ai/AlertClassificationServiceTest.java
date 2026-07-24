package com.smartsoc.application.ai;

import com.smartsoc.application.alerts.AlertIngestedEvent;
import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration de la classification : application du verdict, sauvegarde,
 * publication d'événement, et les deux comportements d'indisponibilité —
 * silencieux à l'ingestion, signalé (503) à la demande.
 */
@ExtendWith(MockitoExtension.class)
class AlertClassificationServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertClassifier classifier;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlertClassificationService service;

    private Alert alert;

    @BeforeEach
    void setUp() {
        alert = Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-1")
                .title("Brute force detected")
                .severity(Severity.HIGH)
                .detectedAt(Instant.parse("2026-07-17T08:00:00Z"))
                .build());
    }

    @Test
    void classifyNowAppliesVerdictSavesAndPublishes() {
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);
        when(classifier.classify(alert)).thenReturn(Optional.of(new AlertClassification(
                0.91, AiVerdict.TRUE_POSITIVE, "model-1", Instant.now())));

        Alert result = service.classifyNow(alert.getId());

        assertThat(result.getAiScore()).isEqualTo(0.91);
        assertThat(result.getAiVerdict()).isEqualTo(AiVerdict.TRUE_POSITIVE);
        verify(alertRepository).save(alert);
        ArgumentCaptor<AlertClassifiedEvent> event =
                ArgumentCaptor.forClass(AlertClassifiedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().alert().getId()).isEqualTo(alert.getId());
    }

    @Test
    void classifyNowSignalsUnavailabilityAndLeavesAlertUntouched() {
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(classifier.classify(alert)).thenReturn(Optional.empty());

        UUID alertId = alert.getId();
        assertThatThrownBy(() -> service.classifyNow(alertId))
                .isInstanceOf(AiServiceUnavailableException.class);

        assertThat(alert.getAiScore()).isNull();
        verify(alertRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void classifyNowRejectsUnknownAlert() {
        UUID unknown = UUID.randomUUID();
        when(alertRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.classifyNow(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ingestionPathClassifiesInTheBackground() {
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);
        when(classifier.classify(alert)).thenReturn(Optional.of(new AlertClassification(
                0.15, AiVerdict.FALSE_POSITIVE, "model-1", Instant.now())));

        service.onAlertIngested(new AlertIngestedEvent(alert));

        assertThat(alert.getAiVerdict()).isEqualTo(AiVerdict.FALSE_POSITIVE);
        verify(alertRepository).save(alert);
    }

    @Test
    void ingestionPathDegradesSilentlyWhenClassifierFails() {
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(classifier.classify(alert)).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service.onAlertIngested(new AlertIngestedEvent(alert)))
                .doesNotThrowAnyException();

        assertThat(alert.getAiScore()).isNull();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void ingestionPathDegradesSilentlyWhenClassifierIsUnavailable() {
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(classifier.classify(alert)).thenReturn(Optional.empty());

        assertThatCode(() -> service.onAlertIngested(new AlertIngestedEvent(alert)))
                .doesNotThrowAnyException();

        assertThat(alert.getAiScore()).isNull();
    }
}
