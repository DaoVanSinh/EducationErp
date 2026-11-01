-- =============================================================================
-- V27: Create MCP Hub schema & tables for eduerp-mcp service
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mcp;

CREATE TABLE IF NOT EXISTS mcp.mcp_instances (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT         NOT NULL DEFAULT '',
    module_code     VARCHAR(64)  NOT NULL,
    route_prefix    VARCHAR(64)  NOT NULL UNIQUE,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mcp.oauth_connectors (
    account_id              UUID         PRIMARY KEY REFERENCES public.accounts(id) ON DELETE CASCADE,
    client_id               VARCHAR(96)  NOT NULL UNIQUE,
    client_secret_hash      TEXT         NOT NULL DEFAULT '',
    client_secret_last4     VARCHAR(8)   NOT NULL DEFAULT '',
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at            TIMESTAMPTZ  NULL,
    secret_rotated_at       TIMESTAMPTZ  NULL
);

CREATE TABLE IF NOT EXISTS mcp.oauth_used_codes (
    code_hash       VARCHAR(128) PRIMARY KEY,
    account_id      UUID         NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    client_id       VARCHAR(96)  NOT NULL,
    used_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_mcp_oauth_used_codes_expires_at
    ON mcp.oauth_used_codes (expires_at);

CREATE TABLE IF NOT EXISTS mcp.oauth_families (
    family_id               VARCHAR(96)  PRIMARY KEY,
    account_id              UUID         NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    client_id               VARCHAR(96)  NOT NULL,
    current_refresh_jti     VARCHAR(96)  NOT NULL,
    scope                   TEXT         NOT NULL DEFAULT 'mcp',
    resource                TEXT         NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    rotated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ  NOT NULL,
    revoked_at              TIMESTAMPTZ  NULL,
    revoked_reason          VARCHAR(64)  NULL
);

CREATE INDEX IF NOT EXISTS ix_mcp_oauth_families_account_client
    ON mcp.oauth_families (account_id, client_id);

CREATE TABLE IF NOT EXISTS mcp.connections (
    id              BIGSERIAL PRIMARY KEY,
    account_id      UUID         NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    client_id       VARCHAR(96)  NOT NULL,
    app_key         VARCHAR(160) NOT NULL,
    app_name        VARCHAR(160) NOT NULL,
    app_origin      VARCHAR(255) NOT NULL DEFAULT '',
    redirect_uri    TEXT         NOT NULL DEFAULT '',
    family_id       VARCHAR(96)  NULL,
    scope           TEXT         NOT NULL DEFAULT 'mcp',
    resource        TEXT         NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ  NULL,
    revoked_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_mcp_connections_account_app UNIQUE (account_id, app_key)
);

CREATE INDEX IF NOT EXISTS ix_mcp_connections_account_id
    ON mcp.connections (account_id);

CREATE TABLE IF NOT EXISTS mcp.audit (
    id              BIGSERIAL PRIMARY KEY,
    ts              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actor           VARCHAR(160) NOT NULL,
    action          VARCHAR(96)  NOT NULL,
    target          VARCHAR(160) NOT NULL DEFAULT '',
    detail          TEXT         NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS ix_mcp_audit_ts
    ON mcp.audit (ts DESC);

INSERT INTO mcp.mcp_instances (code, name, description, module_code, route_prefix, enabled)
VALUES (
    'eduerp',
    'EduERP Core MCP',
    'Tra cứu và thao tác nghiệp vụ EduERP (Cơ sở, Khóa học, Lớp học, Giáo viên, Học viên, Ghi danh, Hóa đơn, Bảng lương) theo phân quyền RBAC trực tiếp.',
    'eduerp',
    'eduerp',
    TRUE
)
ON CONFLICT (code) DO NOTHING;
