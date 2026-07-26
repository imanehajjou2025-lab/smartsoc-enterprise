package com.smartsoc.infrastructure.reporting;

import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportMetrics;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExporterAdapterTest {

    private final ReportExporterAdapter exporter = new ReportExporterAdapter();

    private static Report sampleReport() {
        ReportMetrics metrics = new ReportMetrics(
                new AlertPeriodMetrics(1, Map.of(Severity.CRITICAL, 1L), Map.of(AlertStatus.NEW, 1L)),
                new IncidentPeriodMetrics(0, 1, 4.5),
                new ExecutionPeriodMetrics(0, 0, 0),
                0, 1, List.of(new MitreCoverageCount("T1110", 1)));
        return Report.generate("Hebdo SOC",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-08T00:00:00Z"),
                "admin", metrics);
    }

    @Test
    void csvExportPreservesAccentedFrenchLabelsAsUtf8() {
        // Régression : "Content-Type: text/csv" sans charset retombe en
        // ISO-8859-1 côté client HTTP — vérifié ici sur les octets bruts,
        // indépendamment de tout en-tête (voir ReportController).
        String csv = new String(exporter.toCsv(sampleReport()), StandardCharsets.UTF_8);

        assertThat(csv).contains("Sévérité: CRITICAL").contains("Clôturés").contains("Résolution moyenne");
    }

    @Test
    void csvExportStartsWithTheExpectedHeaderRow() {
        String csv = new String(exporter.toCsv(sampleReport()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("Section,Metric,Value\r\n");
    }

    @Test
    void pdfExportProducesAValidPdfDocument() {
        byte[] pdf = exporter.toPdf(sampleReport());
        assertThat(pdf.length).isGreaterThan(4);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
