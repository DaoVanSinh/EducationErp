# PLAN — EduERP MCP Service (`eduerp-mcp`) & Frontend MCP Connector Integration

## 1. Goal
Build a production-grade `eduerp-mcp` service in `EducationErp` derived from the `mcp-hub` backend architecture, without a standalone UI, integrating the MCP connector management screen directly into the `EducationErp` React 19 / Vite frontend:
- Connect **Claude (`claude.ai`)** and **ChatGPT (`chatgpt.com` / `chat.openai.com`)** via **OAuth 2.1 Authorization Code + PKCE (`S256`)** (`/oauth/authorize`, `/oauth/token`, `/oauth/revoke`, `/.well-known/*`).
- Connect **AI IDEs & CLIs** (Antigravity, Cursor, Claude Code, VS Code) via **Bearer Token** (`<client_id>.<client_secret>`) and auto-generated `mcp.json` configuration snippets.
- Authenticate human web sessions and `/api/mcp/my/*` requests directly against EduERP's shared `access_token` (`HS256` JWT) cookie, `XSRF-TOKEN` / `X-XSRF-TOKEN` CSRF protection, and shared Redis token blacklist (`blacklist:jti:<jti>`).
- Enforce live EduERP RBAC permissions (`resource`, `action`, `scope`: `PERSONAL < BRANCH < ORGANIZATION`) on every MCP tool invocation by reading from shared Redis (`perm:account:<accountId>`) with PostgreSQL fallback (`public.account_roles`, `public.role_permission_groups`, `public.account_groups`, `public.group_permission_groups`, `public.permission_group_items`, `public.permissions`).
- Route `/mcp`, `/oauth/`, `/.well-known/`, and `/api/mcp/` through Nginx (`frontend/nginx.conf`), Vite dev proxy (`frontend/vite.config.ts`), and Docker Compose (`docker-compose.yml`, `docker-compose.prod.yml`).

---

## 2. Scope

### In Scope
1. **Shared PostgreSQL Migration (`V33__create_mcp_tables.sql`)**:
   - Create `mcp` schema and tables (`mcp.mcp_instances`, `mcp.oauth_connectors`, `mcp.oauth_used_codes`, `mcp.oauth_families`, `mcp.connections`, `mcp.audit`) + seed the default `eduerp` MCP instance.
2. **New `eduerp-mcp/` Service (Python 3.12 / FastMCP / Starlette / SQLAlchemy Core / Redis)**:
   - Strip unnecessary external modules (`plane`, DX SSO, admin catalog CRUD) from `mcp-hub/backend/` while preserving the modular tier/strata architecture (`shared -> infrastructure -> integrations -> runtime -> platform -> modules -> api -> core`) and `scripts/check_backend_boundaries.py`.
   - `platform/sessions`: Validate EduERP `access_token` JWT cookie (`HS256`, `IDENTITY_JWT_SECRET`), check `blacklist:jti:<jti>` in shared Redis, verify `public.accounts.status == 'ACTIVE'` in shared PostgreSQL, and enforce `XSRF-TOKEN` / `X-XSRF-TOKEN` CSRF validation on state-changing requests.
   - `platform/permissions`: Read live effective permissions from shared Redis key `perm:account:<accountId>`; on cache miss, compute effective permissions from EduERP RBAC tables in shared PostgreSQL (`PERSONAL < BRANCH < ORGANIZATION`) and populate Redis with a 600s TTL.
   - `platform/oauth`: OAuth 2.1 Authorization Code + PKCE (`S256`) server (`/oauth/authorize`, `/oauth/token`, `/oauth/revoke`, `/.well-known/*`) + `<client_id>.<client_secret>` direct Bearer token verifier (`HubTokenVerifier`). Redirect unauthenticated `/oauth/authorize` requests to `/login?next=/oauth/authorize?...`.
   - `platform/personal`: Self-service JSON endpoints at `/api/mcp/my/connector`, `/api/mcp/my/connector/secret`, `/api/mcp/my/mcps`, `/api/mcp/my/connections`, `/api/mcp/my/connections/{id}/revoke`.
   - `integrations/eduerp`: Async HTTP client to EduERP backend (`http://app:8080`) that mints short-lived internal HS256 JWT `access_token` + `XSRF-TOKEN` cookies for the authenticated caller's `account_id` so the Spring Boot backend also enforces its own `@RequirePermission` and data scoping.
   - `modules/eduerp`: FastMCP server mounted at `/mcp/eduerp` with 5 permission-aware tools:
     - `eduerp_my_context`
     - `eduerp_search_endpoints`
     - `eduerp_describe_endpoint`
     - `eduerp_read`
     - `eduerp_write`
