package com.smartsoc.infrastructure.persistence.reputation;

import com.smartsoc.domain.intelligence.IndicatorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataObservableReputationRepository extends JpaRepository<ObservableReputationJpaEntity, UUID> {

    Optional<ObservableReputationJpaEntity> findBySourceAndTypeAndValue(String source, IndicatorType type, String value);
}
