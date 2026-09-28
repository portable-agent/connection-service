CREATE TABLE oauth_sessions (
    state_hash CHAR(64) PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    encrypted_verifier BYTEA NOT NULL,
    verifier_nonce BYTEA NOT NULL,
    key_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_oauth_state_hash CHECK (state_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_oauth_provider CHECK (provider IN ('google-calendar')),
    CONSTRAINT ck_oauth_key_version CHECK (key_version > 0),
    CONSTRAINT ck_oauth_verifier_not_empty CHECK (octet_length(encrypted_verifier) > 0),
    CONSTRAINT ck_oauth_nonce_not_empty CHECK (octet_length(verifier_nonce) > 0),
    CONSTRAINT ck_oauth_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_oauth_sessions_cleanup ON oauth_sessions (expires_at);
