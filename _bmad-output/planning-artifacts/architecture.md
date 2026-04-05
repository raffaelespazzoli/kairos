---
stepsCompleted:
  - 1
  - 2
  - 3
  - 4
  - 5
  - 6
  - 7
  - 8
lastStep: 8
status: 'complete'
completedAt: '2026-04-02'
inputDocuments:
  - 'planning-artifacts/prd.md'
  - 'planning-artifacts/product-brief.md'
  - 'planning-artifacts/product-brief-distillate.md'
  - 'planning-artifacts/ux-design-specification.md'
workflowType: 'architecture'
project_name: 'bmad'
user_name: 'Raffa'
date: '2026-04-02'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

38 functional requirements spanning 11 capability areas, delivered across 4 phases:

| Phase | Capability Areas | FRs | Integrations Added |
|---|---|---|---|
| 1 — Onboarding | Authentication, Application Onboarding, Environment Management, Cluster Management | FR1-16 + cluster admin | OIDC, Git, Vault, ArgoCD |
| 2 — Inner Loop | DevSpaces Integration | FR31 | DevSpaces |
| 3 — CI/CD | Pipeline Management, Release Management, Deployment & Promotion | FR17-26 | Tekton, Container Registry, Argo Rollouts |
| 4 — Observability | Health, DORA Metrics, Team Views, Deep Links | FR27-30, FR32-37 | Grafana/OTEL |
| Cross-cutting | Authorization | FR38 | — |

The portal is a **read-write orchestration layer** — it is not the source of truth for any data. All state is owned by the underlying platform systems. The portal reads live state on every request and writes through the systems' APIs or via GitOps commits.

**Non-Functional Requirements:**

| Category | Requirement | Architectural Impact |
|---|---|---|
| Performance | <5s page load, <3s standard API, <10s orchestration API | Backend must parallelize integration calls; no serial chains of API calls for a single view |
| Performance | <500ms SPA navigation | Client-side routing, no server round-trip for view switching |
| Security | OIDC-only authentication, no portal-specific credentials | Stateless auth via OIDC tokens; backend validates tokens on every request |
| Security | No long-lived credentials; Vault-issued creds cached within TTL | Credential management layer with TTL-aware caching |
| Security | Server-side authorization enforcement (FR38) | Middleware-level role checking, not frontend gating |
| Security | TLS for all platform tool communication | Certificate management for on-prem environments |
| Scalability | 50 teams before refactoring | Database schema must support efficient team-scoped queries |
| Scalability | Horizontal scaling via stateless replicas | No in-memory session state; all state in DB or client |
| Resilience | Clear error on unreachable systems, no silent failures | Per-integration health checking and error propagation |
| Resilience | No caching beyond current request | Every API response reflects live state; no stale data |
| Accessibility | WCAG 2.1 AA | PatternFly provides baseline; custom components need explicit ARIA |

**Scale & Complexity:**

- Primary domain: Full-stack web application (Platform Engineering / Developer Tooling)
- Complexity level: **High**
- Estimated architectural components: ~15-20 (SPA shell, REST API layer, authentication/authorization, database, integration adapters, GitOps contract engine, domain model services, cluster registry)

### Technical Constraints & Dependencies

**Explicitly Specified:**
- Quarkus preferred as backend framework
- PatternFly 5 as frontend component framework (implies React)
- SPA architecture with REST API
- PostgreSQL or similar relational database (implied by Quarkus ecosystem)
- Containerized deployment on OpenShift
- On-premises, disconnected-environment capable — no external cloud dependencies
- Not a Kubernetes operator — standalone application
- All platform tool integrations are backend-side; frontend never calls external systems directly

**Platform Dependencies (all on-prem, all reachable from portal runtime):**

| System | Protocol | Portal Operations |
|---|---|---|
| OIDC Provider | OIDC/OAuth2 | Authenticate users, extract team groups, determine roles |
| Git Server | Git/HTTPS API | Validate app repo contracts, create PRs to infra repo, create tags |
| Vault | Vault HTTP API | Retrieve cluster credentials at runtime |
| Tekton | Kubernetes API (via Vault creds) | Trigger PipelineRuns, query status, retrieve logs |
| ArgoCD | ArgoCD REST API | Query Application sync/health status, trigger syncs |
| Argo Rollouts | Kubernetes API (via Vault creds) | Query rollout status, observe progressive delivery |
| Container Registry | OCI Distribution API | Reference images, track release artifacts |
| Grafana/OTEL | Grafana HTTP API | Query health signals, DORA metrics, generate deep link URLs |
| DevSpaces | DevSpaces API/URL | Generate launch URLs scoped to application repository |

**Credential Model:**
- Portal authenticates to clusters via Vault at `/infra/<cluster>/kubernetes-secret-engine/<role>`
- Short-lived, scoped credentials per cluster per role
- Same pattern as ArgoCD and other platform tools — portal is a first-class platform citizen

**Cluster Registry:**
- Admin-only function to register clusters (name + API server URL)
- Stored in portal database
- Cluster credentials fetched from Vault at runtime using the registered cluster name
- Multiple environments may share a cluster (e.g., dev and QA on the same cluster, or dev and build on the same cluster)
- Registered clusters become available as targets when configuring application environment-to-cluster mapping

### GitOps Contract Specification

**Application Repository Contract:**

An application repository must contain a `.helm/` directory with the following structure to be eligible for onboarding:

```
app-repo/
├── .helm/
│   ├── build/                      # Helm chart — Tekton CI manifests
│   │   ├── Chart.yaml
│   │   └── templates/
│   ├── run/                        # Helm chart — application deployment manifests
│   │   ├── Chart.yaml
│   │   └── templates/
│   ├── values-build.yaml           # Values for the build chart
│   ├── values-run-dev.yaml         # Values for run chart — dev environment
│   ├── values-run-qa.yaml          # Values for run chart — QA environment
│   └── values-run-prod.yaml        # Values for run chart — prod environment
```

**Contract Validation Checks:**
1. `.helm/build/` directory exists and contains a valid Helm chart (`Chart.yaml`)
2. `.helm/run/` directory exists and contains a valid Helm chart (`Chart.yaml`)
3. `values-build.yaml` exists in `.helm/`
4. At least one `values-run-<env>.yaml` exists in `.helm/`
5. Runtime detection: presence of `pom.xml` (Quarkus/Java), `package.json` (Node.js), or `*.csproj` (.NET)

**Centralized Infrastructure Repository:**

Owned by the platform/infra team. Single repo for all teams and applications. Structure:

```
infra-repo/
├── <cluster>/
│   └── <namespace>/
│       ├── namespace.yaml              # Namespace object with labels (team, app, size)
│       └── argocd-application.yaml     # ArgoCD Application manifest
```

Multiple environments may map to the same cluster. Example for an app with dev and build on the same cluster:

