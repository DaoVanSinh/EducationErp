# EducationERP Project — Full-Stack Development Instructions

> This file provides standard instructions for coding agents (Claude Code, Gemini, Antigravity).
> Read carefully before starting any task.

---

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| **Frontend** | **React 19 / Vite** (App/Router) | TypeScript, modular architecture (`shared → entities → modules → app`), TanStack Query v5, Tailwind CSS v4, Zod |
| **Backend** | **Spring Boot 3.4+ / Java 21** | Spring Modulith 1.3+, modular scaffold (`core → modules → integrations → shared`), PostgreSQL, Redis, Flyway |
| **UI/UX** | **`ui-ux-pro-max` plugin — MANDATORY** | Mandatory during Phase 3 for any UI code (styling, layout, colors, typography, a11y) |
| **Analysis** | **`superpowers` plugin — MANDATORY** | Invoke during Phase 0 → Phase 2 (understanding scope, planning) |
| **Database** | **PostgreSQL 16 / Hibernate** | Flyway migrations, HikariCP, JPA repository, Event Publication Registry |

---

## Core Non-Negotiable Rules (Front & Back)

### 1. Zero Hardcoded String Literals (Không Hard-Type String)
- **Frontend**:
  - NEVER hardcode string literals for auth status, roles, resources, actions, permissions, or query keys.
  - ALWAYS import typed constants from `@/shared/constants/` (`AUTH_STATUS`, `RESOURCES`, `ACTIONS`, `PERMISSIONS`, `ROUTES`).
  - Use `isAuthenticated(session)` type guards instead of manual `session.status === "authenticated"` string comparisons.
  - Pass typed constants to permission barriers: `<Can I={ACTIONS.READ} a={RESOURCES.ACCOUNT}>`, never `<Can I="READ" a="ACCOUNT">`.
- **Backend**:
  - NEVER use bare string literals for permissions, roles, paths, cache keys, or error codes.
  - Group constants into static inner classes inside `<Module>Constants.java` (`Limits`, `Resources`, `Actions`, `RoleCodes`, `Authorities`, `CacheNamespaces`, `ErrorCodes`).
  - Assemble cache keys strictly via `integrations.cache.CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.SESSIONS, id)`.

### 2. Frontend: Tách Biệt Hoàn Toàn Logic Khỏi Render (Clean Separation)
- **UI Components are Declarative/Presentational**:
  - A component in `ui/*.tsx` must focus purely on **rendering** (layout, Tailwind styles, accessibility, visual states).
  - **Zero sprawling inline handlers**: Never write giant inline `onSubmit={async () => { ... }}` or multi-step logic inside JSX.
- **Extract to Custom Hooks (`hooks/use-*.ts`)**:
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

### 3. Component Sạch & Giới Hạn Độ Phức Tạp (Cyclomatic Complexity < 15)
- **Complexity Budget (< 15)**:
  - Every function, method, and React component MUST maintain a **cyclomatic complexity < 15** (enforced by ESLint and PMD/Checkstyle).
  - If a component has multiple branching conditions, extract them into pure helper rules or sub-components.
- **Component Size Budget (< 150–200 lines)**:
  - Pure UI components must not exceed ~150–200 lines. If a screen is large, decompose it into focused sub-components (`<AccountHeader>`, `<AccountFilterBar>`, `<AccountTableList>`, `<AccountPagination>`).
- **No Nested Ternary Hell**:
  - Forbidden: `condA ? (condB ? <Comp1 /> : <Comp2 />) : <Comp3 />`.
  - Use early returns, pattern matching, or separate sub-components for clean readability.

---

## Architecture Principles (Frontend & Backend)

### 1. Frontend Modular Architecture (`frontend/src/`)
- **Unidirectional Layer Flow**: `shared → entities → modules → app`
  - `shared/`: Generic UI components, hooks, utilities, types, and centralized constants (`shared/constants/`).
  - `entities/<name>/`: Domain models, Zod schemas (`model/schema.ts`), query keys (`api/query-keys.ts`), and read-only fetchers/hooks (`api/fetchers.ts`, `hooks/use-*.ts`).
  - `modules/<feature>/`: Feature-specific workflows, mutation hooks, dialogs/forms/views (`ui/`, `hooks/`).
  - `app/`: Routing and providers only (`routes.tsx`, `App.tsx`) — thin layer composing module UI.
- **Entities vs Modules (Single Source of Truth)**:
  - Domain models/schemas live in `entities/` (`@/entities/account`, `@/entities/role`, `@/entities/permission`).
  - **Never create duplicate schemas or query keys** inside `modules/`.
  - **Never use proxy re-export files** (e.g. creating `modules/roles/model/schema.ts` to re-export `entities/role`). Callers import directly from `@/entities/<name>`.

### 2. Spring Boot Modular Scaffold (`backend/src/main/java/com/eduerp/`)
- **Spring Modulith 1.3 Conventions**:
  - Configured with `spring.modulith.detection-strategy: explicitly-annotated`.
  - Every domain package under `modules/` and integration under `integrations/` carries `@ApplicationModule` in its `package-info.java`.
