package com.smartsoc.application.intelligence;

import com.smartsoc.application.intelligence.IndicatorService.DeclareIndicatorCommand;
import com.smartsoc.application.intelligence.IndicatorService.IngestionResult;
import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration du référentiel CTI : l'ingestion d'un flux est un upsert
 * qui ne doit jamais échouer sur un rejeu, la déclaration manuelle refuse
 * le doublon, et dans les deux cas la recherche d'identité se fait avec la
 * valeur NORMALISÉE — chercher avec la valeur brute du flux créerait un
 * doublon à chaque passage.
 */
@ExtendWith(MockitoExtension.class)
class IndicatorServiceTest {

    @Mock
    private IndicatorRepository indicatorRepository;

    private IndicatorService service;

    @BeforeEach
    void setUp() {
        service = new IndicatorService(indicatorRepository);
    }

    private static Indicator.Observation.ObservationBuilder observation() {
        return Indicator.Observation.builder()
                .type(IndicatorType.IPV4)
                .value("45.83.12.7")
                .confidence(70)
                .feedSource("misp");
    }

    @Test
    void ingestCreatesWhenIdentityIsUnknown() {
        when(indicatorRepository.findByIdentity(IndicatorType.IPV4, "45.83.12.7"))
                .thenReturn(Optional.empty());
        when(indicatorRepository.save(any(Indicator.class)))
                .thenAnswer(call -> call.getArgument(0));

        IngestionResult result = service.ingest(observation().build());

        assertThat(result.created()).isTrue();
        assertThat(result.indicator().getValue()).isEqualTo("45.83.12.7");
    }

    @Test
    void ingestLooksUpWithTheNORMALIZEDValueNotTheRawFeedValue() {
        // Le flux pousse une forme défangée, en majuscules et espacée.
        when(indicatorRepository.findByIdentity(any(), any())).thenReturn(Optional.empty());
        when(indicatorRepository.save(any(Indicator.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.ingest(observation()
                .type(IndicatorType.DOMAIN).value("  EVIL[.]COM. ").build());

        // Sans normalisation AVANT la recherche, l'IOC déjà connu ne serait
        // jamais retrouvé et un doublon naîtrait à chaque passage du flux.
        verify(indicatorRepository).findByIdentity(eq(IndicatorType.DOMAIN), eq("evil.com"));
    }

    @Test
    void ingestRefreshesMetadataOfAKnownIndicatorInsteadOfCreatingASecondOne() {
        Indicator existing = Indicator.declare(observation().confidence(40).build());
        when(indicatorRepository.findByIdentity(IndicatorType.IPV4, "45.83.12.7"))
                .thenReturn(Optional.of(existing));
        when(indicatorRepository.save(any(Indicator.class)))
                .thenAnswer(call -> call.getArgument(0));

        IngestionResult result = service.ingest(observation().confidence(95).build());

        assertThat(result.created()).isFalse();
        ArgumentCaptor<Indicator> saved = ArgumentCaptor.forClass(Indicator.class);
        verify(indicatorRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getConfidence()).isEqualTo(95);
    }

    @Test
    void ingestOfARevokedIndicatorDoesNotResurrectIt() {
        Indicator revoked = Indicator.declare(observation().build());
        revoked.revoke("Proxy interne — faux positif");
        when(indicatorRepository.findByIdentity(IndicatorType.IPV4, "45.83.12.7"))
                .thenReturn(Optional.of(revoked));
        when(indicatorRepository.save(any(Indicator.class)))
                .thenAnswer(call -> call.getArgument(0));

        IngestionResult result = service.ingest(observation().confidence(100).build());

        assertThat(result.indicator().isRevoked()).isTrue();
        assertThat(result.indicator().isActionableAt(java.time.Instant.now())).isFalse();
    }

    @Test
    void declareRefusesADuplicateEvenWrittenDifferently() {
        Indicator existing = Indicator.declare(observation()
                .type(IndicatorType.DOMAIN).value("evil.com").build());
        when(indicatorRepository.findByIdentity(IndicatorType.DOMAIN, "evil.com"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.declare(new DeclareIndicatorCommand(
                IndicatorType.DOMAIN, "  EVIL[.]COM  ", 80, TlpMarking.AMBER,
                null, Set.of(), null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("evil.com");

        verify(indicatorRepository, never()).save(any());
    }

    @Test
    void declareStampsTheManualSource() {
        when(indicatorRepository.findByIdentity(any(), any())).thenReturn(Optional.empty());
        when(indicatorRepository.save(any(Indicator.class)))
                .thenAnswer(call -> call.getArgument(0));

        Indicator declared = service.declare(new DeclareIndicatorCommand(
                IndicatorType.SHA256, "A".repeat(64), 90, TlpMarking.RED,
                "Charge utile du dropper", Set.of("Ransomware"), null));

        assertThat(declared.getFeedSource()).isEqualTo("manual");
        assertThat(declared.getValue()).isEqualTo("a".repeat(64));
        assertThat(declared.getTlp()).isEqualTo(TlpMarking.RED);
        assertThat(declared.getTags()).containsExactly("ransomware");
    }

    @Test
    void revokeRequiresAnExistingIndicator() {
        UUID id = UUID.randomUUID();
        when(indicatorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(id, "Faux positif"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
