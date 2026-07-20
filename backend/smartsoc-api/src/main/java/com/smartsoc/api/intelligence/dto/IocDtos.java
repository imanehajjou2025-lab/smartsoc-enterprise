package com.smartsoc.api.intelligence.dto;

import com.smartsoc.domain.intelligence.IndicatorStatus;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contrats REST du contexte intelligence (CTI). */
public final class IocDtos {

    /**
     * Plafond d'un lot d'ingestion. Un flux CTI complet se compte en
     * centaines de milliers d'indicateurs : il doit être poussé en lots,
     * pas en une requête que la plateforme ne pourrait ni valider ni
     * journaliser proprement.
     */
    public static final int MAX_BATCH_SIZE = 1000;

    private IocDtos() {
    }

    /** Déclaration manuelle par un analyste. La source vaudra « manual ». */
    public record DeclareIocRequest(
            @NotNull IndicatorType type,
            @NotBlank @Size(max = 2048) String value,
            @Min(0) @Max(100) int confidence,
            TlpMarking tlp,
            String description,
            Set<@Size(max = 100) String> tags,
            Instant validUntil) {
    }

    public record RevokeIocRequest(
            @NotBlank @Size(max = 500) String reason) {
    }

    /**
     * Un indicateur tel qu'un flux le pousse. La valeur peut arriver
     * défangée ou en majuscules : la normalisation appartient au serveur.
     */
    public record IngestIocItem(
            @NotNull IndicatorType type,
            @NotBlank @Size(max = 2048) String value,
            @Min(0) @Max(100) int confidence,
            TlpMarking tlp,
            @Size(max = 255) String externalId,
            String description,
            Set<@Size(max = 100) String> tags,
            Instant observedAt,
            Instant validUntil) {
    }

    /** Le flux s'identifie une fois pour tout le lot. */
    public record IngestIocBatchRequest(
            @NotBlank @Size(max = 100) String feedSource,
            @NotEmpty @Size(max = MAX_BATCH_SIZE) List<@Valid @NotNull IngestIocItem> indicators) {
    }

    /**
     * Compte rendu d'un lot. Les indicateurs valides sont ingérés même si
     * d'autres sont rejetés : un flux CTI réel contient des entrées
     * malformées, et refuser le lot entier priverait le SOC de tout le
     * reste. Chaque rejet est nommé pour que le producteur puisse corriger.
     */
    public record IngestIocBatchResponse(
            int received,
            int created,
            int updated,
            int rejected,
            List<IocIngestionError> errors) {
    }

    public record IocIngestionError(
            int index,
            String value,
            String code,
            String message) {
    }

    /**
     * {@code status} est CALCULÉ à l'instant de la réponse, jamais lu dans
     * une colonne — voir IndicatorStatus.
     */
    public record IocResponse(
            UUID id,
            IndicatorType type,
            String value,
            IndicatorStatus status,
            int confidence,
            TlpMarking tlp,
            String feedSource,
            String externalId,
            String description,
            Set<String> tags,
            Instant firstSeen,
            Instant lastSeen,
            Instant validUntil,
            boolean revoked,
            String revocationReason,
            Instant revokedAt) {
    }
}
