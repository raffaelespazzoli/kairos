---
project_name: 'bmad'
user_name: 'Raffa'
date: '2026-04-03'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'code_quality_style', 'workflow_rules', 'critical_rules']
status: 'complete'
rule_count: 95
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

**Core Technologies:**

| Technology | Version | Notes |
|---|---|---|
| Quarkus | 3.34.x | Backend framework — use latest 3.34 patch |
| Java | 17+ | Quarkus minimum; prefer 17 for compilation target |
| PatternFly React | 6.x (v6.4.1) | **NOT PatternFly 5** — PF6 is the active line. UX spec references PF5 patterns; all carry over to PF6 with minor API renames |
| React | 18.x | Stable; do NOT use React 19 |
| TypeScript | 5.x | Strict mode enabled |
| Vite | 5.x | Frontend build; used by PatternFly itself |
| Quarkus Quinoa | 2.7.x | Serves React SPA from within Quarkus — single deployable artifact |
| PostgreSQL | Latest stable | Via `jdbc-postgresql` extension |
| Flyway | (Quarkus-managed) | Versioned SQL migrations in `src/main/resources/db/migration/` |
| jCasbin | v1.99.0 | RBAC authorization — static policy file, not database-driven for MVP |
| Qute | (bundled with Quarkus) | Template engine for GitOps YAML generation |

**Quarkus Extensions (Core):**
`rest`, `rest-jackson`, `oidc`, `hibernate-orm-panache`, `jdbc-postgresql`, `quinoa`, `smallrye-health`

**Quarkus Extensions (Added During Development):**
`rest-client-jackson` (Phase 1), `oidc-client` (Phase 1), `kubernetes-client` (Phase 3), `scheduler` (Phase 4), `container-image-jib` (Phase 1)

**Frontend Dependencies:**
`@patternfly/react-core`, `@patternfly/react-table`, `@patternfly/react-icons`, `@patternfly/react-charts`, `react-router-dom` (v6)

**Testing Stack:**

| Layer | Framework | Runner |
|---|---|---|
| Backend unit | JUnit 5 | Maven Surefire |
| Backend integration | `@QuarkusTest` + REST Assured | Maven Failsafe |
| Frontend components | Vitest + React Testing Library | Vitest |

**Version Constraints:**
- PatternFly 6 is mandatory — the UX design spec references PF5 component names but this is a greenfield project; use PF6 equivalents
- JVM mode only for container images — native mode (GraalVM) is deferred post-MVP
- No Redux or external state management — React Context + hooks only
- No Tailwind, no CSS modules — PatternFly CSS custom properties (design tokens) exclusively

## Critical Implementation Rules

### Language-Specific Rules

**Java (Quarkus Backend):**

- Use Quarkus REST (formerly RESTEasy Reactive) annotation style — `@Path`, `@GET`, `@POST`, etc. Do NOT use Spring-style annotations
- CDI injection via `@Inject` or constructor injection — Quarkus uses ArC (build-time CDI), not Spring DI
- All CDI beans that are integration adapters must be `@ApplicationScoped` — not `@RequestScoped` or `@Dependent`
- Panache Active Record pattern for entities — extend `PanacheEntityBase` (NOT `PanacheEntity`), use `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` explicitly. `PanacheEntity` defaults to SEQUENCE strategy which requires a `<table>_seq` sequence object; our PostgreSQL schema uses `BIGSERIAL` (identity columns). Use static finder methods (e.g., `Application.find("team.id", teamId).list()`)
- Entity fields use `camelCase` in Java; Hibernate maps to `snake_case` DB columns automatically — do NOT add `@Column(name=...)` for simple mappings
- Use `@ConfigProperty` for portal-specific configuration with `portal.*` prefix (e.g., `portal.oidc.role-claim`, `portal.git.provider`)
- Quarkus config profiles: `application.properties` (shared), `application-dev.properties` (dev), `application-test.properties` (test) — environment variables override in production
- All REST resource methods must return DTOs, never Panache entities — entities are internal to their domain package
- `CompletableFuture` or Mutiny reactive types for parallel integration calls — views aggregating multiple platform systems must call adapters concurrently, not sequentially. When using `CompletableFuture::join`, always unwrap `CompletionException` to extract the real cause — `join()` wraps checked exceptions in `CompletionException`
- Global `ExceptionMapper` converts `PortalIntegrationException` → standardized error JSON with `502` status
- Flyway migration files named `V<number>__<description>.sql` — sequential, never modify existing migrations

