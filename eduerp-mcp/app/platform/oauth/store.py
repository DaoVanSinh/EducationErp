"""Database store for OAuth 2.1 connectors, single-use codes, token families, and connections."""

from __future__ import annotations

from datetime import timedelta
from urllib.parse import urlsplit
from uuid import UUID

from sqlalchemy import and_, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.exc import IntegrityError

from app.infrastructure.postgres import PostgresDatabase
from app.infrastructure.schema import (
    connections,
    oauth_connectors,
    oauth_families,
    oauth_used_codes,
)
from app.integrations.cache.public import SharedRedisCache
from app.platform.oauth.constants import OAuthConstants
from app.platform.oauth.crypto import OAuthCrypto
from app.platform.oauth.models import ConnectionRecord, ConnectorRecord
from app.shared.base.markers import database, helper, rule
from app.shared.clock import Clock
from app.shared.exceptions import NotFound


class OAuthAppNaming:
    """Resolve friendly display names for connected AI applications."""

    @staticmethod
    @rule
    def resolve_app_metadata(redirect_uri: str) -> tuple[str, str, str]:
        if not redirect_uri:
            return OAuthConstants.IDE_APP_KEY, OAuthConstants.IDE_APP_NAME, ""
        parts = urlsplit(redirect_uri)
        host = (parts.hostname or "").lower()
        origin = f"{parts.scheme}://{parts.netloc}" if parts.scheme and parts.netloc else host
        for domain, label in OAuthConstants.KNOWN_APP_NAMES.items():
            if host == domain or host.endswith(f".{domain}"):
                return f"oauth:{domain}", label, origin
        return f"oauth:{host or 'client'}", f"OAuth App ({host or 'Client'})", origin