3. **EduERP Frontend Integration (`frontend/src/`)**:
   - Constants in `shared/constants/app-routes.ts` (`APP_ROUTE.mcpConnector = "/mcp-connector"`) and `shared/constants/api-routes.ts` (`API_ROUTE.mcp.*`).
   - Support `?next=/oauth/...` external/full-page redirect after login in `redirect-when-signed-in.tsx`, `login-page.tsx`, and `use-auth-mutations.ts` so `SameSite=Strict` cookies are sent on same-origin navigation to `/oauth/authorize`.
   - `entities/mcp-connector/`: Zod schemas (`model/mcp-connector-schema.ts`), query keys (`api/mcp-connector-query-keys.ts`), API fetchers & query hooks (`api/mcp-connector-api.ts`, `api/use-mcp-connector-queries.ts`).
   - `modules/mcp-connector/`: Pure helper models (`model/mcp-config-snippets.ts`), controller hook (`hooks/use-mcp-connector-controller.ts`), and declarative presentational UI components (`ui/mcp-urls-card.tsx`, `ui/mcp-credentials-card.tsx`, `ui/mcp-ide-config-card.tsx`, `ui/mcp-connections-card.tsx`, `ui/mcp-client-guide-card.tsx`, `pages/mcp-connector-page.tsx`).
   - Navigation & Routing: Register `/mcp-connector` in `app-router.tsx` and add "Kết nối AI (MCP)" under "Cá nhân" in `nav-items.ts`.
4. **Nginx, Vite, Docker Compose & Env Config**:
   - Update `frontend/nginx.conf`, `frontend/vite.config.ts`, `docker-compose.yml`, `docker-compose.prod.yml`, `Makefile`, and `.env*.example`.

### Out of Scope
- Standalone frontend container for `eduerp-mcp`.
- Legacy `plane` module or external DX SSO integration.
- Automatic retries on write mutations (`eduerp_write` executes at most once per tool call).

---

## 3. Skills Applied & Constraints

1. **Zero Hardcoded String Literals**:
   - Frontend: All routes, API paths, query keys, and constants live in `@/shared/constants/` or module `constants.ts`.
   - Backend (`eduerp-mcp`): Every module owns its `constants.py` with static classes (`CacheNamespaces`, `ErrorCodes`, `Resources`, `Actions`, `Scopes`, `Limits`).
2. **Clean Separation of Logic from Render (Frontend)**:
   - UI components in `modules/mcp-connector/ui/*.tsx` and `pages/*.tsx` are purely declarative/presentational.
   - All state, clipboard timers, secret drafts, mutations, and event handlers live in `modules/mcp-connector/hooks/use-mcp-connector-controller.ts`.
3. **Cyclomatic Complexity < 15 & Component Size < 150–200 Lines**:
   - Every function, method, and React component stays well below cyclomatic complexity 15 and 200 lines; no nested ternaries.
4. **Unidirectional Layer Flow**:
   - Frontend: `shared -> entities -> modules -> app` (no proxy re-exports).
   - `eduerp-mcp`: Enforced by `scripts/check_backend_boundaries.py` (`shared -> infrastructure -> integrations -> runtime -> platform -> modules -> api -> core`).
5. **Live Permission Enforcement & Shared State**:
   - Every MCP tool checks the caller's live permissions from shared Redis (`perm:account:<accountId>`) / shared PostgreSQL before executing any ERP endpoint.

