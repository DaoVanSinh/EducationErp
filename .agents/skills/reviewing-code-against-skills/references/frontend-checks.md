# Frontend Checks — Next.js / React

Run the tools first (`eslint`, `eslint-plugin-boundaries` if configured, `tsc --noEmit`), then check for these.

---

## 1. Zero Hardcoded Strings & Missing Constants
Flag any raw string literals used for auth statuses, roles, resources, actions, permissions, or query keys across components:
- `session.status === "authenticated"` or `session.status !== "authenticated"` → MUST use `isAuthenticated(session)` or `AUTH_STATUS.AUTHENTICATED`.
- `<Can I="read" a="user">` or `<Can I="READ" a="ACCOUNT">` → MUST use `<Can I={ACTIONS.READ} a={RESOURCES.ACCOUNT}>`.
- `<RequirePermission resource="account" action="read">` → MUST use constants `RESOURCES.ACCOUNT` and `ACTIONS.READ`.
- Role names (e.g. `"admin"`) → MUST use `SYSTEM_ROLE_NAMES.ADMIN` or `isProtectedAdminRole()`.
- Inline query keys `queryKey: ["accounts", id]` → MUST use typed query key factory `accountQueryKeys.detail(id)`.

---

## 2. Separation of Logic from Render (Tách Biệt Logic Khỏi Render)
Flag any UI component under `ui/*.tsx` that violates clean render discipline:
- **Sprawling Inline Handlers**: Flag inline async functions in JSX props (e.g. `onSubmit={async (data) => { ... }}`). All submission workflows must live in a controller hook.
- **Direct Query/Mutation Plumbing in UI**: Flag components that directly manage `useMutation`, `useQueryClient.invalidateQueries`, form schemas, and error toasts inline.
- **Rule**: Extract into a dedicated custom hook (`hooks/use-<feature>-controller.ts`). The UI component should only call the hook, receive typed state + event callbacks, and render JSX.

---

## 3. Clean Components & Complexity Budget (< 15 Complexity, < 200 Lines)
- **Cyclomatic Complexity**: `eslint`'s `complexity: ["error", 15]` flags functions/components with complexity ≥ 15. Flag any component with sprawling branching logic.
- **Component File Size**: Flag pure UI components exceeding ~150–200 lines. The fix is decomposing into smaller sub-components (`<ItemHeader>`, `<ItemTable>`, `<ItemPagination>`).
- **No Nested Ternaries**: Flag nested ternary operators in JSX (`condA ? (condB ? <Comp1 /> : <Comp2 />) : <Comp3 />`). Must use early returns or separate sub-components.

---

## 4. Cross-Module Imports & Proxy Re-exports
A `modules/<a>/` file importing directly from `modules/<b>/ui|model|api` (instead of through `entities/` or `shared/`) is the single most common violation in `nextjs-modular-architecture` projects:

```ts
// violation
import { UserAvatar } from "@/modules/profile/ui/user-avatar";

// fix: promote the shared concept to entities/, import through its public API
import { UserAvatar } from "@/entities/user";
```

**Proxy Re-exports (FORBIDDEN):** Flag any file created inside a module purely to re-export an entity's schema/types (e.g. `modules/roles/model/schema.ts` containing `export * from "@/entities/role"`). The module must import directly from `@/entities/role` at the call site.

---

## 5. Missing SSR Prefetch & Unconditional SSR Prefetch
- **Missing prefetch:** A page component marked `"use client"` at the top level that calls `useQuery` with no corresponding server-side `prefetchQuery` + `HydrationBoundary` in its `page.tsx` — this causes a client-side fetch waterfall.
- **Unconditional prefetch (Security/Integrity):** A Server Component `page.tsx` that calls `queryClient.prefetchQuery(...)` unconditionally without checking `hasPermission(session, resource, action)` first — this causes unauthorized 403 server-side prefetch attempts. Always guard SSR prefetch with `hasPermission`.

---

## 6. Framer Motion / Lucide-React
- `import { motion } from "framer-motion"` instead of the shared `LazyMotion` + `m` wrapper — ships the full animation engine to every page that imports it.
- `import * as Icons from "lucide-react"` instead of named imports — defeats tree-shaking.

---

## 7. Circular Imports
Flag any circular dependencies reported by `import/no-cycle`. Move the shared types/components to `entities/` or `shared/` so dependency arrows remain strictly unidirectional.

---

## 8. Suppression Comments Without a Reason
Flag on sight:
- `// eslint-disable-next-line` (or `/* eslint-disable */`) with no rule name.
- Any `eslint-disable*`/`@ts-ignore`/`@ts-expect-error` with no `-- reason` explaining why.
- `@ts-ignore` where `@ts-expect-error` would do.
