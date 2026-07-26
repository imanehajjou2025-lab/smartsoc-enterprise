package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.AlertStatistics;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.intelligence.Observable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class AlertRepositoryAdapter implements AlertRepository {

    private final SpringDataAlertRepository springDataRepository;
    private final AlertJpaMapper mapper;

    @Override
    public Alert save(Alert alert) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(alert)));
    }

    @Override
    public Optional<Alert> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Alert> findBySourceAndExternalId(String source, String externalId) {
        return springDataRepository.findBySourceAndExternalId(source, externalId)
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Alert> search(AlertQuery query) {
        Specification<AlertJpaEntity> spec = Specification.unrestricted();
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.severity() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("severity"), query.severity()));
        }
        if (query.source() != null && !query.source().isBlank()) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("source"), query.source().trim().toLowerCase()));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        Page<AlertJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                query.page().page(),
                query.page().size());
    }

    @Override
    public PageResult<Alert> findByNormalizedHostname(String normalizedHostname, PageQuery page) {
        // Le tri (detected_at desc) vit dans la requête native : le
        // Pageable ne porte que la pagination.
        Page<AlertJpaEntity> result = springDataRepository.findByNormalizedHostname(
                normalizedHostname, PageRequest.of(page.page(), page.size()));
        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getTotalElements(),
                page.page(),
                page.size());
    }

    /**
     * Retro-hunt : aucun état pré-calculé n'est consulté ici, la
     * correspondance est établie au moment de la lecture. C'est ce qui
     * fait qu'un indicateur créé après une alerte la retrouve quand même,
     * sans travail de rattrapage.
     */
    @Override
    public PageResult<Alert> findByObservable(Observable observable, PageQuery page) {
        // Le tri (detected_at desc) vit dans la requête native : le
        // Pageable ne porte que la pagination.
        Page<AlertJpaEntity> result = springDataRepository.findByObservable(
                observable.type().name(), observable.value(),
                PageRequest.of(page.page(), page.size()));
        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getTotalElements(),
                page.page(),
                page.size());
    }

    /**
     * Retro-hunt MITRE : aucun état pré-calculé n'est consulté, la
     * correspondance est établie à la lecture — une alerte remonte pour une
     * technique consultée après son ingestion, sans rattrapage.
     */
    @Override
    public PageResult<Alert> findByMitreTechnique(String normalizedAttackId, PageQuery page) {
        // Le tri (detected_at desc) vit dans la requête native : le Pageable
        // ne porte que la pagination.
        Page<AlertJpaEntity> result = springDataRepository.findByMitreTechnique(
                jsonArrayOf(normalizedAttackId), PageRequest.of(page.page(), page.size()));
        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getTotalElements(),
                page.page(),
                page.size());
    }

    @Override
    public List<MitreCoverageCount> mitreCoverage() {
        return springDataRepository.mitreCoverageCounts().stream()
                .map(row -> new MitreCoverageCount((String) row[0], (Long) row[1]))
                .toList();
    }

    @Override
    public AlertStatistics statistics(int timelineDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(timelineDays - 1L);

        Map<LocalDate, Long> countsPerDay = new LinkedHashMap<>();
        for (Object[] row : springDataRepository.countPerDaySince(
                from.atStartOfDay().toInstant(ZoneOffset.UTC))) {
            countsPerDay.put(LocalDate.parse((String) row[0]), (Long) row[1]);
        }
        // Jours vides inclus : une courbe d'activité montre aussi les silences.
        List<AlertStatistics.DailyCount> timeline = from.datesUntil(today.plusDays(1))
                .map(day -> new AlertStatistics.DailyCount(day, countsPerDay.getOrDefault(day, 0L)))
                .toList();

        Map<Severity, Long> bySeverity = groupCounts(
                springDataRepository.countGroupedBySeverity(), Severity.class::cast);
        Map<AlertStatus, Long> byStatus = groupCounts(
                springDataRepository.countGroupedByStatus(), AlertStatus.class::cast);
        Map<String, Long> bySource = groupCounts(
                springDataRepository.countGroupedBySource(), String.class::cast);

        long total = bySeverity.values().stream().mapToLong(Long::longValue).sum();
        return new AlertStatistics(total, bySeverity, byStatus, bySource, timeline);
    }

    @Override
    public AlertPeriodMetrics periodMetrics(Instant from, Instant to) {
        Map<Severity, Long> bySeverity = groupCounts(
                springDataRepository.countGroupedBySeverityInPeriod(from, to), Severity.class::cast);
        Map<AlertStatus, Long> byStatus = groupCounts(
                springDataRepository.countGroupedByStatusInPeriod(from, to), AlertStatus.class::cast);
        long total = bySeverity.values().stream().mapToLong(Long::longValue).sum();
        return new AlertPeriodMetrics(total, bySeverity, byStatus);
    }

    private static <K> Map<K, Long> groupCounts(List<Object[]> rows, Function<Object, K> keyMapper) {
        Map<K, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(keyMapper.apply(row[0]), (Long) row[1]);
        }
        return counts;
    }

    /** L'attackId devient un tableau JSONB d'un élément, forme attendue par {@code @>}. */
    private static String jsonArrayOf(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "[\"" + escaped + "\"]";
    }
}
