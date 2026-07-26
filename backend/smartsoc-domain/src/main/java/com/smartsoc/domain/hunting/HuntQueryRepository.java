package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.PageResult;

import java.time.Instant;
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

    /**
     * Nombre de requêtes de chasse dont la dernière exécution
     * ({@code lastExecutedAt}) tombe dans {@code [from, to)} — brique
     * « hunting » d'un rapport (module reporting). Approximation
     * annoncée en clair : aucun historique d'exécution n'est persisté
     * (ADR-011), donc une requête ré-exécutée plusieurs fois dans la
     * période ne compte qu'une fois, et une requête exécutée dans la
     * période puis ré-exécutée après ne compte plus du tout (son
     * {@code lastExecutedAt} a avancé hors période).
     */
    long countExecutedInPeriod(Instant from, Instant to);
}
