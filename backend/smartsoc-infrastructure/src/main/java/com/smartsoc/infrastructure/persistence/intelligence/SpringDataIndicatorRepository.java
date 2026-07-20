package com.smartsoc.infrastructure.persistence.intelligence;

import com.smartsoc.domain.intelligence.IndicatorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataIndicatorRepository
        extends JpaRepository<IndicatorJpaEntity, UUID>,
        JpaSpecificationExecutor<IndicatorJpaEntity> {

    /** Recherche par identité métier — emprunte ux_indicators_identity. */
    Optional<IndicatorJpaEntity> findByTypeAndValue(IndicatorType type, String value);

    /**
     * Indicateurs ACTIFS correspondant à un lot d'observables.
     *
     * <p>Les couples cherchés arrivent en deux tableaux parallèles que
     * {@code unnest} recompose en table : le moteur voit alors une
     * jointure ordinaire sur {@code (type, value)} et emprunte
     * {@code ux_indicators_identity}. C'est la formulation qui reste
     * indexable quel que soit le nombre d'observables — une longue
     * disjonction de {@code OR} dégénérerait.
     *
     * <p><b>Les deux conditions d'activité sont DANS la requête</b> :
     * un indicateur révoqué, ou dont la fenêtre de validité est dépassée
     * à l'instant demandé, ne peut pas remonter. Rien à filtrer ensuite
     * côté Java, donc rien à oublier de filtrer.
     */
    @Query(value = """
            SELECT i.*
            FROM indicators i
            JOIN unnest(CAST(:types AS text[]), CAST(:values AS text[])) AS o(type, value)
              ON i.type = o.type AND i.value = o.value
            WHERE i.revoked = false
              AND (i.valid_until IS NULL OR i.valid_until > :evaluatedAt)
            """, nativeQuery = true)
    List<IndicatorJpaEntity> findActiveMatching(@Param("types") String[] types,
                                                @Param("values") String[] values,
                                                @Param("evaluatedAt") Instant evaluatedAt);
}
