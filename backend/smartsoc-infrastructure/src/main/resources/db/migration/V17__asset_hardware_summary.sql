-- ============================================================
-- V17 - Extension additive du module assets (ADR-014, syscollector)
-- Meme doctrine que V16 : nullable, jamais requis. hardware_summary
-- provient d'un appel SEPARE (syscollector) qui peut echouer sans
-- que ce soit une regression de l'inventaire de base.
-- ============================================================

ALTER TABLE assets
    ADD COLUMN hardware_summary VARCHAR(255);

COMMENT ON COLUMN assets.hardware_summary IS 'Resume materiel lisible (CPU, RAM) issu du syscollector Wazuh - NULL si jamais scanne ou non applicable';
