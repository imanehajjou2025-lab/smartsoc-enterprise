package com.smartsoc.application.alerts;

import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.AlertStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statistiques du dashboard SOC. */
@Service
@RequiredArgsConstructor
public class AlertStatsService {

    /** Fenêtre par défaut de la timeline d'activité. */
    public static final int DEFAULT_TIMELINE_DAYS = 7;

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public AlertStatistics statistics() {
        return alertRepository.statistics(DEFAULT_TIMELINE_DAYS);
    }
}
