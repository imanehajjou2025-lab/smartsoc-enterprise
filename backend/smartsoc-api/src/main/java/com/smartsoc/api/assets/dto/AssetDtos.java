package com.smartsoc.api.assets.dto;

import com.smartsoc.domain.assets.AgentConnectionStatus;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetStatus;
import com.smartsoc.domain.assets.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Contrats REST du contexte assets. */
public final class AssetDtos {

    private AssetDtos() {
    }

    public record RegisterAssetRequest(
            @NotBlank @Size(max = 255) String hostname,
            @Size(max = 255) String displayName,
            @NotNull AssetType type,
            @NotNull AssetCriticality criticality,
            @NotNull AssetExposure exposure,
            @Size(max = 45) String ipAddress,
            @Size(max = 100) String owner,
            String description) {
    }

    /** Mise à jour complète hors hostname (immuable) et hors statut
     *  (endpoints dédiés decommission/reactivate). */
    public record UpdateAssetRequest(
            @Size(max = 255) String displayName,
            @Size(max = 45) String ipAddress,
            @Size(max = 100) String owner,
            String description,
            @NotNull AssetType type,
            @NotNull AssetCriticality criticality,
            @NotNull AssetExposure exposure) {
    }

    public record AssetResponse(
            UUID id,
            String hostname,
            String displayName,
            AssetType type,
            AssetCriticality criticality,
            AssetExposure exposure,
            String ipAddress,
            String owner,
            String description,
            AssetStatus status,
            Instant registeredAt,
            Instant decommissionedAt,
            String operatingSystem,
            Instant lastSeenAt,
            String hardwareSummary,
            String externalSource,
            AgentConnectionStatus agentConnectionStatus) {
    }
}