```
infra-repo/
├── ocp-dev-01/
│   ├── payments-payment-svc-dev/
│   │   ├── namespace.yaml
│   │   └── argocd-app-run-dev.yaml
│   └── payments-payment-svc-build/
│       ├── namespace.yaml
│       └── argocd-app-build.yaml
├── ocp-qa-01/
│   └── payments-payment-svc-qa/
│       ├── namespace.yaml
│       └── argocd-app-run-qa.yaml
├── ocp-prod-01/
│   └── payments-payment-svc-prod/
│       ├── namespace.yaml
│       └── argocd-app-run-prod.yaml
```

**Onboarding Workflow:**
1. Portal validates app repo against the `.helm/` contract
2. Portal generates Namespace objects (with team, app, size labels) and ArgoCD Application manifests
3. Portal creates a **PR** to the centralized infra repo — not a direct commit
4. Infra team reviews and approves the PR
5. After merge, ArgoCD automatically syncs: namespaces created first (namespace-configuration-operator applies quotas/RBAC from labels), then ArgoCD Applications reconcile the Helm charts
6. Portal does not track PR status — onboarding action ends at PR creation

**ArgoCD Applications created per onboarded app:**

| ArgoCD Application | Source | Values File | Target |
|---|---|---|---|
| `<app>-build` | App repo `.helm/build/` | `values-build.yaml` | Build namespace on assigned cluster |
| `<app>-run-<env>` | App repo `.helm/run/` | `values-run-<env>.yaml` | Env namespace on assigned cluster |

Total: **1 (build) + N (environments)** ArgoCD Applications per onboarded application.

### Cross-Cutting Concerns Identified

**1. Multi-Tenancy**
OIDC groups define team boundaries. Every API endpoint must scope data access to the user's team(s). No data leakage between teams. Team membership is authoritative from the OIDC provider — no portal-side team management.

**2. Multi-Cluster Topology**
Applications span one or more clusters. Multiple environments can share a cluster. Clusters are registered by admins in the portal database. The portal fetches cluster-specific credentials from Vault at runtime. Environment chain configuration maps each environment to a cluster+namespace pair.

**3. GitOps Contract Enforcement**
The portal generates Namespace objects, ArgoCD Application manifests, and PR content that must conform exactly to the platform's GitOps contract. The `.helm/` contract in the app repo and the `/<cluster>/<namespace>/` convention in the infra repo are the integration specifications. An incorrect PR can break the platform for a team.

**4. Integration Resilience**
Each platform system can be independently unavailable. The portal must clearly indicate which system is affected. No silent failures. No graceful degradation for MVP — explicit error messages are acceptable. State is never cached beyond the current request.

**5. Developer Abstraction Layer**
All portal interfaces (API responses, UI labels, error messages) must speak in the developer domain model: Applications, Builds, Releases, Deployments, Environments — not Kubernetes resources, GitOps repositories, or Vault paths. This translation is a core architectural responsibility.

**6. Authorization Enforcement**
Three-tier model: admin (cluster registration), team lead (production deployment), team member (pre-production access). Must be enforced server-side via OIDC group/role claims. Frontend disables buttons but backend rejects unauthorized requests independently.

## Starter Template Evaluation

### Primary Technology Domain

Full-stack web application: **Quarkus (Java) backend + React (TypeScript) SPA frontend**, served as a single deployable via Quarkus Quinoa. On-premises deployment on OpenShift.

### Technology Version Baseline (as of April 2026)

| Technology | Version | Status |
|---|---|---|
| Quarkus | 3.34.x | Latest stable |
| Java | 17+ | Quarkus minimum |
| PatternFly React | 6.x (v6.4.1) | Active (PF5 is legacy) |
| React | 18.x | Stable |
| TypeScript | 5.x | Stable |
| Vite | 5.x | Build tooling (used by PatternFly itself) |
| Quarkus Quinoa | 2.7.x | Stable, serves SPA from Quarkus |

**Note on PatternFly version:** The UX design specification references PatternFly 5. PatternFly 6 is now the active development line. All UX patterns specified (cards, tables, wizards, labels, status variants, charts) carry over to PF6 with minor API renames. Use PatternFly 6 for this greenfield project.

### Deployment Architecture Decision: Quarkus + Quinoa Monorepo

**Decision:** Single deployable artifact — Quarkus serves both the REST API and the React SPA via the Quinoa extension.

**Rationale:**
- Single container image, single OpenShift deployment — simpler ops for a side project
- `quarkus dev` runs frontend and backend together with hot reload
- Frontend communicates with backend via relative URLs — no CORS configuration
- One CI pipeline, one artifact to version, one image to promote through environments
- Quinoa handles the frontend build lifecycle (npm install, npm build) as part of the Quarkus build
- Horizontal scaling still works — multiple stateless pod replicas all serve the same artifact

### Backend Starter: Quarkus

**Initialization Command:**

```bash
quarkus create app com.portal:developer-portal \
  --extensions=rest,rest-jackson,oidc,hibernate-orm-panache,jdbc-postgresql,quinoa,smallrye-health
```

**Core Quarkus Extensions:**

| Extension | Purpose |
|---|---|
| `rest` + `rest-jackson` | REST API endpoints with JSON serialization |
| `oidc` | OIDC Bearer token authentication — validates tokens from the organization's OIDC provider |
| `hibernate-orm-panache` | Database access with Active Record / Repository pattern — portal-specific persistence (app registry, cluster registry, environment chains) |
| `jdbc-postgresql` | PostgreSQL JDBC driver |
| `quinoa` | Build and serve the React SPA frontend from within Quarkus |
| `smallrye-health` | Health check endpoints for OpenShift readiness/liveness probes |

**Additional extensions to add during development:**

| Extension | Purpose | Phase |
|---|---|---|
| `rest-client-jackson` | Typed REST clients for ArgoCD API, Grafana API, Git server API, Container Registry API | Phase 1 |
| `oidc-client` | Acquire tokens for service-to-service calls if needed | Phase 1 |
| `kubernetes-client` | Kubernetes API access for Tekton and Argo Rollouts (via Vault-issued credentials) | Phase 3 |
| `scheduler` | Scheduled tasks if needed (e.g., background DORA metric aggregation) | Phase 4 |
| `container-image-jib` | Build container images without Docker daemon (CI-friendly) | Phase 1 |

**Architectural Decisions Provided by Quarkus:**

| Decision | Quarkus Provides |
|---|---|
| Language & Runtime | Java 17+, GraalVM native-image optional |
| Dependency Injection | CDI (ArC — build-time optimized) |
| REST Framework | Quarkus REST (formerly RESTEasy Reactive) — annotation-driven JAX-RS style |
| ORM | Hibernate ORM with Panache — Active Record or Repository pattern |
| Database | PostgreSQL via JDBC |
| Authentication | OIDC Bearer token validation, `@RolesAllowed` annotation for authorization |
| Configuration | `application.properties` / `application.yaml` with profile support (dev, test, prod) |
| Testing | JUnit 5 + `@QuarkusTest` integration testing, REST Assured for API tests |
| Health Checks | SmallRye Health — `/q/health/ready` and `/q/health/live` |
| Dev Experience | `quarkus dev` with hot reload, Dev UI at `/q/dev`, continuous testing |
| Build | Maven (default) — produces uber-jar or native executable |
| Container | Dockerfile provided in `src/main/docker/`, or Jib for daemonless builds |

