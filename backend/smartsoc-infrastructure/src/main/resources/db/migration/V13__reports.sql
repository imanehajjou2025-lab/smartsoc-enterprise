-- ============================================================
-- V13 - Bounded context: reporting (rapports SOC)
-- Un rapport est un instantane FIGE d'indicateurs sur une periode
-- passee, distinct du tableau de bord (toujours "maintenant").
-- Artefact d'audit une fois genere : pas de mise a jour, pas de
-- suppression.
--
-- closed_at complete incidents (V4) : necessaire pour calculer le
-- temps moyen de resolution d'un rapport, absent jusqu'ici car aucun
-- module n'en avait besoin. Colonne nullable, retrocompatible, remplie
-- desormais par Incident.transitionTo() a l'entree en CLOSED.
-- ============================================================

ALTER TABLE incidents ADD COLUMN closed_at TIMESTAMPTZ;

COMMENT ON COLUMN incidents.closed_at IS 'Set once, when the incident enters the terminal CLOSED status';

CREATE TABLE reports (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    title          VARCHAR(200)  NOT NULL,
    period_start   TIMESTAMPTZ   NOT NULL,
    period_end     TIMESTAMPTZ   NOT NULL,
    generated_at   TIMESTAMPTZ   NOT NULL,
    generated_by   VARCHAR(50)   NOT NULL,
    -- Instantane fige des indicateurs (ReportMetrics) : record plat sans
    -- hierarchie scellee, serialise nativement en JSONB par Jackson,
    -- meme choix que playbooks.steps (V12).
    metrics        JSONB         NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by     VARCHAR(50)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_reports_period CHECK (period_start < period_end)
);

COMMENT ON TABLE reports IS 'Generated SOC report snapshots — immutable once created, no delete';

CREATE INDEX ix_reports_generated_at ON reports (generated_at DESC);
