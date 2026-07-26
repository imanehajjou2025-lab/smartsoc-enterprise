package com.smartsoc.infrastructure.persistence.reporting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataReportRepository extends JpaRepository<ReportJpaEntity, UUID> {

    Page<ReportJpaEntity> findAllByOrderByGeneratedAtDesc(Pageable pageable);
}
