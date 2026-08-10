-- ============================================================
-- V20 - Extension additive du module assets (ADR-014)
-- Statut de connexion RAPPORTE par un connecteur d'agents (Wazuh),
-- distinct du statut de cycle de vie de l'actif (assets.status).
-- Nullable, jamais requis : un actif enregistre a la main ne l'a
-- jamais renseigne, meme doctrine que V17 (hardware_summary).
-- ============================================================

ALTER TABLE assets
    ADD COLUMN agent_connection_status VARCHAR(20)
        CONSTRAINT ck_assets_agent_connection_status
        CHECK (agent_connection_status IN ('ACTIVE', 'DISCONNECTED', 'NEVER_CONNECTED'));

COMMENT ON COLUMN assets.agent_connection_status IS 'Etat de connexion rapporte par le connecteur (Wazuh) -- NULL si actif enregistre a la main, jamais lie au cycle de vie assets.status';
