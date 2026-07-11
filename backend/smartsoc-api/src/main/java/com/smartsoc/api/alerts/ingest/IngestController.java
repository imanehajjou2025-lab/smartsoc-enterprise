package com.smartsoc.api.alerts.ingest;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.alerts.dto.AlertDtos.IngestAlertRequest;
import com.smartsoc.application.alerts.AlertIngestionService;
import com.smartsoc.application.alerts.AlertIngestionService.IngestAlertCommand;
import com.smartsoc.application.alerts.AlertIngestionService.IngestionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook d'ingestion pour les outils SOC (authentification : X-API-Key).
 * Idempotent : 201 à la première ingestion, 200 avec l'alerte existante si
 * le même événement (source + externalId) est rejoué.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final AlertIngestionService ingestionService;
    private final AlertApiMapper mapper;

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
}
