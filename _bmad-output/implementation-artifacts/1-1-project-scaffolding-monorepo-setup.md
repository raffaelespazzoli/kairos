# Story 1.1: Project Scaffolding & Monorepo Setup

Status: done

## Story

As a developer on the portal team,
I want the project initialized as a Quarkus + Quinoa monorepo with a React/TypeScript/PatternFly 6 frontend,
So that we have a working full-stack development environment that builds and runs as a single artifact.

## Acceptance Criteria

1. **Quarkus project creation with required extensions**
   - **Given** the project does not yet exist
   - **When** the Quarkus application is created with extensions rest, rest-jackson, oidc, hibernate-orm-panache, jdbc-postgresql, quinoa, smallrye-health
   - **Then** the project compiles successfully with Maven
   - **And** the pom.xml includes all specified extensions plus rest-client-jackson and container-image-jib

2. **React SPA scaffold with PatternFly 6**
   - **Given** the Quarkus project exists
   - **When** the React SPA is scaffolded in `src/main/webui/` using Vite + React + TypeScript
   - **Then** PatternFly 6 React packages (@patternfly/react-core, @patternfly/react-table, @patternfly/react-icons, @patternfly/react-charts) and react-router-dom are installed
   - **And** `npm run build` produces output in `src/main/webui/dist/`

3. **Quinoa dev server integration**
   - **Given** Quinoa is configured in application.properties (dev-server.port=5173, build-dir=dist, enable-spa-routing=true, ui-dir=src/main/webui)
   - **When** `quarkus dev` is executed
   - **Then** both the Quarkus backend and Vite frontend dev server start with hot reload
   - **And** the SPA is accessible at the application root URL

4. **Single artifact build**
   - **Given** the project is built
   - **When** `mvn package` completes
   - **Then** a single deployable artifact (fast-jar layout) is produced that serves both the REST API and the React SPA
   - **And** `Dockerfile.jvm` exists in `src/main/docker/` for building the container image

5. **Directory structure compliance**
   - **Given** the project structure follows the architecture specification
   - **When** reviewing the directory layout
   - **Then** backend packages follow `com.portal.<domain>` convention under `src/main/java/`
   - **And** frontend source lives under `src/main/webui/src/` with directories: api/, hooks/, routes/, components/, types/
   - **And** `.gitignore` and `.editorconfig` are present with appropriate rules

6. **Dev Services PostgreSQL**
   - **Given** PostgreSQL is required for development
   - **When** `quarkus dev` starts
   - **Then** Quarkus Dev Services automatically provisions a PostgreSQL container
   - **And** the dev datasource is configured via `%dev` profile keys in `application.properties`

7. **Health endpoints**
   - **Given** the SmallRye Health extension is included
   - **When** GET `/q/health/ready` is called
   - **Then** a 200 response is returned indicating the application is ready
   - **And** GET `/q/health/live` returns 200 indicating the application is alive

## Tasks / Subtasks

