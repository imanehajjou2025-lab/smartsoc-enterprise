-- ============================================================
-- V12 - Bounded context: SOAR (playbooks de reponse)
-- Ce module documente des procedures de reponse et suit leur
-- deroulement par un analyste contre un incident -- ce n'est PAS
-- un moteur d'automatisation. L'automatisation reelle (bloquer une
-- IP, isoler un poste) reste le role de Shuffle, opere hors de la
-- plateforme (ADR-006, ADR-012).
--
-- Versioning leger : playbooks.version est un compteur incremente
-- a chaque edition. Aucune table d'historique separee : chaque
-- execution fige sa PROPRE copie (playbook_version/playbook_name),
-- donc une edition ulterieure du gabarit ne change jamais une
-- execution deja demarree ou terminee.
--
-- Pas de suppression de playbook (connaissance institutionnelle,
-- contrairement a une requete de chasse) : archive uniquement.
-- ============================================================

CREATE TABLE playbooks (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(200)  NOT NULL,
    description    TEXT,
    version        INTEGER       NOT NULL DEFAULT 1,
    -- Gabarit d'etapes ordonnees : [{order, title, description}].
    steps          JSONB         NOT NULL,
    archived       BOOLEAN       NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by     VARCHAR(50)   NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE playbooks IS 'SOAR response playbook definitions (documented procedure, not an automation engine)';
COMMENT ON COLUMN playbooks.steps IS 'Ordered step template array: [{order, title, description}]';

CREATE INDEX ix_playbooks_archived ON playbooks (archived);

CREATE TABLE playbook_executions (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    playbook_id       UUID          NOT NULL REFERENCES playbooks (id),
    -- Snapshots pris au demarrage : une edition ulterieure du
    -- playbook (version, nom) ne change jamais cette execution.
    playbook_version  INTEGER       NOT NULL,
    playbook_name     VARCHAR(200)  NOT NULL,
    incident_id       UUID          NOT NULL REFERENCES incidents (id),
    status            VARCHAR(20)   NOT NULL
        CONSTRAINT ck_playbook_executions_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    started_at        TIMESTAMPTZ   NOT NULL,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)   NOT NULL DEFAULT 'system',

    -- Un statut terminal porte sa date ; IN_PROGRESS n'en porte aucune
    -- (meme forme que ck_indicators_revocation).
    CONSTRAINT ck_playbook_executions_completion CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (status <> 'IN_PROGRESS' AND completed_at IS NOT NULL))
);

CREATE INDEX ix_playbook_executions_incident ON playbook_executions (incident_id);

CREATE TABLE playbook_execution_steps (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id   UUID          NOT NULL REFERENCES playbook_executions (id) ON DELETE CASCADE,
    -- "order" est un mot reserve SQL : colonne nommee step_order.
    step_order     INTEGER       NOT NULL,
    -- Snapshot du titre au demarrage de l'execution (meme raison que
    -- playbook_name ci-dessus).
    title          VARCHAR(500)  NOT NULL,
    status         VARCHAR(20)   NOT NULL
        CONSTRAINT ck_playbook_execution_steps_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'SKIPPED')),
    note           TEXT,
    completed_at   TIMESTAMPTZ
);

CREATE INDEX ix_playbook_execution_steps_execution ON playbook_execution_steps (execution_id, step_order);