### Frontend Starter: Vite + React + TypeScript + PatternFly 6

**Initialization (within the Quarkus project):**

```bash
cd src/main/webui
npm create vite@latest . -- --template react-ts
npm install @patternfly/react-core @patternfly/react-table @patternfly/react-icons @patternfly/react-charts
npm install react-router-dom
```

**Quinoa Configuration (`application.properties`):**

```properties
quarkus.quinoa.dev-server.port=5173
quarkus.quinoa.build-dir=dist
quarkus.quinoa.enable-spa-routing=true
quarkus.quinoa.package-manager-install=true
quarkus.quinoa.ui-dir=src/main/webui
```

**Architectural Decisions for Frontend:**

| Decision | Choice |
|---|---|
| Language | TypeScript (strict mode) |
| Build Tool | Vite 5.x — fast HMR, used by PatternFly itself |
| Component Framework | PatternFly 6 React — all standard components (Page, Table, Card, Wizard, Tabs, Breadcrumb, Chart) |
| Routing | React Router v6 — client-side SPA routing |
| State Management | React Context + hooks for MVP (no Redux needed — state is fetched fresh from API on every view) |
| Styling | PatternFly CSS custom properties (design tokens) exclusively — no Tailwind, no CSS modules |
| Charts | PatternFly Charts (Victory-based) — DORA metrics, sparklines |
| HTTP Client | `fetch` API with typed wrapper functions — relative URLs via Quinoa |
| Testing | Vitest + React Testing Library |
| Linting | ESLint with TypeScript plugin |

### Project Structure

```
developer-portal/
├── pom.xml                             # Quarkus Maven build (includes Quinoa)
├── src/
│   ├── main/
│   │   ├── java/com/portal/
│   │   │   ├── auth/                   # OIDC token validation, role extraction
│   │   │   ├── cluster/                # Cluster registry (admin CRUD)
│   │   │   ├── application/            # Application domain model + onboarding logic
│   │   │   ├── environment/            # Environment chain modeling
│   │   │   ├── build/                  # CI pipeline integration (Tekton)
│   │   │   ├── release/                # Release management (Git tags + images)
│   │   │   ├── deployment/             # Deployment + promotion (ArgoCD, Argo Rollouts)
│   │   │   ├── health/                 # Health + DORA metrics (Grafana/OTEL)
│   │   │   ├── integration/            # Platform tool adapters
│   │   │   │   ├── git/                # Git server API client
│   │   │   │   ├── vault/              # Vault API client + credential cache
│   │   │   │   ├── argocd/             # ArgoCD API client
│   │   │   │   ├── tekton/             # Tekton/K8s API client
│   │   │   │   ├── registry/           # Container registry client
│   │   │   │   ├── grafana/            # Grafana API client
│   │   │   │   └── devspaces/          # DevSpaces URL generator
│   │   │   └── gitops/                 # GitOps contract engine (manifest + PR generation)
│   │   ├── resources/
│   │   │   ├── application.properties  # Quarkus config (OIDC, DB, Quinoa, integrations)
│   │   │   └── db/migration/           # Flyway database migrations
│   │   └── webui/                      # React SPA (Quinoa serves this)
│   │       ├── package.json
│   │       ├── vite.config.ts
│   │       ├── tsconfig.json
│   │       └── src/
│   │           ├── App.tsx
│   │           ├── routes/             # Page-level route components
│   │           ├── components/         # Reusable UI components (env chain, DORA card, etc.)
│   │           ├── api/                # Typed API client functions
│   │           ├── hooks/              # Custom React hooks
│   │           └── types/              # TypeScript type definitions
│   └── test/
│       └── java/com/portal/           # Quarkus integration tests
├── src/main/docker/
│   ├── Dockerfile.jvm                  # JVM-mode container image
│   └── Dockerfile.native               # Native-mode container image (optional)
└── README.md
```

**Note:** Project initialization using these commands should be the first implementation story.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Data model and persistence scope
- Authentication via OIDC + Casbin RBAC + tenant isolation
- Integration adapter pattern for 9 platform systems
- GitOps manifest generation and multi-provider Git support
- REST API resource structure

**Important Decisions (Shape Architecture):**
- Vault credential lifecycle (TTL-aware caching)
- Parallel integration call strategy
- Error response format standardization
- Configuration management approach

**Deferred Decisions (Post-MVP):**
- Native image compilation (GraalVM)
- Database HA
- Casbin policy from database (dynamic policy updates)
- Dark theme support

### Data Architecture

**Database:** PostgreSQL, accessed via Hibernate ORM Panache (Active Record pattern).

**Portal Persistence Scope — what the portal stores:**

| Entity | Purpose | Key Attributes |
|---|---|---|
| `Cluster` | Admin-registered clusters | name, apiServerUrl |
| `Team` | Team metadata cached from OIDC | name, oidcGroupId |
| `Application` | Registered apps | name, team, gitRepoUrl, runtimeType, onboardingPrUrl, onboardedAt |
| `Environment` | Environment chain entries per app | name, cluster, namespace, promotionOrder |

**What the portal does NOT persist (fetched live):**
- Build status, pipeline runs → Tekton API
- Deployment status, sync state → ArgoCD API
- Health signals, DORA metrics → Grafana/OTEL API
- Release artifacts → Container Registry + Git tags
- Secrets → Vault API

**Migration strategy:** Flyway — versioned SQL migration files, Quarkus ecosystem default.

### Authentication & Security

**Two-layer authorization architecture:**

| Layer | Concern | Mechanism |
|---|---|---|
| **Layer 1: Permission** | "What can this role do?" | Casbin (jCasbin v1.99.0) — static policy, role → resource type → action |
| **Layer 2: Tenant isolation** | "Which data can this user see?" | Team context from JWT claim, injected as CDI bean, applied as query filter and integration call scoping |

**Authentication flow:**
1. User authenticates via OIDC provider → receives JWT
2. Quarkus OIDC extension validates the JWT on every request
3. `PermissionFilter` (JAX-RS `ContainerRequestFilter`) extracts role from configurable JWT claim → Casbin checks permission → 403 if denied
4. `TeamContextFilter` extracts team from configurable JWT claim → sets `TeamContext` CDI bean
5. All data access and integration calls scoped by `TeamContext`

**JWT claim configuration:**

| Setting | Default | Purpose |
|---|---|---|
| `portal.oidc.role-claim` | `role` | JWT claim name for user role |
| `portal.oidc.team-claim` | `team` | JWT claim name for team identity |

**Casbin RBAC model — three pre-existing groups:**

```ini
[request_definition]
r = sub, obj, act

[policy_definition]
p = sub, obj, act

[role_definition]
g = _, _

[policy_effect]
e = some(where (p.eft == allow))

[matchers]
m = g(r.sub, p.sub) && r.obj == p.obj && r.act == p.act
```

**Policy — role hierarchy with permission inheritance:**

| Group | Inherits | Additional Permissions |
|---|---|---|
| `member` | — | Read all resources, onboard apps, trigger builds, create releases, deploy to non-prod |
| `lead` | `member` | Deploy to production |
| `admin` | `lead` | Cluster CRUD |

