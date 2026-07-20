package com.smartsoc.application.alerts;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.intelligence.Observable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
            List<Observable.Raw> observables,
            String rawPayload) {
    }

    /**
     * Le compte rendu des observables accompagne TOUJOURS le résultat,
     * y compris sur un rejeu : il décrit ce que la plateforme a compris
     * du payload reçu, pas ce qu'elle a écrit en base. Un producteur qui
     * rejoue un événement doit continuer à voir son erreur de mapping.
     */
    public record IngestionResult(Alert alert, boolean created,
                                  Observable.ParseResult observables) {
    }

    public IngestionResult ingest(IngestAlertCommand command) {
        String source = command.source() == null ? null : command.source().trim().toLowerCase();
        String externalId = command.externalId() == null ? null : command.externalId().trim();

        // Lecture tolérante : les observables mal formés sont écartés et
        // nommés, l'alerte est ingérée quand même. Perdre une détection
        // parce qu'un de ses observables est invalide serait un très
        // mauvais échange (ADR-009).
        Observable.ParseResult observables = Observable.parseTolerant(command.observables());

        return alertRepository.findBySourceAndExternalId(source, externalId)
                .map(existing -> new IngestionResult(existing, false, observables))
                .orElseGet(() -> insertNew(command, source, externalId, observables));
    }

    private IngestionResult insertNew(IngestAlertCommand command, String source, String externalId,
                                      Observable.ParseResult observables) {
        Alert alert = Alert.ingest(Alert.IngestionData.builder()
                .source(command.source())
                .externalId(command.externalId())
                .title(command.title())
                .description(command.description())
                .severity(command.severity())
                .detectedAt(command.detectedAt())
                .hostname(command.hostname())
                .ruleId(command.ruleId())
                .mitreTechniques(command.mitreTechniques())
                .observables(observables.accepted())
                .rawPayload(command.rawPayload())
                .build());
        try {
            Alert saved = alertRepository.save(alert);
            log.info("Alert ingested: source={}, externalId={}, severity={}, observables={}/{}",
                    saved.getSource(), saved.getExternalId(), saved.getSeverity(),
                    observables.accepted().size(),
                    observables.accepted().size() + observables.rejected().size());
            if (!observables.rejected().isEmpty()) {
                log.warn("Alert {}/{}: {} observable(s) rejected — check the producer mapping",
                        saved.getSource(), saved.getExternalId(), observables.rejected().size());
            }
            eventPublisher.publishEvent(new AlertIngestedEvent(saved));
            return new IngestionResult(saved, true, observables);
        } catch (DataIntegrityViolationException e) {
            // Course perdue contre un webhook identique : l'autre a inséré.
            return alertRepository.findBySourceAndExternalId(source, externalId)
                    .map(existing -> new IngestionResult(existing, false, observables))
                    .orElseThrow(() -> e);
        }
    }
}
