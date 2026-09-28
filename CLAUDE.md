# Instructions for Claude Code

@AGENTS.md

## Three Core Mandates (MUST ALWAYS ENFORCE)

1. **No Hardcoded Strings (Không Hard-Type String)**:
   - Frontend: Centralized constants from `@/shared/constants/` (`AUTH_STATUS`, `RESOURCES`, `ACTIONS`, `PERMISSIONS`, `ROUTES`). Type guards (`isAuthenticated(session)`).
   - Backend: Class-scoped static inner classes in `<Module>Constants.java` (`Limits`, `Resources`, `Actions`, `RoleCodes`, `Authorities`, `CacheNamespaces`, `ErrorCodes`).
2. **Frontend: Tách Logic Khỏi Render (Separation of Logic from Render)**:
   - UI components (`ui/*.tsx`) are purely declarative/presentational ("Clean Render").
   - Extract form state, queries, mutations, and handlers into custom hooks (`hooks/use-*.ts` or `hooks/use-<feature>-controller.ts`).
   - No giant inline `async () => { ... }` handlers in JSX props.
3. **Clean Components & Complexity Budget (< 15 Cyclomatic Complexity)**:
   - All functions, methods, and React components MUST have cyclomatic complexity < 15.
   - Component line budget: Pure UI components stay strictly under 150–200 lines (split into sub-components).
   - No nested ternary hell (`a ? (b ? c : d) : e`).

---

## Quick Reference Commands

### Backend (Spring Boot 3.4 / Java 21)
```bash
# Run application locally
cd backend && ./mvnw spring-boot:run

# Run Spring Modulith boundary verification tests
cd backend && mvn test -Dtest=ModularityTests

# Run all backend tests
cd backend && mvn test

# Compile and verify package
cd backend && mvn clean compile test-compile

# Scaffold a new domain module
python .agents/skills/springboot-modular-scaffold/scripts/scaffold.py --add-module billing --package com.eduerp --output ./backend/src/main/java
```

### Frontend (React 19 / Vite / TypeScript)
```bash
# Run development server
cd frontend && npm run dev

# Type check
cd frontend && npm run typecheck

# Lint (ESLint + boundaries)
cd frontend && npm run lint

# Production build
cd frontend && npm run build
```
