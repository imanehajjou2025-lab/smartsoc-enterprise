package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.AgentInventoryPort.AgentSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Anti-Corruption Layer Wazuh (ADR-014) — seul point du système qui
 * connaît le modèle brut de l'API Wazuh. Chaque règle ci-dessous répond
 * à un cas RÉELLEMENT observé dans un échantillon capturé (voir
 * {@code docs/integration/fixtures/wazuh/agents-sample.json}), jamais
 * une anticipation théorique.
 */
@Component
public class WazuhAgentMapper {

    /** L'agent id="000" EST le Wazuh Manager lui-même — jamais un actif supervisé. */
    static final String MANAGER_AGENT_ID = "000";

    /** Wazuh renvoie cette sentinelle pour l'agent 000 : "toujours vivant", pas une vraie observation. */
    private static final Duration SENTINEL_THRESHOLD = Duration.ofDays(365 * 50);

    /**
     * Traduit la liste brute en snapshots exploitables, en EXCLUANT le
     * Manager (agent 000) — ce n'est pas un actif à superviser, c'est le
     * SIEM lui-même.
     */
    public List<AgentSnapshot> toSnapshots(List<WazuhAgentDto> agents) {
        return agents.stream()
                .filter(a -> !MANAGER_AGENT_ID.equals(a.id()))
                .map(this::toSnapshot)
                .toList();
    }

    /**
     * La version du Manager (champ {@code version} de l'agent 000, ex.
     * {@code "Wazuh v4.12.0"}) sert de version détectée du CONNECTEUR
     * (ADR-014 §6.5) — c'est la seule ligne de la réponse qui décrit
     * l'outil lui-même plutôt qu'un agent supervisé.
     */
    public Optional<String> detectManagerVersion(List<WazuhAgentDto> agents) {
        return agents.stream()
                .filter(a -> MANAGER_AGENT_ID.equals(a.id()))
                .map(WazuhAgentDto::version)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private AgentSnapshot toSnapshot(WazuhAgentDto dto) {
        return new AgentSnapshot(
                dto.id(),
                dto.name(),
                normalizeIp(dto.ip()),
                describeOs(dto.os()),
                parseObservationInstant(dto.lastKeepAlive()));
    }

    /**
     * Un agent {@code never_connected} porte {@code ip="any"} — une
     * valeur littérale, pas une adresse. La traiter comme une IP réelle
     * polluerait le module Actifs d'une fausse donnée.
     */
    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank() || "any".equalsIgnoreCase(ip.trim())) {
            return null;
        }
        return ip.trim();
    }

    /**
     * Un agent {@code never_connected} n'a AUCUN objet {@code os} — pas
     * un objet vide, le champ est absent du JSON. Une description
     * "nom + version" reste lisible même si la version manque.
     */
    private static String describeOs(WazuhAgentDto.WazuhAgentOsDto os) {
        if (os == null || os.name() == null || os.name().isBlank()) {
            return null;
        }
        return (os.version() == null || os.version().isBlank())
                ? os.name()
                : os.name() + " " + os.version();
    }

    /**
     * {@code null} pour un agent jamais connecté (champ absent), un
     * format inattendu (dégradation silencieuse — un défaut de
     * synchronisation ne doit jamais faire échouer tout l'agent), OU la
     * sentinelle du Manager ({@code 9999-12-31...}) qui n'est jamais une
     * vraie observation.
     */
    private static Instant parseObservationInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Instant parsed = OffsetDateTime.parse(raw).toInstant();
            if (Duration.between(Instant.now(), parsed).compareTo(SENTINEL_THRESHOLD) > 0) {
                return null;
            }
            return parsed;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