**TypeScript (React Frontend):**

- TypeScript strict mode is mandatory — no `any` types, no `@ts-ignore` unless documented with justification
- All API response types must be defined in `src/types/` — one file per domain (e.g., `application.ts`, `build.ts`, `environment.ts`)
- Use `fetch` API via the shared `apiFetch()` wrapper in `src/api/client.ts` — never call `fetch` directly; the wrapper adds OIDC bearer token and handles error parsing
- All API calls use relative URLs (e.g., `/api/v1/teams/...`) — Quinoa proxies to Quarkus; never hardcode absolute URLs
- Custom hooks return `{ data, error, isLoading }` tuple — every data-fetching hook follows this pattern
- No automatic polling or client-side caching between navigations — each view fetches fresh data on mount
- React Router v6 for client-side routing — use `<Outlet>` for nested layouts, `useParams()` for path params
- No barrel files (`index.ts` re-exports) — import directly from the source file
- All `useEffect` side-effects must handle cleanup: use `AbortController` for fetch calls, clear timeouts/intervals, and guard against state updates on unmounted components. Always verify the effect's dependencies are exhaustive — stale closures over state cause subtle bugs in multi-step flows (wizards, progress trackers)

### Framework-Specific Rules

**Quarkus Backend:**

- Domain-centric package organization under `com.portal.<domain>` — each domain package contains its own Resource, Service, Entity, DTOs, and Mapper
- No cross-package entity imports — reference other domains by ID only; use a service to cross domain boundaries
- REST resource → Service → Adapter call chain — resources never call adapters directly
- Two-layer auth enforced via JAX-RS `ContainerRequestFilter` pipeline:
  1. `TeamContextFilter` (`@Priority(AUTHENTICATION + 10)`) — extracts team + role from configurable JWT claims → populates `TeamContext` CDI bean
  2. `PermissionFilter` (`@Priority(AUTHORIZATION)`) — reads role from `TeamContext` → Casbin check → 403 if denied
- `TeamContext` is a `@RequestScoped` CDI bean injected into every service — all data access and integration calls scoped by team
- JWT claim names are configurable: `portal.oidc.role-claim` (default: `role`), `portal.oidc.team-claim` (default: `team`)
- Casbin policy is a static file (`src/main/resources/casbin/model.conf` + `policy.csv`) — three roles with inheritance: `member` → `lead` → `admin`
- All integration adapters throw `PortalIntegrationException` with: system name, operation, message, and optional deep link — never throw raw HTTP/connection exceptions from adapters
- `SecretManagerCredentialProvider` wraps `SecretManagerAdapter` with TTL-aware in-memory cache keyed by `(cluster, role)` — credentials never written to database or disk
- Qute templates in `src/main/resources/templates/gitops/` for Namespace and ArgoCD Application YAML generation — template files must use plain `.yaml` extension (NOT `.qute.yaml`); the `.qute.*` suffix prevents Qute engine resolution at runtime despite being recommended by some docs for IDE syntax highlighting
- SmallRye Health endpoints at `/q/health/ready` and `/q/health/live` for OpenShift probes
- Quarkus Dev UI at `/q/dev` — available in dev mode only

**React + PatternFly 6 Frontend:**

