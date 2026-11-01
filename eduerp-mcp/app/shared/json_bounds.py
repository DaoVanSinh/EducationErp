"""Bound decoded JSON before recursive rendering or business validation."""

import math
from typing import Any

MAX_JSON_DEPTH = 64


def validate_json(value: Any) -> Any:
    stack = [(iter((value,)), 0)]
    exhausted = object()
    while stack:
        items, depth = stack[-1]
        item = next(items, exhausted)
        if item is exhausted:
            stack.pop()
        elif isinstance(item, (dict, list)):
            if depth >= MAX_JSON_DEPTH:
                raise ValueError("JSON nesting is too deep")
            stack.append((iter(item.values() if isinstance(item, dict) else item), depth + 1))
        elif isinstance(item, float) and not math.isfinite(item):
            raise ValueError("Non-finite JSON number")
    return value
