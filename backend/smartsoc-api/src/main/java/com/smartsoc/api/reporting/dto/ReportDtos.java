package com.smartsoc.api.reporting.dto;

import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrats REST du contexte reporting (rapports SOC). */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record GenerateReportRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull Instant periodStart,
            @NotNull Instant periodEnd) {
    }

    public record AlertMetricsResponse(long total, Map<Severity, Long> bySeverity, Map<AlertStatus, Long> byStatus) {
    }

    public record IncidentMetricsResponse(long opened, long closed, Double avgResolutionHours) {
    }

    public record SoarMetricsResponse(long started, long completed, long cancelled) {
    }

    public record MitreTechniqueCountResponse(String attackId, long alertCount) {
    }

    public record ReportMetricsResponse(
            AlertMetricsResponse alerts,
            IncidentMetricsResponse incidents,
            SoarMetricsResponse soar,
            long huntQueriesExecuted,
            long mitreDistinctTechniquesCovered,
            List<MitreTechniqueCountResponse> mitreTopTechniques) {
    }

    public record ReportResponse(
            UUID id,
            String title,
            Instant periodStart,
            Instant periodEnd,
            Instant generatedAt,
            String generatedBy,
            ReportMetricsResponse metrics) {
    }

    public record ReportSummaryResponse(
            UUID id,
            String title,
            Instant periodStart,
            Instant periodEnd,
            Instant generatedAt,
            String generatedBy) {
    }
}
