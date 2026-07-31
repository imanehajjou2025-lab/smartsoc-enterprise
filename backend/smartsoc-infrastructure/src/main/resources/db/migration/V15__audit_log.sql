CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ NOT NULL,
    action         VARCHAR(30) NOT NULL,
    actor_username VARCHAR(50) NOT NULL,
    actor_id       UUID,
    target_type    VARCHAR(50),
    target_id      VARCHAR(100),
    details        VARCHAR(500),
    ip_address     VARCHAR(45)
);

-- Tri par defaut (le plus recent d'abord) et filtre par action : les deux
-- accès de la console Paramètres.
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log_action ON audit_log (action);
