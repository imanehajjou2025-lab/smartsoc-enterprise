package com.smartsoc.api.alerts.ingest;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.alerts.dto.AlertDtos.IngestAlertRequest;
import com.smartsoc.api.intelligence.dto.IocDtos.IngestIocBatchRequest;
import com.smartsoc.api.intelligence.dto.IocDtos.IngestIocBatchResponse;
import com.smartsoc.api.intelligence.dto.IocDtos.IocIngestionError;
import com.smartsoc.application.alerts.AlertIngestionService;
import com.smartsoc.application.alerts.AlertIngestionService.IngestAlertCommand;
import com.smartsoc.application.alerts.AlertIngestionService.IngestionResult;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.BatchResult;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhooks d'ingestion pour les outils SOC (authentification : X-API-Key).
 *
 * <p>Alertes : idempotent — 201 à la première ingestion, 200 avec
 * l'alerte existante si le même événement (source + externalId) est
 * rejoué. Indicateurs CTI : upsert par lot sur l'identité (type, valeur).
 *
 * <p>Dans les deux cas, le flux POUSSE vers la plateforme ; SmartSOC
 * n'interroge aucun outil SOC et ne détient aucun identifiant vers eux
 * (ADR-005).
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final AlertIngestionService ingestionService;
    private final AlertApiMapper mapper;
    private final IndicatorFeedIngestionService feedIngestionService;

    @PostMapping("/alerts")
    public ResponseEntity<AlertResponse> ingestAlert(@Valid @RequestBody IngestAlertRequest request) {
        IngestionResult result = ingestionService.ingest(new IngestAlertCommand(
                request.source(),
                request.externalId(),
                request.title(),
                request.description(),
                request.severity(),
                request.detectedAt(),
                request.hostname(),
                request.ruleId(),
                request.mitreTechniques(),
                request.rawPayload() == null ? null : request.rawPayload().toString()));

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(mapper.toResponse(result.alert()));
    }

    /**
     * Alimentation du référentiel CTI par un flux (MISP via Shuffle, OTX…).
     *
     * <p>Le lot est un UPSERT sur l'identité (type, valeur normalisée) :
     * repousser un indicateur déjà connu rafraîchit ses métadonnées au
     * lieu d'échouer — les rejeux du producteur sont sûrs, comme pour les
     * alertes. La valeur peut arriver défangée ou en majuscules, la
     * normalisation appartient au serveur.
     *
     * <p>Toujours 200 avec un compte rendu : les indicateurs valides
     * entrent même si d'autres sont rejetés, et chaque rejet est nommé
     * (index, valeur, code) pour que le producteur corrige sa source.
     */
    @PostMapping("/iocs")
    public IngestIocBatchResponse ingestIocs(@Valid @RequestBody IngestIocBatchRequest request) {
        BatchResult result = feedIngestionService.ingestBatch(
                request.feedSource(),
                request.indicators().stream()
                        .map(item -> new FeedObservation(
                                item.type(), item.value(), item.confidence(), item.tlp(),
                                item.externalId(), item.description(), item.tags(),
                                item.observedAt(), item.validUntil()))
                        .toList());

        return new IngestIocBatchResponse(
                result.received(), result.created(), result.updated(), result.rejected(),
                result.failures().stream()
                        .map(f -> new IocIngestionError(f.index(), f.value(), f.code(), f.message()))
                        .toList());
    }
}
