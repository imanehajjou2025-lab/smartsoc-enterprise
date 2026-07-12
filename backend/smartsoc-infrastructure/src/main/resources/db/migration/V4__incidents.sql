-- ============================================================
-- V4 - Bounded context: incidents
-- Un incident regroupe des alertes et porte le travail d'analyse.
-- Reference lisible INC-YYYY-NNNN via une sequence dediee.
-- Pas de soft delete : un incident est une piece de dossier SOC.
-- ============================================================

CREATE SEQUENCE incident_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE incidents (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         VARCHAR(20)  NOT NULL,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    severity          VARCHAR(10)  NOT NULL
        CONSTRAINT ck_incidents_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT ck_incidents_status
        CHECK (status IN ('OPEN', 'INVESTIGATING', 'CONTAINED', 'RESOLVED', 'CLOSED')),
    assignee_username VARCHAR(50),
    opened_at         TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)  NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE incidents IS 'SOC incidents grouping alerts under an analyst workflow';

CREATE UNIQUE INDEX ux_incidents_reference ON incidents (reference);
CREATE INDEX ix_incidents_status    ON incidents (status);
CREATE INDEX ix_incidents_severity  ON incidents (severity);
CREATE INDEX ix_incidents_assignee  ON incidents (assignee_username) WHERE assignee_username IS NOT NULL;
CREATE INDEX ix_incidents_opened_at ON incidents (opened_at DESC);

-- Liaison N alertes <-> 1 incident (une alerte peut rejoindre un incident)
CREATE TABLE incident_alerts (
    incident_id UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    alert_id    UUID        NOT NULL REFERENCES alerts (id),
    linked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (incident_id, alert_id)
);

CREATE INDEX ix_incident_alerts_alert ON incident_alerts (alert_id);

-- Timeline d'investigation (trace horodatee)
CREATE TABLE incident_timeline (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID        NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    entry_type  VARCHAR(20) NOT NULL
        CONSTRAINT ck_incident_timeline_type
        CHECK (entry_type IN ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'UNASSIGNED',
                              'NOTE', 'ALERT_LINKED', 'ALERT_UNLINKED')),
    message     TEXT        NOT NULL,
    author      VARCHAR(50) NOT NULL DEFAULT 'system',
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_incident_timeline_incident ON incident_timeline (incident_id, occurred_at);
