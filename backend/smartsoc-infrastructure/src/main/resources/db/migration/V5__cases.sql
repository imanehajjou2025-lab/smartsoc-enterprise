-- ============================================================
-- V5 - Bounded context: investigations
-- Un cas (Case) est le dossier d'enquete au-dessus des incidents :
-- il regroupe incidents et alertes, porte une checklist de taches
-- et une timeline propre, et se conclut formellement.
-- Reference lisible CASE-YYYY-NNNN via une sequence dediee.
-- CLOSED est definitif : la reprise passe par un cas de suivi
-- reference par origin_case_id (auto-reference).
-- Pas de soft delete : un cas est une piece de dossier SOC.
-- ============================================================

CREATE SEQUENCE case_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE cases (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         VARCHAR(20)  NOT NULL,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    priority          VARCHAR(10)  NOT NULL
        CONSTRAINT ck_cases_priority
        CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT ck_cases_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    assignee_username VARCHAR(50),
    conclusion        TEXT,
    origin_case_id    UUID         REFERENCES cases (id),
    opened_at         TIMESTAMPTZ  NOT NULL,
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)  NOT NULL DEFAULT 'system',
    -- Un cas clos porte sa conclusion et sa date ; un cas ouvert ni l'un ni l'autre.
    CONSTRAINT ck_cases_closure CHECK (
        (status = 'CLOSED' AND conclusion IS NOT NULL AND closed_at IS NOT NULL)
        OR (status <> 'CLOSED' AND conclusion IS NULL AND closed_at IS NULL))
);

COMMENT ON TABLE cases IS 'Investigation cases grouping incidents/alerts under a formal enquiry';

CREATE UNIQUE INDEX ux_cases_reference ON cases (reference);
CREATE INDEX ix_cases_status    ON cases (status);
CREATE INDEX ix_cases_priority  ON cases (priority);
CREATE INDEX ix_cases_assignee  ON cases (assignee_username) WHERE assignee_username IS NOT NULL;
CREATE INDEX ix_cases_origin    ON cases (origin_case_id) WHERE origin_case_id IS NOT NULL;
CREATE INDEX ix_cases_opened_at ON cases (opened_at DESC);

-- Liaisons N <-> N (idempotentes via PK composite + ON CONFLICT DO NOTHING)
CREATE TABLE case_incidents (
    case_id     UUID        NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    incident_id UUID        NOT NULL REFERENCES incidents (id),
    linked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, incident_id)
);

CREATE INDEX ix_case_incidents_incident ON case_incidents (incident_id);

CREATE TABLE case_alerts (
    case_id   UUID        NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    alert_id  UUID        NOT NULL REFERENCES alerts (id),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, alert_id)
);

CREATE INDEX ix_case_alerts_alert ON case_alerts (alert_id);

-- Checklist des taches d'analyse
CREATE TABLE case_tasks (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           UUID        NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    title             VARCHAR(500) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'TODO'
        CONSTRAINT ck_case_tasks_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    assignee_username VARCHAR(50),
    created_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ
);

CREATE INDEX ix_case_tasks_case ON case_tasks (case_id, created_at);

-- Timeline propre au cas (audit de l'enquete globale)
CREATE TABLE case_timeline (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id     UUID        NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    entry_type  VARCHAR(20) NOT NULL
        CONSTRAINT ck_case_timeline_type
        CHECK (entry_type IN ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'UNASSIGNED',
                              'INCIDENT_LINKED', 'INCIDENT_UNLINKED',
                              'ALERT_LINKED', 'ALERT_UNLINKED',
                              'TASK_ADDED', 'TASK_UPDATED', 'TASK_COMPLETED',
                              'NOTE_ADDED', 'CLOSED', 'FOLLOW_UP_OPENED')),
    message     TEXT        NOT NULL,
    author      VARCHAR(50) NOT NULL DEFAULT 'system',
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_case_timeline_case ON case_timeline (case_id, occurred_at);