### API & Communication Patterns

**REST API resource structure:**

```
/api/v1/teams                                              # User's teams
/api/v1/teams/{teamId}/applications                        # Team's applications
/api/v1/teams/{teamId}/applications/{appId}                # Single application
/api/v1/teams/{teamId}/applications/{appId}/environments   # Environment chain
/api/v1/teams/{teamId}/applications/{appId}/builds         # Builds (Tekton)
/api/v1/teams/{teamId}/applications/{appId}/releases       # Releases
/api/v1/teams/{teamId}/applications/{appId}/deployments    # Deployments
/api/v1/teams/{teamId}/applications/{appId}/health         # Health per env
/api/v1/teams/{teamId}/applications/{appId}/dora           # DORA metrics
/api/v1/teams/{teamId}/applications/{appId}/onboard        # POST: onboarding
/api/v1/teams/{teamId}/dashboard                           # Team dashboard
/api/v1/admin/clusters                                     # Admin: cluster CRUD
```

**API versioning:** URL-based (`/api/v1/`) — simplest for internal APIs.

**Error response format:**

```json
{
  "error": "deployment-failed",
  "message": "Deployment to QA failed: health check timeout after 120s",
  "detail": "ArgoCD sync completed but pod readiness probe failed",
  "system": "argocd",
  "deepLink": "https://argocd.internal/applications/payments-checkout-api-qa",
  "timestamp": "2026-04-02T14:30:00Z"
}
```

Every error includes: what happened, which system, and a deep link when applicable.

**Parallel integration calls:** Views that aggregate data from multiple platform systems (e.g., application overview needs ArgoCD + Tekton + Grafana) use `CompletableFuture` or Mutiny reactive types to call adapters in parallel, then aggregate. This is how the <3s API response target is met.

### Integration Architecture

**Adapter pattern:** Each platform system gets a dedicated `@ApplicationScoped` CDI bean that encapsulates all communication, translates platform concepts to the portal domain model, and throws portal-domain exceptions.

**Secret manager credential lifecycle:**
- `SecretManagerCredentialProvider` CDI bean — TTL-aware in-memory cache keyed by `(cluster, role)`
- Delegates to `SecretManagerAdapter` interface (Vault implementation for MVP)
- Lazy fetch on first use per cluster, refresh before TTL expiry
- No persistent credential storage — credentials never written to database or disk
- Portal pod authenticates to Vault via Kubernetes auth (service account)

**GitOps manifest generation:**
- Qute template engine (ships with Quarkus) for YAML generation
- Templates for: Namespace objects, ArgoCD Application manifests
- Templates version-controlled alongside portal code
- Readable YAML that platform engineers can review against the GitOps contract

**Git provider abstraction:**

```java
public interface GitProvider {
    void createBranch(String repo, String branch);
    void commitFiles(String repo, String branch, Map<String, String> files, String message);
    PullRequest createPullRequest(String repo, String branch, String title, String description);
}
```

Four implementations: `GitHubProvider`, `GitLabProvider`, `GiteaProvider`, `BitbucketProvider`. Active provider selected via `portal.git.provider` configuration.

### Infrastructure & Deployment

**Container image:** JVM mode — fastest build times, full library compatibility, proven in production. Native mode deferred as post-MVP optimization.

**Configuration management:** All settings via Quarkus config (`application.properties`) with environment-specific overrides via environment variables on OpenShift.

**Key configuration properties:**

```properties
# OIDC
quarkus.oidc.auth-server-url=${OIDC_SERVER_URL}
portal.oidc.role-claim=${OIDC_ROLE_CLAIM:role}
portal.oidc.team-claim=${OIDC_TEAM_CLAIM:team}

# Git Provider
portal.git.provider=${GIT_PROVIDER:github}
portal.git.infra-repo-url=${GIT_INFRA_REPO_URL}
portal.git.token=${GIT_TOKEN}

# Vault
portal.vault.url=${VAULT_URL}
portal.vault.credential-path-template=/infra/{cluster}/kubernetes-secret-engine/{role}

# ArgoCD, Grafana, DevSpaces, Container Registry
portal.argocd.url=${ARGOCD_URL}
portal.grafana.url=${GRAFANA_URL}
portal.devspaces.url=${DEVSPACES_URL}
portal.registry.url=${REGISTRY_URL}
```

**Logging:** Quarkus JBoss Logging with structured JSON output for production. Every integration call logged with target system, operation, duration, and success/failure.

**OpenShift deployment resources:**
- `Deployment` with readiness/liveness probes (SmallRye Health `/q/health/ready`, `/q/health/live`)
- `Service` + `Route` for ingress
- `ConfigMap` for non-sensitive configuration (integration URLs, feature flags)
- `ServiceAccount` configured for Vault Kubernetes auth
- No Kubernetes `Secret` objects — all sensitive values fetched from Vault at runtime via pod service account

### Decision Impact Analysis

**Implementation Sequence:**
1. Project scaffolding (Quarkus + Quinoa + base extensions)
2. Database schema + Flyway migrations (Cluster, Team, Application, Environment entities)
3. OIDC authentication + Casbin authorization + TeamContext filter
4. Integration adapter framework (Vault credential provider first, then Git provider)
5. Onboarding flow (Git contract validation → Qute manifest generation → PR creation)
6. Remaining adapters phase by phase (Tekton, ArgoCD, Grafana)

**Cross-Component Dependencies:**
- All adapters depend on `VaultCredentialProvider` for cluster access
- All REST endpoints depend on the two-layer auth (Casbin + TeamContext)
- Onboarding depends on Git provider + Qute templates + multiple adapters
- Frontend views depend on parallel aggregation in REST endpoints

## Implementation Patterns & Consistency Rules

### Naming Patterns

**Database Naming:**

| Element | Convention | Example |
|---|---|---|
| Tables | `snake_case`, plural | `applications`, `environments`, `clusters` |
| Columns | `snake_case` | `git_repo_url`, `api_server_url`, `promotion_order` |
| Primary keys | `id` (bigint, auto-generated) | `id` |
| Foreign keys | `<referenced_table_singular>_id` | `team_id`, `cluster_id`, `application_id` |
| Indexes | `idx_<table>_<columns>` | `idx_applications_team_id` |
| Unique constraints | `uq_<table>_<columns>` | `uq_clusters_name` |
| Timestamps | `created_at`, `updated_at` | Always UTC, `TIMESTAMPTZ` |

**Java Backend Naming:**

