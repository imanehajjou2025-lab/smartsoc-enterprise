package com.smartsoc.domain.audit;

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
 * Entrée du journal d'audit : trace immuable d'une action sensible,
 * jamais modifiée ni supprimée une fois enregistrée (même doctrine que
 * {@code Report} — un journal d'audit qui se réécrit n'en est plus un).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLogEntry {

    private static final String INVALID = "INVALID_AUDIT_LOG_ENTRY";

    private final UUID id;
    private final Instant occurredAt;
    private final AuditAction action;
    private final String actorUsername;
    private final UUID actorId;
    private final String targetType;
    private final String targetId;
    private final String details;
    private final String ipAddress;

    public static AuditLogEntry record(AuditAction action, String actorUsername, UUID actorId,
                                        String targetType, String targetId, String details,
                                        String ipAddress) {
        Objects.requireNonNull(action, "action");
        String normalizedActor = TextNormalization.blankToNull(actorUsername);
        if (normalizedActor == null) {
            throw new BusinessRuleViolationException(INVALID, "An audit log entry must record an actor");
        }

        return AuditLogEntry.builder()
                .id(UUID.randomUUID())
                .occurredAt(Instant.now())
                .action(action)
                .actorUsername(normalizedActor)
                .actorId(actorId)
                .targetType(TextNormalization.blankToNull(targetType))
                .targetId(TextNormalization.blankToNull(targetId))
                .details(TextNormalization.blankToNull(details))
                .ipAddress(TextNormalization.blankToNull(ipAddress))
                .build();
    }
}
