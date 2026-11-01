"""Reading time in standardized timezones."""

from __future__ import annotations

import time
from datetime import UTC, datetime, timedelta, timezone

from app.shared.base.markers import helper


class Clock:
    """UTC for storage, UTC+7 for local display."""

    VN_TZ = timezone(timedelta(hours=7))

    @staticmethod
    @helper
    def epoch() -> float:
        """Seconds since the epoch, read at call time so tests can control it."""
        return time.time()

    @staticmethod
    @helper
    def utc_now() -> datetime:
        return datetime.now(UTC)

    @classmethod
    @helper
    def local_now(cls) -> datetime:
        return datetime.now(cls.VN_TZ)

    @classmethod
    @helper
    def to_vn(cls, dt: datetime) -> datetime:
        return dt.astimezone(cls.VN_TZ)

    @staticmethod
    @helper
    def as_utc(dt: datetime) -> datetime:
        """Attach UTC to a naive datetime."""
        return dt if dt.tzinfo is not None else dt.replace(tzinfo=UTC)

    @classmethod
    @helper
    def parse_iso(cls, value: str | None) -> datetime | None:
        """Parse an ISO-8601 string to an aware UTC datetime."""
        if not value:
            return None
        try:
            return cls.as_utc(datetime.fromisoformat(value))
        except ValueError:
            return None
