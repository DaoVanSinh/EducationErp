"""Shape of one curated EduERP endpoint exposed through MCP."""

from __future__ import annotations

import re
from dataclasses import dataclass

_PATH_PARAM = re.compile(r"\{([A-Za-z0-9_]+)\}")

PAGING = ("page", "size", "sort")


@dataclass(frozen=True)
class EndpointSpec:
    endpoint_id: str
    domain: str
    method: str  # "GET" | "POST" | "PUT" | "PATCH"
    path_template: str
    resource: str
    action: str
    min_scope: str
    summary: str
    path_params: tuple[str, ...] = ()
    query_params: tuple[str, ...] = ()
    body_fields: tuple[str, ...] = ()


def endpoint(
    endpoint_id: str,
    domain: str,
    method: str,
    path_template: str,
    permission: tuple[str, str, str],
    summary: str,
    *,
    query_params: tuple[str, ...] = (),
    body_fields: tuple[str, ...] = (),
) -> EndpointSpec:
    """Build a spec, deriving path params from the template. `permission` = (resource, action, min scope)."""
    resource, action, min_scope = permission
    return EndpointSpec(
        endpoint_id=endpoint_id,
        domain=domain,
        method=method,
        path_template=path_template,
        resource=resource,
        action=action,
        min_scope=min_scope,
        summary=summary,
        path_params=tuple(_PATH_PARAM.findall(path_template)),
        query_params=query_params,
        body_fields=body_fields,
    )
