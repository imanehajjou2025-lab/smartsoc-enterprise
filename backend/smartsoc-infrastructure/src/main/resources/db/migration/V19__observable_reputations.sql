-- ============================================================
-- V19 - Bounded context: reputation (ADR-014 phase 3)
-- Cache OBLIGATOIRE des reputations d'observables (VirusTotal) -- pas
-- une optimisation, la protection du quota strict (4 req/min, 500/jour
-- en offre gratuite). Consulte a la demande d'un analyste uniquement,
-- jamais sur le flux (R4). Identite = (source, type, valeur) : meme
-- vocabulaire de type que le contexte intelligence (IndicatorType).
-- ============================================================

CREATE TABLE observable_reputations (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    source             VARCHAR(20)   NOT NULL,
    type               VARCHAR(10)   NOT NULL
        CONSTRAINT ck_observable_reputations_type
        CHECK (type IN ('IPV4', 'IPV6', 'DOMAIN', 'URL',
                        'MD5', 'SHA1', 'SHA256', 'EMAIL')),
    value              VARCHAR(2048) NOT NULL,
    verdict            VARCHAR(10)   NOT NULL
        CONSTRAINT ck_observable_reputations_verdict
        CHECK (verdict IN ('MALICIOUS', 'SUSPICIOUS', 'HARMLESS', 'UNDETECTED')),
    malicious_count    INTEGER       NOT NULL DEFAULT 0,
    suspicious_count   INTEGER       NOT NULL DEFAULT 0,
    harmless_count     INTEGER       NOT NULL DEFAULT 0,
    undetected_count   INTEGER       NOT NULL DEFAULT 0,
    first_checked_at   TIMESTAMPTZ   NOT NULL,
    checked_at         TIMESTAMPTZ   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by         VARCHAR(50)   NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE observable_reputations IS 'Cached reputation lookups (VirusTotal), consulted on analyst demand only -- cache is the quota safeguard, not an optimization';

-- IDENTITE (source, type, value) : la cle du cache, verifiee AVANT tout
-- appel a la source externe (voir ObservableReputationService).
CREATE UNIQUE INDEX ux_observable_reputations_identity
    ON observable_reputations (source, type, value);

-- Fraicheur du cache : "ce relevé a-t-il moins de X heures ?"
CREATE INDEX ix_observable_reputations_checked_at ON observable_reputations (checked_at);
