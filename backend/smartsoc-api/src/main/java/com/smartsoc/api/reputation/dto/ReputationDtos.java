package com.smartsoc.api.reputation.dto;

import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ReputationVerdict;

import java.time.Instant;
import java.util.UUID;

public final class ReputationDtos {

    private ReputationDtos() {
    }

    public record ReputationResponse(
            UUID id,
            String source,
            IndicatorType type,
            String value,
            ReputationVerdict verdict,
            int maliciousCount,
            int suspiciousCount,
            int harmlessCount,
            int undetectedCount,
            Instant firstCheckedAt,
            Instant checkedAt) {
    }
}
