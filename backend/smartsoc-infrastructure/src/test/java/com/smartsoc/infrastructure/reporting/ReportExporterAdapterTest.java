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
    void csvExportStartsWithAUtf8BomThenTheExpectedHeaderRow() {
        // Le BOM (EF BB BF) est ce qu'Excel exige pour reconnaître l'UTF-8
        // d'un simple double-clic sur le fichier — sans lui, il retombe sur
        // l'ANSI de la machine et corrompt tous les accents (régression
        // constatée : "Sévérité" devenait "SÃ©vÃ©ritÃ©").
        byte[] csv = exporter.toCsv(sampleReport());
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        String body = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(body).startsWith("Section,Metric,Value\r\n");
    }

    @Test
    void pdfExportProducesAValidPdfDocument() {
        byte[] pdf = exporter.toPdf(sampleReport());
        assertThat(pdf.length).isGreaterThan(4);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
