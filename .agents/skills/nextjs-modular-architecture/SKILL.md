---
name: nextjs-modular-architecture
description: Use when scaffolding, structuring, or reviewing a Next.js App Router frontend built with TanStack Query, shadcn/ui, Framer Motion, and lucide-react — creating a new feature, deciding where a type/constant/util/component belongs, wiring up data fetching or mutations, or when a codebase is sliding into a flat components/hooks/utils dump instead of a modular, layered structure.
---

# Next.js Modular Architecture

## Overview
Layered + modular architecture for large Next.js (App Router) or Vite apps built with TanStack Query, shadcn/ui, Framer Motion, lucide-react. Routing stays thin, business logic lives in self-contained `modules/`, business concepts shared by ≥2 modules live in `entities/`, generic code lives in `shared/`. Dependency flows one way: `shared → entities → modules → app`. Optimized for fast load (Server Components by default, streamed, code-split) and for teams to own a module without breaking another.

---

## Non-Negotiable Core Rules

### 1. Zero Hardcoded String Literals (Không Hard-Type String)
- NEVER hardcode string literals for auth status, roles, resources, actions, permissions, or query keys.
- ALWAYS import centralized constants from `@/shared/constants/` (`AUTH_STATUS`, `RESOURCES`, `ACTIONS`, `PERMISSIONS`, `ROUTES`).
- Use `isAuthenticated(session)` type guards instead of manual `session.status === "authenticated"` string comparisons.
- Pass typed constants to permission barriers: `<Can I={ACTIONS.READ} a={RESOURCES.ACCOUNT}>`, never `<Can I="READ" a="ACCOUNT">`.

### 2. Separation of Logic from Render (Tách Biệt Hoàn Toàn Logic Khỏi Render)
- **UI Components are Declarative/Presentational**:
  - Components in `ui/*.tsx` must focus purely on **rendering** (layout, styling, accessibility, visual states).
  - **Zero sprawling inline handlers**: Never write giant inline `onSubmit={async () => { ... }}` or multi-step logic inside JSX props.
- **Extract to Custom Controller Hooks (`hooks/use-*.ts`)**:
  - All state transitions, form state (`react-hook-form` / Zod), mutation hooks (`useMutation`), query hooks (`useQuery`), and event callbacks MUST live inside a dedicated hook (e.g. `use-account-table-controller.ts`, `use-login-form.ts`).
  - The UI component only invokes the hook, receives state and handler callbacks, and renders JSX:
    ```tsx
    // Correct: Component is clean and purely presentational
    export function AccountTableView() {
      const { items, isLoading, onDelete, onPageChange } = useAccountTableController();
      if (isLoading) return <AccountTableSkeleton />;
      return <AccountTable items={items} onDelete={onDelete} onPageChange={onPageChange} />;
    }
    ```

### 3. Clean Components & Complexity Budget (Cyclomatic Complexity < 15)
- **Complexity Budget (< 15)**:
  - Every function, method, and React component MUST maintain a **cyclomatic complexity < 15** (enforced by ESLint `complexity: ["error", 15]`).
  - If a component has multiple branching conditions, extract them into pure helper rules or sub-components.
- **Component Size Budget (< 150–200 lines)**:
  - Pure UI components must not exceed ~150–200 lines. If a screen is large, decompose it into focused sub-components (`<AccountHeader>`, `<AccountFilterBar>`, `<AccountTableList>`, `<AccountPagination>`).
- **No Nested Ternary Hell**:
  - Forbidden: `condA ? (condB ? <Comp1 /> : <Comp2 />) : <Comp3 />`.
  - Use early returns or separate sub-components for clean readability.

---

