package com.smartsoc.domain.reputation;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.intelligence.IndicatorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservableReputationTest {

    private static ObservableReputation.Lookup.LookupBuilder lookup() {
        return ObservableReputation.Lookup.builder()
                .source("  VirusTotal ")
                .type(IndicatorType.IPV4)
                .value("8.8.8.8")
                .maliciousCount(0)
                .suspiciousCount(0)
                .harmlessCount(53)
                .undetectedCount(38)
                .checkedAt(Instant.parse("2026-08-09T00:00:00Z"));
    }

    @Test
    void recordNormalizesIdentityAndDerivesAHarmlessVerdict() {
        ObservableReputation reputation = ObservableReputation.record(lookup().build());

        assertThat(reputation.getId()).isNotNull();
        assertThat(reputation.getSource()).isEqualTo("virustotal");
        assertThat(reputation.getType()).isEqualTo(IndicatorType.IPV4);
        assertThat(reputation.getValue()).isEqualTo("8.8.8.8");
        assertThat(reputation.getVerdict()).isEqualTo(ReputationVerdict.HARMLESS);
    }

    @Test
    void aSingleMaliciousEngineOutranksEveryOtherCategory() {
        ObservableReputation reputation = ObservableReputation.record(lookup()
                .maliciousCount(1).suspiciousCount(5).harmlessCount(50).build());

        assertThat(reputation.getVerdict()).isEqualTo(ReputationVerdict.MALICIOUS);
    }

    @Test
    void suspiciousOutranksHarmlessWhenNoEngineFlagsMalicious() {
        ObservableReputation reputation = ObservableReputation.record(lookup()
                .maliciousCount(0).suspiciousCount(2).harmlessCount(50).build());

        assertThat(reputation.getVerdict()).isEqualTo(ReputationVerdict.SUSPICIOUS);
    }

    @Test
    void noEngineRatingAtAllIsUndetectedRatherThanHarmless() {
        ObservableReputation reputation = ObservableReputation.record(lookup()
                .maliciousCount(0).suspiciousCount(0).harmlessCount(0).undetectedCount(0).build());

        assertThat(reputation.getVerdict()).isEqualTo(ReputationVerdict.UNDETECTED);
    }

    @Test
    void refreshFromUpdatesCountsAndVerdictAndAdvancesCheckedAt() {
        Instant t0 = Instant.parse("2026-08-01T00:00:00Z");
        Instant t1 = Instant.parse("2026-08-09T00:00:00Z");
        ObservableReputation reputation = ObservableReputation.record(lookup().checkedAt(t0).build());

        reputation.refreshFrom(lookup().maliciousCount(3).checkedAt(t1).build());

        assertThat(reputation.getVerdict()).isEqualTo(ReputationVerdict.MALICIOUS);
        assertThat(reputation.getMaliciousCount()).isEqualTo(3);
        assertThat(reputation.getCheckedAt()).isEqualTo(t1);
        assertThat(reputation.getFirstCheckedAt()).isEqualTo(t0);
    }

    @Test
    void refreshFromRejectsALookupWithADifferentIdentity() {
        ObservableReputation reputation = ObservableReputation.record(lookup().build());

        assertThatThrownBy(() -> reputation.refreshFrom(lookup().value("1.1.1.1").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("identity");
    }

    @Test
    void isStaleAtComparesAgainstCheckedAt() {
        ObservableReputation reputation = ObservableReputation.record(
                lookup().checkedAt(Instant.parse("2026-08-09T00:00:00Z")).build());

        assertThat(reputation.isStaleAt(Instant.parse("2026-08-10T00:00:00Z"))).isTrue();
        assertThat(reputation.isStaleAt(Instant.parse("2026-08-08T00:00:00Z"))).isFalse();
    }

    @Test
    void recordRequiresSourceTypeAndValue() {
        assertThatThrownBy(() -> ObservableReputation.record(lookup().source(" ").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> ObservableReputation.record(lookup().type(null).build()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ObservableReputation.record(lookup().value(" ").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("value");
    }
}
