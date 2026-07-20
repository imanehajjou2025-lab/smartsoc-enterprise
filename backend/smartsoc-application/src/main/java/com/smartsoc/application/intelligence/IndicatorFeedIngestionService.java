package com.smartsoc.application.intelligence;

import com.smartsoc.application.intelligence.IndicatorService.IngestionResult;
import com.smartsoc.domain.common.DomainException;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ingestion d'un LOT poussé par un flux CTI.
 *
 * <p><b>Tolérance par élément, volontairement.</b> Un flux réel contient
 * des entrées malformées ; refuser le lot entier priverait le SOC de tous
 * les indicateurs valides à cause d'une seule ligne. Chaque élément est
 * donc ingéré indépendamment, et chaque rejet est nommé pour que le
 * producteur puisse corriger sa source.
 *
 * <p><b>Pourquoi une classe séparée d'{@link IndicatorService}.</b> Deux
 * raisons qui se cumulent, et qui rendent l'orchestration impossible dans
 * le même bean :
 * <ul>
 *   <li>en PostgreSQL, une violation de contrainte rend la transaction
 *       courante irrécupérable — un élément fautif condamnerait tous les
 *       suivants s'ils partageaient sa transaction ; il faut donc UNE
 *       transaction par élément ;</li>
 *   <li>or un appel interne à une méthode {@code @Transactional} du même
 *       bean court-circuite le proxy Spring et n'ouvre aucune
 *       transaction. Passer par un autre bean est ce qui garantit que
 *       chaque {@code ingest()} est réellement isolé.</li>
 * </ul>
 * Cette méthode n'est donc PAS transactionnelle : c'est le point même.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorFeedIngestionService {

    private final IndicatorService indicatorService;

    /** Un indicateur tel que le flux le décrit, avant toute normalisation. */
    public record FeedObservation(
            IndicatorType type,
            String value,
            int confidence,
            TlpMarking tlp,
            String externalId,
            String description,
            Set<String> tags,
            Instant observedAt,
            Instant validUntil) {
    }

    public record ItemFailure(int index, String value, String code, String message) {
    }

    public record BatchResult(int received, int created, int updated, List<ItemFailure> failures) {

        public int rejected() {
            return failures.size();
        }
    }

    public BatchResult ingestBatch(String feedSource, List<FeedObservation> items) {
        int created = 0;
        int updated = 0;
        List<ItemFailure> failures = new ArrayList<>();

        for (int index = 0; index < items.size(); index++) {
            FeedObservation item = items.get(index);
            try {
                IngestionResult result = indicatorService.ingest(Indicator.Observation.builder()
                        .type(item.type())
                        .value(item.value())
                        .confidence(item.confidence())
                        .tlp(item.tlp())
                        .feedSource(feedSource)
                        .externalId(item.externalId())
                        .description(item.description())
                        .tags(item.tags())
                        .observedAt(item.observedAt())
                        .validUntil(item.validUntil())
                        .build());
                if (result.created()) {
                    created++;
                } else {
                    updated++;
                }
            } catch (DomainException e) {
                // Entrée refusée par le domaine (valeur qui n'est pas du
                // type annoncé, confiance hors échelle, course perdue…).
                // Elle est nommée dans le compte rendu, le lot continue.
                failures.add(new ItemFailure(index, item.value(), e.getCode(), e.getMessage()));
            }
        }

        log.info("Feed {} pushed {} indicators: {} created, {} updated, {} rejected",
                feedSource, items.size(), created, updated, failures.size());
        return new BatchResult(items.size(), created, updated, List.copyOf(failures));
    }
}
