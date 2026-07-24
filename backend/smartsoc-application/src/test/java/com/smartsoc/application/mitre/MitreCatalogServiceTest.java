package com.smartsoc.application.mitre;

import com.smartsoc.application.mitre.MitreCatalogService.ImportResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration du catalogue : l'import est un upsert sur l'identité (qui
 * cherche toujours la forme NORMALISÉE, sinon un doublon à chaque passage),
 * et la consultation par identifiant remonte 404 si l'identité est absente.
 */
@ExtendWith(MockitoExtension.class)
class MitreCatalogServiceTest {

    @Mock
    private MitreCatalogRepository catalogRepository;

    @InjectMocks
    private MitreCatalogService service;

    private static MitreTechnique.CatalogEntry entry(String attackId) {
        return MitreTechnique.CatalogEntry.builder()
                .attackId(attackId)
                .name("Command and Scripting Interpreter")
                .tactics(Set.of(MitreTactic.EXECUTION))
                .build();
    }

    @Test
    void importCreatesWhenIdentityIsUnknown() {
        when(catalogRepository.findByAttackId("T1059")).thenReturn(Optional.empty());
        when(catalogRepository.save(any(MitreTechnique.class))).thenAnswer(c -> c.getArgument(0));

        ImportResult result = service.importTechnique(entry("T1059"));

        assertThat(result.created()).isTrue();
        assertThat(result.technique().getAttackId()).isEqualTo("T1059");
    }

    @Test
    void importLooksUpWithTheNormalizedIdNotTheRawForm() {
        when(catalogRepository.findByAttackId(any())).thenReturn(Optional.empty());
        when(catalogRepository.save(any(MitreTechnique.class))).thenAnswer(c -> c.getArgument(0));

        service.importTechnique(entry("  t1059 "));

        // L'upsert cherche la forme canonique : sinon un doublon à chaque import.
        verify(catalogRepository).findByAttackId("T1059");
    }

    @Test
    void importUpdatesInPlaceWhenIdentityIsKnown() {
        when(catalogRepository.findByAttackId("T1059"))
                .thenReturn(Optional.of(MitreTechnique.fromCatalog(entry("T1059"))));
        when(catalogRepository.save(any(MitreTechnique.class))).thenAnswer(c -> c.getArgument(0));

        ImportResult result = service.importTechnique(MitreTechnique.CatalogEntry.builder()
                .attackId("T1059").name("Renamed")
                .tactics(Set.of(MitreTactic.EXECUTION, MitreTactic.INITIAL_ACCESS)).build());

        assertThat(result.created()).isFalse();
        assertThat(result.technique().getName()).isEqualTo("Renamed");
    }

    @Test
    void getTechniqueReturnsByNormalizedIdOrRaises404() {
        when(catalogRepository.findByAttackId("T1059"))
                .thenReturn(Optional.of(MitreTechnique.fromCatalog(entry("T1059"))));
        assertThat(service.getTechnique("t1059").getAttackId()).isEqualTo("T1059");

        when(catalogRepository.findByAttackId("T9999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTechnique("T9999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
