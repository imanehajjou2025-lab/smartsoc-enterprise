package com.smartsoc.application.audit;

import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogEntry;
import com.smartsoc.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Enregistre une action sensible dans le journal d'audit. Deux garanties :
 *
 * <p><b>1. Une entrée d'audit survit toujours à la transaction appelante.</b>
 * {@code REQUIRES_NEW} ouvre une transaction indépendante qui se valide
 * (commit) même si l'opération d'origine échoue ensuite et s'annule — un
 * login refusé doit rester tracé même si son propre traitement lève une
 * exception juste après (même correctif que {@code refresh()} dans
 * {@code AuthService}, généralisé une fois pour toutes ici).
 *
 * <p><b>2. Un échec d'enregistrement n'interrompt jamais l'appelant.</b>
 * Même doctrine que {@code ReportNotifier} : une notification (ici, une
 * trace d'audit) manquée ne doit jamais faire échouer l'action métier
 * qu'elle observe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String actorUsername, UUID actorId,
                        String targetType, String targetId, String details, String ipAddress) {
        try {
            auditLogRepository.save(AuditLogEntry.record(
                    action, actorUsername, actorId, targetType, targetId, details, ipAddress));
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit log entry for action {} by {}: {}",
                    action, actorUsername, ex.getMessage());
        }
    }
}
