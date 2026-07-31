package com.smartsoc.domain.audit;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogEntryTest {

    @Test
    void recordStampsAnIdAndTheOccurrenceInstant() {
        UUID actorId = UUID.randomUUID();

        AuditLogEntry entry = AuditLogEntry.record(AuditAction.LOGIN_SUCCEEDED, "admin", actorId,
                "User", actorId.toString(), null, "203.0.113.7");

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getOccurredAt()).isNotNull();
        assertThat(entry.getAction()).isEqualTo(AuditAction.LOGIN_SUCCEEDED);
        assertThat(entry.getActorUsername()).isEqualTo("admin");
        assertThat(entry.getActorId()).isEqualTo(actorId);
        assertThat(entry.getTargetType()).isEqualTo("User");
        assertThat(entry.getIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void recordRejectsABlankActor() {
        assertThatThrownBy(() -> AuditLogEntry.record(AuditAction.LOGIN_FAILED, " ", null,
                null, null, null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_AUDIT_LOG_ENTRY");
    }

    @Test
    void optionalFieldsDefaultToNullRatherThanBlank() {
        AuditLogEntry entry = AuditLogEntry.record(AuditAction.BACKUP_EXPORTED, "admin", null,
                " ", " ", " ", " ");

        assertThat(entry.getTargetType()).isNull();
        assertThat(entry.getTargetId()).isNull();
        assertThat(entry.getDetails()).isNull();
        assertThat(entry.getIpAddress()).isNull();
    }
}
