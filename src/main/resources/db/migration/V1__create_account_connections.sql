CREATE TABLE account_connections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    encrypted_refresh_token BYTEA NOT NULL,
    token_nonce BYTEA NOT NULL,
    key_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_connection_provider_account
        UNIQUE (tenant_id, actor_id, provider, provider_account_id),
    CONSTRAINT ck_connection_status
        CHECK (status IN ('active', 'reconnect_required', 'disconnected')),
    CONSTRAINT ck_connection_provider
        CHECK (provider IN ('google-calendar')),
    CONSTRAINT ck_connection_key_version CHECK (key_version > 0),
    CONSTRAINT ck_connection_token_not_empty CHECK (octet_length(encrypted_refresh_token) > 0),
    CONSTRAINT ck_connection_nonce_not_empty CHECK (octet_length(token_nonce) > 0)
);

CREATE INDEX ix_connection_owner_provider
    ON account_connections (tenant_id, actor_id, provider, created_at, id);

CREATE INDEX ix_connection_active
    ON account_connections (tenant_id, actor_id, provider, created_at, id)
    WHERE status = 'active';
