-- ============================================================
-- V3 - Bounded context: alerts
-- Alerte de securite normalisee. Pas de soft delete : une alerte
-- est une piece d'evidence SOC, elle ne se supprime pas.
-- L'evenement brut integral est conserve en JSONB (investigation).
-- ============================================================

CREATE TABLE alerts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source           VARCHAR(50)  NOT NULL,
    external_id      VARCHAR(255) NOT NULL,
    title            VARCHAR(500) NOT NULL,
    description      TEXT,
    severity         VARCHAR(10)  NOT NULL
        CONSTRAINT ck_alerts_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    status           VARCHAR(20)  NOT NULL DEFAULT 'NEW'
        CONSTRAINT ck_alerts_status
        CHECK (status IN ('NEW', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'FALSE_POSITIVE')),
    detected_at      TIMESTAMPTZ  NOT NULL,
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    hostname         VARCHAR(255),
    rule_id          VARCHAR(100),
    mitre_techniques JSONB        NOT NULL DEFAULT '[]'::jsonb,
    raw_payload      JSONB,
    ai_score         DOUBLE PRECISION
        CONSTRAINT ck_alerts_ai_score CHECK (ai_score IS NULL OR (ai_score >= 0 AND ai_score <= 1)),
    ai_verdict       VARCHAR(20)
        CONSTRAINT ck_alerts_ai_verdict
        CHECK (ai_verdict IS NULL OR ai_verdict IN ('TRUE_POSITIVE', 'FALSE_POSITIVE')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       VARCHAR(50)  NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE alerts IS 'Normalized security alerts ingested from SOC tools';
COMMENT ON COLUMN alerts.raw_payload IS 'Full original event as received (investigation evidence)';

-- Deduplication de l'ingestion
CREATE UNIQUE INDEX ux_alerts_source_external ON alerts (source, external_id);

-- Chemins d'acces de la console (file de triage, filtres, tri temporel)
CREATE INDEX ix_alerts_status      ON alerts (status);
CREATE INDEX ix_alerts_severity    ON alerts (severity);
CREATE INDEX ix_alerts_detected_at ON alerts (detected_at DESC);
CREATE INDEX ix_alerts_hostname    ON alerts (hostname) WHERE hostname IS NOT NULL;
