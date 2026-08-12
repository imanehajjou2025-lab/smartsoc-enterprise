package com.smartsoc.infrastructure.reporting;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartsoc.application.reporting.ReportExporter;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.reporting.Report;
import com.smartsoc.domain.reporting.ReportMetrics;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rendu déterministe d'un rapport déjà généré — aucun appel externe, donc
 * un seul adaptateur, toujours actif (voir {@link ReportExporter}).
 */
@Component
public class ReportExporterAdapter implements ReportExporter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    /**
     * BOM UTF-8 (EF BB BF) : sans lui, Excel ouvre un .csv sans passer par
     * l'en-tête HTTP {@code charset} (il n'existe déjà plus, le fichier est
     * sur disque) et devine l'ANSI de la machine — chaque accent du CSV
     * ressort corrompu ("SÃ©vÃ©ritÃ©"). Le BOM est le seul signal qu'Excel
     * respecte de façon fiable pour un simple double-clic.
     */
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    @Override
    public byte[] toCsv(Report report) {
        StringBuilder csv = new StringBuilder("Section,Metric,Value\r\n");
        rows(report).forEach((label, value) -> {
            String[] parts = label.split("\\|", 2);
            csv.append(escape(parts[0])).append(',')
                    .append(escape(parts[1])).append(',')
                    .append(escape(value)).append("\r\n");
        });
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, withBom, UTF8_BOM.length, body.length);
        return withBom;
    }

    @Override
    public byte[] toPdf(Report report) {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

            document.add(new Paragraph(report.getTitle(), titleFont));
            document.add(new Paragraph("Période : %s → %s".formatted(
                    TIMESTAMP.format(report.getPeriodStart()), TIMESTAMP.format(report.getPeriodEnd())), metaFont));
            document.add(new Paragraph("Généré le %s par %s".formatted(
                    TIMESTAMP.format(report.getGeneratedAt()), report.getGeneratedBy()), metaFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            addHeaderCell(table, "Section", headerFont);
            addHeaderCell(table, "Indicateur", headerFont);
            addHeaderCell(table, "Valeur", headerFont);
            rows(report).forEach((label, value) -> {
                String[] parts = label.split("\\|", 2);
                table.addCell(new PdfPCell(new Phrase(parts[0], metaFont)));
                table.addCell(new PdfPCell(new Phrase(parts[1], metaFont)));
                table.addCell(new PdfPCell(new Phrase(value, metaFont)));
            });
            document.add(table);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render report %s to PDF".formatted(report.getId()), ex);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    /** {@code "Section|Metric" -> "Value"}, dans l'ordre de rendu — partagé par CSV et PDF. */
    private static Map<String, String> rows(Report report) {
        ReportMetrics m = report.getMetrics();
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Alertes|Total", String.valueOf(m.alerts().total()));
        m.alerts().bySeverity().forEach((severity, count) ->
                rows.put("Alertes|Sévérité: " + severity, String.valueOf(count)));
        m.alerts().byStatus().forEach((status, count) ->
                rows.put("Alertes|Statut: " + status, String.valueOf(count)));
        rows.put("Incidents|Ouverts", String.valueOf(m.incidents().opened()));
        rows.put("Incidents|Clôturés", String.valueOf(m.incidents().closed()));
        rows.put("Incidents|Résolution moyenne (heures)",
                m.incidents().avgResolutionHours() == null
                        ? "n/a" : "%.1f".formatted(m.incidents().avgResolutionHours()));
        rows.put("SOAR|Exécutions démarrées", String.valueOf(m.soar().started()));
        rows.put("SOAR|Terminées", String.valueOf(m.soar().completed()));
        rows.put("SOAR|Annulées", String.valueOf(m.soar().cancelled()));
        rows.put("Hunting|Requêtes exécutées (approx.)", String.valueOf(m.huntQueriesExecuted()));
        rows.put("MITRE ATT&CK|Techniques distinctes couvertes",
                String.valueOf(m.mitreDistinctTechniquesCovered()));
        for (MitreCoverageCount top : m.mitreTopTechniques()) {
            rows.put("MITRE ATT&CK|Top technique: " + top.attackId(), String.valueOf(top.alertCount()));
        }
        return rows;
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