- Use PatternFly 6 components exclusively — no custom HTML/CSS for elements PF provides (Page, Table, Card, Wizard, Tabs, Breadcrumb, Alert, Label, EmptyState, etc.)
- All styling via PatternFly CSS custom properties (design tokens) — no hardcoded color values, no inline styles, no custom CSS classes
- Frontend lives in `src/main/webui/` — Quinoa builds it as part of the Quarkus Maven build
- App shell uses PF6 `Page` with `Masthead` and side navigation (`Nav` with `NavItem`)
- Loading states: every data-fetching component shows PF6 `Spinner` or `Skeleton` while loading — no global loading overlay
- Errors displayed inline using PF6 `Alert` component — no modal error dialogs
- Environment chain is the signature visual element — horizontal pipeline showing release position, health, version, and deployment status per environment
- Onboarding uses PF6 `Wizard` component — multi-step flow with contract validation, plan preview, and confirmation
- Frontend never calls platform systems directly — all data through `/api/v1/` REST endpoints only
- Deep links to native tools (Tekton, ArgoCD, Grafana, Vault) rendered as PF6 `Button` with `component="a"` and `target="_blank"`

### Testing Rules

**Backend Testing (Java):**

- Unit tests: `<Class>Test.java` in matching package under `src/test/java/com/portal/<domain>/`
- Integration tests: `<Class>IT.java` — use `@QuarkusTest` annotation; these start the full Quarkus runtime
- REST endpoint tests use REST Assured fluent API: `given().when().get("/api/v1/...").then().statusCode(200)`
- Mock platform integrations in tests — never call real ArgoCD, Tekton, Vault, etc. from tests
- Use `@InjectMock` (Quarkus CDI mock) for adapter mocking in integration tests — not Mockito standalone. `@InjectMock` works even for beans produced by CDI factory methods (e.g., `GitProvider` from `GitProviderFactory`) — it replaces the CDI bean regardless of how it was produced
- Never use `assertInstanceOf` or `instanceof` checks on `@ApplicationScoped` CDI beans in tests — Quarkus wraps them in `ClientProxy` subclasses. Verify behavior instead of concrete type
- Test Casbin authorization separately: verify each role's allowed/denied operations against the policy file
- Test `TeamContext` isolation: verify that queries scoped to one team cannot return data from another team
- Test that cross-team resource access returns `404` (not `403`) — this is a security requirement

**Frontend Testing (TypeScript):**

- Test files co-located with components: `<Component>.test.tsx` next to `<Component>.tsx`
- Use Vitest as runner, React Testing Library for component rendering
- Test user interactions, not implementation details — query by role, label, and text, not by CSS class or test ID
- Mock API responses at the `apiFetch()` level — not at the browser fetch level
- Test loading, success, and error states for every data-fetching component
- Test that PF6 `Alert` components appear for error states with the correct variant (`danger` for errors, `warning` for degraded)
- Account for React 18 StrictMode in all effect-driven logic — StrictMode double-invokes `useEffect` mount callbacks in development. Side-effects (API calls, subscriptions, timers) must be idempotent or use cleanup functions / AbortController guards to prevent duplicate work and stale state

**Testing Boundaries:**

- Unit tests: single class or function in isolation, dependencies mocked
- Integration tests (`@QuarkusTest`): full request lifecycle through REST endpoint → service → adapter (with mocked external calls)
- Frontend component tests: component renders correctly with given props/state, user interactions trigger expected behavior
- No end-to-end browser tests for MVP

### Code Quality & Style Rules

**Naming Conventions:**

