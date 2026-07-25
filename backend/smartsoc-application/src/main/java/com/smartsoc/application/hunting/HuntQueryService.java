package com.smartsoc.application.hunting;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.hunting.HuntQuery;
import com.smartsoc.domain.hunting.HuntQuery.DeclareCommand;
import com.smartsoc.domain.hunting.HuntQueryFilter;
import com.smartsoc.domain.hunting.HuntQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cas d'usage des requêtes de chasse sauvegardées : déclaration, mise à
 * jour, consultation, suppression. Une requête n'est pas une pièce
 * d'évidence SOC — contrairement aux alertes/cas/IOC, la suppression est
 * un acte réel, pas une révocation.
 */
@Service
@RequiredArgsConstructor
public class HuntQueryService {

    private final HuntQueryRepository huntQueryRepository;

    @Transactional
    public HuntQuery declare(DeclareCommand command) {
        return huntQueryRepository.save(HuntQuery.declare(command));
    }

    @Transactional
    public HuntQuery update(UUID id, DeclareCommand command) {
        HuntQuery hunt = requireHunt(id);
        hunt.update(command);
        return huntQueryRepository.save(hunt);
    }

    @Transactional
    public void delete(UUID id) {
        requireHunt(id);
        huntQueryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public HuntQuery get(UUID id) {
        return requireHunt(id);
    }

    @Transactional(readOnly = true)
    public PageResult<HuntQuery> search(HuntQueryFilter filter) {
        return huntQueryRepository.search(filter);
    }

    private HuntQuery requireHunt(UUID id) {
        return huntQueryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HuntQuery", id));
    }
}
