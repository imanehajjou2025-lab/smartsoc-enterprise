package com.smartsoc.infrastructure.connectors.misp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.domain.intelligence.IndicatorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vérifie l'ACL contre un ÉCHANTILLON RÉEL capturé sur une vraie
 * instance MISP le 2026-08-09 (voir {@code docs/integration/fixtures/
 * misp/attributes-restsearch-sample.json}), pas des données inventées.
 */
class MispIndicatorMapperTest {

    private final MispIndicatorMapper mapper = new MispIndicatorMapper();
    private MispAttributesSearchResponse realResponse;

    @BeforeEach
    void loadRealFixture() throws Exception {
        ObjectMapper json = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(
                "/connectors/misp/attributes-restsearch-sample.json")) {
            realResponse = json.readValue(in, MispAttributesSearchResponse.class);
        }
    }

    @Test
    void mapsTheThreeRealAttributesFaithfully() {
        List<FeedObservation> observations = mapper.toObservations(realResponse);

        assertThat(observations).hasSize(3);
        assertThat(observations).extracting(FeedObservation::type)
                .containsExactly(IndicatorType.IPV4, IndicatorType.DOMAIN, IndicatorType.URL);
        assertThat(observations).extracting(FeedObservation::value)
                .containsExactly("185.220.101.25", "evil-domain.com", "http://malicious.test");
    }

    @Test
    void confidenceIsDerivedFromTheEventThreatLevel() {
        // Les 3 attributs reels partagent le meme evenement, threat_level_id "2" (Medium).
        List<FeedObservation> observations = mapper.toObservations(realResponse);

        assertThat(observations).extracting(FeedObservation::confidence)
                .containsExactly(65, 65, 65);
    }

    @Test
    void externalIdIsTheAttributeUuidAndTimestampIsParsedFromEpochSeconds() {
        FeedObservation ipObservation = mapper.toObservations(realResponse).get(0);

        assertThat(ipObservation.externalId()).isEqualTo("31b1b071-dedd-4a5c-9dd6-e765d4b5f083");
        assertThat(ipObservation.observedAt()).isEqualTo(Instant.ofEpochSecond(1784642314L));
        assertThat(ipObservation.description()).contains("SmartSOC Test IOC");
    }

    @Test
    void tlpIsLeftNullSoTheDomainDefaultAmberApplies() {
        List<FeedObservation> observations = mapper.toObservations(realResponse);

        assertThat(observations).isNotEmpty().allSatisfy(o -> assertThat(o.tlp()).isNull());
    }

    @Test
    void ipSrcAndIpDstAreDisambiguatedByValueShapeNotByMispType() {
        MispAttributesSearchResponse ipv6 = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-1", "Network activity", "ip-dst", true, "1700000000", "",
                                "2001:db8::1",
                                new MispAttributesSearchResponse.Event("2", "Test", "3")))));

        List<FeedObservation> observations = mapper.toObservations(ipv6);

        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).type()).isEqualTo(IndicatorType.IPV6);
    }

    @Test
    void unsupportedAttributeTypesAreSilentlySkippedRatherThanFailingTheWholeCycle() {
        MispAttributesSearchResponse withUnsupported = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-2", "Payload delivery", "filename", true, "1700000000", "",
                                "malware.exe",
                                new MispAttributesSearchResponse.Event("3", "Test", "1")))));

        assertThat(mapper.toObservations(withUnsupported)).isEmpty();
    }

    @Test
    void emptyResponseMapsToEmptyList() {
        assertThat(mapper.toObservations(null)).isEmpty();
        assertThat(mapper.toObservations(new MispAttributesSearchResponse(null))).isEmpty();
    }

    @Test
    void aNullAttributeTypeIsSkippedRatherThanCrashing() {
        MispAttributesSearchResponse withNullType = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-3", "Other", null, true, "1700000000", "",
                                "whatever", new MispAttributesSearchResponse.Event("4", "Test", "1")))));

        assertThat(mapper.toObservations(withNullType)).isEmpty();
    }

    @Test
    void descriptionCombinesEventInfoAndCommentWhenBothArePresent() {
        MispAttributesSearchResponse response = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-4", "Network activity", "domain", true, "1700000000",
                                "Observé sur un hôte compromis",
                                "c2.example.test",
                                new MispAttributesSearchResponse.Event("5", "Campagne test", "1")))));

        FeedObservation observation = mapper.toObservations(response).get(0);

        assertThat(observation.description())
                .isEqualTo("Événement MISP : Campagne test — Observé sur un hôte compromis");
    }

    @Test
    void descriptionIsNullWhenNeitherEventInfoNorCommentIsAvailable() {
        MispAttributesSearchResponse response = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-5", "Network activity", "domain", true, "1700000000", "   ",
                                "no-context.test",
                                new MispAttributesSearchResponse.Event("6", "  ", "1")))));

        assertThat(mapper.toObservations(response).get(0).description()).isNull();
    }

    @Test
    void malformedThreatLevelAndTimestampFallBackToSafeDefaultsRatherThanCrashing() {
        MispAttributesSearchResponse response = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-6", "Network activity", "domain", true, "not-a-number", "",
                                "malformed.test",
                                new MispAttributesSearchResponse.Event("7", "Test", "not-a-number")))));

        FeedObservation observation = mapper.toObservations(response).get(0);

        // threat_level_id illisible -> Undefined (50) ; timestamp illisible -> horodatage courant.
        assertThat(observation.confidence()).isEqualTo(50);
        assertThat(observation.observedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void missingEventBlockFallsBackToUndefinedConfidence() {
        MispAttributesSearchResponse response = new MispAttributesSearchResponse(
                new MispAttributesSearchResponse.Response(List.of(
                        new MispAttributesSearchResponse.Attribute(
                                "uuid-7", "Network activity", "domain", true, "1700000000", "",
                                "no-event.test", null))));

        assertThat(mapper.toObservations(response).get(0).confidence()).isEqualTo(50);
    }
}