class OAuthStore:
    """Manage connectors, authorization codes, refresh token families, and app connections."""

    def __init__(self, db: PostgresDatabase, cache: SharedRedisCache) -> None:
        self._db = db
        self._cache = cache

    @staticmethod
    @helper
    def _to_connector(row: dict) -> ConnectorRecord:
        secret_hash = str(row["client_secret_hash"] or "")
        return ConnectorRecord(
            account_id=row["account_id"],
            client_id=str(row["client_id"]),
            has_secret=bool(secret_hash),
            secret_last4=str(row["client_secret_last4"] or ""),
            enabled=bool(row["enabled"]),
            created_at=Clock.as_utc(row["created_at"]),
            updated_at=Clock.as_utc(row["updated_at"]),
            last_used_at=Clock.as_utc(row["last_used_at"]) if row["last_used_at"] else None,
            secret_rotated_at=(
                Clock.as_utc(row["secret_rotated_at"]) if row["secret_rotated_at"] else None
            ),
        )

    @staticmethod
    @helper
    def _to_connection(row: dict) -> ConnectionRecord:
        return ConnectionRecord(
            id=int(row["id"]),
            account_id=row["account_id"],
            client_id=str(row["client_id"]),
            app_key=str(row["app_key"]),
            app_name=str(row["app_name"]),
            app_origin=str(row["app_origin"] or ""),
            redirect_uri=str(row["redirect_uri"] or ""),
            family_id=str(row["family_id"]) if row["family_id"] else None,
            scope=str(row["scope"] or OAuthConstants.DEFAULT_SCOPE),
            created_at=Clock.as_utc(row["created_at"]),
            updated_at=Clock.as_utc(row["updated_at"]),
            last_used_at=Clock.as_utc(row["last_used_at"]) if row["last_used_at"] else None,
            revoked_at=Clock.as_utc(row["revoked_at"]) if row["revoked_at"] else None,
        )

    @database
    def ensure_connector(self, account_id: UUID) -> ConnectorRecord:
        now = Clock.utc_now()
        stmt = (
            pg_insert(oauth_connectors)
            .values(
                account_id=account_id,
                client_id=OAuthCrypto.generate_client_id(),
                client_secret_hash="",
                client_secret_last4="",
                enabled=True,
                created_at=now,
                updated_at=now,
            )
            .on_conflict_do_nothing(index_elements=[oauth_connectors.c.account_id])
        )
        with self._db.transaction() as conn:
            conn.execute(stmt)
            row = (
                conn.execute(
                    select(oauth_connectors).where(oauth_connectors.c.account_id == account_id)
                )
                .mappings()
                .one()
            )
        return self._to_connector(dict(row))

    @database
    def get_connector_by_client_id(self, client_id: str) -> tuple[ConnectorRecord, str] | None:
        with self._db.connect() as conn:
            row = (
                conn.execute(
                    select(oauth_connectors).where(oauth_connectors.c.client_id == client_id)
                )
                .mappings()
                .first()
            )
        if row is None:
            return None
        row_dict = dict(row)
        return self._to_connector(row_dict), str(row_dict["client_secret_hash"] or "")

    @database
    def rotate_secret(self, account_id: UUID) -> tuple[ConnectorRecord, str]:
        self.ensure_connector(account_id)
        raw_secret = OAuthCrypto.generate_client_secret()
        secret_hash = OAuthCrypto.hash_secret(raw_secret)
        last4 = raw_secret[-4:]
        now = Clock.utc_now()
        with self._db.transaction() as conn:
            row = (
                conn.execute(
                    update(oauth_connectors)
                    .where(oauth_connectors.c.account_id == account_id)
                    .values(
                        client_secret_hash=secret_hash,
                        client_secret_last4=last4,
                        updated_at=now,
                        secret_rotated_at=now,
                    )
                    .returning(oauth_connectors)
                )
                .mappings()
                .one()
            )
        return self._to_connector(dict(row)), raw_secret

    @database
    def verify_client_credentials(self, client_id: str, client_secret: str) -> UUID | None:
        lookup = self.get_connector_by_client_id(client_id)
        if lookup is None:
            return None
        connector, secret_hash = lookup
        if not connector.enabled or not secret_hash:
            return None
        if not OAuthCrypto.verify_secret(client_secret, secret_hash):
            return None
        self.touch_connector_and_ide_connection(connector.account_id, client_id)
        return connector.account_id

    @database
    def touch_connector_and_ide_connection(self, account_id: UUID, client_id: str) -> None:
        now = Clock.utc_now()
        upsert_conn = (
            pg_insert(connections)
            .values(
                account_id=account_id,
                client_id=client_id,
                app_key=OAuthConstants.IDE_APP_KEY,
                app_name=OAuthConstants.IDE_APP_NAME,
                app_origin="Bearer Token",
                redirect_uri="",
                family_id=None,
                scope=OAuthConstants.DEFAULT_SCOPE,
                created_at=now,
                updated_at=now,
                last_used_at=now,
                revoked_at=None,
            )
            .on_conflict_do_update(
                constraint="uq_mcp_connections_account_app",
                set_={
                    "client_id": client_id,
                    "updated_at": now,
                    "last_used_at": now,
                    "revoked_at": None,
                },
            )
        )
        with self._db.transaction() as conn:
            conn.execute(
                update(oauth_connectors)
                .where(oauth_connectors.c.account_id == account_id)
                .values(last_used_at=now)
            )
            conn.execute(upsert_conn)

    @database
    def consume_auth_code_once(
        self, code: str, account_id: UUID, client_id: str, ttl_seconds: int
    ) -> bool:
        code_hash = OAuthCrypto.hash_code(code)
        now = Clock.utc_now()
        expires_at = now + timedelta(seconds=ttl_seconds)
        try:
            with self._db.transaction() as conn:
                conn.execute(
                    oauth_used_codes.insert().values(
                        code_hash=code_hash,
                        account_id=account_id,
                        client_id=client_id,
                        used_at=now,
                        expires_at=expires_at,
                    )
                )
            return True
        except IntegrityError:
            return False

    @database
    def create_token_family_and_connection(
        self,
        *,
        family_id: str,
        account_id: UUID,
        client_id: str,
        refresh_jti: str,
        scope: str,
        resource: str,
        redirect_uri: str,
    ) -> None:
        now = Clock.utc_now()
        expires_at = now + timedelta(seconds=OAuthConstants.REFRESH_TOKEN_TTL_SECONDS)
        app_key, app_name, app_origin = OAuthAppNaming.resolve_app_metadata(redirect_uri)

        upsert_conn = (
            pg_insert(connections)
            .values(
                account_id=account_id,
                client_id=client_id,
                app_key=app_key,
                app_name=app_name,
                app_origin=app_origin,
                redirect_uri=redirect_uri,
                family_id=family_id,
                scope=scope,
                resource=resource,
                created_at=now,
                updated_at=now,
                last_used_at=now,
                revoked_at=None,
            )
            .on_conflict_do_update(
                constraint="uq_mcp_connections_account_app",
                set_={
                    "client_id": client_id,
                    "app_name": app_name,
                    "app_origin": app_origin,
                    "redirect_uri": redirect_uri,
                    "family_id": family_id,
                    "scope": scope,
                    "resource": resource,
                    "updated_at": now,
                    "last_used_at": now,
                    "revoked_at": None,
                },
            )
        )

        with self._db.transaction() as conn:
            conn.execute(
                oauth_families.insert().values(
                    family_id=family_id,
                    account_id=account_id,
                    client_id=client_id,
                    current_refresh_jti=refresh_jti,
                    scope=scope,
                    resource=resource,
                    created_at=now,
                    rotated_at=now,
                    expires_at=expires_at,
                )
            )
            conn.execute(upsert_conn)
            conn.execute(
                update(oauth_connectors)
                .where(oauth_connectors.c.account_id == account_id)
                .values(last_used_at=now)
            )

    @database
    def rotate_refresh_token(
        self,
        *,
        family_id: str,
        expected_jti: str,
        new_jti: str,
    ) -> dict | None:
        now = Clock.utc_now()
        new_expires = now + timedelta(seconds=OAuthConstants.REFRESH_TOKEN_TTL_SECONDS)
        with self._db.transaction() as conn:
            family = (
                conn.execute(
                    select(oauth_families)
                    .where(oauth_families.c.family_id == family_id)
                    .with_for_update()
                )
                .mappings()
                .first()
            )
            if family is None:
                return None
            if family["revoked_at"] is not None or Clock.as_utc(family["expires_at"]) <= now:
                return None
            if str(family["current_refresh_jti"]) != expected_jti:
                # Refresh token replay detected -> revoke the entire family!
                conn.execute(
                    update(oauth_families)
                    .where(oauth_families.c.family_id == family_id)
                    .values(
                        revoked_at=now,
                        revoked_reason=OAuthConstants.RevokeReasons.REFRESH_REPLAY,
                    )
                )
                self._cache.mark_oauth_family_revoked(family_id)
                return None

            conn.execute(
                update(oauth_families)
                .where(oauth_families.c.family_id == family_id)
                .values(
                    current_refresh_jti=new_jti,
                    rotated_at=now,
                    expires_at=new_expires,
                )
            )
            conn.execute(
                update(connections)
                .where(connections.c.family_id == family_id)
                .values(last_used_at=now, updated_at=now)
            )
            return dict(family)

    @database
    def is_family_active(self, family_id: str) -> bool:
        if self._cache.is_oauth_family_revoked(family_id):
            return False
        now = Clock.utc_now()
        with self._db.connect() as conn:
            row = (
                conn.execute(
                    select(oauth_families.c.revoked_at, oauth_families.c.expires_at).where(
                        oauth_families.c.family_id == family_id
                    )
                )
                .mappings()
                .first()
            )
        if row is None:
            return False
        return row["revoked_at"] is None and Clock.as_utc(row["expires_at"]) > now

    @database
    def revoke_family(
        self, family_id: str, reason: str = OAuthConstants.RevokeReasons.USER_REVOKED
    ) -> None:
        now = Clock.utc_now()
        with self._db.transaction() as conn:
            conn.execute(
                update(oauth_families)
                .where(oauth_families.c.family_id == family_id)
                .values(revoked_at=now, revoked_reason=reason)
            )
            conn.execute(
                update(connections)
                .where(connections.c.family_id == family_id)
                .values(revoked_at=now, updated_at=now)
            )
        self._cache.mark_oauth_family_revoked(family_id)

    @database
    def list_connections(self, account_id: UUID) -> list[ConnectionRecord]:
        stmt = (
            select(connections)
            .where(connections.c.account_id == account_id)
            .order_by(connections.c.updated_at.desc(), connections.c.id.desc())
        )
        with self._db.connect() as conn:
            rows = conn.execute(stmt).mappings().all()
        return [self._to_connection(dict(r)) for r in rows]

    @database
    def revoke_connection(self, account_id: UUID, connection_id: int) -> ConnectionRecord:
        now = Clock.utc_now()
        with self._db.transaction() as conn:
            row = (
                conn.execute(
                    select(connections)
                    .where(
                        and_(
                            connections.c.id == connection_id,
                            connections.c.account_id == account_id,
                        )
                    )
                    .with_for_update()
                )
                .mappings()
                .first()
            )
            if row is None:
                raise NotFound(message="Không tìm thấy kết nối ứng dụng AI.")

            family_id = row["family_id"]
            if family_id:
                conn.execute(
                    update(oauth_families)
                    .where(oauth_families.c.family_id == str(family_id))
                    .values(
                        revoked_at=now,
                        revoked_reason=OAuthConstants.RevokeReasons.CONNECTION_REVOKED,
                    )
                )
                self._cache.mark_oauth_family_revoked(str(family_id))

            updated = (
                conn.execute(
                    update(connections)
                    .where(connections.c.id == connection_id)
                    .values(revoked_at=now, updated_at=now)
                    .returning(connections)
                )
                .mappings()
                .one()
            )
        return self._to_connection(dict(updated))
