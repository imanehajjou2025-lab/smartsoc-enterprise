package com.smartsoc.application.alerts;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.AnalystTier;
import com.smartsoc.domain.audit.AuditAction;
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
    private final AuditRecorder auditRecorder;

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

    @Transactional
    public Alert assign(UUID id, AnalystTier tier, String username, ActorContext actor) {
        Alert alert = requireAlert(id);
        alert.assignToTier(tier, username);
        Alert saved = alertRepository.save(alert);
        String details = "tier=%s%s".formatted(tier,
                saved.getAssignedToUsername() == null ? "" : "; assignee=" + saved.getAssignedToUsername());
        auditRecorder.record(AuditAction.ALERT_ASSIGNED, actor.username(), actor.userId(),
                "Alert", id.toString(), details, actor.ipAddress());
        log.info("Alert {} assigned to tier {} ({})", id, tier, saved.getAssignedToUsername());
        return saved;
    }

    @Transactional
    public Alert unassign(UUID id, ActorContext actor) {
        Alert alert = requireAlert(id);
        alert.unassign();
        Alert saved = alertRepository.save(alert);
        auditRecorder.record(AuditAction.ALERT_UNASSIGNED, actor.username(), actor.userId(),
                "Alert", id.toString(), null, actor.ipAddress());
        log.info("Alert {} unassigned", id);
        return saved;
    }

    private Alert requireAlert(UUID id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }
}
