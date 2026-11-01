"""Server-created command identity; browser fields never create an Actor."""

from dataclasses import dataclass
from uuid import UUID

from app.shared.constants import ActorKind


@dataclass(frozen=True)
class Actor:
    kind: ActorKind.Type
    name: str
    principal_id: UUID | None = None
    email: str | None = None
    full_name: str | None = None
    home_branch_id: UUID | None = None
    jti: str | None = None
    request_time: int | None = None
