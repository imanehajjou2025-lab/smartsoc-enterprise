-- ============================================================
-- V1 - Bounded context: identity
-- Conventions ADR-004 appliquées à toutes les tables du projet :
--   * clé primaire UUID (gen_random_uuid, natif PG13+)
--   * colonnes d'audit (created_at/by, updated_at/by)
--   * soft delete via deleted_at quand pertinent
--   * index uniques partiels excluant les lignes supprimées
-- Une migration mergée ne se modifie JAMAIS : toute évolution
-- passe par une nouvelle version.
-- ============================================================

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL
        CONSTRAINT ck_users_role
        CHECK (role IN ('ADMIN', 'SOC_MANAGER', 'SOC_ANALYST', 'VIEWER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(50)  NOT NULL DEFAULT 'system',
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    VARCHAR(50)  NOT NULL DEFAULT 'system',
    deleted_at    TIMESTAMPTZ
);

COMMENT ON TABLE users IS 'Platform users (SOC analysts, managers, admins, viewers)';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete marker: non-null means the user is logically deleted';

-- Unicité insensible à la casse, uniquement parmi les comptes actifs :
-- un username/email redevient disponible après suppression logique.
CREATE UNIQUE INDEX ux_users_username ON users (LOWER(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_email    ON users (LOWER(email))    WHERE deleted_at IS NULL;
