package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Modèle BRUT de {@code GET /syscollector/{agent_id}/hardware}, d'après
 * l'échantillon réel capturé en phase 0
 * ({@code docs/integration/fixtures/wazuh/syscollector-hardware-sample.json}).
 *
 * <p>{@code board_serial} vaut littéralement la chaîne {@code "None"}
 * quand absent — une fuite de Python côté API Wazuh, PAS un JSON
 * {@code null}. Le champ n'est même pas repris ici : il n'apporte rien à
 * l'usage actuel (résumé matériel lisible), et le porter aurait exigé de
 * traiter ce cas partout où il serait lu.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhSyscollectorHardwareResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("affected_items") List<Item> affectedItems) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Cpu cpu, Ram ram) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cpu(String name, int cores, int mhz) {
    }

    /** {@code total}/{@code free} en KILO-OCTETS (confirmé sur l'échantillon réel). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ram(long total, long free, int usage) {
    }
}