| Element | Convention | Example |
|---|---|---|
| DB tables | `snake_case`, plural | `applications`, `environments`, `clusters` |
| DB columns | `snake_case` | `git_repo_url`, `api_server_url`, `promotion_order` |
| DB primary keys | `id` (bigint, auto-generated) | `id` |
| DB foreign keys | `<table_singular>_id` | `team_id`, `cluster_id` |
| DB indexes | `idx_<table>_<columns>` | `idx_applications_team_id` |
| DB unique constraints | `uq_<table>_<columns>` | `uq_clusters_name` |
| DB timestamps | `created_at`, `updated_at` | Always UTC, `TIMESTAMPTZ` |
| Java packages | `com.portal.<domain>` | `com.portal.application`, `com.portal.integration.argocd` |
| Java entities | PascalCase, singular | `Application`, `Environment`, `Cluster` |
| Java REST resources | `<Entity>Resource` | `ApplicationResource`, `ClusterResource` |
| Java adapters | `<System>Adapter` | `ArgoCdAdapter`, `TektonAdapter` |
| Java services | `<Domain>Service` | `OnboardingService`, `DeploymentService` |
| Java DTOs | `<Entity><Purpose>Dto` | `ApplicationSummaryDto`, `EnvironmentStatusDto` |
| Java exceptions | `Portal<Context>Exception` | `PortalIntegrationException`, `PortalAuthorizationException` |
| TS components | PascalCase file and export | `EnvironmentChain.tsx`, `DoraStatCard.tsx` |
| TS hooks | `camelCase`, `use` prefix | `useApplications.ts`, `useTeamContext.ts` |
| TS API functions | `camelCase`, verb prefix | `fetchApplications()`, `triggerBuild()` |
| TS types/interfaces | PascalCase | `Application`, `EnvironmentStatus`, `BuildSummary` |
| TS route components | PascalCase + `Page` suffix | `ApplicationOverviewPage.tsx`, `TeamDashboardPage.tsx` |
| TS directories | `kebab-case` | `components/`, `api/`, `hooks/`, `types/` |
| REST endpoints | Plural nouns, `kebab-case` | `/applications`, `/environments` |
| REST path params | `camelCase` | `{teamId}`, `{appId}` |
| REST JSON fields | `camelCase` | `gitRepoUrl`, `apiServerUrl`, `promotionOrder` |
| REST actions | `POST` on sub-resource | `POST /applications/{appId}/onboard` |

**API Response & Error Format:**

- Success responses return the resource directly — no wrapper objects
- Collections return arrays
- Dates: ISO 8601 UTC strings everywhere (`2026-04-02T14:30:00Z`) — frontend formats to local time
- Error response structure: `{ error, message, detail, system, deepLink, timestamp }`
- HTTP status codes: `200` GET/PUT, `201` POST creates, `204` DELETE, `400` validation, `401` missing/invalid JWT, `403` Casbin deny, `404` not found OR cross-team access, `502` integration failure, `503` portal unhealthy

**Documentation:**

- JSDoc/Javadoc on all public interfaces and adapter methods — document the contract, not the implementation
- No redundant comments that narrate what code does — comments explain _why_, not _what_
- README at project root with setup, development, and build instructions

### Development Workflow Rules

**Project Structure:**

- Monorepo: Quarkus project root with React SPA at `src/main/webui/`
- `quarkus dev` runs both backend and frontend together with hot reload — Quinoa proxies frontend dev server on port 5173
- Maven is the build system — `./mvnw` wrapper for reproducible builds
- Quinoa handles `npm install` and `npm run build` automatically during Maven build — do not run npm separately in CI

**Quinoa Configuration (`application.properties`):**

```
quarkus.quinoa.dev-server.port=5173
quarkus.quinoa.build-dir=dist
quarkus.quinoa.enable-spa-routing=true
quarkus.quinoa.package-manager-install=true
quarkus.quinoa.ui-dir=src/main/webui
```

**Story Completion Gate:**

- Before a story can be marked `done` in `sprint-status.yaml`, its story file in `implementation-artifacts/` MUST be fully updated: `Status: done`, all task checkboxes checked, Dev Agent Record populated (Agent Model Used, Debug Log References, Completion Notes List, File List). This is a prerequisite gate — the next story MUST NOT start until the previous story file is complete
- If the dev agent finishes code but forgets to update the story file, the SM or next agent must reject the `done` transition until the documentation is current

**Database Migrations:**

- Flyway migrations in `src/main/resources/db/migration/`
- File naming: `V1__create_clusters.sql`, `V2__create_teams.sql`, etc. — sequential, double underscore after version
- Never modify an existing migration — always create a new versioned file
- Migrations run automatically on Quarkus startup

**Configuration Management:**

- All environment-specific values via environment variables on OpenShift — never commit secrets or environment-specific URLs
- Portal-specific config keys use `portal.*` prefix
- Quarkus standard config keys for OIDC, datasource, etc.
- Dev profile (`application-dev.properties`) provides sane defaults for local development

**Container & Deployment:**

