package com.smartsoc.domain.assets;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Actif supervisé — l'inventaire qui donne son contexte métier au SOC
 * (criticité, exposition, propriétaire d'une machine qui lève une alerte).
 *
 * Le hostname est la CLÉ DE CORRÉLATION avec les alertes : normalisé
 * (minuscules, sans espaces) et IMMUABLE après enregistrement — en
 * changer reviendrait à changer d'identité d'actif. La corrélation
 * compare le hostname de l'actif au hostname des alertes normalisé côté
 * SQL (les alertes stockent la valeur brute de la source). Limitation
 * actée : FQDN et nom court ne se correspondent pas (pas de
 * rapprochement flou — un faux rattachement est pire qu'une absence) ;
 * l'évolution prévue si le SOC réel mélange les formes est une liste
 * d'alias explicites par actif.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Asset {

    private final UUID id;
    private final String hostname;
    private String displayName;
    private AssetType type;
    private AssetCriticality criticality;
    private AssetExposure exposure;
    private String ipAddress;
    private String owner;
    private String description;
    private AssetStatus status;
    private final Instant registeredAt;
    private Instant decommissionedAt;

    /** Données d'enregistrement — parameter object du point d'entrée unique. */
    @Builder
    public record RegistrationData(
            String hostname,
            String displayName,
            AssetType type,
            AssetCriticality criticality,
            AssetExposure exposure,
            String ipAddress,
            String owner,
            String description) {
    }

    /** Point d'entrée unique : un actif naît de son enregistrement à l'inventaire. */
    public static Asset register(RegistrationData data) {
        String hostname = normalizeHostname(data.hostname());
        if (data.type() == null) {
            throw new BusinessRuleViolationException("INVALID_ASSET",
                    "An asset must have a type");
        }
        if (data.criticality() == null) {
            throw new BusinessRuleViolationException("INVALID_ASSET",
                    "An asset must have a criticality");
        }
        if (data.exposure() == null) {
            throw new BusinessRuleViolationException("INVALID_ASSET",
                    "An asset must have an exposure");
        }
        return Asset.builder()
                .id(UUID.randomUUID())
                .hostname(hostname)
                .displayName(data.displayName() == null || data.displayName().isBlank()
                        ? hostname : data.displayName().trim())
                .type(data.type())
                .criticality(data.criticality())
                .exposure(data.exposure())
                .ipAddress(blankToNull(data.ipAddress()))
                .owner(blankToNull(data.owner()))
                .description(data.description())
                .status(AssetStatus.ACTIVE)
                .registeredAt(Instant.now())
                .build();
    }

    /** Faits descriptifs (nom d'affichage, IP, propriétaire, description). */
    public void updateDetails(String displayName, String ipAddress,
                              String owner, String description) {
        requireActive();
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName.trim();
        }
        this.ipAddress = blankToNull(ipAddress);
        this.owner = blankToNull(owner);
        this.description = description;
    }

    /** Classification SOC (type, criticité, exposition). */
    public void reclassify(AssetType type, AssetCriticality criticality,
                           AssetExposure exposure) {
        requireActive();
        if (type == null || criticality == null || exposure == null) {
            throw new BusinessRuleViolationException("INVALID_ASSET",
                    "Reclassification requires a type, a criticality and an exposure");
        }
        this.type = type;
        this.criticality = criticality;
        this.exposure = exposure;
    }

    public void decommission() {
        requireActive();
        this.status = AssetStatus.DECOMMISSIONED;
        this.decommissionedAt = Instant.now();
    }

    public void reactivate() {
        if (status != AssetStatus.DECOMMISSIONED) {
            throw new BusinessRuleViolationException("INVALID_ASSET_STATUS",
                    "Only a decommissioned asset can be reactivated");
        }
        this.status = AssetStatus.ACTIVE;
        this.decommissionedAt = null;
    }

    /**
     * Normalisation de la clé de corrélation : minuscules, sans espaces.
     * Locale.ROOT obligatoire : la normalisation Java doit correspondre
     * exactement au lower(trim(...)) SQL, indépendant de la locale —
     * en locale turque, toLowerCase() sans locale divergerait sur le I.
     */
    public static String normalizeHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_ASSET",
                    "Field 'hostname' must not be blank");
        }
        return hostname.trim().toLowerCase(Locale.ROOT);
    }

    private void requireActive() {
        if (status != AssetStatus.ACTIVE) {
            throw new BusinessRuleViolationException("ASSET_DECOMMISSIONED",
                    "A decommissioned asset is read-only; reactivate it first");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