| Element | Convention | Example |
|---|---|---|
| Packages | `com.portal.<domain>` | `com.portal.application`, `com.portal.integration.argocd` |
| Entity classes | `PascalCase`, singular | `Application`, `Environment`, `Cluster` |
| REST resource classes | `<Entity>Resource` | `ApplicationResource`, `ClusterResource` |
| Adapter classes | `<System>Adapter` | `ArgoCdAdapter`, `TektonAdapter`, `VaultCredentialProvider` |
| Service classes | `<Domain>Service` | `OnboardingService`, `DeploymentService` |
| DTOs | `<Entity><Purpose>Dto` | `ApplicationSummaryDto`, `EnvironmentStatusDto` |
| Configuration classes | `<Domain>Config` | `PortalOidcConfig`, `GitProviderConfig` |
| Exception classes | `Portal<Context>Exception` | `PortalIntegrationException`, `PortalAuthorizationException` |
| Methods | `camelCase` | `findByTeam()`, `triggerBuild()`, `createOnboardingPr()` |
| Constants | `UPPER_SNAKE_CASE` | `DEFAULT_ROLE_CLAIM`, `MAX_RETRY_ATTEMPTS` |

**TypeScript Frontend Naming:**

| Element | Convention | Example |
|---|---|---|
| Components | `PascalCase` file and export | `EnvironmentChain.tsx`, `DoraStatCard.tsx` |
| Hooks | `camelCase`, `use` prefix | `useApplications.ts`, `useTeamContext.ts` |
| API functions | `camelCase`, verb prefix | `fetchApplications()`, `triggerBuild()`, `createRelease()` |
| Types/Interfaces | `PascalCase` | `Application`, `EnvironmentStatus`, `BuildSummary` |
| Route components | `PascalCase` + `Page` suffix | `ApplicationOverviewPage.tsx`, `TeamDashboardPage.tsx` |
| CSS classes | PatternFly tokens only | No custom CSS class naming — use PF6 components and tokens exclusively |
| Constants | `UPPER_SNAKE_CASE` | `API_BASE_URL`, `REFRESH_INTERVAL` |
| Directories | `kebab-case` | `components/`, `api/`, `hooks/`, `types/` |

**REST API Naming:**

| Element | Convention | Example |
|---|---|---|
| Endpoints | Plural nouns, `kebab-case` for multi-word | `/applications`, `/environments` |
| Path parameters | `camelCase` | `{teamId}`, `{appId}` |
| Query parameters | `camelCase` | `?pageSize=20&sortBy=name` |
| JSON fields | `camelCase` | `gitRepoUrl`, `apiServerUrl`, `promotionOrder` |
| Action endpoints | `POST` on sub-resource | `POST /applications/{appId}/onboard`, `POST /builds` |

### Structure Patterns

**Backend organization — domain-centric packages:**

Each domain package contains its own resource, service, entity, and DTOs. No cross-package entity imports — use IDs for references between domains.

```
com.portal.application/
├── Application.java            # Panache entity
├── ApplicationResource.java    # JAX-RS REST endpoints
├── ApplicationService.java     # Business logic
├── ApplicationSummaryDto.java  # API response DTO
└── ApplicationMapper.java      # Entity ↔ DTO mapping

com.portal.integration.argocd/
├── ArgoCdAdapter.java          # ArgoCD API client
├── ArgoCdConfig.java           # ArgoCD-specific config
└── model/                      # ArgoCD API response models (internal)
```

**Frontend organization — co-located by feature:**

```
src/
├── routes/
│   ├── ApplicationOverviewPage.tsx
│   └── TeamDashboardPage.tsx
├── components/
│   ├── EnvironmentChain.tsx
│   ├── DoraStatCard.tsx
│   └── ContractValidationChecklist.tsx
├── api/
│   ├── applications.ts
│   ├── builds.ts
│   └── clusters.ts
├── hooks/
│   ├── useApplications.ts
│   └── useTeamContext.ts
└── types/
    ├── application.ts
    └── environment.ts
```

**Test organization:**

| Layer | Location | Framework | Convention |
|---|---|---|---|
| Backend unit tests | `src/test/java/com/portal/<domain>/` | JUnit 5 | `<Class>Test.java` |
| Backend integration tests | `src/test/java/com/portal/<domain>/` | `@QuarkusTest` + REST Assured | `<Class>IT.java` |
| Frontend component tests | Co-located with component | Vitest + React Testing Library | `<Component>.test.tsx` |

### Format Patterns

**API response format — direct response, no wrapper:**

Success responses return the resource directly. Collections return arrays. Errors return the standardized error object.

**Date/time format:** ISO 8601 strings in UTC (`2026-04-02T14:30:00Z`) everywhere — API, database, frontend display (formatted to local time in browser).

**HTTP status code usage:**

| Status | Usage |
|---|---|
| `200 OK` | Successful GET, PUT |
| `201 Created` | Successful POST that creates a resource |
| `204 No Content` | Successful DELETE |
| `400 Bad Request` | Validation errors (malformed input) |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | Valid JWT but Casbin denies permission or team mismatch |
| `404 Not Found` | Resource doesn't exist or not in user's team scope |
| `502 Bad Gateway` | Platform system integration failure |
| `503 Service Unavailable` | Portal itself unhealthy |

**Key rule:** `404` is returned both for genuinely missing resources AND for resources outside the user's team scope — never reveal that a resource exists in another team.

### Process Patterns

**Backend error handling:** All integration adapters throw `PortalIntegrationException` with system, operation, message, and optional deepLink. A global JAX-RS `ExceptionMapper` returns the standardized error JSON with `502` status.

**Frontend error handling:** Shared `apiFetch()` wrapper adds OIDC bearer token, checks response status, parses errors into typed objects. Components display errors inline using PatternFly `Alert`.

**Frontend loading states:** Every data-fetching hook returns `{ data, error, isLoading }`. No global loading state — each view section loads independently.

**Frontend data freshness:** No automatic polling for MVP. User-initiated refresh. No client-side caching between navigations.

### Enforcement Guidelines

**All AI Agents MUST:**

1. Follow naming conventions without exception
2. Place code in the correct package/directory structure
3. Use the standardized error response format for all API errors
4. Use `PortalIntegrationException` for all integration failures
5. Return `404` (not `403`) when a resource is outside the user's team scope
6. Use PatternFly 6 components exclusively — no custom HTML/CSS for elements PF provides
7. Use PatternFly CSS tokens for all styling — no hardcoded values
8. Keep all integration logic in adapter classes
9. Write developer-language error messages — never expose infrastructure terms
10. Include JSDoc/Javadoc on all public interfaces and adapter methods

### Anti-Patterns to Avoid

| Anti-Pattern | Correct Pattern |
|---|---|
| Entity in one package importing entity from another | Reference by ID; use a service to cross boundaries |
| REST resource calling ArgoCD directly | REST resource → Service → Adapter |
| Frontend calling `/api/v1/...` with hardcoded URL | Use `apiFetch()` wrapper with relative URL |
| Returning `403` for cross-team access | Return `404` — don't leak resource existence |
| Custom CSS for something PatternFly provides | Use the PatternFly component |
| Catching `Exception` broadly in adapters | Catch specific exceptions, wrap in `PortalIntegrationException` |
| Storing Vault credentials in database | In-memory TTL cache only via `SecretManagerCredentialProvider` |
| Using `camelCase` in database columns | Use `snake_case` — Panache handles the mapping |

## Project Structure & Boundaries

### Complete Project Directory Structure

