package com.smartsoc.application.alerts;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Ingestion idempotente des alertes : rejouer le même webhook (même couple
 * source/externalId) renvoie l'alerte existante au lieu d'une erreur — les
 * outils SOC réémettent fréquemment (retries, redémarrages de pipeline).
 *
 * Volontairement SANS @Transactional englobant : en cas de course entre deux
 * webhooks identiques, la violation d'unicité avorterait la transaction et
 * empêcherait la relecture ; chaque opération est atomique individuellement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertIngestionService {

    private final AlertRepository alertRepository;

    public record IngestAlertCommand(
            String source,
            String externalId,
            String title,
            String description,
            Severity severity,
            Instant detectedAt,
            String hostname,
            String ruleId,
            List<String> mitreTechniques,
            String rawPayload) {
    }

    public record IngestionResult(Alert alert, boolean created) {
    }

    public IngestionResult ingest(IngestAlertCommand command) {
        String source = command.source() == null ? null : command.source().trim().toLowerCase();
        String externalId = command.externalId() == null ? null : command.externalId().trim();

        return alertRepository.findBySourceAndExternalId(source, externalId)
                .map(existing -> new IngestionResult(existing, false))
                .orElseGet(() -> insertNew(command, source, externalId));
    }

    private IngestionResult insertNew(IngestAlertCommand command, String source, String externalId) {
        Alert alert = Alert.ingest(
                command.source(),
                command.externalId(),
                command.title(),
                command.description(),
                command.severity(),
                command.detectedAt(),
                command.hostname(),
                command.ruleId(),
                command.mitreTechniques(),
                command.rawPayload());
        try {
            Alert saved = alertRepository.save(alert);
            log.info("Alert ingested: source={}, externalId={}, severity={}",
                    saved.getSource(), saved.getExternalId(), saved.getSeverity());
            return new IngestionResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Course perdue contre un webhook identique : l'autre a inséré.
            return alertRepository.findBySourceAndExternalId(source, externalId)
                    .map(existing -> new IngestionResult(existing, false))
                    .orElseThrow(() -> e);
        }
    }
}
