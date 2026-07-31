package com.smartsoc.domain.audit;

import com.smartsoc.domain.common.PageQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogQueryTest {

    @Test
    void allFiltersAreCarriedAsGiven() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-08T00:00:00Z");

        AuditLogQuery query = new AuditLogQuery(AuditAction.LOGIN_FAILED, "admin", from, to, PageQuery.of(0, 25));

        assertThat(query.action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(query.actorUsername()).isEqualTo("admin");
        assertThat(query.from()).isEqualTo(from);
        assertThat(query.to()).isEqualTo(to);
        assertThat(query.page()).isEqualTo(PageQuery.of(0, 25));
    }
}