- JVM mode Dockerfile at `src/main/docker/Dockerfile.jvm` — no native mode for MVP
- Single container image: Quarkus serves both API and SPA
- OpenShift deployment with `Deployment`, `Service`, `Route`, health probes, `ConfigMap` for non-sensitive config
- `ServiceAccount` configured for Vault Kubernetes auth — no `Secret` objects for credentials
- Structured JSON logging in production (Quarkus JBoss Logging)
- Every integration call logged with: target system, operation, duration, success/failure

### Critical Don't-Miss Rules

**Anti-Patterns — NEVER Do These:**

| Anti-Pattern | Correct Pattern |
|---|---|
| Entity in one package importing entity from another | Reference by ID; use a service to cross boundaries |
| REST resource calling an adapter directly | REST resource → Service → Adapter |
| Frontend calling `/api/v1/...` with hardcoded absolute URL | Use `apiFetch()` wrapper with relative URL |
| Returning `403` for cross-team resource access | Return `404` — never reveal that a resource exists in another team |
| Custom CSS for something PatternFly provides | Use the PatternFly component |
| Catching `Exception` broadly in adapters | Catch specific exceptions, wrap in `PortalIntegrationException` |
| Storing Vault credentials in database or on disk | In-memory TTL cache only via `SecretManagerCredentialProvider` |
| Using `camelCase` in database columns | Use `snake_case` — Panache handles the mapping |
| Frontend calling platform systems (ArgoCD, Tekton, etc.) directly | All data through backend REST API only |
| Caching platform tool state beyond the current request | Every API response reflects live state — no stale data |
| Using Spring annotations (`@RestController`, `@Autowired`, `@Service`) | Use Quarkus/JAX-RS/CDI annotations (`@Path`, `@Inject`, `@ApplicationScoped`) |
| Creating portal-specific user accounts or passwords | All authentication via OIDC only |
| Displaying infrastructure terms to developers (namespace, pod, CRD, GitOps) | Use developer domain language: application, build, release, deployment, environment |

**Security Rules:**

- Production deployment authorization (`lead` role) enforced server-side via Casbin — frontend disables buttons but backend rejects independently
- `404` returned for both genuinely missing resources AND resources outside user's team scope — never reveal cross-team resource existence
- No portal-specific credentials — OIDC tokens only
- No long-lived credentials — Vault-issued creds cached within TTL only
- All portal-to-platform-tool communication over TLS
- Portal authenticates to Vault via Kubernetes auth (pod service account), not AppRole or token

**Resource Ownership Validation — Mandatory Scoping Pattern:**

The portal enforces authorization at two layers. Layer 1 (Casbin via `PermissionFilter`) is automatic and handles role-based access. Layer 2 (resource ownership scoping) is manual and historically error-prone — Epic 4 retrospective identified this as a recurring gap caught only during code reviews.

_Rule: Every service method that receives entity IDs from URL path parameters MUST validate the full ownership chain before executing any business logic or adapter calls. Omission of ownership validation is a review rejection._

**Ownership relationships that must be validated:**

| URL Pattern | Ownership Chain | Validation Required |
|---|---|---|
| `/teams/{teamId}/applications/{appId}/...` | App belongs to team | `app.teamId == teamId`, else 404 |
| `/teams/{teamId}/applications/{appId}/builds/{buildId}` | Build belongs to app | `build.applicationName == app.name`, else 404 |
| `/teams/{teamId}/applications/{appId}/releases/...` | Release belongs to app | App resolved via team scoping; release fetched from app's Git repo |
| `/teams/{teamId}/applications/{appId}/deployments/...` | Deployment belongs to app+env | App resolved via team; environment belongs to app |
| `/teams/{teamId}/applications/{appId}/environments/{envId}` | Environment belongs to app | `env.applicationId == appId`, else 404 |

**Implementation pattern — `require*` methods:**

Services should use declaratively-named `require*` helper methods that throw `NotFoundException` on ownership violation. The naming convention makes omission visible during review:

```java
Application requireTeamApplication(Long teamId, Long appId)    // 404 if app missing or wrong team
Cluster requireBuildCluster(Application app)                     // IllegalStateException if no build config
void requireBuildBelongsToApp(BuildDetailDto build, String appName)  // 404 if build is for different app
```

When a code reviewer sees a service method that calls an adapter without a preceding `require*` call for every entity ID in the URL path, the omission is a defect.

