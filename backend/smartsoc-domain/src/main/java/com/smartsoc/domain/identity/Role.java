package com.smartsoc.domain.identity;

/**
 * RBAC roles of the platform, ordered from most to least privileged.
 * A user holds exactly one role; permissions are derived from it at the
 * API layer. Kept as an enum (not a table): the role set is part of the
 * application's behaviour, not runtime data.
 */
public enum Role {
    /** Full platform administration: users, settings, integrations. */
    ADMIN,
    /** SOC supervision: dashboards, reports, incident escalation. */
    SOC_MANAGER,
    /** Day-to-day operations: alerts, incidents, investigations, playbooks. */
    SOC_ANALYST,
    /** Read-only access to dashboards and reports. */
    VIEWER
}
