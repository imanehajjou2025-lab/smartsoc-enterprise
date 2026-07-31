package com.smartsoc.domain.audit;

/**
 * Actions sensibles tracées par le journal d'audit (console Paramètres).
 * Liste volontairement restreinte aux actions réellement interceptées par
 * la plateforme aujourd'hui — jamais une catégorie "au cas où" sans point
 * d'enregistrement réel derrière.
 */
public enum AuditAction {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    USER_CREATED,
    USER_UPDATED,
    USER_ROLE_CHANGED,
    USER_ENABLED,
    USER_DISABLED,
    USER_DELETED,
    BACKUP_EXPORTED
}
