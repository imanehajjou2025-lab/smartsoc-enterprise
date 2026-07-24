-- ============================================================
-- V9 - Bounded context: MITRE ATT&CK
-- Referentiel des techniques de la matrice Enterprise. L'IDENTITE
-- d'une technique est son identifiant ATT&CK normalise (T####,
-- T####.###) : unique, immuable, et cle de correlation avec les
-- techniques brutes deja portees par alerts.mitre_techniques.
-- Les metadonnees (nom, description, url, tactiques, depreciation,
-- version) sont rafraichies a chaque import du catalogue. Pas de
-- suppression : une technique depreciee reste consultable, pour que
-- les alertes historiques continuent de s'enrichir de son nom.
-- Provenance externe (ADR-005) : le catalogue est seme puis
-- rafraichi par import, la plateforme n'interroge aucun service
-- ATT&CK au runtime.
-- Table nommee 'mitre_technique_catalog' et non 'mitre_techniques'
-- pour la distinguer sans ambiguite de la colonne JSONB
-- alerts.mitre_techniques a laquelle elle donne enfin un sens.
-- ============================================================

CREATE TABLE mitre_technique_catalog (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- T + 4 chiffres (technique) ou T + 4 chiffres + . + 3 chiffres
    -- (sous-technique) : 5 a 9 caracteres, deja normalise en majuscule.
    attack_id      VARCHAR(9)    NOT NULL,
    sub_technique  BOOLEAN       NOT NULL DEFAULT false,
    -- Identifiant de la technique parente d'une sous-technique (T####).
    parent_id      VARCHAR(5),
    name           VARCHAR(256)  NOT NULL,
    description    TEXT,
    url            VARCHAR(512),
    -- Tactiques (colonnes de la matrice) en JSONB, meme choix que les
    -- tags d'un IOC : un petit ensemble denormalise, filtre par
    -- containment @> (index GIN), jamais mute independamment.
    tactics        JSONB         NOT NULL DEFAULT '[]'::jsonb,
    -- Fait DU referentiel ATT&CK (pas une decision d'analyste) : l'import
    -- fait foi. Une technique depreciee reste stockee, jamais supprimee.
    deprecated     BOOLEAN       NOT NULL DEFAULT false,
    attack_version VARCHAR(20),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(50)   NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by     VARCHAR(50)   NOT NULL DEFAULT 'system',

    -- L'identifiant est stocke DEJA NORMALISE par le domaine
    -- (MitreTechniqueId.normalize) ; la base grave l'invariant pour
    -- qu'aucune ecriture ne le contourne : majuscule et longueur bornee.
    CONSTRAINT ck_mitre_attack_id_normalized CHECK (
        attack_id = upper(attack_id)
        AND char_length(attack_id) BETWEEN 5 AND 9),

    -- Une sous-technique porte un parent ; une technique de base, aucun
    -- (meme forme que ck_indicators_revocation / ck_assets_decommission).
    CONSTRAINT ck_mitre_subtechnique CHECK (
        (sub_technique = true  AND parent_id IS NOT NULL)
        OR (sub_technique = false AND parent_id IS NULL))
);

COMMENT ON TABLE mitre_technique_catalog IS 'MITRE ATT&CK Enterprise technique catalog';
COMMENT ON COLUMN mitre_technique_catalog.attack_id IS 'Normalized correlation key - see MitreTechniqueId.normalize()';
COMMENT ON COLUMN mitre_technique_catalog.deprecated IS 'ATT&CK-derived fact; deprecated techniques stay queryable, never deleted';

-- IDENTITE. Interdit le doublon, sert de cle a l'upsert d'import, et
-- c'est l'index qu'empruntera la correlation technique <-> alertes.
CREATE UNIQUE INDEX ux_mitre_attack_id ON mitre_technique_catalog (attack_id);

-- Sous-techniques d'une technique de base (navigation de la matrice).
CREATE INDEX ix_mitre_parent_id ON mitre_technique_catalog (parent_id);

-- Filtre par tactique : appartenance a un tableau JSONB, servie par le
-- containment @> (JsonbFunctionContributor). Classe d'operateurs par
-- defaut (jsonb_ops), meme choix que indicators.tags.
CREATE INDEX ix_mitre_tactics ON mitre_technique_catalog USING gin (tactics);
