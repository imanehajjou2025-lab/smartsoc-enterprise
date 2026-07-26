package com.smartsoc.domain.reporting;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Rapport SOC : instantané figé d'indicateurs sur une période passée,
 * distinct du tableau de bord qui est toujours « maintenant ». Artefact
 * d'audit une fois généré — aucune mutation, aucune suppression, même
 * doctrine que les incidents et les cas d'investigation.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Report {

    private static final String INVALID = "INVALID_REPORT";
    private static final int MAX_TITLE_LENGTH = 200;

    private final UUID id;
    private final String title;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final Instant generatedAt;
    private final String generatedBy;
    private final ReportMetrics metrics;

    public static Report generate(String title, Instant periodStart, Instant periodEnd,
                                   String generatedBy, ReportMetrics metrics) {
        String normalizedTitle = requireTitle(title);
        if (periodStart == null || periodEnd == null) {
            throw new BusinessRuleViolationException(INVALID, "A report period must be fully specified");
        }
        if (!periodStart.isBefore(periodEnd)) {
            throw new BusinessRuleViolationException(INVALID, "A report period start must be before its end");
        }
        String normalizedGeneratedBy = TextNormalization.blankToNull(generatedBy);
        if (normalizedGeneratedBy == null) {
            throw new BusinessRuleViolationException(INVALID, "A report must record who generated it");
        }
        Objects.requireNonNull(metrics, "metrics");

        return Report.builder()
                .id(UUID.randomUUID())
                .title(normalizedTitle)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .generatedAt(Instant.now())
                .generatedBy(normalizedGeneratedBy)
                .metrics(metrics)
                .build();
    }

    private static String requireTitle(String title) {
        String normalized = TextNormalization.blankToNull(title);
        if (normalized == null) {
            throw new BusinessRuleViolationException(INVALID, "A report title must not be blank");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BusinessRuleViolationException(INVALID,
                    "A report title must not exceed %d characters".formatted(MAX_TITLE_LENGTH));
        }
        return normalized;
    }
}
