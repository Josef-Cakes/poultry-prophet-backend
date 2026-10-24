-- Long-lived credentials for PWA sessions. Store only a hash of the raw token.
CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    CONSTRAINT fk_auth_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user
    ON auth_refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_expires_at
    ON auth_refresh_token(expires_at);
