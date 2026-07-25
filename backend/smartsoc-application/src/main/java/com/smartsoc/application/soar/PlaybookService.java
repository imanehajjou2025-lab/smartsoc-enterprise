package com.smartsoc.application.soar;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.Playbook.DeclareCommand;
import com.smartsoc.domain.soar.PlaybookQuery;
import com.smartsoc.domain.soar.PlaybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cas d'usage des définitions de playbook. Pas de suppression : un
 * playbook s'archive (voir {@link Playbook}).
 */
@Service
@RequiredArgsConstructor
public class PlaybookService {

    private final PlaybookRepository playbookRepository;

    @Transactional
    public Playbook declare(DeclareCommand command) {
        return playbookRepository.save(Playbook.declare(command));
    }

    @Transactional
    public Playbook update(UUID id, DeclareCommand command) {
        Playbook playbook = requirePlaybook(id);
        playbook.update(command);
        return playbookRepository.save(playbook);
    }

    @Transactional
    public Playbook archive(UUID id) {
        Playbook playbook = requirePlaybook(id);
        playbook.archive();
        return playbookRepository.save(playbook);
    }

    @Transactional(readOnly = true)
    public Playbook get(UUID id) {
        return requirePlaybook(id);
    }

    @Transactional(readOnly = true)
    public PageResult<Playbook> search(PlaybookQuery query) {
        return playbookRepository.search(query);
    }

    private Playbook requirePlaybook(UUID id) {
        return playbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook", id));
    }
}
