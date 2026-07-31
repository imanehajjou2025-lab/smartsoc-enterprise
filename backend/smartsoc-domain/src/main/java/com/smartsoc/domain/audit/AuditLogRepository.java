package com.smartsoc.domain.audit;

import com.smartsoc.domain.common.PageResult;

/**
 * Outbound port for audit log persistence. Aucune méthode de mise à jour
 * ni de suppression : une entrée d'audit est immuable une fois enregistrée
 * (voir {@link AuditLogEntry}).
 */
public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);

    PageResult<AuditLogEntry> search(AuditLogQuery query);
}
