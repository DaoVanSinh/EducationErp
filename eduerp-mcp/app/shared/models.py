"""Base Pydantic models applied across the whole application."""

from __future__ import annotations

from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, field_serializer
from pydantic.alias_generators import to_camel


class CustomModel(BaseModel):
    """Base model enforcing camelCase on the wire and one datetime format."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)

    @field_serializer("*", when_used="json", check_fields=False)
    def _serialize_datetimes(self, value: Any):
        """Emit every datetime in UTC with an explicit offset."""
        if isinstance(value, datetime):
            if value.tzinfo is None:
                value = value.replace(tzinfo=ZoneInfo("UTC"))
            return value.isoformat(timespec="seconds")
        return value


class FrozenModel(CustomModel):
    """Immutable base for values crossing a layer or a cache boundary."""

    model_config = ConfigDict(
        alias_generator=to_camel, populate_by_name=True, from_attributes=True, frozen=True
    )