- [x] Task 1: Create Quarkus project with all required extensions (AC: #1)
  - [x] Run `quarkus create app com.portal:developer-portal` with extensions: rest, rest-jackson, oidc, hibernate-orm-panache, jdbc-postgresql, quinoa, smallrye-health
  - [x] Add rest-client-jackson and container-image-jib to pom.xml
  - [x] Verify Maven compile succeeds
- [x] Task 2: Scaffold React SPA in src/main/webui/ (AC: #2)
  - [x] Run `npm create vite@latest . -- --template react-ts` inside `src/main/webui/`
  - [x] Install PatternFly 6 packages: @patternfly/react-core, @patternfly/react-table, @patternfly/react-icons, @patternfly/react-charts
  - [x] Install react-router-dom
  - [x] Import PatternFly base CSS in main.tsx: `import '@patternfly/react-core/dist/styles/base.css';`
  - [x] Verify `npm run build` produces dist/ output
- [x] Task 3: Configure Quinoa integration (AC: #3)
  - [x] Set Quinoa properties in application.properties
  - [x] Verify `quarkus dev` starts both Quarkus and Vite dev servers with HMR
  - [x] Verify SPA accessible at root URL
- [x] Task 4: Configure build for single artifact (AC: #4)
  - [x] Verify `mvn package` produces uber-jar serving REST + SPA
  - [x] Confirm Dockerfile.jvm exists in src/main/docker/
- [x] Task 5: Create project directory structure (AC: #5)
  - [x] Create backend domain packages under src/main/java/com/portal/ (placeholder packages for auth, team, cluster, application, environment, build, release, deployment, health, integration, gitops)
  - [x] Create frontend directory structure under src/main/webui/src/ (api/, hooks/, routes/, components/, components/layout/, components/shared/, types/)
  - [x] Create src/main/resources/db/migration/ for Flyway
  - [x] Create src/main/resources/casbin/ for future RBAC model/policy
  - [x] Add .gitignore with Java, Node, IDE, and build output rules
  - [x] Add .editorconfig with consistent formatting rules
- [x] Task 6: Configure Dev Services and database (AC: #6)
  - [x] Configure Dev Services PostgreSQL in application.properties (%dev profile)
  - [x] Create application-dev.properties with dev datasource config if needed
- [x] Task 7: Verify health endpoints (AC: #7)
  - [x] Start application and confirm GET /q/health/ready returns 200
  - [x] Confirm GET /q/health/live returns 200
- [x] Task 8: Create minimal placeholder App.tsx with PatternFly Page (AC: #2, #5)
  - [x] Wire up React Router with BrowserRouter
  - [x] Create placeholder App.tsx using PatternFly Page component
  - [x] Set up TypeScript strict mode in tsconfig.json

## Dev Notes

### Technical Stack — Exact Versions

| Technology | Version | Notes |
|---|---|---|
| Quarkus | 3.34.2 | Latest stable (April 2026) |
| Java | 17+ | Quarkus minimum |
| Quarkus Quinoa | 2.7.2 | Quarkiverse extension |
| React | 18.x | |
| TypeScript | 5.x | Strict mode enabled |
| Vite | 5.x | react-ts template |
| PatternFly React | 6.x (latest) | PF6 not PF5 — architecture mandates PF6 for greenfield |
| react-router-dom | v6 | Client-side routing |
| PostgreSQL | Dev Services auto | Quarkus provisions container automatically |

### Quarkus Create Command

```bash
quarkus create app com.portal:developer-portal \
  --extensions=rest,rest-jackson,oidc,hibernate-orm-panache,jdbc-postgresql,quinoa,smallrye-health
```

After creation, manually add to pom.xml:
- `quarkus-rest-client-jackson` — needed for outbound REST calls to platform tools in later stories
- `quarkus-container-image-jib` — daemonless container image builds for CI

### application.properties — Required Entries for This Story

```properties
# Quinoa SPA integration
quarkus.quinoa.dev-server.port=5173
quarkus.quinoa.build-dir=dist
quarkus.quinoa.enable-spa-routing=true
quarkus.quinoa.package-manager-install=true
quarkus.quinoa.ui-dir=src/main/webui

# OIDC placeholder (required by extension, won't authenticate yet)
quarkus.oidc.auth-server-url=http://localhost:8180/realms/portal
quarkus.oidc.client-id=developer-portal
quarkus.oidc.application-type=service

# Dev Services PostgreSQL
%dev.quarkus.datasource.devservices.enabled=true
%dev.quarkus.datasource.db-kind=postgresql

# Hibernate (drop-and-create for dev only; Flyway takes over in later stories)
%dev.quarkus.hibernate-orm.database.generation=drop-and-create

# Health
quarkus.smallrye-health.root-path=/q/health
```

### PatternFly 6 Critical Setup Notes

- Import base CSS once in `src/main/webui/src/main.tsx`:
  ```typescript
  import '@patternfly/react-core/dist/styles/base.css';
  ```
- Use PatternFly CSS custom properties (design tokens) exclusively — **no hardcoded colors, spacing, or font sizes**
- PF6 tokens use `--pf-t--global--*` prefix (not PF5's `--pf-v5-global--*`)
- Font stack: Red Hat Display, Red Hat Text, Red Hat Mono (shipped with PatternFly CSS)
- WCAG 2.1 AA out of the box — do not override PF accessibility behaviors

### Project Directory Structure — Complete

```
developer-portal/
├── pom.xml
├── .gitignore
├── .editorconfig
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/portal/
│   │   │   ├── PortalApplication.java          # (optional, Quarkus may not need it)
│   │   │   ├── auth/                            # placeholder
│   │   │   ├── team/                            # placeholder
│   │   │   ├── cluster/                         # placeholder
│   │   │   ├── application/                     # placeholder
│   │   │   ├── environment/                     # placeholder
│   │   │   ├── build/                           # placeholder
│   │   │   ├── release/                         # placeholder
│   │   │   ├── deployment/                      # placeholder
│   │   │   ├── health/                          # placeholder
│   │   │   ├── integration/                     # placeholder (subpackages: git, vault, argocd, tekton, registry, grafana, devspaces)
│   │   │   └── gitops/                          # placeholder
│   │   ├── resources/
│   │   │   ├── application.properties
│   │   │   ├── application-dev.properties       # if separate dev config needed
│   │   │   ├── db/migration/                    # empty, Flyway migrations added in later stories
│   │   │   └── casbin/                          # empty, model.conf + policy.csv in Story 1.3
│   │   ├── docker/
│   │   │   ├── Dockerfile.jvm
│   │   │   └── Dockerfile.native                # optional, provided by Quarkus
│   │   └── webui/
│   │       ├── package.json
│   │       ├── vite.config.ts
│   │       ├── tsconfig.json
│   │       ├── index.html
│   │       └── src/
│   │           ├── main.tsx
│   │           ├── App.tsx
│   │           ├── api/                         # empty, apiFetch() in Story 1.5
│   │           ├── hooks/                       # empty
│   │           ├── routes/                      # empty
│   │           ├── components/
│   │           │   ├── layout/                  # placeholder for AppShell in Story 1.4
│   │           │   └── shared/                  # placeholder for ErrorAlert, LoadingSpinner in Story 1.5
│   │           └── types/                       # empty
│   └── test/
│       └── java/com/portal/                     # test packages mirror main
├── src/main/docker/                             # Quarkus-generated Dockerfiles
```

### Naming Conventions

| Layer | Convention | Examples |
|---|---|---|
| Java packages | `com.portal.<domain>` | `com.portal.auth`, `com.portal.team` |
| Java entities | PascalCase singular | `Team`, `Application`, `Cluster` |
| Java resources | `<Entity>Resource` | `TeamResource` |
| Java services | `<Domain>Service` | `TeamService` |
| Java adapters | `<System>Adapter` | `ArgocdAdapter` |
| Java DTOs | `<Entity><Purpose>Dto` | `ApplicationCreateDto` |
| REST paths | `/api/v1/` prefix, plural, kebab-case | `/api/v1/teams`, `/api/v1/admin/clusters` |
| TS components | PascalCase | `AppShell.tsx`, `ErrorAlert.tsx` |
| TS hooks | `use*` camelCase | `useAuth.ts`, `useTeamContext.ts` |
| TS API functions | camelCase verb prefix | `fetchTeams()`, `createApplication()` |
| TS route pages | `*Page.tsx` | `TeamDashboardPage.tsx` |
| DB tables | snake_case plural | `teams`, `applications`, `clusters` |
| DB columns | snake_case | `oidc_group_id`, `created_at` |
| DB indexes | `idx_*` | `idx_applications_team_id` |
| Flyway migrations | `V<N>__<description>.sql` | `V1__create_clusters.sql` |

### Frontend — Minimal App.tsx Scaffold

The App.tsx for this story should be minimal but use PatternFly:

```typescript
import { Page } from '@patternfly/react-core';
import { BrowserRouter } from 'react-router-dom';

function App() {
  return (
    <BrowserRouter>
      <Page>
        {/* Shell layout (masthead, sidebar, breadcrumbs) added in Story 1.4 */}
        <div>Developer Portal — scaffold complete</div>
      </Page>
    </BrowserRouter>
  );
}

export default App;
```

### Docker — JVM Mode

Quarkus generates `Dockerfile.jvm` in `src/main/docker/`. Use the generated file as-is. Key points:
- JVM mode (not native) for production — native deferred post-MVP
- Container includes the uber-jar with bundled SPA assets
- Health probes will target `/q/health/ready` and `/q/health/live`

### Deployment Target

- OpenShift (on-premises, disconnected-environment capable)
- No external cloud dependencies
- No CDN for assets — everything bundled in the container image
- Browser targets: Chrome, Firefox, Edge (latest 2 versions) — no Safari, no mobile

### Testing Framework (Establish Conventions)

| Layer | Framework | File Pattern | Location |
|---|---|---|---|
| Backend unit | JUnit 5 | `<Class>Test.java` | `src/test/java/com/portal/<domain>/` |
| Backend integration | @QuarkusTest + REST Assured | `<Class>IT.java` | `src/test/java/com/portal/<domain>/` |
| Frontend | Vitest + React Testing Library | `<Component>.test.tsx` | co-located with component |

For this story: verify `mvn test` runs (even if no custom tests yet) and Quarkus continuous testing works in dev mode.

### What NOT to Build in This Story

- No OIDC authentication flow (Story 1.2)
- No Casbin RBAC (Story 1.3)
- No AppShell/sidebar/breadcrumbs/tabs (Story 1.4)
- No apiFetch() or error handling (Story 1.5)
- No Vault integration (Story 1.6)
- No admin cluster registration (Story 1.7)
- No Flyway migration SQL files (Story 1.2+ introduce entities)
- No actual REST endpoints beyond health checks

### Project Structure Notes

- The `src/main/webui/` directory is the Quinoa-managed React SPA root — Quarkus builds it as part of `mvn package`
- Backend packages are domain-centric per AR19 — each domain gets its own package with entity, resource, service, DTOs, mapper
- Flyway directory `db/migration/` created empty — first migration (`V1__create_clusters.sql`) arrives in Story 1.7, `V2__create_teams.sql` in Story 1.2
- Casbin directory created empty — `model.conf` and `policy.csv` arrive in Story 1.3
- The placeholder packages should contain a `.gitkeep` or package-info.java to survive git

### References

- [Source: planning-artifacts/architecture.md] — Complete project directory structure, technology stack, naming conventions, Quinoa configuration, extension rationale
- [Source: planning-artifacts/prd.md] — NFR performance targets (page < 5s, SPA < 500ms), WCAG 2.1 AA, on-premises deployment, no cloud dependencies
- [Source: planning-artifacts/ux-design-specification.md] — PatternFly design tokens, accessibility requirements, status vocabulary, responsive breakpoints, loading patterns
- [Source: planning-artifacts/epics.md § Epic 1 / Story 1.1] — Acceptance criteria, story statement

## Dev Agent Record

### Agent Model Used
claude-4.6-opus-high-thinking

### Debug Log References
- Quinoa build initially failed due to missing `node-version` property when `package-manager-install=true` — resolved by adding `quarkus.quinoa.package-manager-install.node-version=22.22.0`
- OIDC connection warning expected at startup since no Keycloak is running (Story 1.2 scope)
- React 18.x, Vite 5.x, and react-router-dom v6 explicitly pinned (create-vite@latest installs React 19, Vite 8, and router v7 by default)

### Completion Notes List
- ✅ Quarkus 3.34.2 project created with all 7 core extensions plus rest-client-jackson and container-image-jib
- ✅ React 18.3.1 SPA scaffolded with Vite 5.4.21, TypeScript 5.9.3 (strict mode), PatternFly 6.4.1, react-router-dom 6.30.3
- ✅ Quinoa configured: dev-server port 5173, build-dir=dist, SPA routing enabled, package-manager-install with Node 22.22.0
- ✅ `mvn package` produces fast-jar layout at target/quarkus-app/ bundling both REST API and SPA (Dockerfile.jvm wired for layered Docker builds)
- ✅ Dockerfile.jvm present in src/main/docker/ (Quarkus-generated)
- ✅ Backend domain packages (12 domains + 7 integration subpackages) with package-info.java, mirrored in test
- ✅ Frontend directories: api/, hooks/, routes/, components/layout/, components/shared/, types/
- ✅ Flyway migration dir and Casbin dir created empty with .gitkeep
- ✅ .gitignore covers Java, Node, IDE, Quarkus, Quinoa cache, OS files
- ✅ .editorconfig with 4-space Java, 2-space TS/JSON/YAML, LF line endings
- ✅ Dev Services PostgreSQL configured for %dev profile with drop-and-create schema generation
- ✅ Health endpoints verified: GET /q/health/ready → 200 UP, GET /q/health/live → 200 UP
- ✅ Minimal App.tsx with PatternFly Page + BrowserRouter, PatternFly base CSS imported in main.tsx
- ✅ `mvn test` passes (no custom tests yet, as expected for scaffolding story)
- ✅ Maven wrapper (mvnw) installed for reproducible builds

### Change Log
- 2026-04-03: Story 1.1 implemented — full project scaffolding complete
- 2026-04-04: Code review — AC4 updated from uber-jar to fast-jar (Quarkus default, better Docker layering). AC6 updated to use %dev profile keys in single application.properties (Quarkus-idiomatic over separate application-dev.properties). README replaced with project-specific content.

### File List
- developer-portal/pom.xml (new)
- developer-portal/.gitignore (new)
- developer-portal/.editorconfig (new)
- developer-portal/.dockerignore (new)
- developer-portal/mvnw (new)
- developer-portal/mvnw.cmd (new)
- developer-portal/README.md (new)
- developer-portal/src/main/resources/application.properties (new)
- developer-portal/src/main/resources/db/migration/.gitkeep (new)
- developer-portal/src/main/resources/casbin/.gitkeep (new)
- developer-portal/src/main/docker/Dockerfile.jvm (new)
- developer-portal/src/main/docker/Dockerfile.legacy-jar (new)
- developer-portal/src/main/docker/Dockerfile.native (new)
- developer-portal/src/main/docker/Dockerfile.native-micro (new)
- developer-portal/src/main/java/com/portal/auth/package-info.java (new)
- developer-portal/src/main/java/com/portal/team/package-info.java (new)
- developer-portal/src/main/java/com/portal/cluster/package-info.java (new)
- developer-portal/src/main/java/com/portal/application/package-info.java (new)
- developer-portal/src/main/java/com/portal/environment/package-info.java (new)
- developer-portal/src/main/java/com/portal/build/package-info.java (new)
- developer-portal/src/main/java/com/portal/release/package-info.java (new)
- developer-portal/src/main/java/com/portal/deployment/package-info.java (new)
- developer-portal/src/main/java/com/portal/health/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/git/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/vault/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/argocd/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/tekton/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/registry/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/grafana/package-info.java (new)
- developer-portal/src/main/java/com/portal/integration/devspaces/package-info.java (new)
- developer-portal/src/main/java/com/portal/gitops/package-info.java (new)
- developer-portal/src/test/java/com/portal/ (mirrored test packages, new)
- developer-portal/src/main/webui/package.json (new)
- developer-portal/src/main/webui/package-lock.json (new)
- developer-portal/src/main/webui/vite.config.ts (new)
- developer-portal/src/main/webui/tsconfig.json (new)
- developer-portal/src/main/webui/tsconfig.app.json (new)
- developer-portal/src/main/webui/tsconfig.node.json (new)
- developer-portal/src/main/webui/index.html (new)
- developer-portal/src/main/webui/eslint.config.js (new)
- developer-portal/src/main/webui/src/main.tsx (new)
- developer-portal/src/main/webui/src/App.tsx (new)
- developer-portal/src/main/webui/src/vite-env.d.ts (new)
- developer-portal/src/main/webui/src/api/.gitkeep (new)
- developer-portal/src/main/webui/src/hooks/.gitkeep (new)
- developer-portal/src/main/webui/src/routes/.gitkeep (new)
- developer-portal/src/main/webui/src/components/layout/.gitkeep (new)
- developer-portal/src/main/webui/src/components/shared/.gitkeep (new)
- developer-portal/src/main/webui/src/types/.gitkeep (new)
