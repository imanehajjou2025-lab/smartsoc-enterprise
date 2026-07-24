package com.smartsoc.infrastructure.persistence.mitre;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataMitreTechniqueRepository
        extends JpaRepository<MitreTechniqueJpaEntity, UUID>,
        JpaSpecificationExecutor<MitreTechniqueJpaEntity> {

    /** Recherche par identité — emprunte ux_mitre_attack_id. */
    Optional<MitreTechniqueJpaEntity> findByAttackId(String attackId);
}
