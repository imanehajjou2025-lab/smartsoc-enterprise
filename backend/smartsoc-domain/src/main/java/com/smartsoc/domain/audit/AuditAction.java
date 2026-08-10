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
    BACKUP_EXPORTED,
    /**
     * Action réelle sur un agent Wazuh (ADR-014 phase 5) — toujours
     * enregistrée, succès ou échec, l'issue étant portée par {@code details}
     * (jamais deux valeurs d'enum pour un même acte, pour que le plafond
     * horaire de {@code SocActionService} compte tout appel réel en un
     * seul filtre).
     */
    WAZUH_AGENT_RESTART_REQUESTED,
    /**
     * Active-response {@code firewall-drop} (ADR-014 phase 5) — seule
     * commande active-response réellement configurée sur le manager SOC,
     * confirmée en réel avant tout code. Même doctrine que le redémarrage :
     * un seul type d'audit, succès ET échec, {@code details} porte l'issue.
     */
    WAZUH_AGENT_FIREWALL_DROP_REQUESTED,
    /**
     * Déclenchement manuel d'un workflow Shuffle par un analyste (ADR-014
     * phase 5) — même doctrine : un seul type d'audit, succès ET échec,
     * {@code details} porte l'issue et l'identifiant d'exécution Shuffle.
     */
    SHUFFLE_WORKFLOW_TRIGGER_REQUESTED
}
