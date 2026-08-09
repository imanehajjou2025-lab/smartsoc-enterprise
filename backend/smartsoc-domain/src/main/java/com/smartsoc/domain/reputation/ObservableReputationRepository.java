package com.smartsoc.domain.reputation;

import com.smartsoc.domain.intelligence.IndicatorType;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for the observable reputation cache (ADR-014 phase 3). */
public interface ObservableReputationRepository {

    ObservableReputation save(ObservableReputation reputation);

    Optional<ObservableReputation> findById(UUID id);

    /** Recherche par identité — la clé du cache, vérifiée AVANT tout appel à la source externe. */
    Optional<ObservableReputation> findByIdentity(String source, IndicatorType type, String value);
}
