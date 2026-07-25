package com.smartsoc.application.mitre;

import com.smartsoc.application.mitre.MitreCorrelationService.ResolvedTechnique;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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
 * Corrélation alerte ↔ catalogue, calculée à la lecture. L'enrichissement
 * résout les identifiants bruts contre le catalogue mais garde VISIBLE tout
 * identifiant inconnu (hors format ou absent) ; le retro normalise avant la
 * requête.
 */
@ExtendWith(MockitoExtension.class)
class MitreCorrelationServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private MitreCatalogRepository catalogRepository;

    @InjectMocks
    private MitreCorrelationService service;

    private static Alert alertCiting(List<String> techniques) {
        return Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-" + UUID.randomUUID())
                .title("t")
                .description("d")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .hostname("h")
                .ruleId("r")
                .mitreTechniques(techniques)
                .rawPayload("{}")
                .build());
    }

    private static MitreTechnique powershell() {
        return MitreTechnique.fromCatalog(MitreTechnique.CatalogEntry.builder()
                .attackId("T1059").name("Command and Scripting Interpreter")
                .tactics(Set.of(MitreTactic.EXECUTION)).build());
    }

    @Test
    void enrichResolvesKnownIdsAndKeepsUnknownOnesVisible() {
        UUID id = UUID.randomUUID();
        // Trois cas : un connu (casse quelconque), un bien formé mais absent,
        // un hors format. Les trois restent visibles dans l'ordre d'origine.
        when(alertRepository.findById(id))
                .thenReturn(Optional.of(alertCiting(List.of("t1059", "T9999", "not-an-id"))));
        when(catalogRepository.findByAttackId("T1059")).thenReturn(Optional.of(powershell()));
        when(catalogRepository.findByAttackId("T9999")).thenReturn(Optional.empty());

        List<ResolvedTechnique> resolved = service.enrichAlert(id);

        assertThat(resolved).extracting(ResolvedTechnique::rawId)
                .containsExactly("t1059", "T9999", "not-an-id");
        assertThat(resolved.get(0).known()).isTrue();
        assertThat(resolved.get(0).catalogEntry().getAttackId()).isEqualTo("T1059");
        assertThat(resolved.get(1).known()).isFalse();
        assertThat(resolved.get(2).known()).isFalse();
        // Un identifiant hors format n'interroge JAMAIS le catalogue.
        verify(catalogRepository, never()).findByAttackId("not-an-id");
    }

    @Test
    void enrichRaises404WhenAlertIsMissing() {
        when(alertRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enrichAlert(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retroNormalizesTheIdentifierBeforeQuerying() {
        when(alertRepository.findByMitreTechnique(eq("T1059"), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 25));

        service.alertsForTechnique("  t1059 ", PageQuery.of(0, 25));

        // La requête part avec la forme canonique, sinon aucun rapprochement.
        verify(alertRepository).findByMitreTechnique(eq("T1059"), any());
    }

    @Test
    void coverageDelegatesToTheRepository() {
        when(alertRepository.mitreCoverage())
                .thenReturn(List.of(new MitreCoverageCount("T1059", 3)));

        assertThat(service.coverage()).containsExactly(new MitreCoverageCount("T1059", 3));
    }
}
