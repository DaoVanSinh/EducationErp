"""Check architectural import boundaries across eduerp-mcp tiers and strata.

Tiers (from lowest to highest):
  0: shared
  1: infrastructure
  2: integrations
  3: runtime
  4: platform
  5: modules
  6: api
  7: core
"""

from __future__ import annotations

import ast
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app"

TIER_ORDER: dict[str, int] = {
    "shared": 0,
    "infrastructure": 1,
    "integrations": 2,
    "runtime": 3,
    "platform": 4,
    "modules": 5,
    "api": 6,
    "core": 7,
}

# Platform strata (lower cannot import higher)
PLATFORM_STRATA: dict[str, int] = {
    "audit": 0,
    "catalog": 0,
    "identity": 1,
    "permissions": 2,
    "sessions": 3,
    "oauth": 4,
    "personal": 5,
}


def _module_parts(py_file: Path) -> tuple[str, ...]:
    rel = py_file.relative_to(ROOT).with_suffix("")
    parts = rel.parts
    if parts and parts[-1] == "__init__":
        parts = parts[:-1]
    return parts


def _imported_app_targets(tree: ast.AST) -> list[tuple[int, tuple[str, ...]]]:
    targets: list[tuple[int, tuple[str, ...]]] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name == "app" or alias.name.startswith("app."):
                    parts = tuple(alias.name.split(".")[1:])
                    if parts:
                        targets.append((node.lineno, parts))
        elif isinstance(node, ast.ImportFrom) and node.module:
            if node.module == "app":
                for alias in node.names:
                    targets.append((node.lineno, (alias.name,)))
            elif node.module.startswith("app."):
                base = tuple(node.module.split(".")[1:])
                targets.append((node.lineno, base))
    return targets


def check_boundaries() -> list[str]:
    violations: list[str] = []
    for py_file in sorted(ROOT.rglob("*.py")):
        src_parts = _module_parts(py_file)
        if not src_parts:
            continue
        src_tier = src_parts[0]
        if src_tier not in TIER_ORDER:
            violations.append(f"{py_file}: unknown top-level package '{src_tier}'")
            continue

        tree = ast.parse(py_file.read_text(encoding="utf-8"), filename=str(py_file))
        for lineno, dst_parts in _imported_app_targets(tree):
            dst_tier = dst_parts[0]
            if dst_tier not in TIER_ORDER:
                violations.append(
                    f"{py_file.relative_to(ROOT.parent)}:{lineno}: unknown target tier '{dst_tier}'"
                )
                continue

            if TIER_ORDER[dst_tier] > TIER_ORDER[src_tier]:
                # Exception: api may import core.config / core.container / core.lifespan
                if src_tier == "api" and dst_tier == "core":
                    continue
                violations.append(
                    f"{py_file.relative_to(ROOT.parent)}:{lineno}: tier '{src_tier}' cannot import higher tier '{dst_tier}' ({'.'.join(dst_parts)})"
                )
                continue

            # Modules cannot import platform directly (must go through runtime/integrations/shared)
            if src_tier == "modules" and dst_tier == "platform":
                violations.append(
                    f"{py_file.relative_to(ROOT.parent)}:{lineno}: business module '{src_parts[1]}' cannot import 'platform' directly"
                )

            # Platform strata check
            if src_tier == "platform" and dst_tier == "platform" and len(src_parts) > 1 and len(dst_parts) > 1:
                src_sub = src_parts[1]
                dst_sub = dst_parts[1]
                if src_sub != dst_sub:
                    src_stratum = PLATFORM_STRATA.get(src_sub, -1)
                    dst_stratum = PLATFORM_STRATA.get(dst_sub, -1)
                    if dst_stratum > src_stratum:
                        violations.append(
                            f"{py_file.relative_to(ROOT.parent)}:{lineno}: platform.{src_sub} (stratum {src_stratum}) cannot import higher platform.{dst_sub} (stratum {dst_stratum})"
                        )
    return violations


def main() -> int:
    violations = check_boundaries()
    if violations:
        for v in violations:
            print(f"BOUNDARY VIOLATION: {v}", file=sys.stderr)
        return 1
    print("All backend architectural boundaries passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
