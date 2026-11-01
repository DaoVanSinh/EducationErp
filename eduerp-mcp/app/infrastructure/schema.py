"""SQLAlchemy Core table definitions for shared EduERP public schema and MCP schema."""

from __future__ import annotations

from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    MetaData,
    String,
    Table,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID

public_metadata = MetaData()
mcp_metadata = MetaData(schema="mcp")

# =============================================================================
# Read-only Shared EduERP Tables (public schema)
# =============================================================================

erp_accounts = Table(
    "accounts",
    public_metadata,
    Column("id", PG_UUID(as_uuid=True), primary_key=True),
    Column("email", String(255), nullable=False),
    Column("full_name", String(200), nullable=False),
    Column("avatar_url", String(512), nullable=True),
    Column("status", String(20), nullable=False),
    Column("home_branch_id", PG_UUID(as_uuid=True), nullable=True),
    Column("last_login_at", DateTime(timezone=True), nullable=True),
)

erp_roles = Table(
    "roles",
    public_metadata,
    Column("id", PG_UUID(as_uuid=True), primary_key=True),
    Column("code", String(64), nullable=False),
    Column("name", String(160), nullable=False),
    Column("system_default", Boolean, nullable=False),
)

erp_account_roles = Table(
    "account_roles",
    public_metadata,
    Column("account_id", PG_UUID(as_uuid=True), primary_key=True),
    Column("role_id", PG_UUID(as_uuid=True), nullable=False),
)

erp_role_permission_groups = Table(
    "role_permission_groups",
    public_metadata,
    Column("role_id", PG_UUID(as_uuid=True), primary_key=True),
    Column("permission_group_id", PG_UUID(as_uuid=True), primary_key=True),
)

erp_account_groups = Table(
    "account_groups",
    public_metadata,
    Column("account_id", PG_UUID(as_uuid=True), primary_key=True),
    Column("group_id", PG_UUID(as_uuid=True), primary_key=True),
)

erp_group_permission_groups = Table(
    "group_permission_groups",
    public_metadata,
    Column("group_id", PG_UUID(as_uuid=True), primary_key=True),
    Column("permission_group_id", PG_UUID(as_uuid=True), primary_key=True),
)

erp_permission_group_items = Table(
    "permission_group_items",
    public_metadata,
    Column("id", PG_UUID(as_uuid=True), primary_key=True),
    Column("permission_group_id", PG_UUID(as_uuid=True), nullable=False),
    Column("permission_id", PG_UUID(as_uuid=True), nullable=False),
    Column("scope", String(32), nullable=False),
)

erp_permissions = Table(
    "permissions",
    public_metadata,
    Column("id", PG_UUID(as_uuid=True), primary_key=True),
    Column("resource", String(64), nullable=False),
    Column("action", String(32), nullable=False),
)

# =============================================================================
# Owned MCP Service Tables (mcp schema)
# =============================================================================

mcp_instances = Table(
    "mcp_instances",
    mcp_metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column("code", String(64), nullable=False, unique=True),
    Column("name", String(160), nullable=False),
    Column("description", Text, nullable=False, server_default=""),
    Column("module_code", String(64), nullable=False),
    Column("route_prefix", String(64), nullable=False, unique=True),
    Column("enabled", Boolean, nullable=False, server_default="true"),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("updated_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
)

oauth_connectors = Table(
    "oauth_connectors",
    mcp_metadata,
    Column(
        "account_id",
        PG_UUID(as_uuid=True),
        ForeignKey(erp_accounts.c.id, ondelete="CASCADE"),
        primary_key=True,
    ),
    Column("client_id", String(96), nullable=False, unique=True),
    Column("client_secret_hash", Text, nullable=False, server_default=""),
    Column("client_secret_last4", String(8), nullable=False, server_default=""),
    Column("enabled", Boolean, nullable=False, server_default="true"),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("updated_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("last_used_at", DateTime(timezone=True), nullable=True),
    Column("secret_rotated_at", DateTime(timezone=True), nullable=True),
)

oauth_used_codes = Table(
    "oauth_used_codes",
    mcp_metadata,
    Column("code_hash", String(128), primary_key=True),
    Column(
        "account_id",
        PG_UUID(as_uuid=True),
        ForeignKey(erp_accounts.c.id, ondelete="CASCADE"),
        nullable=False,
    ),
    Column("client_id", String(96), nullable=False),
    Column("used_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("expires_at", DateTime(timezone=True), nullable=False),
)

oauth_families = Table(
    "oauth_families",
    mcp_metadata,
    Column("family_id", String(96), primary_key=True),
    Column(
        "account_id",
        PG_UUID(as_uuid=True),
        ForeignKey(erp_accounts.c.id, ondelete="CASCADE"),
        nullable=False,
    ),
    Column("client_id", String(96), nullable=False),
    Column("current_refresh_jti", String(96), nullable=False),
    Column("scope", Text, nullable=False, server_default="mcp"),
    Column("resource", Text, nullable=False, server_default=""),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("rotated_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("expires_at", DateTime(timezone=True), nullable=False),
    Column("revoked_at", DateTime(timezone=True), nullable=True),
    Column("revoked_reason", String(64), nullable=True),
)

connections = Table(
    "connections",
    mcp_metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column(
        "account_id",
        PG_UUID(as_uuid=True),
        ForeignKey(erp_accounts.c.id, ondelete="CASCADE"),
        nullable=False,
    ),
    Column("client_id", String(96), nullable=False),
    Column("app_key", String(160), nullable=False),
    Column("app_name", String(160), nullable=False),
    Column("app_origin", String(255), nullable=False, server_default=""),
    Column("redirect_uri", Text, nullable=False, server_default=""),
    Column("family_id", String(96), nullable=True),
    Column("scope", Text, nullable=False, server_default="mcp"),
    Column("resource", Text, nullable=False, server_default=""),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("updated_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("last_used_at", DateTime(timezone=True), nullable=True),
    Column("revoked_at", DateTime(timezone=True), nullable=True),
    UniqueConstraint("account_id", "app_key", name="uq_mcp_connections_account_app"),
)

audit = Table(
    "audit",
    mcp_metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column("ts", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("actor", String(160), nullable=False),
    Column("action", String(96), nullable=False),
    Column("target", String(160), nullable=False, server_default=""),
    Column("detail", Text, nullable=False, server_default=""),
)
