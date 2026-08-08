-- ============================================================
-- V16 - Bounded context: connectors (ADR-014)
-- Etat des connecteurs SOC + extension additive du module assets
-- pour recevoir les donnees synchronisees (agents Wazuh, phase 1.2).
-- ============================================================

CREATE TABLE soc_connectors (
    type                     VARCHAR(20)  PRIMARY KEY
        CONSTRAINT ck_soc_connectors_type
        CHECK (type IN ('WAZUH', 'OPENSEARCH', 'MISP', 'VIRUSTOTAL', 'SHUFFLE')),
    status                   VARCHAR(20)  NOT NULL
        CONSTRAINT ck_soc_connectors_status
        CHECK (status IN ('NOT_CONFIGURED', 'DISABLED', 'CONNECTED', 'DEGRADED', 'DISCONNECTED')),
    detected_version         VARCHAR(100),
    -- Capacites detectees, tableau de chaines JSONB : une capacite absente
    -- est declaree indisponible dans la console, jamais masquee (ADR-014).
    detected_capabilities    JSONB,
    descriptor_detected_at   TIMESTAMPTZ,
    last_checked_at          TIMESTAMPTZ,
    last_successful_sync_at  TIMESTAMPTZ,
    last_error               VARCHAR(500),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                VARCHAR(50)  NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE soc_connectors IS 'Current connector state, one row per ConnectorType - not a call log (see sync_runs)';

CREATE TABLE sync_runs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_type   VARCHAR(20)  NOT NULL
        CONSTRAINT ck_sync_runs_connector_type
        CHECK (connector_type IN ('WAZUH', 'OPENSEARCH', 'MISP', 'VIRUSTOTAL', 'SHUFFLE')),
    started_at       TIMESTAMPTZ  NOT NULL,
    finished_at      TIMESTAMPTZ,
    outcome          VARCHAR(10)
        CONSTRAINT ck_sync_runs_outcome
        CHECK (outcome IN ('SUCCESS', 'PARTIAL', 'FAILURE')),
    items_processed  INTEGER      NOT NULL DEFAULT 0,
    items_rejected   INTEGER      NOT NULL DEFAULT 0,
    error_message    VARCHAR(1000),
    -- En cours = finished_at et outcome tous deux absents ; jamais l'un sans l'autre.
    CONSTRAINT ck_sync_runs_terminal_pair CHECK (
        (finished_at IS NULL AND outcome IS NULL)
        OR (finished_at IS NOT NULL AND outcome IS NOT NULL))
);

COMMENT ON TABLE sync_runs IS 'Timestamped trace of one synchronization execution - the automated counterpart of audit_log';

-- Vue "dernieres executions d'un connecteur" affichee par la console.
CREATE INDEX ix_sync_runs_connector_started
    ON sync_runs (connector_type, started_at DESC);

-- Detection des executions restees "en cours" anormalement longtemps
-- (callback jamais recu, reconciliation phase 5) : outcome NULL = en cours.
CREATE INDEX ix_sync_runs_in_progress
    ON sync_runs (connector_type) WHERE outcome IS NULL;

-- ============================================================
-- Extension additive du module assets (ADR-014) : nullable, jamais
-- requis - un actif enregistre a la main reste pleinement valide sans
-- jamais renseigner ces colonnes.
-- ============================================================

ALTER TABLE assets
    ADD COLUMN operating_system VARCHAR(255),
    ADD COLUMN last_seen_at     TIMESTAMPTZ,
    ADD COLUMN external_id      VARCHAR(100),
    ADD COLUMN external_source  VARCHAR(20);

COMMENT ON COLUMN assets.external_id IS 'Identifiant chez la source externe (ex. id agent Wazuh) - PAS unique a lui seul, voir ux_assets_external_ref';
COMMENT ON COLUMN assets.external_source IS 'Connecteur d''origine (ex. wazuh) - NULL pour un actif enregistre a la main';

-- Un meme id externe peut exister pour des sources differentes (Wazuh id
-- "004" et un futur inventaire tiers avec sa propre numerotation) : l'unicite
-- porte sur le COUPLE (source, id), jamais sur external_id seul. Partielle :
-- ne s'applique qu'aux actifs synchronises (external_source IS NOT NULL) --
-- un actif saisi a la main n'a pas a etre concerne par cette contrainte.
CREATE UNIQUE INDEX ux_assets_external_ref
    ON assets (external_source, external_id)
    WHERE external_source IS NOT NULL;
