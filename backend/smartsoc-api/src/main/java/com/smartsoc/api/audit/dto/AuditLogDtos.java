package com.smartsoc.api.audit.dto;

import com.smartsoc.domain.audit.AuditAction;

import java.time.Instant;
import java.util.UUID;

public final class AuditLogDtos {

    private AuditLogDtos() {
    }

    public record AuditLogEntryResponse(
            UUID id,
            Instant occurredAt,
            AuditAction action,
            String actorUsername,
            UUID actorId,
            String targetType,
            String targetId,
            String details,
            String ipAddress) {
    }
}