**Story acceptance criteria template — include in every endpoint story:**

Every story that introduces or modifies a REST endpoint MUST include these negative-path acceptance criteria:
- Given a request targets a resource outside the caller's team scope, Then 404 is returned (never 403)
- Given a request targets a child resource that does not belong to the parent resource in the URL path, Then 404 is returned
- Given a request targets a non-existent resource, Then 404 is returned

**Domain Language Translation — The Abstraction Layer:**

The portal is a developer abstraction layer. All user-facing content (API responses, UI labels, error messages, logs shown to users) must speak in the developer domain model:

| Infrastructure Concept | Developer-Facing Term |
|---|---|
| Namespace | Environment |
| PipelineRun | Build |
| Git tag + container image | Release |
| ArgoCD sync | Deployment |
| Argo Rollout | Deployment (progressive) |
| Vault path | Secrets |
| Kubernetes cluster | (hidden — user sees environments, not clusters) |

**Data Model — Portal Is Not the Source of Truth:**

- The portal persists only: `Cluster`, `Team`, `Application`, `Environment` (4 entities)
- Everything else is fetched live from platform systems on every request
- Build status → Tekton API; deployment status → ArgoCD API; health → Prometheus API; releases → Git tags + Registry
- Deployment history → Git commit history on `values-run-<env>.yaml` files (not a database table)
- No background sync jobs for MVP — all data is request-scoped

**Deployment Mechanism — Git-Based (Design Decision 2026-04-09):**

- Deployment = Git commit to app repo's `.helm/run/values-run-<env>.yaml` updating `image.tag`
- The portal NEVER calls ArgoCD write APIs (no PATCH, no sync) — ArgoCD auto-sync detects Git drift and reconciles
- All portal write operations flow through Git: onboarding (PR to infra repo), releases (Git tags), deployments (commit to values file)
- Deployment history = `GitProvider.listCommits()` on the values file — Git is the deployment ledger
- "Who deployed" = commit author / `Deployed-By:` trailer in commit message
- Commit message convention: `deploy: <version> to <env>\n\nDeployed-By: <user>`
- Live deployment status still read from ArgoCD via `getEnvironmentStatuses` (read-only)
- Production gating enforced by Casbin server-side (Story 5.4) — the Git commit mechanism is the same for all environments

**Observability Integration — PromQL as Configuration (Design Decision 2026-04-11):**

- Prometheus queries are externalized as configuration under `portal.prometheus.queries.*` — the PrometheusAdapter is a generic query executor, not a PromQL author
- Query templates use `{namespace}` and `{interval}` placeholders that the adapter substitutes at runtime (e.g., `portal.prometheus.queries.latency-p95=histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{namespace="{namespace}"}[{interval}]))`)
- This allows platform teams to tune queries for their specific metric names and labels without code changes
- The adapter's responsibility: HTTP call to Prometheus API, parameter substitution, response parsing into portal DTOs
- The adapter does NOT need to understand PromQL semantics — it executes configured queries and returns numeric results
- DORA metric queries follow the same pattern — externalized as configuration, parameterized by application and time range
- **DORA Query Optimization (Design Decision 2026-04-12):** Each DORA metric needs a current value, a previous-period value (for trend), and a time series (for charts). The current value is extracted from the range query's last data point — do NOT make a separate instant query for it. The previous-period value uses Prometheus `offset` modifier via an instant query — do NOT attempt to derive it by extending the range window to 2×, because partial data at boundaries produces skewed trend comparisons. The offset instant query and range query run in parallel via `CompletableFuture` (they are independent). Result: 2 parallel calls per metric instead of 3 sequential

**GitOps Contract — Critical for Onboarding and Deployment:**

- App repo must have `.helm/build/` (Helm chart) + `.helm/run/` (Helm chart) + `values-build.yaml` + at least one `values-run-<env>.yaml`
- `values-run-<env>.yaml` must contain `image.tag` field under the `image` section — this is the field the portal updates during deployment
- Infra repo structure: `/<cluster>/<namespace>/namespace.yaml` + `argocd-application.yaml`
- Onboarding creates a PR to the infra repo — NOT a direct commit; onboarding ends at PR creation
- Multiple environments can share a cluster (e.g., dev and build on `ocp-dev-01`)
- Per onboarded app: 1 ArgoCD Application for build + N ArgoCD Applications for environments
- ArgoCD auto-sync must be enabled on run Applications for deployment to work without manual sync triggers

