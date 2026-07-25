package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for playbook definition persistence. Pas de suppression : voir {@link Playbook}. */
public interface PlaybookRepository {

    Playbook save(Playbook playbook);

    Optional<Playbook> findById(UUID id);

    PageResult<Playbook> search(PlaybookQuery query);
}
