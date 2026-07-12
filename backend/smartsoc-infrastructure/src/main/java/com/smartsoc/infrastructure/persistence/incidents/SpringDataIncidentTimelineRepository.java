package com.smartsoc.infrastructure.persistence.incidents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataIncidentTimelineRepository
        extends JpaRepository<IncidentTimelineJpaEntity, UUID> {

    List<IncidentTimelineJpaEntity> findByIncidentIdOrderByOccurredAt(UUID incidentId);
}