**Interactive Control Rendering — Conditional Action Buttons and Menus:**

When rendering action controls (buttons, dropdowns, menus) whose visibility or behavior depends on multiple state dimensions (entity status, user role, loading state, data availability), follow these rules to prevent edge-case UX failures:

1. **Guard on all required inputs before rendering** — if an action control needs data to function (e.g., environmentId, releaseVersion, role), verify every required value is defined before rendering the control. Never render an interactive element that will silently no-op on click due to missing data
2. **Preserve control presence during loading** — when an action is in progress (API call, deploy, promote), disable the control and show a Spinner inline. Never unmount/hide the control during its own loading state — the user loses visual continuity and cannot tell what is happening
3. **Match control labels to the actual action** — if the same underlying API serves multiple UX actions (e.g., deploy vs promote both call POST /deployments), ensure the button label, confirmation dialog text, and loading message all reflect the user-facing action, not the API operation
4. **Surface upstream data failures inline** — if a parent data fetch fails (e.g., releases list fails to load), show an inline Alert explaining why dependent actions are unavailable. Never silently hide action controls when the reason is a fetch error — the user should understand *why* they cannot act
5. **Document the state matrix** — for components with 3+ state dimensions controlling action visibility, include a comment-level truth table (status x role x data availability → visible controls) so reviewers can verify completeness without reverse-engineering the JSX conditionals
6. **Test every guard rule** — for each guard condition that hides or disables a control, a corresponding frontend test must assert the control is absent/disabled under that condition. Guards without tests are invisible regressions waiting to happen

**Section-Level Error Handling — Partial Data with Inline Alerts:**

When a page or section fetches data and the response includes both available data and a section-level error indicator (e.g., `healthError`, `doraError`, `activityError`), follow these rules to prevent content replacement failures:

1. **Render available data alongside the error** — when partial data is available, render it normally AND show an inline `Alert` (warning variant) explaining the degraded section. Never replace the entire section's content with an error-only view
2. **Independent section loading states** — each section (e.g., DORA cards, health grid, activity feed) shows its own loading indicator (Spinner/Skeleton). Never use a single global loading overlay that blocks all sections
3. **Section errors do not block sibling sections** — if one section has an error, all other sections that received data must render normally. Errors are isolated to their section
4. **Error + data pattern for stat cards** — when a stat card section has an error but individual cards can show data, render the cards in their insufficient-data state rather than replacing all cards with a single Alert
5. **Test both paths** — for every section that can have partial errors, test: (a) section renders data when no error, (b) section renders data + warning Alert when error is present, (c) sibling sections still render when this section errors

**Unique Test Fixture Data — Preventing Text Collisions:**

When multiple components on the same page display overlapping data types (e.g., application names in both a health grid and an activity feed), test fixtures must use distinct values per component to prevent `getByText` ambiguity:

1. **Use unique reference values per test section** — if both a BuildTable and an ActivityFeed render on the same page, the build fixtures and activity fixtures must use different application names, version strings, and identifiers
2. **Prefer `getAllByText` for intentionally duplicated text** — when text genuinely appears in multiple components (e.g., a metric title in both a stat card and a trend chart), use `getAllByText` with length assertions instead of `getByText`
3. **Test data should be obviously synthetic** — use values like `"test-app-builds"` and `"test-app-activity"` rather than generic `"checkout-api"` that might collide across components

---

## Usage Guidelines

**For AI Agents:**

- Read this file before implementing any code
- Follow ALL rules exactly as documented
- When in doubt, prefer the more restrictive option
- Refer to the architecture document for detailed project structure and data flow diagrams

**For Humans:**

- Keep this file lean and focused on agent needs
- Update when technology stack changes
- Review quarterly for outdated rules
- Remove rules that become obvious over time

Last Updated: 2026-04-14