---

## 4. Architecture Impact

| Layer / Component | Path | Impact |
|---|---|---|
| **Database Migration** | `backend/src/main/resources/db/migration/V33__create_mcp_tables.sql` | Adds `mcp` schema & OAuth/MCP tables in shared PostgreSQL |
| **MCP Service** | `eduerp-mcp/` | New Python 3.12 FastMCP + Starlette service sharing PostgreSQL & Redis with Spring Boot |
| **Frontend Constants** | `frontend/src/shared/constants/{app-routes,api-routes}.ts` | Adds `/mcp-connector` route and `/api/mcp/my/*` endpoints |
| **Frontend Auth Redirect** | `frontend/src/app/router/redirect-when-signed-in.tsx`, `frontend/src/modules/auth/*` | Supports `?next=/oauth/authorize...` full-page redirect for OAuth 2.1 consent flow |
| **Frontend Entity** | `frontend/src/entities/mcp-connector/` | Schemas, query keys, read fetchers, and query hooks |
| **Frontend Module** | `frontend/src/modules/mcp-connector/` | Controller hook, snippet builders, and presentational cards/dialogs |
| **Gateway / Infra** | `frontend/nginx.conf`, `frontend/vite.config.ts`, `docker-compose*.yml` | Proxies `/mcp`, `/oauth/`, `/.well-known/`, `/api/mcp/` to `eduerp-mcp:8700` |

---

## 5. Numbered Steps

1. Create `backend/src/main/resources/db/migration/V33__create_mcp_tables.sql` for the `mcp` schema and tables.
2. Create `eduerp-mcp/` core foundation (`pyproject.toml`, `Dockerfile`, `main.py`, `scripts/check_backend_boundaries.py`, `app/shared/`, `app/infrastructure/`, `app/runtime/`).
3. Implement `eduerp-mcp/app/integrations/cache/` (shared Redis client + EduERP permission/blacklist keys) and `eduerp-mcp/app/integrations/eduerp/` (HTTP client to EduERP Spring Boot backend).
4. Implement `eduerp-mcp/app/platform/` (`audit`, `catalog`, `identity`, `permissions`, `sessions`, `oauth`, `personal`).
5. Implement `eduerp-mcp/app/modules/eduerp/` (endpoint registry mapped to EduERP `Resource`, `Action`, `Scope`, live RBAC authorization, and 5 FastMCP tools) + `app/api/` and `app/core/`.
6. Write unit and boundary tests in `eduerp-mcp/tests/` and verify with `check_backend_boundaries.py`, `ruff`, and `pytest`.
7. Update `frontend/src/shared/constants/`, OAuth login redirect handling, `frontend/src/entities/mcp-connector/`, `frontend/src/modules/mcp-connector/`, `app-router.tsx`, and `nav-items.ts`.
8. Update `frontend/nginx.conf`, `frontend/vite.config.ts`, `docker-compose.yml`, `docker-compose.prod.yml`, `Makefile`, and `.env*.example`.
9. Run full verification checklist (`ModularityTests`, `check_backend_boundaries.py`, `pytest`, `npm run lint`, `npm run typecheck`, `npm run build`).

---

## 6. Verification Checklist

- [ ] `backend`: `mvn test -Dtest=ModularityTests` passes.
- [ ] `eduerp-mcp`: `python scripts/check_backend_boundaries.py` passes with 0 violations.
- [ ] `eduerp-mcp`: `pytest` passes all unit/integration tests (JWT cookie auth, Redis `perm:account:<id>` & `blacklist:jti:<jti>`, OAuth 2.1 PKCE, Bearer `<client_id>.<client_secret>`, and RBAC tool authorization).
- [ ] `frontend`: `npm run lint`, `npm run typecheck`, and `npm run build` pass with 0 errors.
- [ ] Code review against skills: zero hardcoded strings, clean separation of logic from render, cyclomatic complexity < 15, unidirectional imports.
