package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.application.connectors.SystemInventoryPort.SystemDetails;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'ACL contre les ÉCHANTILLONS RÉELS capturés en phase 0
 * ({@code docs/integration/fixtures/wazuh/syscollector-*.json}).
 */
class WazuhSyscollectorMapperTest {

    private final WazuhSyscollectorMapper mapper = new WazuhSyscollectorMapper();
    private final ObjectMapper json = new ObjectMapper();

    private WazuhSyscollectorOsResponse loadRealOsFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/connectors/wazuh/syscollector-os-sample.json")) {
            return json.readValue(in, WazuhSyscollectorOsResponse.class);
        }
    }

    private WazuhSyscollectorHardwareResponse loadRealHardwareFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/connectors/wazuh/syscollector-hardware-sample.json")) {
            return json.readValue(in, WazuhSyscollectorHardwareResponse.class);
        }
    }

    @Test
    void realOsFixtureProducesARichDescription() throws Exception {
        WazuhSyscollectorOsResponse response = loadRealOsFixture();

        String description = mapper.toOperatingSystemDetail(response);

        // Plus riche que le simple "Microsoft Windows 10 Home 10.0.19045.3803"
        // de la liste d'agents de base : porte l'edition (22H2) et le build.
        assertThat(description).isEqualTo("Microsoft Windows 10 Home 22H2 (build 19045.3803)");
    }

    @Test
    void realHardwareFixtureProducesAReadableSummary() throws Exception {
        WazuhSyscollectorHardwareResponse response = loadRealHardwareFixture();

        String summary = mapper.toHardwareSummary(response);

        assertThat(summary).isEqualTo("12th Gen Intel(R) Core(TM) i5-12450H, 1 coeur, 2.0 Go RAM");
    }

    @Test
    void combinedSystemDetailsMergesBothHalves() throws Exception {
        Optional<SystemDetails> details = mapper.toSystemDetails(loadRealOsFixture(), loadRealHardwareFixture());

        assertThat(details).isPresent();
        assertThat(details.get().operatingSystemDetail())
                .isEqualTo("Microsoft Windows 10 Home 22H2 (build 19045.3803)");
        assertThat(details.get().hardwareSummary())
                .isEqualTo("12th Gen Intel(R) Core(TM) i5-12450H, 1 coeur, 2.0 Go RAM");
    }

    @Test
    void neverScannedAgentReturnsEmptyNotFabricated() {
        WazuhSyscollectorOsResponse emptyOs = new WazuhSyscollectorOsResponse(
                new WazuhSyscollectorOsResponse.Data(java.util.List.of()));
        WazuhSyscollectorHardwareResponse emptyHardware = new WazuhSyscollectorHardwareResponse(
                new WazuhSyscollectorHardwareResponse.Data(java.util.List.of()));

        assertThat(mapper.toOperatingSystemDetail(emptyOs)).isNull();
        assertThat(mapper.toHardwareSummary(emptyHardware)).isNull();
        assertThat(mapper.toSystemDetails(emptyOs, emptyHardware)).isEmpty();
    }

    @Test
    void oneHalfPresentTheOtherAbsentStillProducesPartialDetails() throws Exception {
        WazuhSyscollectorHardwareResponse emptyHardware = new WazuhSyscollectorHardwareResponse(
                new WazuhSyscollectorHardwareResponse.Data(java.util.List.of()));

        Optional<SystemDetails> details = mapper.toSystemDetails(loadRealOsFixture(), emptyHardware);

        assertThat(details).isPresent();
        assertThat(details.get().operatingSystemDetail()).isNotNull();
        assertThat(details.get().hardwareSummary()).isNull();
    }
}
