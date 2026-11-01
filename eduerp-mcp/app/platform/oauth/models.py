"""Data models for OAuth connectors and connected client apps."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID


@dataclass(frozen=True)
class ConnectorRecord:
    account_id: UUID
    client_id: str
    has_secret: bool
    secret_last4: str
    enabled: bool
    created_at: datetime
    updated_at: datetime
    last_used_at: datetime | None
    secret_rotated_at: datetime | None


@dataclass(frozen=True)
class ConnectionRecord:
    id: int
    account_id: UUID
    client_id: str
    app_key: str
    app_name: str
    app_origin: str
    redirect_uri: str
    family_id: str | None
    scope: str
    created_at: datetime
    updated_at: datetime
    last_used_at: datetime | None
    revoked_at: datetime | None
