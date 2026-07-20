package com.smartsoc.infrastructure.persistence.intelligence;

import com.smartsoc.domain.intelligence.IndicatorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataIndicatorRepository
        extends JpaRepository<IndicatorJpaEntity, UUID>,
        JpaSpecificationExecutor<IndicatorJpaEntity> {

    /** Recherche par identité métier — emprunte ux_indicators_identity. */
    Optional<IndicatorJpaEntity> findByTypeAndValue(IndicatorType type, String value);
}
