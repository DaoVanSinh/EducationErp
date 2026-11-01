"""Layer markers: decorator classes that tag a method with its architectural role."""

import functools
from collections.abc import Callable
from typing import Any, TypeVar, cast

F = TypeVar("F", bound=Callable[..., Any])


class _MethodMarker:
    """Base for every marker below. Never used directly."""

    layer: str

    def __new__(cls, func: F) -> F:  # type: ignore[misc]
        instance = super().__new__(cls)
        instance._init(func)
        return cast(F, instance)

    def _init(self, func: Callable[..., Any]) -> None:
        functools.update_wrapper(self, func)
        self._func = func
        setattr(func, "__layer__", self.layer)  # noqa: B010

    def __get__(self, obj: Any, objtype: type | None = None) -> Any:
        if obj is None:
            return self._func
        return self._func.__get__(obj, objtype)

    def __call__(self, *args: Any, **kwargs: Any) -> Any:
        return self._func(*args, **kwargs)


class database(_MethodMarker):
    """A method that reads or writes a module's own tables directly."""

    layer = "database"


class helper(_MethodMarker):
    """A method with no business decision: formatting, normalization, or support for another method."""

    layer = "helper"


class rule(_MethodMarker):
    """A method holding a pure business decision — no I/O."""

    layer = "rule"


class use_case(_MethodMarker):
    """The execute() of a use case — one class, one operation."""

    layer = "use_case"


class facade(_MethodMarker):
    """A method exposed to other modules through public.py."""

    layer = "facade"


class integration(_MethodMarker):
    """A method that calls an external system directly (Redis, HTTP)."""

    layer = "integration"
