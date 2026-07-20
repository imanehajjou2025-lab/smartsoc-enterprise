package com.smartsoc.application.intelligence;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.DuplicateResourceException;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorQuery;
import com.smartsoc.domain.intelligence.IndicatorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Cas d'usage du référentiel CTI.
 *
 * <p>Deux façons d'entrer un IOC, volontairement distinctes :
 * <ul>
 *   <li><b>l'ingestion d'un flux</b> ({@link #ingest}) est un UPSERT —
 *       repousser un indicateur déjà connu rafraîchit ses métadonnées et
 *       ne doit jamais échouer, sinon la promesse « vos rejeux sont sûrs »
 *       du contrat d'ingestion ne tient pas ;</li>
 *   <li><b>la déclaration manuelle</b> ({@link #declare}) par un analyste
 *       refuse le doublon en 409 : créer deux fois le même IOC à la main
 *       est une erreur qu'il faut lui montrer, pas absorber.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorService {

    private static final String MANUAL_SOURCE = "manual";

    private final IndicatorRepository indicatorRepository;

    /** Issue d'une ingestion : le 201/200 de l'API en dépend. */
    public record IngestionResult(Indicator indicator, boolean created) {
    }

    public record DeclareIndicatorCommand(
            IndicatorType type,
            String value,
            int confidence,
            TlpMarking tlp,
            String description,
            Set<String> tags,
            Instant validUntil) {
    }

    /**
     * Upsert d'ingestion, sur l'identité (type, valeur normalisée).
     *
     * <p>La normalisation est faite ICI, avant la recherche : chercher
     * avec la valeur brute du flux ne trouverait jamais l'enregistrement
     * existant et créerait un doublon à chaque passage.
     *
     * <p>Limite connue : deux flux qui poussent le MÊME indicateur
     * exactement en même temps peuvent se croiser entre la recherche et
     * l'insertion. Le perdant reçoit un conflit — que la contrainte
     * d'unicité garantit — et son rejeu suivant passera par la branche de
     * mise à jour. On ne rattrape pas la course dans la transaction : une
     * violation de contrainte la marque irrécupérable côté PostgreSQL.
     */
    @Transactional
    public IngestionResult ingest(Indicator.Observation observation) {
        if (observation == null || observation.type() == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_INDICATOR", "An indicator must have a type");
        }
        String normalizedValue = observation.type().normalize(observation.value());

        return indicatorRepository.findByIdentity(observation.type(), normalizedValue)
                .map(existing -> {
                    existing.refreshFrom(observation);
                    return new IngestionResult(indicatorRepository.save(existing), false);
                })
                .orElseGet(() -> {
                    try {
                        Indicator saved = indicatorRepository.save(Indicator.declare(observation));
                        log.info("Indicator {}:{} ingested from feed {}",
                                saved.getType(), saved.getValue(), saved.getFeedSource());
                        return new IngestionResult(saved, true);
                    } catch (DataIntegrityViolationException e) {
                        throw new DuplicateResourceException("INDICATOR_CONFLICT",
                                "Indicator %s:%s was created concurrently; retry the push"
                                        .formatted(observation.type(), normalizedValue));
                    }
                });
    }

    /** Déclaration manuelle par un analyste : le doublon est une erreur. */
    @Transactional
    public Indicator declare(DeclareIndicatorCommand command) {
        if (command == null || command.type() == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_INDICATOR", "An indicator must have a type");
        }
        String normalizedValue = command.type().normalize(command.value());
        indicatorRepository.findByIdentity(command.type(), normalizedValue)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("INDICATOR_ALREADY_EXISTS",
                            "An indicator already exists for %s '%s'"
                                    .formatted(command.type(), normalizedValue));
                });

        Indicator indicator = Indicator.declare(Indicator.Observation.builder()
                .type(command.type())
                .value(command.value())
                .confidence(command.confidence())
                .tlp(command.tlp())
                .feedSource(MANUAL_SOURCE)
                .description(command.description())
                .tags(command.tags())
                .validUntil(command.validUntil())
                .build());
        try {
            return indicatorRepository.save(indicator);
        } catch (DataIntegrityViolationException e) {
            // Course perdue contre une création concurrente du même IOC.
            throw new DuplicateResourceException("INDICATOR_ALREADY_EXISTS",
                    "An indicator already exists for %s '%s'"
                            .formatted(command.type(), normalizedValue));
        }
    }

    /** Décision d'analyste : cet IOC n'enrichira plus aucune alerte. */
    @Transactional
    public Indicator revoke(UUID id, String reason) {
        Indicator indicator = requireIndicator(id);
        indicator.revoke(reason);
        Indicator saved = indicatorRepository.save(indicator);
        log.info("Indicator {}:{} revoked", saved.getType(), saved.getValue());
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Indicator> search(IndicatorQuery query) {
        return indicatorRepository.search(query);
    }

    @Transactional(readOnly = true)
    public Indicator getIndicator(UUID id) {
        return requireIndicator(id);
    }

    private Indicator requireIndicator(UUID id) {
        return indicatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Indicator", id));
    }
}