```
developer-portal/
├── pom.xml
├── README.md
├── .gitignore
├── .editorconfig
│
├── src/main/java/com/portal/
│   │
│   ├── PortalApplication.java
│   │
│   ├── auth/
│   │   ├── PermissionFilter.java
│   │   ├── TeamContextFilter.java
│   │   ├── TeamContext.java
│   │   ├── CasbinEnforcer.java
│   │   └── PortalAuthorizationException.java
│   │
│   ├── team/
│   │   ├── Team.java
│   │   ├── TeamResource.java
│   │   ├── TeamService.java
│   │   └── TeamSummaryDto.java
│   │
│   ├── cluster/
│   │   ├── Cluster.java
│   │   ├── ClusterResource.java
│   │   ├── ClusterService.java
│   │   └── ClusterDto.java
│   │
│   ├── application/
│   │   ├── Application.java
│   │   ├── ApplicationResource.java
│   │   ├── ApplicationService.java
│   │   ├── ApplicationSummaryDto.java
│   │   ├── ApplicationDetailDto.java
│   │   └── ApplicationMapper.java
│   │
│   ├── environment/
│   │   ├── Environment.java
│   │   ├── EnvironmentResource.java
│   │   ├── EnvironmentService.java
│   │   ├── EnvironmentStatusDto.java
│   │   └── EnvironmentMapper.java
│   │
│   ├── onboarding/
│   │   ├── OnboardingResource.java
│   │   ├── OnboardingService.java
│   │   ├── ContractValidator.java
│   │   ├── ContractValidationResult.java
│   │   └── OnboardingResultDto.java
│   │
│   ├── build/
│   │   ├── BuildResource.java
│   │   ├── BuildService.java
│   │   └── BuildSummaryDto.java
│   │
│   ├── release/
│   │   ├── ReleaseResource.java
│   │   ├── ReleaseService.java
│   │   └── ReleaseSummaryDto.java
│   │
│   ├── deployment/
│   │   ├── DeploymentResource.java
│   │   ├── DeploymentService.java
│   │   └── DeploymentStatusDto.java
│   │
│   ├── health/
│   │   ├── HealthResource.java
│   │   ├── HealthService.java
│   │   ├── HealthStatusDto.java
│   │   ├── DoraResource.java
│   │   ├── DoraService.java
│   │   └── DoraMetricsDto.java
│   │
│   ├── dashboard/
│   │   ├── DashboardResource.java
│   │   ├── DashboardService.java
│   │   └── TeamDashboardDto.java
│   │
│   ├── deeplink/
│   │   ├── DeepLinkService.java
│   │   └── DeepLinkConfig.java
│   │
│   ├── integration/
│   │   ├── IntegrationException.java
│   │   │
│   │   ├── secrets/
│   │   │   ├── SecretManagerAdapter.java
│   │   │   ├── SecretManagerCredentialProvider.java
│   │   │   ├── SecretManagerConfig.java
│   │   │   └── vault/
│   │   │       ├── VaultSecretManagerAdapter.java
│   │   │       ├── VaultConfig.java
│   │   │       └── model/
│   │   │           └── VaultCredential.java
│   │   │
│   │   ├── git/
│   │   │   ├── GitProvider.java
│   │   │   ├── GitHubProvider.java
│   │   │   ├── GitLabProvider.java
│   │   │   ├── GiteaProvider.java
│   │   │   ├── BitbucketProvider.java
│   │   │   ├── GitProviderConfig.java
│   │   │   ├── GitProviderFactory.java
│   │   │   └── model/
│   │   │       └── PullRequest.java
│   │   │
│   │   ├── argocd/
│   │   │   ├── ArgoCdAdapter.java
│   │   │   ├── ArgoCdConfig.java
│   │   │   └── model/
│   │   │       ├── ArgoCdApplication.java
│   │   │       └── ArgoCdSyncStatus.java
│   │   │
│   │   ├── tekton/
│   │   │   ├── TektonAdapter.java
│   │   │   ├── TektonConfig.java
│   │   │   └── model/
│   │   │       ├── PipelineRun.java
│   │   │       └── PipelineLog.java
│   │   │
│   │   ├── registry/
│   │   │   ├── RegistryAdapter.java
│   │   │   ├── RegistryConfig.java
│   │   │   └── model/
│   │   │       └── ImageReference.java
│   │   │
│   │   └── prometheus/
│   │       ├── PrometheusAdapter.java
│   │       ├── PrometheusConfig.java
│   │       └── model/
│   │           ├── HealthSignal.java
│   │           └── DoraMetric.java
│   │
│   └── gitops/
│       ├── ManifestGenerator.java
│       ├── OnboardingPrBuilder.java
│       └── templates/
│           ├── namespace.yaml.qute
│           └── argocd-application.yaml.qute
│
├── src/main/resources/
│   ├── application.properties
│   ├── application-dev.properties
│   ├── application-test.properties
│   ├── casbin/
│   │   ├── model.conf
│   │   └── policy.csv
│   └── db/migration/
│       ├── V1__create_clusters.sql
│       ├── V2__create_teams.sql
│       ├── V3__create_applications.sql
│       └── V4__create_environments.sql
│
├── src/main/webui/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       │
│       ├── api/
│       │   ├── client.ts
│       │   ├── teams.ts
│       │   ├── applications.ts
│       │   ├── environments.ts
│       │   ├── builds.ts
│       │   ├── releases.ts
│       │   ├── deployments.ts
│       │   ├── health.ts
│       │   ├── dora.ts
│       │   ├── dashboard.ts
│       │   └── clusters.ts
│       │
│       ├── hooks/
│       │   ├── useAuth.ts
│       │   ├── useTeamContext.ts
│       │   ├── useApplications.ts
│       │   ├── useEnvironments.ts
│       │   ├── useBuilds.ts
│       │   ├── useReleases.ts
│       │   ├── useDeployments.ts
│       │   ├── useHealth.ts
│       │   ├── useDora.ts
│       │   └── useDashboard.ts
│       │
│       ├── routes/
│       │   ├── TeamDashboardPage.tsx
│       │   ├── ApplicationOverviewPage.tsx
│       │   ├── ApplicationBuildsPage.tsx
│       │   ├── ApplicationReleasesPage.tsx
│       │   ├── ApplicationEnvironmentsPage.tsx
│       │   ├── ApplicationHealthPage.tsx
│       │   ├── ApplicationSettingsPage.tsx
│       │   ├── OnboardingWizardPage.tsx
│       │   └── AdminClustersPage.tsx
│       │
│       ├── components/
│       │   ├── layout/
│       │   │   ├── AppShell.tsx
│       │   │   ├── Sidebar.tsx
│       │   │   └── AppBreadcrumb.tsx
│       │   ├── environment/
│       │   │   ├── EnvironmentChain.tsx
│       │   │   ├── EnvironmentCard.tsx
│       │   │   └── PromotionConfirmation.tsx
│       │   ├── onboarding/
│       │   │   ├── ContractValidationChecklist.tsx
│       │   │   ├── ProvisioningPlanPreview.tsx
│       │   │   └── ProvisioningProgressTracker.tsx
│       │   ├── build/
│       │   │   ├── BuildTable.tsx
│       │   │   └── BuildLogDetail.tsx
│       │   ├── release/
│       │   │   └── ReleaseTable.tsx
│       │   ├── dashboard/
│       │   │   ├── DoraStatCard.tsx
│       │   │   ├── ApplicationHealthGrid.tsx
│       │   │   └── ActivityFeed.tsx
│       │   ├── health/
│       │   │   ├── HealthStatusBadge.tsx
│       │   │   └── GoldenSignalsPanel.tsx
│       │   └── shared/
│       │       ├── DeepLinkButton.tsx
│       │       ├── ErrorAlert.tsx
│       │       ├── LoadingSpinner.tsx
│       │       └── RefreshButton.tsx
│       │
│       └── types/
│           ├── application.ts
│           ├── environment.ts
│           ├── build.ts
│           ├── release.ts
│           ├── deployment.ts
│           ├── health.ts
│           ├── dora.ts
│           ├── dashboard.ts
│           ├── cluster.ts
│           ├── team.ts
│           └── error.ts
│
├── src/test/java/com/portal/
│   ├── auth/
│   │   ├── PermissionFilterTest.java
│   │   └── TeamContextFilterTest.java
│   ├── application/
│   │   ├── ApplicationResourceIT.java
│   │   └── ApplicationServiceTest.java
│   ├── onboarding/
│   │   ├── ContractValidatorTest.java
│   │   ├── OnboardingServiceTest.java
│   │   └── OnboardingResourceIT.java
│   ├── integration/
│   │   ├── secrets/
│   │   │   ├── SecretManagerCredentialProviderTest.java
│   │   │   └── vault/VaultSecretManagerAdapterTest.java
│   │   ├── git/
│   │   │   ├── GitHubProviderTest.java
│   │   │   └── GitLabProviderTest.java
│   │   ├── argocd/ArgoCdAdapterTest.java
│   │   ├── tekton/TektonAdapterTest.java
│   │   └── prometheus/PrometheusAdapterTest.java
│   └── gitops/
│       ├── ManifestGeneratorTest.java
│       └── OnboardingPrBuilderTest.java
│
└── src/main/docker/
    ├── Dockerfile.jvm
    └── Dockerfile.native
```

