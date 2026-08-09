package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.domain.intelligence.IndicatorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Anti-Corruption Layer MISP (ADR-014 phase 2) — seul point du système
 * qui connaît le vocabulaire de types d'attributs MISP. Chaque règle
 * répond à un cas RÉELLEMENT observé dans l'échantillon capturé le
 * 2026-08-09 (voir {@code docs/integration/fixtures/misp/
 * attributes-restsearch-sample.json}).
 *
 * <p>MISP porte des dizaines de types d'attributs (filename, regkey,
 * mutex, yara…) dont la plupart ne correspondent à AUCUN des 8
 * {@link IndicatorType} du domaine — ce n'est jamais une anomalie,
 * seulement hors périmètre CTI actuel de la plateforme : ces attributs
 * sont silencieusement écartés (pas journalisés en warning, contrairement
 * à une vraie donnée inattendue).
 */
@Slf4j
@Component
public class MispIndicatorMapper {

    /**
     * {@code ip-src}/{@code ip-dst} ne distinguent pas IPv4 d'IPv6 côté
     * MISP (les deux formes partagent le même type) : désambiguïsé par la
     * forme de la valeur elle-même (présence de « : »), pas par le type
     * MISP annoncé.
     */
    private static final Set<String> IP_TYPES = Set.of("ip-src", "ip-dst");
    private static final Map<String, IndicatorType> TYPE_MAPPING = Map.of(
            "domain", IndicatorType.DOMAIN,
            "hostname", IndicatorType.DOMAIN,
            "url", IndicatorType.URL,
            "uri", IndicatorType.URL,
            "md5", IndicatorType.MD5,
            "sha1", IndicatorType.SHA1,
            "sha256", IndicatorType.SHA256,
            "email-src", IndicatorType.EMAIL,
            "email-dst", IndicatorType.EMAIL,
            "email", IndicatorType.EMAIL);

    public List<FeedObservation> toObservations(MispAttributesSearchResponse response) {
        if (response == null || response.response() == null || response.response().attribute() == null) {
            return List.of();
        }
        return response.response().attribute().stream()
                .map(this::toObservation)
                .filter(Objects::nonNull)
                .toList();
    }

    private FeedObservation toObservation(MispAttributesSearchResponse.Attribute attribute) {
        IndicatorType type = resolveType(attribute);
        if (type == null) {
            return null;
        }
        MispAttributesSearchResponse.Event event = attribute.event();
        return new FeedObservation(
                type,
                attribute.value(),
                confidenceFrom(event == null ? null : event.threatLevelId()),
                null, // TLP non exposé par restSearch sans tags : le defaut restrictif du domaine (AMBER) s'applique.
                attribute.uuid(),
                describeSource(attribute, event),
                Set.of(),
                parseEpochSeconds(attribute.timestamp()),
                null);
    }

    private IndicatorType resolveType(MispAttributesSearchResponse.Attribute attribute) {
        String rawType = attribute.type();
        if (rawType == null) {
            return null;
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        if (IP_TYPES.contains(normalized)) {
            return attribute.value() != null && attribute.value().contains(":")
                    ? IndicatorType.IPV6 : IndicatorType.IPV4;
        }
        return TYPE_MAPPING.get(normalized);
    }

    /**
     * MISP n'expose aucun score de confiance 0-100 sur un attribut : les
     * deux seuls signaux analystes réellement disponibles côté API sont
     * {@code Event.threat_level_id} (1=High, 2=Medium, 3=Low, 4=Undefined
     * — échelle MISP standard) et {@code to_ids} (déjà filtré à
     * {@code true} par la requête, donc sans pouvoir discriminant ici).
     * Traduction assumée, jamais une valeur inventée sans source.
     */
    private int confidenceFrom(String rawThreatLevelId) {
        int threatLevelId = parseIntOrDefault(rawThreatLevelId, 4);
        return switch (threatLevelId) {
            case 1 -> 90;
            case 2 -> 65;
            case 3 -> 35;
            default -> 50;
        };
    }

    private String describeSource(MispAttributesSearchResponse.Attribute attribute,
                                   MispAttributesSearchResponse.Event event) {
        String eventInfo = event == null ? null : event.info();
        String comment = attribute.comment();
        StringBuilder description = new StringBuilder();
        if (eventInfo != null && !eventInfo.isBlank()) {
            description.append("Événement MISP : ").append(eventInfo.trim());
        }
        if (comment != null && !comment.isBlank()) {
            if (!description.isEmpty()) {
                description.append(" — ");
            }
            description.append(comment.trim());
        }
        return description.isEmpty() ? null : description.toString();
    }

    private Instant parseEpochSeconds(String rawTimestamp) {
        Long epochSeconds = parseLongOrNull(rawTimestamp);
        return epochSeconds == null ? Instant.now() : Instant.ofEpochSecond(epochSeconds);
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Long parseLongOrNull(String raw) {
        try {
            return raw == null ? null : Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
