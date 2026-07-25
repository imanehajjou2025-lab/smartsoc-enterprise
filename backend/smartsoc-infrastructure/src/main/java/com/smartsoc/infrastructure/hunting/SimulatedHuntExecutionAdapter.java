package com.smartsoc.infrastructure.hunting;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntExecutionPort;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntExecutionSummary;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntNode;
import com.smartsoc.domain.hunting.HuntStatistics;
import com.smartsoc.infrastructure.persistence.alerts.AlertJpaEntity;
import com.smartsoc.infrastructure.persistence.alerts.AlertJpaMapper;
import com.smartsoc.infrastructure.persistence.alerts.SpringDataAlertRepository;
import com.smartsoc.infrastructure.persistence.common.JsonbFunctionContributor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptateur {@code simulation} du port de chasse — traduit un
 * {@link HuntGroup} en {@link Specification} JPA sur les alertes déjà
 * ingérées, réutilisant {@code SpringDataAlertRepository} tel quel (aucune
 * table ni entité propre au module hunting pour les résultats).
 *
 * <p>Seul adaptateur livré en V1 (voir ADR-011) : le mode {@code live}
 * (OpenSearch via les connecteurs, chemin déjà décidé par ADR-004) est
 * différé jusqu'à disposer d'un schéma d'index réel plutôt que deviné.
 *
 * <p>La forme V1 des critères, imposée côté domaine par {@code HuntQuery},
 * garantit que la racine est un {@code AND} de {@link HuntCondition}
 * plates — cet adaptateur peut donc caster chaque enfant sans re-vérifier.
 */
@Component
@RequiredArgsConstructor
public class SimulatedHuntExecutionAdapter implements HuntExecutionPort {

    private final SpringDataAlertRepository alertRepository;
    private final AlertJpaMapper alertMapper;
    private final EntityManager entityManager;

    @Override
    public HuntExecutionResult execute(HuntGroup criteria, PageQuery page) {
        Instant executedAt = Instant.now();
        long startNanos = System.nanoTime();

        Specification<AlertJpaEntity> spec = toSpecification(criteria);
        PageRequest pageRequest = PageRequest.of(page.page(), page.size(),
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        Page<AlertJpaEntity> jpaPage = alertRepository.findAll(spec, pageRequest);

        HuntStatistics statistics = new HuntStatistics(
                groupedCounts(spec, "severity", Severity.class),
                groupedCounts(spec, "status", AlertStatus.class),
                groupedCounts(spec, "source", String.class));

        long tookMillis = (System.nanoTime() - startNanos) / 1_000_000;
        PageResult<Alert> matches = new PageResult<>(
                jpaPage.getContent().stream().map(alertMapper::toDomain).toList(),
                jpaPage.getTotalElements(), page.page(), page.size());

        // huntId=null : ce port ignore l'origine (sauvegardée ou ad hoc),
        // la couche application l'attribue via HuntExecutionResult#withHuntId.
        // truncated=false : comptage exact PostgreSQL (voir HuntExecutionSummary).
        HuntExecutionSummary summary = new HuntExecutionSummary(
                null, executedAt, tookMillis, jpaPage.getTotalElements(), false);

        return new HuntExecutionResult(summary, statistics, matches);
    }

    /** Racine V1 garantie AND de conditions plates par le domaine (HuntQuery). */
    private static Specification<AlertJpaEntity> toSpecification(HuntGroup criteria) {
        Specification<AlertJpaEntity> spec = Specification.unrestricted();
        for (HuntNode child : criteria.children()) {
            spec = spec.and(toPredicate((HuntCondition) child));
        }
        return spec;
    }

    private static Specification<AlertJpaEntity> toPredicate(HuntCondition condition) {
        return switch (condition.field()) {
            case SEVERITY -> (root, q, cb) ->
                    cb.equal(root.get("severity"), Severity.valueOf(condition.value()));
            case STATUS -> (root, q, cb) ->
                    cb.equal(root.get("status"), AlertStatus.valueOf(condition.value()));
            case SOURCE -> textPredicate("source", condition);
            case HOSTNAME -> textPredicate("hostname", condition);
            case RULE_ID -> textPredicate("ruleId", condition);
            case DETECTED_AT -> detectedAtPredicate(condition);
            case MITRE_TECHNIQUE -> (root, q, cb) -> cb.isTrue(cb.function(
                    JsonbFunctionContributor.JSONB_ARRAY_CONTAINS, Boolean.class,
                    root.get("mitreTechniques"), cb.literal(jsonArrayOf(condition.value()))));
            // Recherche dans l'événement brut intégral (raw_payload,
            // colonne mappée JSON) : ILIKE non indexé en V1, limite
            // assumée et documentée (voir HuntField.RAW_PAYLOAD_TEXT).
            // Cast explicite requis : Hibernate 6 refuse de passer un
            // attribut JSON directement à lower() (voir JsonbFunctionContributor).
            case RAW_PAYLOAD_TEXT -> (root, q, cb) -> cb.like(
                    cb.lower(cb.function(JsonbFunctionContributor.JSONB_AS_TEXT, String.class,
                            root.get("rawPayload"))),
                    "%" + condition.value().toLowerCase(Locale.ROOT) + "%");
        };
    }

    /** EQUALS/CONTAINS insensibles à la casse : la valeur est déjà trim/normalisée par le domaine. */
    private static Specification<AlertJpaEntity> textPredicate(String attribute, HuntCondition condition) {
        String value = condition.value().toLowerCase(Locale.ROOT);
        return switch (condition.operator()) {
            case EQUALS -> (root, q, cb) -> cb.equal(cb.lower(root.get(attribute)), value);
            case CONTAINS -> (root, q, cb) -> cb.like(cb.lower(root.get(attribute)), "%" + value + "%");
            default -> throw new IllegalStateException(
                    "Unreachable: HuntField already restricts operators for " + attribute);
        };
    }

    private static Specification<AlertJpaEntity> detectedAtPredicate(HuntCondition condition) {
        Instant value = Instant.parse(condition.value());
        return switch (condition.operator()) {
            case GREATER_THAN -> (root, q, cb) -> cb.greaterThan(root.get("detectedAt"), value);
            case LESS_THAN -> (root, q, cb) -> cb.lessThan(root.get("detectedAt"), value);
            default -> throw new IllegalStateException(
                    "Unreachable: HuntField already restricts operators for detectedAt");
        };
    }

    private static String jsonArrayOf(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "[\"" + escaped + "\"]";
    }

    /**
     * Répartition du jeu de résultats par {@code attribute}, filtrée par la
     * MÊME spécification que la page de correspondances — même garantie
     * que les statistiques du dashboard : aucun compte séparé qui pourrait
     * diverger.
     */
    private <K> Map<K, Long> groupedCounts(Specification<AlertJpaEntity> spec, String attribute, Class<K> keyType) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<AlertJpaEntity> root = query.from(AlertJpaEntity.class);
        Path<K> path = root.get(attribute);
        query.multiselect(path, cb.count(root));

        Predicate predicate = spec.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        query.groupBy(path);

        Map<K, Long> counts = new LinkedHashMap<>();
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            counts.put(keyType.cast(row[0]), (Long) row[1]);
        }
        return counts;
    }
}
