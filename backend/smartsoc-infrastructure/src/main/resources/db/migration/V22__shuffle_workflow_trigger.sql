-- ============================================================
-- V22 - Declenchement Shuffle (ADR-014 phase 5, effet reel)
-- Un playbook peut desormais etre lie a un workflow Shuffle reel :
-- shuffle_workflow_id (identifiant du workflow, pour la consultation
-- de statut) et shuffle_webhook_path (identifiant du declencheur
-- webhook, pour l'execution) sont deux ressources Shuffle distinctes,
-- confirmees en reel avant tout code -- jamais interchangeables.
-- Les deux colonnes restent nulles pour un playbook purement
-- documentaire (suivi guide manuel, comportement inchange).
-- ============================================================

ALTER TABLE playbooks
    ADD COLUMN shuffle_workflow_id  VARCHAR(100),
    ADD COLUMN shuffle_webhook_path VARCHAR(200);

ALTER TABLE playbook_executions
    ADD COLUMN external_execution_id VARCHAR(100),
    ADD COLUMN result_summary        VARCHAR(2000);

COMMENT ON COLUMN playbooks.shuffle_workflow_id IS 'Shuffle workflow id, used to query GET /api/v2/workflows/{id}/executions for reconciliation';
COMMENT ON COLUMN playbooks.shuffle_webhook_path IS 'Shuffle webhook trigger path, used to POST /api/v1/hooks/{path}';
COMMENT ON COLUMN playbook_executions.external_execution_id IS 'Shuffle execution_id returned by the trigger webhook, null for a manually guided execution';
COMMENT ON COLUMN playbook_executions.result_summary IS 'Failure reason or Shuffle result, populated on a terminal state reached externally';

-- ORPHANED n'est PAS terminal (aucun callback ni reconciliation
-- n'a encore tranche) : traite comme IN_PROGRESS pour completed_at,
-- meme doctrine que la contrainte existante.
ALTER TABLE playbook_executions DROP CONSTRAINT ck_playbook_executions_status;
ALTER TABLE playbook_executions ADD CONSTRAINT ck_playbook_executions_status
    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'START_FAILED', 'ORPHANED', 'PARTIAL_FAILURE'));

ALTER TABLE playbook_executions DROP CONSTRAINT ck_playbook_executions_completion;
ALTER TABLE playbook_executions ADD CONSTRAINT ck_playbook_executions_completion CHECK (
    (status IN ('IN_PROGRESS', 'ORPHANED') AND completed_at IS NULL)
    OR (status NOT IN ('IN_PROGRESS', 'ORPHANED') AND completed_at IS NOT NULL));