- **Module Structure (Advanced Module)**:
  - **Base package**: Public API — Facade (`<Module>Management.java`), `<Module>Constants.java`, `<Module>Properties.java`, `<Module>Exception.java`, `<Module>Events.java`.
  - `dto/`: Request/Response records; carries `@NamedInterface("dto")` so callers can reference contract types without accessing internal entities.
  - `usecase/`: One class per use case, single `public` `@Transactional` method named `execute(...)`.
  - `web/`: HTTP adapters (`@RestController`), cookies, filters. Thin layer delegating to use cases.
  - `internal/`: Implementation hidden by Spring Modulith (`model/` JPA entities, `repository/` Spring Data interfaces, `rules/` pure decisions, `util/` pure transforms).
- **Avoid God Modules (Strict Bounded Contexts)**:
  - `modules.identity` ONLY owns accounts, credentials, auth sessions, and basic profile.
  - Roles, permissions, and effective permissions belong in `modules.access` (RBAC).
  - Branches and campus hierarchy belong in `modules.organization`.
  - Audit logging belongs in `modules.audit` (listens asynchronously via `@ApplicationModuleListener`).
  - Mail/SMS notifications belong in `integrations.mail` (or `modules.notification`).
  - Dashboard aggregations belong in `modules.dashboard`.
- **No Cross-Module SQL JOINs or JPA `@ManyToOne`**:
  - Cross-module reads go through the Facade. Compose in the use case.
- **Post-Commit Cache Eviction**:
  - Invalidate after database commit using `TransactionSynchronizationManager.registerSynchronization(afterCommit(...))`.

---

## Mandatory Full-Stack Dev Workflow

### PHASE 0 — Understand the Task
1. Read the user's request carefully.
2. Inspect current branch and working tree (`git status`).
3. Determine scope: Frontend only / Backend only / Full-stack | New feature / Bugfix / Refactor.
4. If critical information is missing, ask the user once.

### PHASE 0.5 — Read Related Skills (MANDATORY before Plan & before editing code)
Do not create a plan or edit code before reading related skills:
- Frontend: `.agents/skills/nextjs-modular-architecture/SKILL.md`
- Backend: `.agents/skills/springboot-modular-scaffold/SKILL.md`
- Review: `.agents/skills/reviewing-code-against-skills/SKILL.md`
- Workflow: `.agents/skills/full-stack-dev-workflow/SKILL.md`

List 3–7 concrete constraints copied from skills before proceeding.

### PHASE 1 — Research & Verify
1. Verify documentation and APIs when not 100% certain.
2. Check dependencies in `backend/pom.xml` and `frontend/package.json`.

### PHASE 2 — Plan (MANDATORY before editing code)
Write a plan covering:
- Goal
- Scope (In / Out)
- Skills Applied & Constraints
- Architecture Impact (Modules affected)
- Numbered Steps
- Verification Checklist

### PHASE 3 — Implement
1. Follow the exact step order from the plan.
2. Respect module boundaries (`shared → entities → modules → app` in frontend; Facade + `@NamedInterface` in backend).
3. Ensure **Separation of Logic from Render** in frontend (custom hooks for logic, presentational components).
4. Maintain cyclomatic complexity **< 15** and component size **< 200 lines**.
5. If UI code is touched (layout, colors, spacing, typography), invoke `ui-ux-pro-max`.
6. Run typecheck and tests as you build.

### PHASE 4 — Review Against Skills (MANDATORY)
Run the review checklist:
- [ ] Backend: `mvn test -Dtest=ModularityTests` passes (Spring Modulith boundary verification).
- [ ] Backend: No cross-module SQL joins or `@Entity` leaking across boundaries.
- [ ] Backend: No God modules (`identity` does not own audit, mail, branches, or dashboard).
- [ ] Backend: Constants grouped in static inner classes; cache invalidation after commit.
- [ ] Frontend: `npm run lint` and `npm run typecheck` pass with 0 errors.
- [ ] Frontend: Unidirectional imports (`shared → entities → modules → app`); no proxy re-exports.
- [ ] Frontend: No hardcoded string literals for auth status, permissions, roles, or resources.
- [ ] Frontend: Logic separated from render via custom hooks; UI components are declarative.
- [ ] All code: Cyclomatic complexity < 15, no bloated files.

### PHASE 5 — Branch / Commit / PR
1. **Never commit or push directly to `main` or `develop`.**
2. Feature/fix branches: `feature/<slug>`, `fix/<slug>`, `refactor/<slug>` branched from `develop`, PR into `develop`.
3. Commit messages follow Conventional Commits: `feat: ...`, `fix: ...`, `refactor: ...`, `chore: ...`.

---

## Hard Rules

1. **Read skills before Plan and before editing code** (Phase 0.5).
2. **Do not skip Plan** when the task touches > 1 file or has architectural impact.
3. **No hardcoded string literals** anywhere (use `shared/constants/` and `<Module>Constants`).
4. **Separate logic from render** in frontend components (extract to hooks).
5. **Keep complexity < 15 and component lines < 200.**
6. **No proxy re-exports in frontend.** Import directly from `@/entities/<name>`.
7. **No God Modules in backend.** Separate `identity`, `access`, `organization`, `audit`, and `mail`.
8. **Spring proxy AOP requirement**: Use cases and `@Transactional` methods must be `public`.
9. **All error responses follow RFC 9457 `ProblemDetail`** with stable `errorCode` strings.
10. **Never push directly to `main` or `develop`.**
