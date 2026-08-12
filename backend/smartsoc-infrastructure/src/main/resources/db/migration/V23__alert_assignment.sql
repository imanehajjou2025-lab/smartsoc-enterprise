-- Affectation de triage d'une alerte à un niveau de responsabilité SOC
-- (N1/N2/N3), avec analyste nommé optionnel — distincte de l'escalade en
-- incident (table incidents). Nullable : une alerte non affectée reste
-- pleinement fonctionnelle (comportement inchangé par défaut).
ALTER TABLE alerts
    ADD COLUMN assigned_tier VARCHAR(10)
        CONSTRAINT ck_alerts_assigned_tier CHECK (assigned_tier IN ('N1', 'N2', 'N3')),
    ADD COLUMN assigned_to_username VARCHAR(50);

CREATE INDEX ix_alerts_assigned_tier ON alerts (assigned_tier) WHERE assigned_tier IS NOT NULL;
CREATE INDEX ix_alerts_assigned_to   ON alerts (assigned_to_username) WHERE assigned_to_username IS NOT NULL;
