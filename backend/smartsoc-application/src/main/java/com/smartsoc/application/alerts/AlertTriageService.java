package com.smartsoc.application.alerts;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consultation et triage des alertes par les analystes. Les règles de
 * transition appartiennent au domaine (AlertStatus) ; ce service ne fait
 * qu'orchestrer chargement, transition et persistance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertTriageService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public PageResult<Alert> search(AlertQuery query) {
        return alertRepository.search(query);
    }

    @Transactional(readOnly = true)
    public Alert getAlert(UUID id) {
        return requireAlert(id);
    }

    @Transactional
    public Alert changeStatus(UUID id, AlertStatus newStatus) {
        Alert alert = requireAlert(id);
        AlertStatus previous = alert.getStatus();
        alert.transitionTo(newStatus);
        Alert saved = alertRepository.save(alert);
        log.info("Alert {} triaged: {} -> {}", id, previous, newStatus);
        return saved;
    }

    private Alert requireAlert(UUID id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }
}
