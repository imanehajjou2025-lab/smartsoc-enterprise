-- Enrichissement complémentaire optionnel du classifieur TP/FP (ADR-008,
-- contrat ai-classifier-api.yaml v1.1.0) : zone de routage recommandée,
-- dérogation forcée, justifications explicables. Additif à ai_score/
-- ai_verdict existants, jamais un remplacement — nullable, la plateforme
-- fonctionne intégralement sans (ADR-005).
ALTER TABLE alerts
    ADD COLUMN ai_zone VARCHAR(30),
    ADD COLUMN ai_hard_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_justifications JSONB;
