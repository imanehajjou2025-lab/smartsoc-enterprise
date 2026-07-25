package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for saved hunt query persistence. */
public interface HuntQueryRepository {

    HuntQuery save(HuntQuery query);

    Optional<HuntQuery> findById(UUID id);

    PageResult<HuntQuery> search(HuntQueryFilter filter);

    /**
     * Une requête de chasse sauvegardée n'est pas une pièce d'évidence SOC
     * (contrairement à une alerte ou un cas) — c'est un gabarit de
     * recherche. La suppression réelle est donc autorisée, sans
     * contrepartie en no-delete comme ailleurs sur la plateforme.
     */
    void deleteById(UUID id);
}
