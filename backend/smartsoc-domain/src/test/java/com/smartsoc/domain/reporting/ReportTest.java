package com.smartsoc.domain.reporting;

import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-08T00:00:00Z");

    private static ReportMetrics emptyMetrics() {
        return new ReportMetrics(
                new AlertPeriodMetrics(0, Map.of(), Map.of()),
                new IncidentPeriodMetrics(0, 0, null),
                new ExecutionPeriodMetrics(0, 0, 0),
                0, 0, List.of());
    }

    @Test
    void generateStampsAnIdAndTheGenerationInstant() {
        Report report = Report.generate("Hebdo SOC", START, END, "admin", emptyMetrics());

        assertThat(report.getId()).isNotNull();
        assertThat(report.getTitle()).isEqualTo("Hebdo SOC");
        assertThat(report.getGeneratedBy()).isEqualTo("admin");
        assertThat(report.getGeneratedAt()).isNotNull();
        assertThat(report.getMetrics()).isEqualTo(emptyMetrics());
    }

    @Test
    void generateRejectsABlankTitle() {
        assertThatThrownBy(() -> Report.generate(" ", START, END, "admin", emptyMetrics()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REPORT");
    }

    @Test
    void generateRejectsATitleLongerThan200Characters() {
        String tooLong = "x".repeat(201);
        assertThatThrownBy(() -> Report.generate(tooLong, START, END, "admin", emptyMetrics()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REPORT");
    }

    @Test
    void generateRejectsAPeriodWhereStartIsNotBeforeEnd() {
        assertThatThrownBy(() -> Report.generate("Hebdo SOC", END, START, "admin", emptyMetrics()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REPORT");
        assertThatThrownBy(() -> Report.generate("Hebdo SOC", START, START, "admin", emptyMetrics()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REPORT");
    }

    @Test
    void generateRequiresWhoGeneratedIt() {
        assertThatThrownBy(() -> Report.generate("Hebdo SOC", START, END, " ", emptyMetrics()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REPORT");
    }
}
