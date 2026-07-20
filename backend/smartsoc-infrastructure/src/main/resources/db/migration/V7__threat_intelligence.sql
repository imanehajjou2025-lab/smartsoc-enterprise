-- ============================================================
-- V7 - Bounded context: threat intelligence (CTI)
-- Referentiel des indicateurs de compromission. L'IDENTITE d'un
-- IOC est le couple (type, valeur normalisee) : unique, immuable,
-- et cle de correlation avec les observables des alertes. Les
-- metadonnees CTI (confiance, source, tags, fenetre de validite)
-- sont rafraichies a chaque passage du flux. Pas de suppression :
-- un IOC expire (valid_until depassee) ou se revoque.
-- Provenance externe (ADR-005) : les flux poussent, la plateforme
-- n'interroge aucun MISP.
-- ============================================================

CREATE TABLE indicators (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type              VARCHAR(10)   NOT NULL
        CONSTRAINT ck_indicators_type
        CHECK (type IN ('IPV4', 'IPV6', 'DOMAIN', 'URL',
                        'MD5', 'SHA1', 'SHA256', 'EMAIL')),
    value             VARCHAR(2048) NOT NULL,
    -- INTEGER et non SMALLINT : le domaine porte un int, et Hibernate
    -- (ddl-auto=validate) refuse le decalage int2/int4. La garantie de
    -- l'echelle 0-100 vient de la contrainte, pas du type.
    confidence        INTEGER       NOT NULL
        CONSTRAINT ck_indicators_confidence CHECK (confidence BETWEEN 0 AND 100),
    tlp               VARCHAR(10)   NOT NULL
        CONSTRAINT ck_indicators_tlp
        CHECK (tlp IN ('CLEAR', 'GREEN', 'AMBER', 'RED')),
    feed_source       VARCHAR(100)  NOT NULL,
    external_id       VARCHAR(255),
    description       TEXT,
    tags              JSONB         NOT NULL DEFAULT '[]'::jsonb,
    first_seen        TIMESTAMPTZ   NOT NULL,
    last_seen         TIMESTAMPTZ   NOT NULL,
    -- Fin de validite du renseignement. NULL = pas de peremption connue.
    -- L'etat EXPIRED n'est PAS une colonne : il se deduit de cette date
    -- au moment de la lecture. Aucune colonne de statut a maintenir,
    -- donc aucun batch de peremption a surveiller -- et donc aucun IOC
    -- perime qui continuerait a se declarer actif si ce batch tombait.
    valid_until       TIMESTAMPTZ,
    -- La revocation, elle, est un FAIT : une decision d'analyste, qui
    -- survit aux re-observations du flux.
    revoked           BOOLEAN       NOT NULL DEFAULT false,
    revocation_reason TEXT,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)   NOT NULL DEFAULT 'system',

    -- Un IOC revoque porte son motif ET sa date ; un IOC actif ni l'un
    -- ni l'autre (meme forme que ck_assets_decommission).
    CONSTRAINT ck_indicators_revocation CHECK (
        (revoked = true  AND revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
        OR (revoked = false AND revoked_at IS NULL AND revocation_reason IS NULL)),

    -- La valeur est stockee DEJA NORMALISEE par le domaine ; la base le
    -- grave pour qu'aucune ecriture ne puisse la contourner.
    -- Exception URL assumee : le chemin d'une URL est sensible a la casse
    -- (/Login et /login sont deux ressources), seuls le schema et l'hote
    -- y sont mis en minuscules -- une valeur d'URL n'est donc pas
    -- entierement en minuscules, contrairement a tous les autres types.
    CONSTRAINT ck_indicators_value_normalized CHECK (
        value = trim(value)
        AND char_length(value) > 0
        AND (type = 'URL' OR value = lower(value))),

    -- Une premiere observation ne peut pas suivre la derniere.
    CONSTRAINT ck_indicators_seen_order CHECK (last_seen >= first_seen)
);

COMMENT ON TABLE indicators IS 'Threat intelligence indicators of compromise (IOC)';
COMMENT ON COLUMN indicators.value IS 'Normalized correlation key - see IndicatorType.normalize()';
COMMENT ON COLUMN indicators.valid_until IS 'End of validity; EXPIRED status is derived from it, never stored';

-- IDENTITE METIER. Sert trois roles a la fois : elle interdit le doublon,
-- elle est la cle de recherche de l'upsert d'ingestion, et c'est l'index
-- qu'empruntera la correlation alerte -> IOC (jointure sur le COUPLE
-- (type, valeur), jamais sur la valeur seule).
CREATE UNIQUE INDEX ux_indicators_identity ON indicators (type, value);

-- Chemins d'acces de la console
CREATE INDEX ix_indicators_feed_source ON indicators (feed_source);
CREATE INDEX ix_indicators_last_seen   ON indicators (last_seen DESC);
-- Le filtre d'activite porte sur la fenetre de validite des IOC non
-- revoques : index partiel, les revoques n'ont pas a etre parcourus.
CREATE INDEX ix_indicators_valid_until ON indicators (valid_until) WHERE revoked = false;
-- Recherche par tag (operateur de containment JSONB).
CREATE INDEX ix_indicators_tags ON indicators USING gin (tags jsonb_path_ops);
