-- ============================================================
-- V11 - Bounded context: threat hunting
-- Requetes de chasse sauvegardees sur les alertes deja ingerees.
-- Le champ raw_payload des alertes (V3) a ete choisi en JSONB
-- precisement pour etre requetable en threat hunting -- ce jalon
-- lui donne enfin cet usage, sans retoucher le domaine des alertes.
--
-- Une requete de chasse n'est PAS une piece d'evidence SOC (ce
-- n'est qu'un gabarit de recherche sauvegarde) : contrairement au
-- reste de la plateforme, la suppression reelle est autorisee.
-- ============================================================

CREATE TABLE hunt_queries (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(200)  NOT NULL,
    description       TEXT,
    -- Arbre de conditions AND/OR/NOT imbrique, en JSONB. La V1 impose
    -- une racine AND de conditions plates (aucune imbrication, aucun
    -- OR/NOT) -- regle portee par le domaine (HuntQuery), PAS par le
    -- schema : debloquer l'imbrication plus tard est un changement de
    -- validation applicative, jamais une migration.
    criteria          JSONB         NOT NULL,
    -- Stockee mais PAS ENCORE appliquee par aucun filtre de lecture :
    -- une requete PRIVATE reste aujourd'hui visible par tout utilisateur
    -- authentifie, comme le reste de la plateforme ne cloisonne les
    -- donnees par utilisateur nulle part ailleurs.
    visibility        VARCHAR(10)   NOT NULL DEFAULT 'PRIVATE'
        CONSTRAINT ck_hunt_queries_visibility CHECK (visibility IN ('PRIVATE', 'TEAM')),
    last_executed_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)   NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE hunt_queries IS 'Saved threat hunting queries (structured filters over ingested alerts)';
COMMENT ON COLUMN hunt_queries.criteria IS 'AND/OR/NOT condition tree (JSON); V1 restricts to a flat AND root, enforced by the domain, not this schema';
COMMENT ON COLUMN hunt_queries.visibility IS 'Stored but NOT YET enforced by any read filter (v1: every hunt is visible to any authenticated user)';

-- Repere d'usage recent ("mes chasses recentes") : le seul chemin d'acces
-- de premier ordre sur cette table (peu de lignes attendues, pas besoin
-- d'indexer la recherche par nom en V1 -- meme doctrine que raw_payload).
CREATE INDEX ix_hunt_queries_last_executed ON hunt_queries (last_executed_at DESC NULLS LAST);