### Architectural Boundaries

**API boundary:** All frontend communication goes through `/api/v1/` REST endpoints. The frontend NEVER calls platform systems directly.

**Service boundary:** REST resources delegate to service classes. Services orchestrate between database access (Panache) and integration adapters. Services may call multiple adapters in parallel for aggregated views.

**Adapter boundary:** All platform tool communication is encapsulated in `integration/` adapters. No adapter logic leaks into services or resources. Adapters translate platform concepts to portal domain types.

**Adapter abstraction boundary:** Pluggable integrations use an interface + implementation pattern:
- `SecretManagerAdapter` → `VaultSecretManagerAdapter` (selected via `portal.secrets.provider`)
- `GitProvider` → `GitHubProvider` / `GitLabProvider` / `GiteaProvider` / `BitbucketProvider` (selected via `portal.git.provider`)

**Data boundary:** Panache entities are internal to their domain package. REST resources return DTOs, never entities. Cross-domain references use IDs, not entity imports.

**Deep link boundary:** Grafana and DevSpaces are deep-link-only. `DeepLinkService` generates scoped URLs using configured base URLs and application/environment context. No API calls to these systems.

**GitOps boundary:** All manifest generation and PR creation logic lives in `gitops/`. Templates in `gitops/templates/`. `OnboardingPrBuilder` orchestrates the full PR workflow using `ManifestGenerator` + `GitProvider`.

### Integration Adapters — Complete List

**Pluggable adapters (interface + implementation):**

| Interface | Implementation | Config Key | Phase |
|---|---|---|---|
| `SecretManagerAdapter` | `VaultSecretManagerAdapter` | `portal.secrets.provider=vault` | 1 |
| `GitProvider` | `GitHubProvider`, `GitLabProvider`, `GiteaProvider`, `BitbucketProvider` | `portal.git.provider=github` | 1 |

**Concrete adapters:**

| Adapter | Package | Protocol | Phase | Operations |
|---|---|---|---|---|
| `SecretManagerCredentialProvider` | `integration/secrets/` | In-memory cache | 1 | TTL-aware cache over any SecretManagerAdapter |
| `ArgoCdAdapter` | `integration/argocd/` | ArgoCD REST API | 1 | App status, sync operations |
| `TektonAdapter` | `integration/tekton/` | Kubernetes API | 3 | Pipeline triggers, status, logs |
| `RegistryAdapter` | `integration/registry/` | OCI Distribution API | 3 | Image references, release artifacts |
| `PrometheusAdapter` | `integration/prometheus/` | Prometheus HTTP API | 4 | Health signals (golden signals), DORA metrics via PromQL |

**Deep-link-only systems (no adapter):**

| System | URL Pattern | Config | Service |
|---|---|---|---|
| Grafana | `{grafanaUrl}/d/{dashboardId}?var-namespace={ns}` | `portal.grafana.url`, `portal.grafana.dashboard-id` | `DeepLinkService` |
| DevSpaces | `{devspacesUrl}/#/{gitRepoUrl}` | `portal.devspaces.url` | `DeepLinkService` |
| ArgoCD (UI) | `{argocdUrl}/applications/{appName}` | `portal.argocd.url` | `DeepLinkService` |
| Tekton (UI) | `{tektonDashboardUrl}/#/pipelineruns/{runId}` | `portal.tekton.dashboard-url` | `DeepLinkService` |

### Requirements to Structure Mapping

| Phase | FR Category | Backend Package | Frontend Directory | Adapter(s) |
|---|---|---|---|---|
| 1 | Authentication (FR1-4) | `auth/` | `hooks/useAuth.ts` | — (Quarkus OIDC) |
| 1 | Application Onboarding (FR5-13) | `application/`, `onboarding/`, `gitops/` | `routes/OnboardingWizardPage.tsx`, `components/onboarding/` | `git/`, `secrets/` |
| 1 | Environment Management (FR14-16) | `environment/` | `components/environment/` | `argocd/` |
| 1 | Cluster Management | `cluster/` | `routes/AdminClustersPage.tsx` | — (DB only) |
| 2 | Inner Loop (FR31) | `deeplink/` | `DeepLinkButton` | — (URL generation) |
| 3 | CI Pipeline (FR17-20) | `build/` | `routes/ApplicationBuildsPage.tsx`, `components/build/` | `tekton/` |
| 3 | Release Management (FR21-22) | `release/` | `routes/ApplicationReleasesPage.tsx`, `components/release/` | `git/`, `registry/` |
| 3 | Deployment & Promotion (FR23-26) | `deployment/` | `components/environment/PromotionConfirmation.tsx` | `argocd/` |
| 4 | Health (FR27, FR29) | `health/` | `routes/ApplicationHealthPage.tsx`, `components/health/` | `prometheus/` |
| 4 | DORA Metrics (FR28, FR34) | `health/` (Dora*) | `components/dashboard/DoraStatCard.tsx` | `prometheus/` |
| 4 | Team Dashboard (FR32-35) | `dashboard/` | `routes/TeamDashboardPage.tsx`, `components/dashboard/` | Multiple (parallel) |
| All | Deep Links (FR36-37) | `deeplink/` | `components/shared/DeepLinkButton.tsx` | — (URL generation) |
| All | Authorization (FR38) | `auth/` | `components/environment/PromotionConfirmation.tsx` | — (Casbin) |