## When to Use
- Starting a new Next.js / Vite app or a large feature area
- Adding a new domain (auth, billing, orders...)
- Unsure where a type/constant/util/component/hook should live
- A module needs something another module already has (the #1 default mistake — see below)
- Wiring TanStack Query, shadcn/ui, Framer Motion, or lucide-react
- Writing any user-facing string (error, toast, label) — first check whether the project uses i18n (see below)

Skip this for a throwaway prototype or when told to keep things minimal — ask first before imposing it on a small app.

**Before writing any user-facing string, check for i18n.** Look for `next-intl`/`i18next`/`react-i18next` in `package.json`, a `messages/`/`locales/` directory, or a `middleware.ts`/`[locale]` segment. If present, API error messages are shown via `error.code` translated through the i18n system — never the raw backend `message` — see `references/i18n-and-errors.md`. If absent, plain strings are fine.

**Works alongside:** if this system also has a Java/Spring Boot backend, that half is governed by the `springboot-modular-scaffold` skill — same shape of rules (modular, layered, one-way dependencies, strict file budgets), different stack. After changing code on either side, run `reviewing-code-against-skills` before calling the work done.

---

## Layers (top imports only from below)
```
app/      routing only: page.tsx, layout.tsx, loading.tsx, error.tsx — thin, composes modules
modules/  feature/domain code (auth, billing, orders) — NEVER import another module directly
entities/ business concepts shared by ≥2 modules (User, Order) — never imports from modules/
shared/   generic, business-agnostic code (ui kit, lib, utils, types, hooks, constants)
```

---

## Where does this code go?
1. Only one module uses it → keep inside `modules/<name>/`.
2. It's a business concept (a noun with product meaning, e.g. User, Order) needed by ≥2 modules → `entities/<name>/`.
3. It's generic/technical, no business meaning, portable to any project → `shared/`.
4. It's one module's own wire-format types for its own API → stays in that module's `api/`.

**Default mistake to avoid:** when module B needs something module A owns (e.g. `orders` needs the `User` that `profile` owns), the reflex is `import { UserAvatar } from '@/modules/profile/ui/user-avatar'`. That's a direct module→module import — forbidden. Move the shared piece (type + the minimal reusable UI/hook around it) to `entities/user/`, and have both modules import from there instead. See `references/layers-and-modules.md` for the full worked example.

---

## Module Anatomy
```
modules/<name>/
├── api/     fetchers (validate via model/'s zod schema, not a type assertion) + TanStack Query hooks
├── model/   zod schemas + the types derived from them (z.infer), business logic, local store
├── ui/      pure declarative presentational components/screens for this module (< 200 lines)
├── hooks/   controller hooks extracting logic, form handling, and mutations from render
└── index.ts curated public API — never `export *`
entities/<name>/  same shape, leaner: usually just model/ + a read hook in api/ + one ui atom
```

---

## Quick Reference
| Concern | Rule | Detail |
|---|---|---|
| Full worked example, every layer, one module top to bottom | Read references/layer-examples.md |
| No hardcoded strings | Always use `@/shared/constants/` (`AUTH_STATUS`, `RESOURCES`, `ACTIONS`, `PERMISSIONS`) | references/rbac-ui.md |
| Separation of logic from render | UI components only render; extract logic, queries, and forms to `hooks/use-*.ts` | references/layers-and-modules.md |
| Complexity budget | Cyclomatic complexity < 15, component size < 200 lines, no nested ternaries | references/enforcement-and-conventions.md |
| API response shape | Backend returns `{ success, data, error }`, camelCase — unwrap once in `apiFetch`, never per-fetcher | references/data-layer.md |
| Runtime-validating a fetch response | Zod schema in `model/`, `.parse()` in the fetcher — the type is `z.infer` of the schema, not a separate assertion | references/data-layer.md, references/layer-examples.md |
| Loading/error state for a fetched list or detail view | `useSuspenseQuery` + route `loading.tsx`/`error.tsx`, not `isLoading`/`error` checks in the component — one loading UI and one error UI per route | references/data-layer.md, references/layer-examples.md |
| Error messages / i18n | `error.code` is the i18n key when i18n exists; `error.message` is the non-localized fallback — applies to a toast in `onError` exactly as much as an error boundary | references/i18n-and-errors.md |
| Permission-gated buttons/pages/modals/selects | `<Can I={ACTIONS...} a={RESOURCES...}>`/`RequirePermission`, backed by `hasPermission` & `PermissionProvider` — UX only, the backend is the real check | references/rbac-ui.md |
| SSR prefetch guards | Guard `queryClient.prefetchQuery` in Server Components with `hasPermission(session, ...)` before prefetching | references/rbac-ui.md |
| Entity vs Module boundaries | Entities own schema + read hooks (`entities/<name>`); modules own feature mutations/dialogs (`modules/<name>`); never proxy re-export | references/layers-and-modules.md |
| Reads | Server Component prefetch → `HydrationBoundary`, or Route Handler + `useQuery` on the client | references/data-layer.md |
| Mutations | Server Action or Route Handler, wrapped in `useMutation` with optimistic update | references/data-layer.md |
| Query keys | One hierarchical factory per module/entity, never inline strings | references/data-layer.md |
| shadcn/ui | Source lives in `shared/ui/`, generated once, edited deliberately — not hand-patched piecemeal | references/ui-motion-icons.md |
| Framer Motion | `LazyMotion` + `domAnimation` + `m.*`, not the full `motion` import | references/ui-motion-icons.md |
| lucide-react | Named imports only (`import { Truck } from 'lucide-react'`), never `import *` | references/ui-motion-icons.md |
| Performance | RSC by default, `next/dynamic` for heavy client widgets, `next/image`/`next/font`, no export-everything barrels | references/performance-checklist.md |
| Enforcing the layers | `eslint-plugin-boundaries` config | references/enforcement-and-conventions.md |
| Circular imports | Never — `import/no-cycle`. Dynamic `import()` for code-splitting is a different thing and is fine | references/enforcement-and-conventions.md |
| Suppression comments | No `eslint-disable`/`@ts-ignore` without a specific rule and a `-- reason` — `eslint-comments/require-description` | references/enforcement-and-conventions.md |
| Vite instead of Next.js | Same layers, different routing/data glue | references/vite-adaptation.md |
| Scaffold a module fast | `node scripts/create-module.mjs <name> --type=module\|entity` | scripts/create-module.mjs |

---

## Common Mistakes
- Hardcoding string literals for permissions/roles/statuses across UI (`<Can I="read" a="user">` or `session.status === "authenticated"`) → use `RESOURCES`, `ACTIONS`, `AUTH_STATUS` constants and `isAuthenticated(session)`.
- Giant inline handler functions in UI components (`onSubmit={async (data) => { ... }}`) → extract into a dedicated controller hook in `hooks/`.
- Over-complicated components with cyclomatic complexity > 15 or > 200 lines → break down into `<CompHeader>`, `<CompTable>`, `<CompDialog>`.
- Deeply nested ternary chains in JSX → use early returns or sub-components.
- Whole page is `"use client"` fetching with `useQuery` from mount → renders empty/skeleton first, then fetches (client waterfall). Prefetch in the Server Component and pass a `HydrationBoundary` instead — see `references/data-layer.md`.
- Executing `queryClient.prefetchQuery` unconditionally in Server Component pages without checking `hasPermission` first → causes unauthorized 403 server-side prefetch errors. Always guard with `hasPermission(session, resource, action)`.
- Creating duplicate schemas, query-keys, or fetchers inside `modules/<feature>` that mirror `entities/<noun>` → keep `entities/` as the single source of truth; import directly into `modules/`.
- Creating proxy re-export files (e.g. `modules/roles/model/schema.ts` re-exporting `entities/role`) → forbidden anti-pattern. Import directly from `@/entities/role`.
- Module reaches directly into another module's `ui/`/`model/` file path → creates hidden coupling that breaks the moment either module is refactored. Route it through `entities/` or `shared/`.
- Business logic written straight into `app/**/page.tsx` → hard to test, can't be reused. Keep pages thin; they compose a module's `ui/`.
- Importing the full `motion` component tree instead of `LazyMotion`/`m.*` → ships animation code that never gets used.
- A junk-drawer `shared/utils.ts` (or a parallel `helpers/` folder) → split utils by concern, one file per topic, and don't create both `utils/` and `helpers/`.
- Showing a raw backend `error.message` to the user in a project that has i18n → it's the non-localized fallback, not user-facing copy. Translate `error.code` instead — see `references/i18n-and-errors.md`.
- A fetcher returning the envelope (`{ success, data, error }`) instead of the unwrapped payload → unwrap once in `apiFetch`.
- Treating a hidden/disabled button as the security boundary (only server-side `require_permission` is) — see `references/rbac-ui.md`.
- A bare `// eslint-disable-next-line` with no rule name and no `-- reason` → silences every rule on that line.
