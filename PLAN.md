# Plan: Redesign UI to White Liquid Glass with Pastel Orange & Red Accents

## Goal
Redesign the entire frontend of `EducationErp` to a modern **Cam - Trắng (Orange & White)** color scheme featuring **Liquid Glass** (glassmorphism/frosted glass), fluid **motion** (subtle micro-animations via Framer Motion), with **pastel orange + pastel coral/red** as dominant primary accent colors, strictly adhering to architecture skills.

## Scope
- **In Scope**:
  - `frontend/src/index.css`: Replace dark ink/violet/aqua theme with luminous warm-white liquid glass tokens (`--color-pastel-orange-*`, `--color-pastel-red-*`, `--color-slate-*`, glass utilities `@utility glass`, `@utility glass-raised`, `@utility glass-sheen`, `@utility glass-glow`).
  - `frontend/src/shared/ui/`: Update all shared UI primitives (`glass-button`, `badge`, `glass-input`, `password-input`, `glass-select`, `glass-panel`, `glass-modal`, `error-notice`, `form-field`, `page-header`, `empty-state`, `pagination`, `skeleton`) to white liquid glass aesthetics.
  - `frontend/src/app/layouts/` & `frontend/src/app/ui/`: Update `dashboard-layout.tsx`, `auth-layout.tsx`, `app-nav.tsx` (smooth motion tab indicator), `session-card.tsx`.
  - `frontend/src/entities/account/ui/`: Update `user-avatar.tsx`, `account-status-badge.tsx`.
  - `frontend/src/modules/`: Update dashboard components (`stat-card.tsx`, `role-breakdown.tsx`, `recent-logins.tsx`), auth forms, and rbac rows (`checkable-row.tsx`, `accounts-table.tsx`).
- **Out of Scope**:
  - Backend Java/Spring Boot logic.
  - Modifying backend API contracts or DTOs.
  - Modifying routing structure or auth state machines.

## Skills Applied & Specific Constraints
- **`nextjs-modular-architecture`**:
  1. *Zero hardcoded string literals*: import constants from `@/shared/constants/` (`AUTH_STATUS`, `RESOURCES`, `ACTIONS`, `PERMISSIONS`, `APP_ROUTE`).
  2. *Separation of logic from render*: UI components in `ui/*.tsx` are purely presentational; all logic/handlers reside in controller hooks; no inline async handlers in JSX props.
  3. *Complexity & size budget*: Cyclomatic complexity < 15, component lines < 150–200, no nested ternary hell in JSX.
  4. *Framer Motion optimization*: `LazyMotion` + `domAnimation` + `m.*` components for lightweight, smooth spring transitions.
- **`reviewing-code-against-skills`**:
  - Run `npm run typecheck` and `npm run lint` with 0 errors.
  - Verify unidirectional layer flow (`shared → entities → modules → app`), no proxy re-exports.

## Plugins
- `superpowers`: Invoked during task framing and planning pass (Phase 0–2).
- `ui-ux-pro-max`: Mandatory during Phase 3 (Implement) for visual design, high-contrast readability on frosted glass, warm palette harmony (pastel orange `#FF8C42`, pastel red `#FF5E62`, luminous ivory `#FAF8F5`, frosted glass `backdrop-blur-xl`), and WCAG AA contrast.

## Impact Assessment
- **Symbols / Files to modify**:
  - `frontend/src/index.css` (global theme tokens and utilities)
  - `frontend/src/shared/ui/glass-button.tsx`
  - `frontend/src/shared/ui/badge.tsx`
  - `frontend/src/shared/ui/glass-input.tsx`
  - `frontend/src/shared/ui/password-input.tsx`
  - `frontend/src/shared/ui/glass-select.tsx`
  - `frontend/src/shared/ui/glass-panel.tsx`
  - `frontend/src/shared/ui/glass-modal.tsx`
  - `frontend/src/shared/ui/error-notice.tsx`
  - `frontend/src/shared/ui/form-field.tsx`
  - `frontend/src/shared/ui/page-header.tsx`
  - `frontend/src/shared/ui/empty-state.tsx`
  - `frontend/src/shared/ui/pagination.tsx`
  - `frontend/src/shared/ui/skeleton.tsx`
  - `frontend/src/app/layouts/dashboard-layout.tsx`
  - `frontend/src/app/layouts/auth-layout.tsx`
  - `frontend/src/app/ui/app-nav.tsx`
  - `frontend/src/app/ui/session-card.tsx`
  - `frontend/src/entities/account/ui/user-avatar.tsx`
  - `frontend/src/modules/dashboard/ui/stat-card.tsx`
  - `frontend/src/modules/dashboard/ui/role-breakdown.tsx`
  - `frontend/src/modules/dashboard/ui/recent-logins.tsx`
  - `frontend/src/modules/rbac/ui/checkable-row.tsx`
  - `frontend/src/modules/auth/ui/login-form.tsx`
- **Risk Level**: LOW (visual refactoring; strictly preserving props, types, and logic separation).
- **Mitigation**: Maintain semantic color aliases so existing components remain readable, then progressively polish each component to new classes. Run `npm run typecheck` and `npm run lint` continuously.

## Architecture Impact
- **Modules Affected**:
  - `shared/ui`: All UI atoms refreshed with white liquid glass + pastel orange/red styling.
  - `app/layouts` & `app/ui`: App shell, navigation, and auth wrapper updated.
  - `entities/account/ui`: Avatar and badge styling.
  - `modules/dashboard/ui`, `modules/rbac/ui`, `modules/auth/ui`: Visual updates to match design tokens.

## Steps
1. **Step 1**: Update `frontend/src/index.css` with new design tokens, `@utility glass`, `@utility glass-raised`, `@utility glass-sheen`, and luminous warm-white background mesh.
2. **Step 2**: Update core shared UI components (`glass-button`, `badge`, `glass-input`, `password-input`, `glass-select`, `glass-modal`, `glass-panel`, `error-notice`, `form-field`, `page-header`, `empty-state`, `pagination`, `skeleton`).
3. **Step 3**: Update layouts and navigation (`dashboard-layout`, `auth-layout`, `app-nav`, `session-card`).
4. **Step 4**: Update entities and feature modules (`user-avatar`, `stat-card`, `role-breakdown`, `recent-logins`, `checkable-row`, `login-form`).
5. **Step 5**: Run verification: `npm run typecheck`, `npm run lint`, and verify visual rendering in browser.

## Test / Verify Checklist
- [ ] `npm run typecheck` passes with 0 errors.
- [ ] `npm run lint` passes with 0 errors.
- [ ] No hardcoded string literals added.
- [ ] Separation of logic from render maintained across all UI components.
- [ ] Cyclomatic complexity < 15 and component lines < 200 on all modified files.
- [ ] Frosted liquid glass effect and pastel orange/red accents render smoothly with high readability.

## Risks
- Low contrast if white text is left on white glass surfaces → Mitigated by systematically mapping text to high-contrast slate colors (`text-slate-900`, `text-slate-800`, `text-slate-600`) and ensuring WCAG AA contrast.
