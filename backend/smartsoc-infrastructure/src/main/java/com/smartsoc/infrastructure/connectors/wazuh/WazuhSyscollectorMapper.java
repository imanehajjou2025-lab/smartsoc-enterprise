package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.SystemInventoryPort.SystemDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * ACL de {@code /syscollector/{agent_id}/os} et {@code /hardware}
 * (ADR-014) — combine les deux réponses en un {@link SystemDetails}
 * unique, chacune pouvant être absente indépendamment (endpoints
 * appelés séparément par {@link LiveSystemInventoryAdapter}).
 */
@Component
public class WazuhSyscollectorMapper {

    /** {@code null} si l'agent n'a jamais été scanné (aucun élément renvoyé). */
    public String toOperatingSystemDetail(WazuhSyscollectorOsResponse response) {
        return firstItem(response == null ? null : response.data() == null ? null : response.data().affectedItems())
                .map(WazuhSyscollectorOsResponse.Item::os)
                .map(this::describeOs)
                .orElse(null);
    }

    /** {@code null} si l'agent n'a jamais été scanné, ou si CPU/RAM sont tous deux absents. */
    public String toHardwareSummary(WazuhSyscollectorHardwareResponse response) {
        Optional<WazuhSyscollectorHardwareResponse.Item> item = firstItem(
                response == null ? null : response.data() == null ? null : response.data().affectedItems());
        if (item.isEmpty()) {
            return null;
        }
        String cpuPart = describeCpu(item.get().cpu());
        String ramPart = describeRam(item.get().ram());
        if (cpuPart == null && ramPart == null) {
            return null;
        }
        return List.of(cpuPart, ramPart).stream()
                .filter(part -> part != null)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    private String describeOs(WazuhSyscollectorOsResponse.Os os) {
        if (os == null || os.name() == null || os.name().isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(os.name());
        if (os.displayVersion() != null && !os.displayVersion().isBlank()) {
            sb.append(' ').append(os.displayVersion());
        }
        if (os.build() != null && !os.build().isBlank()) {
            sb.append(" (build ").append(os.build()).append(')');
        }
        return sb.toString();
    }

    private String describeCpu(WazuhSyscollectorHardwareResponse.Cpu cpu) {
        if (cpu == null || cpu.name() == null || cpu.name().isBlank()) {
            return null;
        }
        String cores = cpu.cores() == 1 ? "1 coeur" : cpu.cores() + " coeurs";
        return "%s, %s".formatted(cpu.name(), cores);
    }

    /** RAM en KILO-OCTETS dans la réponse brute (confirmé sur l'échantillon réel) -> Go, arrondi. */
    private String describeRam(WazuhSyscollectorHardwareResponse.Ram ram) {
        if (ram == null || ram.total() <= 0) {
            return null;
        }
        double gigabytes = ram.total() / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.1f Go RAM", gigabytes);
    }

    private <T> Optional<T> firstItem(List<T> items) {
        return (items == null || items.isEmpty()) ? Optional.empty() : Optional.of(items.get(0));
    }

    /** Combine les deux moitiés, chacune tolérant l'absence indépendamment. */
    public Optional<SystemDetails> toSystemDetails(
            WazuhSyscollectorOsResponse osResponse, WazuhSyscollectorHardwareResponse hardwareResponse) {
        String os = toOperatingSystemDetail(osResponse);
        String hardware = toHardwareSummary(hardwareResponse);
        if (os == null && hardware == null) {
            return Optional.empty();
        }
        return Optional.of(new SystemDetails(os, hardware));
    }
}
