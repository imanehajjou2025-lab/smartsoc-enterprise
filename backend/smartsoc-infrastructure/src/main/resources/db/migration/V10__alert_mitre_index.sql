-- ============================================================
-- V10 - Index de correlation MITRE
-- Les alertes citent des techniques ATT&CK en JSONB
-- (alerts.mitre_techniques, depuis V3). Cet index GIN rend le
-- containment @> indexable : le sens technique -> alertes (retro-hunt)
-- et l'agregation de couverture (heatmap) s'y appuient.
--
-- UNIQUE point de contact entre le contexte mitre et la table alerts :
-- le catalogue reste dans sa propre table (mitre_technique_catalog, V9),
-- la correlation ne fait que LIRE le JSONB deja present, sans retoucher
-- le domaine des alertes (ADR-010 §6).
--
-- Classe d'operateurs par defaut (jsonb_ops), meme choix que
-- indicators.tags : elle sert le containment @> teste par le retro-hunt.
-- ============================================================

CREATE INDEX ix_alerts_mitre_techniques ON alerts USING gin (mitre_techniques);
