"""Public security helpers, policies and the bounded attempt limiter."""

from __future__ import annotations

import time
from collections import OrderedDict, deque
from collections.abc import Callable

from app.shared.security.constants import SecurityConstants as SecurityConstants
from app.shared.security.request import RequestSecurity as RequestSecurity


class AttemptLimiter:
    """Refuse tries after `limit` recent misses per key, with bounded in-memory storage."""

    def __init__(
        self,
        limit: int = 10,
        window: float = 300.0,
        clock: Callable[[], float] = time.monotonic,
        *,
        max_buckets: int = 1024,
    ):
        if limit < 1 or window <= 0 or max_buckets < 1:
            raise ValueError("Limiter bounds must be positive")
        self._limit, self._window, self._clock = limit, window, clock
        self._max_buckets = max_buckets
        self._misses: OrderedDict[str, deque[float]] = OrderedDict()

    def _recent(self, key: str) -> deque[float] | None:
        misses = self._misses.get(key)
        if misses is None:
            return None
        now = self._clock()
        while misses and now - misses[0] >= self._window:
            misses.popleft()
        if not misses:
            del self._misses[key]
            return None
        self._misses.move_to_end(key)
        return misses

    def blocked(self, key: str = "default") -> bool:
        misses = self._recent(key)
        return misses not in (None,) and len(misses or ()) >= self._limit

    def miss(self, key: str = "default") -> None:
        misses = self._recent(key)
        if misses is None:
            if len(self._misses) >= self._max_buckets:
                self._misses.popitem(last=False)
            misses = deque(maxlen=self._limit)
            self._misses[key] = misses
        misses.append(self._clock())
