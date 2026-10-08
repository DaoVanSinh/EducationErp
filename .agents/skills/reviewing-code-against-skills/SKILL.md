---
name: reviewing-code-against-skills
description: Use when reviewing changed backend or frontend code for compliance with the project's own architecture skill — after implementing a feature, before handing work back, or when asked to review/audit/check code against the project's conventions rather than generic style. Covers Python/FastAPI and Java/Spring Boot backends (N+1 queries, database ownership rules, modular boundaries) and Next.js/React frontends (separation of logic from render, no hardcoded strings, complexity < 15).
---

# Reviewing Code Against Skills

## Overview
A generic code review checks against style. This checks against **this project's own declared architecture** — the rules baked into whichever skill scaffolded it. Find the governing skill(s) for the changed files, pull its non-negotiable rules and common mistakes as the checklist, run the domain's standard tools, then add the checks tools can't do: N+1 queries, database ownership, cross-module imports, missing SSR prefetch, hardcoded strings, and separation of logic from render.

---

## When to Use
- After implementing a feature or fixing a bug, before calling it done
- Before a PR, or before handing work back to the user
- User asks to "review this", "audit this", "check this follows the conventions"

---

## Core Universal Checkpoints (Front & Back)

1. **Zero Hardcoded Strings (Không Hard-Type String)**:
   - **Frontend**: Check that all auth status, permissions, roles, resources, actions, routes, and query keys use typed constants from `@/shared/constants/`. Flag any inline string literals (`"READ"`, `"ACCOUNT"`, `"authenticated"`).
   - **Backend**: Check that all constants, limits, namespaces, error codes, and authority strings are grouped into static inner classes in `<Module>Constants.java`. Flag any bare string literals in `@PreAuthorize` or cache queries.
2. **Frontend: Separation of Logic from Render (Tách Logic Khỏi Render)**:
   - Flag any UI component in `ui/*.tsx` containing inline async event handlers (`onSubmit={async () => { ... }}`), direct mutation calls, or multi-step form state logic.
   - All logic, form handling, and query wiring MUST be encapsulated in dedicated custom hooks (`hooks/use-*.ts` or `hooks/use-<feature>-controller.ts`).
3. **Clean Components & Complexity Budget (< 15 Cyclomatic Complexity)**:
   - Flag any function, method, or React component with cyclomatic complexity ≥ 15.
   - Flag pure UI components exceeding ~150–200 lines (must decompose into sub-components).
   - Flag nested ternary hell in JSX (`a ? (b ? c : d) : e`).

---

## Workflow
1. **Scope.** `git diff --name-only` against the base branch, or the files just written this session. Group changed files by directory/extension.
2. **Find the governing skill** for each group:
   - `.java` under a Spring Boot tree with `@ApplicationModule` / `com.eduerp.modules.*` → `springboot-modular-scaffold` conventions.
   - `.py` under an `app/` tree with `constants.py`/`config.py`/`public.py` → `fastapi-modular-scaffold` conventions.
   - `.ts`/`.tsx` under `modules/`/`entities/`/`shared/` with TanStack Query and modular layers → `nextjs-modular-architecture` conventions.
3. **Pull the checklist from the skill itself** — read its "Non-negotiable rules" and "Checklist".
4. **Run the domain's tooling** (Quick Reference below).
5. **Report** using the shape in `references/report-format.md`. Every finding names the specific rule and the skill it came from.

---

## Quick Reference
| Domain | Tools | Key Architecture Checks |
|---|---|---|
| Java / Spring Boot | `mvn test -Dtest=ModularityTests`, `mvn test` | Spring Modulith boundaries, no God modules (`identity`), class-scoped constants, post-commit cache eviction, complexity < 15 |
| Python / FastAPI | `ruff check`, `ruff format --check`, `lint-imports` | `check_module_boundaries.py`, Abstract contracts, no bare constants, N+1 queries, post-commit invalidation |
| React / Vite / Next.js | `npm run lint`, `npm run typecheck` | Separation of logic from render, no hardcoded strings, complexity < 15, no proxy re-exports, component < 200 lines |

---

## Common Review Failures to Flag Immediately
- Hardcoded strings for auth, roles, permissions, or query keys anywhere.
- UI components bloated with inline async handlers and direct mutation logic instead of custom controller hooks.
- Deeply nested ternaries in JSX.
- God Modules in backend (`identity` owning audit, mail, branches, or dashboard).
- Proxy re-export files in frontend (`modules/roles/model/schema.ts` re-exporting `entities/role`).
- Unconditional SSR prefetch in Next.js Server Components without checking `hasPermission`.
