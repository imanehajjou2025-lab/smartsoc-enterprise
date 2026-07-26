package com.smartsoc.api.reporting;

import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.GenerateReportRequest;
import com.smartsoc.api.reporting.dto.ReportDtos.ReportResponse;
import com.smartsoc.api.reporting.dto.ReportDtos.ReportSummaryResponse;
import com.smartsoc.application.reporting.ReportExporter;
import com.smartsoc.application.reporting.ReportGenerationService;
import com.smartsoc.application.reporting.ReportQueryService;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Rapports SOC : instantanés figés d'indicateurs sur une période passée.
 * Génération réservée à l'encadrement (ADR-005 rôle SOC_MANAGER), lecture
 * ouverte à tout authentifié (VIEWER inclus, même doctrine que le
 * dashboard).
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final String MANAGER_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER')";

    private final ReportGenerationService generationService;
    private final ReportQueryService queryService;
    private final ReportExporter exporter;
    private final ReportApiMapper mapper;

    @PostMapping
    @PreAuthorize(MANAGER_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse generate(@Valid @RequestBody GenerateReportRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        Report report = generationService.generate(
                request.title(), request.periodStart(), request.periodEnd(), jwt.getSubject());
        return mapper.toResponse(report);
    }

    @GetMapping
    public PageResponse<ReportSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(
                queryService.search(new ReportQuery(PageQuery.of(page, size))), mapper::toSummary);
    }

    @GetMapping("/{id}")
    public ReportResponse get(@PathVariable UUID id) {
        return mapper.toResponse(queryService.get(id));
    }

    @GetMapping(value = "/{id}/export/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID id) {
        Report report = queryService.get(id);
        return download(exporter.toCsv(report), report.getId(), "csv", MediaType.parseMediaType("text/csv"));
    }

    @GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        Report report = queryService.get(id);
        return download(exporter.toPdf(report), report.getId(), "pdf", MediaType.APPLICATION_PDF);
    }

    private static ResponseEntity<byte[]> download(byte[] content, UUID reportId, String extension,
                                                     MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("report-%s.%s".formatted(reportId, extension))
                        .build().toString())
                .body(content);
    }
}