### Data Flow

```
Browser → [OIDC Token] → Quarkus OIDC Validation
  → PermissionFilter (Casbin check)
  → TeamContextFilter (team claim → CDI bean)
  → REST Resource
  → Service (business logic + orchestration)
  → ┬── Panache Entity (PostgreSQL)
    ├── SecretManagerCredentialProvider → SecretManagerAdapter (Vault) → Cluster credential
    ├── ArgoCdAdapter (with cluster cred)
    ├── TektonAdapter (with cluster cred)
    ├── PrometheusAdapter
    ├── GitProvider (GitHub/GitLab/Gitea/Bitbucket)
    ├── RegistryAdapter
    └── DeepLinkService (Grafana, DevSpaces, ArgoCD UI, Tekton UI URLs)
  → DTO assembly
  → JSON response → Browser
```

### Configuration Properties — Complete Reference

```properties
# OIDC
quarkus.oidc.auth-server-url=${OIDC_SERVER_URL}
portal.oidc.role-claim=${OIDC_ROLE_CLAIM:role}
portal.oidc.team-claim=${OIDC_TEAM_CLAIM:team}

# Secret Manager
portal.secrets.provider=${SECRETS_PROVIDER:vault}
portal.secrets.vault.url=${VAULT_URL}
portal.secrets.vault.credential-path-template=/infra/{cluster}/kubernetes-secret-engine/{role}

# Git Provider
portal.git.provider=${GIT_PROVIDER:github}
portal.git.infra-repo-url=${GIT_INFRA_REPO_URL}
portal.git.token=${GIT_TOKEN}

# ArgoCD
portal.argocd.url=${ARGOCD_URL}

# Tekton
portal.tekton.dashboard-url=${TEKTON_DASHBOARD_URL}

# Prometheus
portal.prometheus.url=${PROMETHEUS_URL}

# Container Registry
portal.registry.url=${REGISTRY_URL}

# Deep Links
portal.grafana.url=${GRAFANA_URL}
portal.grafana.dashboard-id=${GRAFANA_DASHBOARD_ID}
portal.devspaces.url=${DEVSPACES_URL}
```

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:** All technology choices verified compatible — Quarkus 3.34.x with OIDC, Panache, Flyway, Quinoa, REST Client, Kubernetes Client extensions work together natively. jCasbin integrates as a plain Maven dependency. PatternFly 6 React with Vite served by Quinoa. No conflicts found.

**Pattern Consistency:** Naming conventions align across layers — `snake_case` DB columns mapped automatically by Panache to `camelCase` Java fields. All adapters follow identical structural pattern (CDI bean + config + internal model). Both pluggable abstractions (SecretManager, GitProvider) use the same interface + factory pattern. Error handling flows consistently from adapter → PortalIntegrationException → ExceptionMapper → standardized JSON.

**Structure Alignment:** Every FR category maps to a specific backend package + frontend directory. All adapters live under `integration/` with consistent sub-package structure. Test organization mirrors source. No orphaned components.

### Requirements Coverage Validation ✅

**Functional Requirements:** All 38 FRs have explicit architectural support with identified backend packages, frontend components, and integration adapters. No gaps.

**Non-Functional Requirements:** All NFRs addressed — performance (parallel adapter calls), security (OIDC + Casbin + TeamContext), scalability (stateless tier + team-scoped queries), accessibility (PatternFly 6 built-in + custom ARIA), resilience (PortalIntegrationException with system identification).

### Implementation Readiness ✅

**Decision Completeness:** All critical decisions documented with specific versions. No TBD decisions remaining.

**Structure Completeness:** Full project tree with ~100 files mapped. Every file has clear purpose and ownership.

**Pattern Completeness:** Naming, structure, format, and process patterns cover all conflict points between AI agents.

### Gap Analysis

**No critical gaps.**

**Important observations (not blocking):**

1. **Onboarding UX adjustment:** The UX spec describes real-time provisioning progress, but onboarding ends at PR creation (infra team approves asynchronously). The onboarding wizard final state should be "PR created successfully" with a link to the PR, not real-time resource materialization. UX spec should be updated to reflect this.

2. **Prometheus PromQL queries:** Specific queries for DORA metrics and golden signals to be defined during Phase 4 implementation — depends on available metric labels in the target environment.

3. **Multi-team membership:** Current model assumes single team per user (single `team` JWT claim). If users can belong to multiple teams, the claim becomes an array and TeamContextFilter needs team selection logic. Confirm during implementation.

### Architecture Completeness Checklist

**Requirements Analysis**
- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed (High)
- [x] Technical constraints identified (on-prem, disconnected, OpenShift)
- [x] Cross-cutting concerns mapped (6 concerns)
- [x] GitOps contract fully specified

**Architectural Decisions**
- [x] Critical decisions documented with versions
- [x] Technology stack fully specified (Quarkus + React + PatternFly 6 + PostgreSQL)
- [x] Integration patterns defined (adapter pattern, pluggable abstractions)
- [x] Performance considerations addressed (parallel calls, stateless tier)
- [x] Security architecture complete (OIDC + Casbin + TeamContext)

**Implementation Patterns**
- [x] Naming conventions established (DB, Java, TypeScript, REST)
- [x] Structure patterns defined (domain-centric packages, co-located frontend)
- [x] Communication patterns specified (REST, error format, status codes)
- [x] Process patterns documented (error handling, loading, data freshness)

**Project Structure**
- [x] Complete directory structure defined (~100 files mapped)
- [x] Component boundaries established (6 boundary types)
- [x] Integration points mapped (5 adapters + 2 abstractions + deep links)
- [x] Requirements to structure mapping complete (all 38 FRs)

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** High

**Key Strengths:**
- Clean separation between portal persistence and live platform state
- Pluggable adapter abstractions for secret management and Git providers
- Two-layer auth (Casbin + tenant isolation) is robust and testable independently
- Monorepo (Quinoa) simplifies deployment and development for a side project
- GitOps contract is fully specified with concrete file structures and naming
- Every FR has a clear home in the project structure

**Areas for Future Enhancement:**
- Multi-team membership support if needed
- Prometheus PromQL query library (Phase 4 design work)
- API pagination for large collections (50+ teams scenario)
- Event-driven notifications (build complete, deployment succeeded) — post-MVP

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and boundaries
- Refer to this document for all architectural questions
- When in doubt about a pattern, check the anti-patterns table

**First Implementation Priority:**
1. `quarkus create app` with specified extensions
2. React SPA scaffold in `src/main/webui/`
3. Flyway migrations for the 4 core entities
4. OIDC + Casbin + TeamContext auth layer
5. First adapter: SecretManagerAdapter (Vault implementation)
