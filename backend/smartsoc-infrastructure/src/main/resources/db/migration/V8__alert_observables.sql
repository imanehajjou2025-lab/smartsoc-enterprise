-- ============================================================
-- V8 - Observables declares par les alertes
-- Ce que l'evenement CITE explicitement : une IP, un domaine, une
-- URL, un hash. DECLARES par le producteur, jamais devines a
-- partir de raw_payload (ADR-009) -- une extraction par
-- expressions regulieres fabriquerait de faux rattachements, et en
-- SOC un faux rattachement coute plus cher qu'une absence.
--
-- Table dediee et NON colonne JSONB : c'est ici que se fait la
-- jointure avec le referentiel IOC, dans les deux sens
-- (alerte -> IOC pour le triage, IOC -> alertes pour le
-- retro-hunt). Une jointure indexee sur un couple demande de vraies
-- colonnes.
-- ============================================================

CREATE TABLE alert_observables (
    alert_id UUID          NOT NULL
        REFERENCES alerts (id) ON DELETE CASCADE,
    type     VARCHAR(10)   NOT NULL
        CONSTRAINT ck_alert_observables_type
        CHECK (type IN ('IPV4', 'IPV6', 'DOMAIN', 'URL',
                        'MD5', 'SHA1', 'SHA256', 'EMAIL')),
    value    VARCHAR(2048) NOT NULL,

    -- L'identite d'un observable est le COUPLE (type, valeur), comme
    -- celle d'un indicateur. La cle primaire dedoublonne donc au
    -- niveau de la base : une alerte ne cite pas deux fois la meme
    -- chose, meme si le producteur l'a declaree deux fois.
    PRIMARY KEY (alert_id, type, value),

    -- Meme regle de normalisation que indicators.value, exception URL
    -- comprise (le chemin d'une URL est sensible a la casse). Les deux
    -- cotes de la correlation sont stockes sous la meme forme : c'est
    -- ce qui rend la jointure en egalite stricte legitime.
    CONSTRAINT ck_alert_observables_value_normalized CHECK (
        value = trim(value)
        AND char_length(value) > 0
        AND (type = 'URL' OR value = lower(value)))
);

COMMENT ON TABLE alert_observables IS 'Observables declared by an alert - correlation key with the CTI referential';
COMMENT ON COLUMN alert_observables.value IS 'Normalized by IndicatorType.normalize(), exactly like indicators.value';

-- LE chemin de la correlation, emprunte dans les deux sens :
--   alerte -> IOC : pour les observables d'une alerte, chercher les
--                   indicateurs actifs de meme couple ;
--   IOC -> alertes : pour un indicateur, retrouver toutes les alertes
--                    qui citent ce couple -- y compris celles ingerees
--                    AVANT sa creation (retro-hunt, sans job de
--                    recalcul).
-- La cle primaire commence par alert_id : elle ne sert donc pas le
-- second sens. D'ou cet index dedie sur le couple seul.
CREATE INDEX ix_alert_observables_identity ON alert_observables (type, value);
