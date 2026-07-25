package com.smartsoc.application.soar;

import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookRepository;
import com.smartsoc.domain.soar.PlaybookStepTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybookServiceTest {

    @Mock
    private PlaybookRepository playbookRepository;

    @InjectMocks
    private PlaybookService service;

    private static Playbook.DeclareCommand command() {
        return Playbook.DeclareCommand.builder()
                .name("Confinement ransomware")
                .steps(List.of(new PlaybookStepTemplate(0, "Isoler l'hôte", null)))
                .build();
    }

    @Test
    void declareSavesANewPlaybook() {
        when(playbookRepository.save(any(Playbook.class))).thenAnswer(c -> c.getArgument(0));

        Playbook declared = service.declare(command());

        assertThat(declared.getName()).isEqualTo("Confinement ransomware");
        assertThat(declared.getVersion()).isEqualTo(1);
    }

    @Test
    void updateBumpsVersionOnTheExistingAggregate() {
        Playbook existing = Playbook.declare(command());
        when(playbookRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(playbookRepository.save(any(Playbook.class))).thenAnswer(c -> c.getArgument(0));

        Playbook updated = service.update(existing.getId(), Playbook.DeclareCommand.builder()
                .name("Renamed")
                .steps(List.of(new PlaybookStepTemplate(0, "x", null)))
                .build());

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getName()).isEqualTo("Renamed");
    }

    @Test
    void archiveRaises404WhenMissing() {
        when(playbookRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.archive(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRaises404WhenMissing() {
        when(playbookRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
