package com.smartsoc.application.hunting;

import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.hunting.HuntExecutionPort;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntQuery;
import com.smartsoc.domain.hunting.HuntQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Exécution d'une chasse — calculée à la lecture (aucun état d'exécution
 * persisté, voir ADR-011). Deux entrées : sur une requête sauvegardée
 * (marque {@code lastExecutedAt} comme effet de bord) ou ad hoc (critères
 * non sauvegardés, aucune écriture).
 */
@Service
@RequiredArgsConstructor
public class HuntExecutionService {

    private final HuntQueryRepository huntQueryRepository;
    private final HuntExecutionPort executionPort;

    @Transactional
    public HuntExecutionResult execute(UUID huntId, PageQuery page) {
        HuntQuery hunt = huntQueryRepository.findById(huntId)
                .orElseThrow(() -> new ResourceNotFoundException("HuntQuery", huntId));

        HuntExecutionResult result = executionPort.execute(hunt.getCriteria(), page);

        // Repère d'usage récent, pas un acte d'écriture métier : mis à
        // jour comme effet de bord d'une simple exécution/lecture.
        hunt.markExecuted(result.summary().executedAt());
        huntQueryRepository.save(hunt);

        return result.withHuntId(huntId);
    }

    @Transactional(readOnly = true)
    public HuntExecutionResult executeAdHoc(HuntGroup criteria, PageQuery page) {
        return executionPort.execute(criteria, page);
    }
}
