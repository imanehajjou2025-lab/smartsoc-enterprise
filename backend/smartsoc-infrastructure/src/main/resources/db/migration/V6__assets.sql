-- ============================================================
-- V6 - Bounded context: assets
-- Inventaire des actifs supervises : le contexte metier du SOC
-- (criticite, exposition, proprietaire d'une machine qui leve
-- une alerte). Le hostname est la cle de correlation avec les
-- alertes : normalise (minuscules, sans espaces), unique et
-- immuable. Pas de suppression physique : un actif se
-- decommissionne (statut) et garde son historique.
-- ============================================================

CREATE TABLE assets (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    hostname          VARCHAR(255) NOT NULL,
    display_name      VARCHAR(255) NOT NULL,
    type              VARCHAR(20)  NOT NULL
        CONSTRAINT ck_assets_type
        CHECK (type IN ('SERVER', 'WORKSTATION', 'NETWORK_DEVICE', 'DATABASE',
                        'APPLICATION', 'CLOUD_RESOURCE', 'OTHER')),
    criticality       VARCHAR(10)  NOT NULL
        CONSTRAINT ck_assets_criticality
        CHECK (criticality IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    exposure          VARCHAR(20)  NOT NULL
        CONSTRAINT ck_assets_exposure
        CHECK (exposure IN ('INTERNET_FACING', 'INTERNAL', 'ISOLATED')),
    ip_address        VARCHAR(45),
    owner             VARCHAR(100),
    description       TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT ck_assets_status
        CHECK (status IN ('ACTIVE', 'DECOMMISSIONED')),
    registered_at     TIMESTAMPTZ  NOT NULL,
    decommissioned_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)  NOT NULL DEFAULT 'system',
    -- Un actif decommissionne porte sa date ; un actif actif non.
    CONSTRAINT ck_assets_decommission CHECK (
        (status = 'DECOMMISSIONED' AND decommissioned_at IS NOT NULL)
        OR (status = 'ACTIVE' AND decommissioned_at IS NULL)),
    -- La cle de correlation est stockee deja normalisee par le domaine.
    CONSTRAINT ck_assets_hostname_normalized CHECK (hostname = lower(trim(hostname)))
);

COMMENT ON TABLE assets IS 'Supervised asset inventory - business context for SOC alerts';

CREATE UNIQUE INDEX ux_assets_hostname ON assets (hostname);
CREATE INDEX ix_assets_type        ON assets (type);
CREATE INDEX ix_assets_criticality ON assets (criticality);
CREATE INDEX ix_assets_status      ON assets (status);

-- Correlation alertes <-> actifs : les alertes stockent le hostname BRUT
-- de la source, la jointure se fait sur lower(trim(hostname)). Index
-- fonctionnel pour que cette jointure ne degenere pas en seq scan.
CREATE INDEX ix_alerts_hostname_normalized
    ON alerts (lower(trim(hostname))) WHERE hostname IS NOT NULL;
