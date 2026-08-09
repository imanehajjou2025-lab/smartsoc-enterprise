-- ============================================================
-- V18 - Bounded context: vulnerabilities (ADR-014 SS1.3)
-- Une vulnerabilite (CVE) detectee par le scanner Wazuh (Indexer,
-- source confirmee en reel le 2026-08-09) sur un actif deja connu du
-- connecteur agents (phase 1.2). Identite = (source, external_id), le
-- doc id du scanner. Pas de suppression : une vulnerabilite corrigee
-- passe RESOLVED et reste consultable (meme doctrine que les indicateurs
-- et les actifs decommissionnes).
-- ============================================================

CREATE TABLE vulnerabilities (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id              UUID          NOT NULL REFERENCES assets (id),
    source                VARCHAR(20)   NOT NULL,
    external_id           VARCHAR(255)  NOT NULL,
    cve_id                VARCHAR(50)   NOT NULL,
    severity              VARCHAR(10)   NOT NULL
        CONSTRAINT ck_vulnerabilities_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNTRIAGED')),
    cvss_score            DOUBLE PRECISION,
    cvss_version          VARCHAR(10),
    description           TEXT,
    package_name          VARCHAR(255),
    package_version       VARCHAR(255),
    package_architecture  VARCHAR(50),
    detected_at           TIMESTAMPTZ,
    published_at          TIMESTAMPTZ,
    reference             VARCHAR(2000),
    status                VARCHAR(10)   NOT NULL
        CONSTRAINT ck_vulnerabilities_status
        CHECK (status IN ('OPEN', 'RESOLVED')),
    first_seen_at         TIMESTAMPTZ   NOT NULL,
    last_seen_at          TIMESTAMPTZ   NOT NULL,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            VARCHAR(50)   NOT NULL DEFAULT 'system',

    -- Un RESOLVED porte sa date ; un OPEN aucune (meme forme que ck_indicators_revocation).
    CONSTRAINT ck_vulnerabilities_resolution CHECK (
        (status = 'RESOLVED' AND resolved_at IS NOT NULL)
        OR (status = 'OPEN' AND resolved_at IS NULL))
);

COMMENT ON TABLE vulnerabilities IS 'CVE findings detected by the Wazuh vulnerability scanner (Indexer), attached to an existing asset';
COMMENT ON COLUMN vulnerabilities.external_id IS 'Scanner doc id (e.g. Wazuh Indexer _id) - PAS unique a lui seul, voir ux_vulnerabilities_external_ref';
COMMENT ON COLUMN vulnerabilities.source IS 'Connecteur d''origine (ex. wazuh)';

-- IDENTITE (source, external_id) : cle de reconciliation d'un cycle de
-- scan, meme role que ux_assets_external_ref.
CREATE UNIQUE INDEX ux_vulnerabilities_external_ref
    ON vulnerabilities (source, external_id);

-- Reconciliation par actif : "ce qui reste OPEN pour cet actif" (voir
-- VulnerabilityReconciliationService.resolveMissing).
CREATE INDEX ix_vulnerabilities_asset_status
    ON vulnerabilities (asset_id, status);

-- Chemins d'acces de la console : fiche d'actif et ecran dedie.
CREATE INDEX ix_vulnerabilities_severity     ON vulnerabilities (severity);
CREATE INDEX ix_vulnerabilities_cve_id       ON vulnerabilities (cve_id);
CREATE INDEX ix_vulnerabilities_last_seen_at ON vulnerabilities (last_seen_at DESC);
