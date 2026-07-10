-- ============================================================
-- V2 - Refresh tokens (rotation avec detection de reutilisation)
-- Seul le hash SHA-256 du token est stocke : une fuite de la base
-- n'expose aucun token utilisable.
-- ============================================================

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL
        CONSTRAINT fk_refresh_tokens_user REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL,
    family_id  UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(50) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(50) NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE refresh_tokens IS 'Hashed refresh tokens; family_id groups tokens of one login session for rotation reuse detection';

CREATE UNIQUE INDEX ux_refresh_tokens_hash   ON refresh_tokens (token_hash);
CREATE INDEX        ix_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX        ix_refresh_tokens_family ON refresh_tokens (family_id);
