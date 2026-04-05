---
stepsCompleted:
  - 'step-01-validate-prerequisites'
  - 'step-02-design-epics'
  - 'step-03-create-stories'
  - 'step-04-final-validation'
status: 'complete'
completedAt: '2026-04-03'
inputDocuments:
  - 'planning-artifacts/prd.md'
  - 'planning-artifacts/architecture.md'
  - 'planning-artifacts/ux-design-specification.md'
---

# Internal Developer Portal - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the Internal Developer Portal, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

- **FR1:** Developers can authenticate via the organization's OIDC provider using their existing credentials
- **FR2:** The system can recognize a developer's team membership from OIDC group metadata without any portal-specific registration
- **FR3:** Developers can view only the teams and applications they have access to based on their OIDC group membership
- **FR4:** Team leads can see all applications belonging to their team
- **FR5:** Developers can register a new application by providing its Git repository URL
- **FR6:** The system can validate that a Git repository conforms to the platform's GitOps contract
- **FR7:** The system can detect the application's runtime type (Quarkus, Node.js, .NET) from the repository
- **FR8:** The system can present an onboarding plan showing what will be provisioned (namespaces, pipelines, deployments, secrets) before the developer confirms
- **FR9:** Upon confirmation, the system can automatically provision namespaces across the application's environment chain via GitOps commits
- **FR10:** Upon confirmation, the system can automatically configure Tekton pipelines for the application
- **FR11:** Upon confirmation, the system can automatically configure ArgoCD applications for each environment
- **FR12:** Upon confirmation, the system can automatically provision Vault secret stores per namespace for the application
- **FR13:** Developers can view a list of all applications onboarded by their team
- **FR14:** Developers can define an environment promotion chain for an application (e.g., dev → qa → staging → prod)
- **FR15:** The system can model environments as configured namespaces across specific clusters
- **FR16:** Developers can view the state of an application across all environments in its promotion chain
- **FR17:** Developers can trigger a CI pipeline for an application from the portal
- **FR18:** Developers can monitor CI pipeline execution status with developer-friendly terminology
- **FR19:** Developers can view CI pipeline logs
- **FR20:** Developers can see the resulting artifact reference (container image) from a successful build
- **FR21:** Developers can create a release from a successful build, which tags the Git commit and associates the container image
- **FR22:** Developers can view a list of releases for an application with their version, creation date, and associated build
- **FR23:** Developers can deploy a specific release to any environment in the application's promotion chain
- **FR24:** Developers can promote a release from one environment to the next in the chain
- **FR25:** Developers can view the deployment status of a release in any environment
- **FR26:** The system can execute deployments via ArgoCD sync operations and Argo Rollouts progressive delivery without exposing these mechanisms to the developer
- **FR27:** Developers can view the health status of an application in each environment where it is deployed
- **FR28:** Developers can view DORA metric trends for an application over time
- **FR29:** Developers can view golden signal metrics for an application per environment
- **FR30:** Developers can deep-link from the portal to the relevant Grafana dashboard scoped to the specific application and environment
- **FR31:** Developers can launch a DevSpaces web IDE session for an application directly from the portal, scoped to the application's repository
- **FR32:** Team leads can view a team-level dashboard showing all applications, their health across environments, and recent activity
- **FR33:** Team leads can view an activity feed of recent builds, deployments, and releases across all team applications
- **FR34:** Team leads can view DORA metric trends aggregated across their team's applications
- **FR35:** Team leads can drill down from the team-level overview to a specific application's detail view
- **FR36:** Developers can deep-link from any portal view to the corresponding native tool UI (Tekton, ArgoCD, Grafana, Vault) scoped to the exact resource in context
- **FR37:** Developers can manage application secrets by navigating to the appropriate Vault path via the portal
- **FR38:** The system can restrict production deployments to team leads, while allowing any team member to deploy to pre-production environments

### NonFunctional Requirements

- **NFR1:** Page load time < 5 seconds for any view, including live state retrieval from platform tools
- **NFR2:** SPA navigation between views < 500ms via client-side routing, no full page reload
- **NFR3:** API responses < 3 seconds for standard read operations, < 10 seconds for orchestration operations (onboarding, deployment triggers)
- **NFR4:** CI pipeline log streaming responsive enough that a developer does not need to switch to the Tekton UI for status
- **NFR5:** All authentication via OIDC — no portal-specific user accounts or passwords
- **NFR6:** The portal must never store long-lived credentials for platform tools; Vault-issued credentials short-lived, cached within TTL
- **NFR7:** Production deployment authorization enforced server-side — not a frontend-only check
- **NFR8:** All communication between the portal and platform tools over TLS
- **NFR9:** The portal's own Vault credentials scoped to the minimum required permissions
- **NFR10:** Support up to 50 onboarded teams before requiring significant refactoring
- **NFR11:** Horizontal scaling of stateless application tier via additional pod replicas
- **NFR12:** WCAG 2.1 AA accessibility compliance
- **NFR13:** When a platform tool is unreachable, display a clear error indicating the affected system — no silent failures
- **NFR14:** No caching or persisting state from platform tools beyond the current request; live state from source-of-truth systems
- **NFR15:** Deployable on-premises in disconnected environments — no external cloud dependencies

### Additional Requirements

- **AR1:** Project scaffolding via Quarkus + Quinoa monorepo (single deployable: Quarkus serves REST API + React SPA via Quinoa extension)
- **AR2:** Quarkus initialization with core extensions: rest, rest-jackson, oidc, hibernate-orm-panache, jdbc-postgresql, quinoa, smallrye-health
- **AR3:** React SPA scaffold in `src/main/webui/` using Vite + React + TypeScript + PatternFly 6
- **AR4:** Flyway database migrations for 4 core entities: Cluster, Team, Application, Environment
- **AR5:** Two-layer authorization: Casbin (jCasbin) RBAC for permissions + TeamContext CDI bean for tenant isolation
- **AR6:** Three-tier Casbin role model: member (read + onboard + deploy pre-prod), lead (inherits member + deploy prod), admin (inherits lead + cluster CRUD)
- **AR7:** SecretManagerAdapter interface with VaultSecretManagerAdapter implementation; TTL-aware in-memory credential caching via SecretManagerCredentialProvider
- **AR8:** GitProvider interface with GitHub, GitLab, Gitea, Bitbucket implementations; active provider selected via configuration
- **AR9:** Qute template engine for GitOps YAML manifest generation (Namespace objects, ArgoCD Application manifests)
- **AR10:** Adapter pattern for all platform integrations (ArgoCD, Tekton, Registry, Prometheus); each adapter is an @ApplicationScoped CDI bean
- **AR11:** Parallel integration calls via CompletableFuture/Mutiny for views aggregating data from multiple platform systems
- **AR12:** Standardized error response format with error code, message, detail, system identification, deep link, and timestamp
- **AR13:** PatternFly 6 (not PF5) — architecture specifies PF6 as the active version for this greenfield project
- **AR14:** JVM mode container image for deployment; native mode deferred post-MVP
- **AR15:** Prometheus HTTP API adapter for health signals (golden signals) and DORA metrics via PromQL
- **AR16:** Deep-link-only integration for Grafana, DevSpaces, ArgoCD UI, Tekton UI (URL generation, no API calls)
- **AR17:** Admin-only cluster registration function (name + API server URL stored in portal database)
- **AR18:** Onboarding workflow ends at PR creation to centralized infra repo — portal does not track PR status or real-time resource materialization
- **AR19:** Domain-centric backend package structure (each domain: entity, resource, service, DTOs, mapper)
- **AR20:** apiFetch() typed wrapper for all frontend HTTP calls with OIDC bearer token injection and error parsing
- **AR21:** Configurable JWT claims for role and team extraction (portal.oidc.role-claim, portal.oidc.team-claim)
- **AR22:** OpenShift deployment resources: Deployment with health probes, Service + Route, ConfigMap, ServiceAccount for Vault K8s auth

### UX Design Requirements

- **UX-DR1:** Environment Chain Card Row — horizontal card-based visualization of promotion chain (dev → qa → prod) with status badges, version labels, promote buttons, arrow connectors; four states per stage: Healthy (green), Unhealthy (red), Deploying (yellow), Not deployed (grey); accessibility: arrow key navigation between stages, aria-label per card with env/version/status
- **UX-DR2:** Contract Validation Checklist — per-requirement pass/fail checklist during onboarding; shows specific fix instructions for failed items (e.g., "Not found — create chart/ directory with Chart.yaml"); states: all passed, partial failure, validation in progress, repo unreachable; uses role="listitem" with combined aria-label
- **UX-DR3:** Provisioning Progress Tracker — real-time per-resource status during onboarding provisioning; shows namespace-to-cluster mapping on completed steps; failed steps expand with error detail + Retry button + deep link; aria-live="polite" for screen reader announcements; counter in header updates in real time
- **UX-DR4:** DORA Stat Card — single metric display with current value, trend arrow, percentage change from previous period; four variants: Deploy Frequency (higher=better), Lead Time (lower=better), Change Failure Rate (lower=better), MTTR (lower=better); states: improving (green), stable (grey), declining (red), no data
- **UX-DR5:** Application Health Grid Row — compact table row for team dashboard showing app name, inline environment health dots (8px, color-coded), version per env, deployment activity sparkline chart
- **UX-DR6:** Inline Promotion Confirmation — popover variant for non-production promotions (lightweight, shows target namespace + cluster), modal variant for production deployments (warning emphasis, explicit confirmation); both show version, target namespace, target cluster
- **UX-DR7:** Status vocabulary standardization — consistent mapping across all views: Success/green/✓ for Healthy/Passed/Synced, Danger/red/✕ for Failed/Unhealthy/Error/Unreachable, Warning/yellow/⟳ for In-progress/Degraded/Pending, Info/blue for informational/new activity, Custom/grey/— for Unknown/Not deployed/No data
- **UX-DR8:** Page shell layout — persistent masthead (logo, user avatar + team name) + collapsible vertical sidebar (team selector top, app list middle, "+ Onboard Application" bottom) + breadcrumb navigation (Team → App → View) + horizontal tab bar within app context (Overview, Builds, Releases, Environments, Health, Settings)
- **UX-DR9:** Application Overview page — environment chain card row at top as primary view, two-column grid below with Recent Builds table and Activity Feed
- **UX-DR10:** Team Dashboard page — four-column DORA stat card row at top, application grid with inline environment health dots in middle, two-column grid below with DORA trend charts and activity feed; default landing for team leads
- **UX-DR11:** Onboarding Wizard — PatternFly wizard with 5 steps: (1) enter Git repo URL + validate access, (2) contract validation checklist with retry, (3) provisioning plan preview showing all resources + cluster mapping, (4) provisioning execution with progress tracker, (5) completion with links to view app / open DevSpaces / trigger first build
- **UX-DR12:** Build & Release page — compact table with expandable row detail for failed builds (stage + error + logs link + Tekton deep link), "Trigger Build" primary action in page header, inline "Create Release" button on successful build rows, release badge on released builds
- **UX-DR13:** Loading and data freshness — spinner with system identification for loads > 3s ("Fetching status from ArgoCD..."), "Last refreshed" indicator on environment cards, manual refresh button (circular arrow), partial data rendering when systems unreachable with inline Alert identifying affected system
- **UX-DR14:** Empty states — contextual empty states for: no applications ("Onboard Application" CTA), no builds ("Trigger Build" CTA + DevSpaces link), no releases (guidance text), not-deployed environments (grey card with deploy CTA), no DORA data ("Available after 7 days of activity"); each includes explanation + primary action
- **UX-DR15:** Deep link treatment — link button variant with ↗ suffix (e.g., "Open in ArgoCD ↗"), opens in new tab, labeled with target tool name, scoped to exact resource context (namespace, PipelineRun ID, dashboard + namespace)
- **UX-DR16:** Error feedback — inline on the failed element (card, row, wizard step); mandatory three-part format: what happened + why (if available) + what to do; deep link to native tool for investigation; never a dead end
- **UX-DR17:** Action hierarchy — one primary button per view section, deep links use link variant with ↗, disabled buttons show tooltip explaining why (e.g., "Production deployments require team lead approval"), destructive actions use danger variant
- **UX-DR18:** Progressive action revelation — Create Release appears only after successful build, Promote to QA appears only after healthy dev deployment, Promote to Prod visible but disabled for non-leads
- **UX-DR19:** Success feedback via inline state change — no toasts, no modals, no separate success pages; the view element updating its state IS the confirmation (card turns green, badge updates, version label changes)
- **UX-DR20:** Custom component accessibility — environment chain: arrow key navigation + aria-labels; provisioning tracker: aria-live="polite"; promotion modal: focus trap + auto-focus primary action + Escape dismiss; all status: color + icon + text label (never color alone)
- **UX-DR21:** Responsive layout — sidebar collapsible to icon-only (64px) at < 1200px, environment chain horizontal scroll at narrow viewports with 180px min card width, chain never stacks vertically; desktop-first, no mobile optimization
- **UX-DR22:** Activity Feed — PatternFly DataList with custom item template showing event type (build/deploy/release), app name, version, timestamp, status; navigable (click item → relevant app/environment context)

### FR Coverage Map

| FR | Epic | Description |
|---|---|---|
| FR1 | Epic 1 | OIDC authentication |
| FR2 | Epic 1 | Team recognition from OIDC groups |
| FR3 | Epic 1 | Team-scoped data access |
| FR4 | Epic 1 | Team lead sees all team applications |
| FR5 | Epic 2 | Register application by Git repo URL |
| FR6 | Epic 2 | Validate Git repo contract |
| FR7 | Epic 2 | Detect application runtime type |
| FR8 | Epic 2 | Present onboarding provisioning plan |
| FR9 | Epic 2 | Auto-provision namespaces via GitOps |
| FR10 | Epic 2 | Auto-configure Tekton pipelines |
| FR11 | Epic 2 | Auto-configure ArgoCD applications |
| FR12 | Epic 2 | Auto-provision Vault secret stores |
| FR13 | Epic 2 | List team's onboarded applications |
| FR14 | Epic 2 | Define environment promotion chain |
| FR15 | Epic 2 | Model environments as namespaces on clusters |
| FR16 | Epic 2 | View application state across environments |
| FR17 | Epic 4 | Trigger CI pipeline |
| FR18 | Epic 4 | Monitor CI pipeline status |
| FR19 | Epic 4 | View CI pipeline logs |
| FR20 | Epic 4 | See build artifact reference |
| FR21 | Epic 4 | Create release from successful build |
| FR22 | Epic 4 | List releases for an application |
| FR23 | Epic 5 | Deploy release to environment |
| FR24 | Epic 5 | Promote release to next environment |
| FR25 | Epic 5 | View deployment status |
| FR26 | Epic 5 | Execute deployments via ArgoCD/Argo Rollouts |
| FR27 | Epic 6 | View health status per environment |
| FR28 | Epic 6 | View DORA metric trends |
| FR29 | Epic 6 | View golden signal metrics |
| FR30 | Epic 6 | Deep-link to Grafana dashboard |
| FR31 | Epic 3 | Launch DevSpaces web IDE |
| FR32 | Epic 7 | Team-level dashboard |
| FR33 | Epic 7 | Activity feed across team apps |
| FR34 | Epic 7 | Aggregated DORA metrics |
| FR35 | Epic 7 | Drill down from team to app detail |
| FR36 | Epic 3 | Deep-link to native tool UIs |
| FR37 | Epic 3 | Navigate to Vault for secrets |
| FR38 | Epic 5 | Production deployment restricted to leads |

## Epic List

### Epic 1: Foundation, Authentication & Platform Setup

Developers can log in via OIDC, see their team recognized automatically, and navigate the portal shell. Platform admins can register clusters. The portal is deployable on OpenShift as a single artifact.

**FRs covered:** FR1, FR2, FR3, FR4
**Key ARs:** AR1-AR7, AR12, AR17, AR19-AR22
**Key UX-DRs:** UX-DR7, UX-DR8, UX-DR13, UX-DR14, UX-DR17, UX-DR21
**NFRs addressed:** NFR1-3, NFR5-9, NFR10-12, NFR15

### Epic 2: Application Onboarding & Environment Chain

Developers can onboard a new application by pointing at a Git repo — the portal validates the contract, presents a provisioning plan, and creates a PR to the infra repo. Once onboarded, developers see their application's environment chain with health and version across all environments.

**FRs covered:** FR5, FR6, FR7, FR8, FR9, FR10, FR11, FR12, FR13, FR14, FR15, FR16
**Key ARs:** AR8-AR11, AR18
**Key UX-DRs:** UX-DR1, UX-DR2, UX-DR3, UX-DR9, UX-DR11, UX-DR16, UX-DR18, UX-DR19, UX-DR20
**NFRs addressed:** NFR13, NFR14

### Epic 3: Developer Tooling & Deep Links

Developers can launch a DevSpaces web IDE scoped to their application, navigate to Vault for secret management, and deep-link from any portal view to the corresponding native tool UI (Tekton, ArgoCD, Grafana, Vault) scoped to the exact resource in context.

**FRs covered:** FR31, FR36, FR37
**Key ARs:** AR16
**Key UX-DRs:** UX-DR15

### Epic 4: CI Pipeline, Build & Release Management

Developers can trigger CI pipelines, monitor build execution with developer-friendly status, view logs, see resulting artifacts, and create versioned releases from successful builds.

**FRs covered:** FR17, FR18, FR19, FR20, FR21, FR22
**Key ARs:** AR10 (Tekton adapter)
**Key UX-DRs:** UX-DR12
**NFRs addressed:** NFR4

### Epic 5: Deployment & Environment Promotion

Developers can deploy a specific release to any environment and promote it through the chain (dev → qa → prod). Production deployments are gated to team leads. ArgoCD and Argo Rollouts execute the mechanics invisibly.

**FRs covered:** FR23, FR24, FR25, FR26, FR38
**Key ARs:** AR10 (ArgoCD adapter for deployments)
**Key UX-DRs:** UX-DR1 (deploy/promote actions), UX-DR6, UX-DR18, UX-DR20
**NFRs addressed:** NFR7

### Epic 6: Observability & Health Monitoring

Developers can view application health status per environment, DORA metric trends over time, golden signal metrics, and deep-link to Grafana dashboards scoped to the specific application and environment.

**FRs covered:** FR27, FR28, FR29, FR30
**Key ARs:** AR15 (Prometheus adapter)
**Key UX-DRs:** UX-DR4

### Epic 7: Team Portfolio Dashboard

Team leads can view a team-level dashboard with all applications, health across environments, recent activity feed (builds, deployments, releases), aggregated DORA metrics, and drill down to any application's detail view.

**FRs covered:** FR32, FR33, FR34, FR35
**Key UX-DRs:** UX-DR5, UX-DR10, UX-DR22

---

## Epic 1: Foundation, Authentication & Platform Setup

Developers can log in via OIDC, see their team recognized automatically, and navigate the portal shell. Platform admins can register clusters. The portal is deployable on OpenShift as a single artifact.

### Story 1.1: Project Scaffolding & Monorepo Setup

As a developer on the portal team,
I want the project initialized as a Quarkus + Quinoa monorepo with a React/TypeScript/PatternFly 6 frontend,
So that we have a working full-stack development environment that builds and runs as a single artifact.

**Acceptance Criteria:**

**Given** the project does not yet exist
**When** the Quarkus application is created with extensions rest, rest-jackson, oidc, hibernate-orm-panache, jdbc-postgresql, quinoa, smallrye-health
**Then** the project compiles successfully with Maven
**And** the pom.xml includes all specified extensions plus rest-client-jackson and container-image-jib

**Given** the Quarkus project exists
**When** the React SPA is scaffolded in `src/main/webui/` using Vite + React + TypeScript
**Then** PatternFly 6 React packages (@patternfly/react-core, @patternfly/react-table, @patternfly/react-icons, @patternfly/react-charts) and react-router-dom are installed
**And** `npm run build` produces output in `src/main/webui/dist/`

**Given** Quinoa is configured in application.properties (dev-server.port=5173, build-dir=dist, enable-spa-routing=true, ui-dir=src/main/webui)
**When** `quarkus dev` is executed
**Then** both the Quarkus backend and Vite frontend dev server start with hot reload
**And** the SPA is accessible at the application root URL

**Given** the project is built
**When** `mvn package` completes
**Then** a single uber-jar is produced that serves both the REST API and the React SPA
**And** `Dockerfile.jvm` exists in `src/main/docker/` for building the container image

**Given** the project structure follows the architecture specification
**When** reviewing the directory layout
**Then** backend packages follow `com.portal.<domain>` convention under `src/main/java/`
**And** frontend source lives under `src/main/webui/src/` with directories: api/, hooks/, routes/, components/, types/
**And** `.gitignore` and `.editorconfig` are present with appropriate rules

**Given** PostgreSQL is required for development
**When** `quarkus dev` starts
**Then** Quarkus Dev Services automatically provisions a PostgreSQL container
**And** `application-dev.properties` configures the dev datasource

**Given** the SmallRye Health extension is included
**When** GET `/q/health/ready` is called
**Then** a 200 response is returned indicating the application is ready
**And** GET `/q/health/live` returns 200 indicating the application is alive

### Story 1.2: OIDC Authentication & Team Recognition

As a developer,
I want to log in using my organization's OIDC credentials and have my team automatically recognized from my JWT claims,
So that I can access the portal without any portal-specific registration or setup.

**Acceptance Criteria:**

**Given** the Quarkus OIDC extension is configured with the organization's OIDC provider URL
**When** an unauthenticated request is made to any `/api/v1/` endpoint
**Then** a 401 Unauthorized response is returned

**Given** a developer authenticates via the OIDC provider
**When** the JWT token is included in the Authorization header as a Bearer token
**Then** Quarkus validates the token and the request proceeds
**And** the developer's identity is extracted from the token

**Given** the JWT contains a team claim (configurable via `portal.oidc.team-claim`, default: "team")
**When** the TeamContextFilter processes the request
**Then** a TeamContext CDI bean is populated with the user's team identity
**And** the TeamContext is available for injection in all downstream services

**Given** the JWT contains a role claim (configurable via `portal.oidc.role-claim`, default: "role")
**When** the TeamContextFilter processes the request
**Then** the user's role (member, lead, or admin) is extracted and available in the request context

**Given** a Team entity and Flyway migration V1__create_teams.sql exist
**When** a developer's team from the JWT does not yet exist in the database
**Then** the team is automatically created in the teams table with the OIDC group identifier
**And** the team name and oidc_group_id are persisted

**Given** a developer is authenticated with a valid team context
**When** GET `/api/v1/teams` is called
**Then** only the teams the developer belongs to (based on JWT claims) are returned
**And** each team includes its name and identifier

**Given** the OIDC provider is unreachable
**When** a developer attempts to authenticate
**Then** the portal returns a clear error indicating the OIDC provider cannot be reached

### Story 1.3: Casbin RBAC & Authorization Layer

As a platform admin,
I want a role-based access control system that enforces member, lead, and admin permissions server-side,
So that users can only perform actions their role permits and team data is isolated.

**Acceptance Criteria:**

**Given** the Casbin model.conf defines request, policy, role, and matcher sections
**When** the CasbinEnforcer CDI bean initializes
**Then** it loads the RBAC model from `src/main/resources/casbin/model.conf`
**And** it loads the policy from `src/main/resources/casbin/policy.csv`

**Given** the policy.csv defines three roles with inheritance (admin inherits lead, lead inherits member)
**When** reviewing the policy
**Then** members can: read all resources, onboard applications, trigger builds, create releases, deploy to non-production environments
**And** leads inherit all member permissions plus: deploy to production
**And** admins inherit all lead permissions plus: cluster CRUD operations

**Given** the PermissionFilter is registered as a JAX-RS ContainerRequestFilter
**When** any API request is received after OIDC authentication
**Then** the filter extracts the user's role from the JWT claim
**And** Casbin checks whether the role is permitted to perform the requested action on the requested resource type
**And** a 403 Forbidden response is returned if the permission check fails

**Given** a developer with the "member" role requests a resource belonging to a different team
**When** the request is processed
**Then** a 404 Not Found response is returned (not 403)
**And** the response does not reveal that the resource exists in another team

**Given** a developer with the "member" role
**When** they attempt to access `/api/v1/admin/clusters`
**Then** a 403 Forbidden response is returned

**Given** a developer with the "lead" role
**When** they attempt a production deployment action
**Then** the request is permitted by Casbin

**Given** a developer with the "member" role
**When** they attempt a production deployment action
**Then** a 403 Forbidden response is returned

### Story 1.4: Portal Page Shell & Navigation

As a developer,
I want a consistent portal layout with sidebar navigation, breadcrumbs, and team context visible at all times,
So that I always know where I am and can navigate efficiently between my team's resources.

**Acceptance Criteria:**

**Given** a developer is authenticated and the SPA loads
**When** the AppShell component renders
**Then** a PatternFly Page component is displayed with a persistent masthead at the top
**And** the masthead shows the portal logo/name and the developer's avatar with their team name
**And** a collapsible vertical sidebar is displayed on the left

**Given** the sidebar is displayed
**When** reviewing its content
**Then** a team selector appears at the top of the sidebar
**And** an application list (initially empty) appears in the middle
**And** a "+ Onboard Application" button appears at the bottom of the sidebar

**Given** the sidebar is displayed at a viewport width >= 1200px
**When** the user has not interacted with the collapse toggle
**Then** the sidebar is expanded showing full labels (256px width)

**Given** the viewport width is < 1200px
**When** the page renders
**Then** the sidebar auto-collapses to icon-only mode (64px width)
**And** the user can manually toggle the sidebar open/closed

**Given** a developer navigates within the portal
**When** any page renders
**Then** breadcrumbs are displayed below the masthead showing the navigation path (Team → Application → View)
**And** each breadcrumb segment is clickable and navigates to that level

**Given** the developer is viewing an application context
**When** the application page renders
**Then** a horizontal tab bar appears below the breadcrumbs with tabs: Overview, Builds, Releases, Environments, Health, Settings
**And** the active tab is highlighted with a blue bottom border
**And** clicking a tab loads the corresponding view without a full page reload (client-side routing)

**Given** no applications have been onboarded for the developer's team
**When** the main content area loads
**Then** a PatternFly EmptyState is displayed with the message "No applications onboarded yet"
**And** a description reads "Your team is recognized — get started by onboarding your first application."
**And** a primary "Onboard Application" button is shown

**Given** the developer switches between applications in the sidebar
**When** selecting a different application
**Then** the current tab selection is preserved (e.g., if viewing Builds for app A, switching to app B shows Builds for app B)
**And** breadcrumbs update immediately

**Given** all interactive elements in the shell
**When** navigating with keyboard only
**Then** all elements are reachable via Tab key
**And** focus indicators are visible on all interactive elements (PatternFly default)
**And** the sidebar, breadcrumbs, and tabs have appropriate ARIA roles and labels

### Story 1.5: API Foundation & Error Handling

As a developer using the portal,
I want clear, consistent error messages when something goes wrong and visible loading states when data is being fetched,
So that I always know the system's state and can take appropriate action.

**Acceptance Criteria:**

**Given** the frontend needs to make an API call
**When** `apiFetch()` is used
**Then** it automatically injects the OIDC Bearer token in the Authorization header
**And** it sets Content-Type to application/json
**And** it uses relative URLs (no hardcoded base URL)
**And** it returns typed response data on success
**And** it parses error responses into typed error objects on failure

**Given** an integration adapter throws a PortalIntegrationException
**When** the global ExceptionMapper catches it
**Then** a 502 Bad Gateway response is returned with the standardized error JSON format:
```json
{
  "error": "<error-code>",
  "message": "<developer-language message>",
  "detail": "<additional context>",
  "system": "<affected-system-name>",
  "deepLink": "<url-to-native-tool-if-applicable>",
  "timestamp": "<ISO-8601-UTC>"
}
```

**Given** a PortalAuthorizationException is thrown
**When** the ExceptionMapper catches it
**Then** a 403 Forbidden response is returned with the standardized error format

**Given** a validation error occurs (e.g., malformed input)
**When** the error is returned
**Then** a 400 Bad Request response is returned with the standardized error format describing what is invalid

**Given** the frontend ErrorAlert component receives an error object
**When** it renders
**Then** a PatternFly inline Alert (danger variant) is displayed
**And** the alert shows the error message in developer-friendly language
**And** if a deepLink is present, a link button with ↗ suffix is shown to open the native tool

**Given** a view is fetching data from the backend
**When** the LoadingSpinner component is active
**Then** a PatternFly Spinner is displayed centered in the content area
**And** if loading exceeds 3 seconds, text appears identifying which system is slow (e.g., "Fetching status from ArgoCD...")

**Given** a view has loaded data
**When** the RefreshButton component is clicked
**Then** the view re-fetches data from the backend
**And** the button shows a brief spinning state during the refresh

### Story 1.6: Vault Credential Provider

As a platform operator,
I want the portal to retrieve cluster credentials from Vault using short-lived tokens with TTL-aware caching,
So that the portal can authenticate to OpenShift clusters securely without storing long-lived credentials.

**Acceptance Criteria:**

**Given** the SecretManagerAdapter interface is defined
**When** reviewing its contract
**Then** it exposes a method to retrieve credentials for a given cluster name and role
**And** it returns a credential object containing the token/kubeconfig and its TTL

**Given** the VaultSecretManagerAdapter is the active implementation (configured via `portal.secrets.provider=vault`)
**When** credentials are requested for a cluster
**Then** it calls the Vault HTTP API at the path template `/infra/{cluster}/kubernetes-secret-engine/{role}` with the cluster name interpolated
**And** it authenticates to Vault via Kubernetes auth using the pod's service account token

**Given** the SecretManagerCredentialProvider wraps the active SecretManagerAdapter
**When** credentials are requested for a (cluster, role) pair that is not cached
**Then** it delegates to the adapter to fetch fresh credentials
**And** it stores the credentials in an in-memory cache keyed by (cluster, role)
**And** it records the TTL expiry time

**Given** credentials for a (cluster, role) pair are already cached
**When** the cached credentials have not expired (TTL has not been reached)
**Then** the cached credentials are returned without calling Vault
**And** no network request is made

**Given** cached credentials are approaching TTL expiry
**When** a request for those credentials is made
**Then** fresh credentials are fetched from Vault before the cached ones expire
**And** the cache is updated with the new credentials and TTL

**Given** Vault is unreachable
**When** credentials are requested
**Then** a PortalIntegrationException is thrown with system="vault" and a developer-friendly message
**And** the error propagates to the API layer as a 502 response

**Given** the credential provider is running
**When** reviewing the runtime state
**Then** no credentials are written to the database or filesystem
**And** credentials exist only in the in-memory cache with TTL enforcement

### Story 1.7: Admin Cluster Registration

As a platform admin,
I want to register OpenShift clusters in the portal by providing their name and API server URL,
So that registered clusters are available as targets when configuring application environment-to-cluster mapping.

**Acceptance Criteria:**

**Given** a Cluster entity and Flyway migration V2__create_clusters.sql exist
**When** the application starts
**Then** the clusters table is created with columns: id (bigserial PK), name (varchar, unique), api_server_url (varchar), created_at (timestamptz), updated_at (timestamptz)

**Given** an admin user is authenticated (admin role in JWT)
**When** POST `/api/v1/admin/clusters` is called with `{"name": "ocp-dev-01", "apiServerUrl": "https://api.ocp-dev-01.example.com:6443"}`
**Then** a new cluster is created in the database
**And** a 201 Created response is returned with the cluster data including the generated id

**Given** an admin user is authenticated
**When** GET `/api/v1/admin/clusters` is called
**Then** all registered clusters are returned as a JSON array
**And** each cluster includes id, name, apiServerUrl, createdAt, and updatedAt

**Given** an admin user is authenticated
**When** PUT `/api/v1/admin/clusters/{clusterId}` is called with updated data
**Then** the cluster record is updated in the database
**And** a 200 OK response is returned with the updated cluster data

**Given** an admin user is authenticated
**When** DELETE `/api/v1/admin/clusters/{clusterId}` is called
**Then** the cluster is removed from the database
**And** a 204 No Content response is returned

**Given** an admin attempts to create a cluster with a name that already exists
**When** the POST request is processed
**Then** a 400 Bad Request response is returned indicating the cluster name must be unique

**Given** a user with "member" or "lead" role
**When** they attempt any operation on `/api/v1/admin/clusters`
**Then** a 403 Forbidden response is returned

**Given** the AdminClustersPage frontend component
**When** an admin navigates to the cluster management view
**Then** a PatternFly table displays all registered clusters with name, API server URL, and creation date
**And** action buttons for Edit and Delete are available per row
**And** a primary "Register Cluster" button is available in the page header
**And** the Register Cluster action opens a form to enter name and API server URL

---

## Epic 2: Application Onboarding & Environment Chain

Developers can onboard a new application by pointing at a Git repo — the portal validates the contract, presents a provisioning plan, and creates a PR to the infra repo. Once onboarded, developers see their application's environment chain with health and version across all environments.

### Story 2.1: Application & Environment Data Model

As a developer on the portal team,
I want Application and Environment entities with a data-driven promotion chain model,
So that portal-specific application state can be persisted and the environment promotion flow is flexible.

**Acceptance Criteria:**

**Given** the Application entity needs to be persisted
**When** Flyway migration V3__create_applications.sql runs
**Then** the applications table is created with columns: id (bigserial PK), name (varchar), team_id (bigint FK → teams), git_repo_url (varchar), runtime_type (varchar), onboarding_pr_url (varchar nullable), onboarded_at (timestamptz nullable), created_at (timestamptz), updated_at (timestamptz)
**And** a unique constraint exists on (team_id, name) — no duplicate app names within a team
**And** an index exists on team_id for efficient team-scoped queries

**Given** the Environment entity needs to be persisted
**When** Flyway migration V4__create_environments.sql runs
**Then** the environments table is created with columns: id (bigserial PK), name (varchar), application_id (bigint FK → applications), cluster_id (bigint FK → clusters), namespace (varchar), promotion_order (integer), created_at (timestamptz), updated_at (timestamptz)
**And** a unique constraint exists on (application_id, name) — no duplicate env names per app
**And** a unique constraint exists on (application_id, promotion_order) — no duplicate ordering per app
**And** an index exists on application_id for efficient application-scoped queries

**Given** the promotion chain is modeled as data via the promotion_order column
**When** environments are created for an application
**Then** each environment has a promotion_order integer (0, 1, 2, ...) that defines its position in the chain
**And** the chain is reconstructed by querying environments ordered by promotion_order ascending
**And** no hardcoded assumption of exactly three environments exists in the entity model

**Given** the Application Panache entity is defined
**When** reviewing the Java class
**Then** it follows Active Record pattern extending PanacheEntity
**And** it includes a `findByTeam(Long teamId)` query method
**And** it maps to the applications table with correct column naming (snake_case DB ↔ camelCase Java)

**Given** the Environment Panache entity is defined
**When** reviewing the Java class
**Then** it includes a `findByApplicationOrderByPromotionOrder(Long applicationId)` query method
**And** this method returns environments sorted by promotion_order, producing the promotion chain in sequence

### Story 2.2: Git Provider Abstraction

As a developer on the portal team,
I want a pluggable Git provider layer that supports multiple Git hosting platforms,
So that the portal can validate repositories and create onboarding PRs against any supported Git server.

**Acceptance Criteria:**

**Given** the GitProvider interface is defined
**When** reviewing its contract
**Then** it exposes the following operations:
- `validateRepoAccess(String repoUrl)` — confirms the portal can read the repository
- `readFile(String repoUrl, String branch, String filePath)` — reads a file's contents
- `listDirectory(String repoUrl, String branch, String dirPath)` — lists files in a directory
- `createBranch(String repoUrl, String branchName, String fromBranch)` — creates a new branch
- `commitFiles(String repoUrl, String branch, Map<String, String> files, String message)` — commits multiple files
- `createPullRequest(String repoUrl, String branch, String targetBranch, String title, String description)` — creates a PR and returns a PullRequest model with the PR URL

**Given** four implementations exist: GitHubProvider, GitLabProvider, GiteaProvider, BitbucketProvider
**When** the active provider is selected via `portal.git.provider` configuration (default: "github")
**Then** a GitProviderFactory CDI bean produces the correct implementation based on the config value
**And** only the active provider is instantiated

**Given** the GitProvider needs to authenticate to the Git server
**When** any operation is called
**Then** it uses the token configured via `portal.git.token`
**And** communication uses HTTPS

**Given** the Git server is unreachable or the token is invalid
**When** any GitProvider operation is called
**Then** a PortalIntegrationException is thrown with system="git" and a developer-friendly message describing the failure

**Given** the infra repo URL is configured via `portal.git.infra-repo-url`
**When** onboarding operations need to write to the infra repo
**Then** the configured URL is used as the target repository for branch creation, commits, and PR creation

### Story 2.3: Application Registration & Contract Validation

As a developer,
I want to register a new application by providing its Git repository URL and see a clear checklist of which contract requirements pass or fail,
So that I know exactly what needs to be fixed before my application can be onboarded.

**Acceptance Criteria:**

**Given** the OnboardingWizardPage renders step 1
**When** the developer enters a Git repository URL and submits
**Then** the backend calls `GitProvider.validateRepoAccess()` to confirm the portal can read the repository
**And** if the repo is unreachable, an error is displayed: "Cannot access repository — check the URL and ensure the portal has read access"
**And** if accessible, the wizard advances to step 2

**Given** the wizard is on step 2 (contract validation)
**When** the backend ContractValidator runs
**Then** it checks the following requirements via GitProvider file/directory reads:
1. `.helm/build/` directory exists and contains `Chart.yaml`
2. `.helm/run/` directory exists and contains `Chart.yaml`
3. `values-build.yaml` exists in `.helm/`
4. At least one `values-run-<env>.yaml` exists in `.helm/`
5. Runtime detection: presence of `pom.xml` (Quarkus/Java), `package.json` (Node.js), or `*.csproj` (.NET)

**Given** the ContractValidationChecklist component renders the results
**When** all checks pass
**Then** each item shows a green ✓ icon with the requirement name and what was found (e.g., "Runtime Detected: Quarkus via pom.xml")
**And** the header shows "5/5 passed" with a green accent
**And** the "Next" button is enabled

**Given** one or more checks fail
**When** the checklist renders
**Then** failed items show a red ✕ icon with the requirement name and a specific fix instruction (e.g., "Helm Build Chart — Not found. Create `.helm/build/` directory with a valid Chart.yaml")
**And** passed items still show green ✓
**And** the header shows "N/5 passed" with a red accent
**And** a "Retry Validation" button is available for the developer to re-validate after fixing their repo
**And** the "Next" button is disabled until all checks pass

**Given** the ContractValidator detects environment-specific values files
**When** files matching `values-run-*.yaml` are found in `.helm/`
**Then** the environment names are extracted (e.g., `values-run-dev.yaml` → "dev", `values-run-qa.yaml` → "qa")
**And** these detected environments are passed forward to the provisioning plan step as the default promotion chain

**Given** the checklist items render
**When** reviewing accessibility
**Then** each item uses role="listitem" with an aria-label combining status, requirement name, and detail text

**Given** POST `/api/v1/teams/{teamId}/applications/{appId}/onboard` is called with the Git repo URL
**When** the backend processes it
**Then** a ContractValidationResult is returned containing the pass/fail status of each check, detected runtime type, and detected environment names

### Story 2.4: Onboarding Plan & Manifest Generation

As a developer,
I want to see exactly what infrastructure will be provisioned before I confirm onboarding,
So that I can review the plan with confidence and understand the topology of my application's environments.

**Acceptance Criteria:**

**Given** the contract validation has passed and environments have been detected
**When** the wizard advances to step 3 (provisioning plan preview)
**Then** the OnboardingService assembles a provisioning plan showing:
- Namespaces to be created: one per environment + one for build, each showing the target cluster
- Namespace naming convention: `<team>-<app>-<env>` (e.g., `payments-payment-svc-dev`)
- ArgoCD Applications to be created: one build + one per environment
- The environment promotion chain in order (e.g., dev → qa → prod)

**Given** the provisioning plan is displayed
**When** the developer reviews it
**Then** each namespace shows its name and the cluster it targets (e.g., `payments-payment-svc-dev → ocp-dev-01`)
**And** the developer can select which registered cluster each environment maps to
**And** the build namespace shows its target cluster separately

**Given** the ManifestGenerator uses Qute templates
**When** generating Namespace YAML
**Then** each Namespace object includes labels: `team: <team>`, `app: <app>`, `env: <env>`, `size: default`
**And** the YAML conforms to the GitOps contract: `<cluster>/<namespace>/namespace.yaml`

**Given** the ManifestGenerator generates ArgoCD Application manifests
**When** generating for a build ArgoCD Application
**Then** it sources from the app repo's `.helm/build/` chart with `values-build.yaml`
**And** targets the build namespace on the assigned cluster
**And** the file path conforms to: `<cluster>/<namespace>/argocd-app-build.yaml`

**Given** the ManifestGenerator generates ArgoCD Application manifests
**When** generating for a run ArgoCD Application per environment
**Then** it sources from the app repo's `.helm/run/` chart with `values-run-<env>.yaml`
**And** targets the environment namespace on the assigned cluster
**And** the file path conforms to: `<cluster>/<namespace>/argocd-app-run-<env>.yaml`

**Given** the provisioning plan is displayed on the frontend
**When** reviewing the plan
**Then** the total count of resources is visible (e.g., "4 namespaces, 4 ArgoCD applications")
**And** a "Confirm & Create PR" primary button is available
**And** a "Back" secondary button returns to the contract validation step

**Given** the default promotion chain is derived from detected values-run files
**When** the plan is assembled
**Then** environments are ordered alphabetically by default (dev < prod < qa → reordered to dev, qa, prod by convention)
**And** promotion_order is assigned sequentially (0, 1, 2, ...)
**And** the chain is stored as data, not hardcoded — future stories can allow user customization of order and environment selection

### Story 2.5: Onboarding PR Creation & Completion

As a developer,
I want the portal to create an infrastructure PR with all required manifests and save my application as onboarded,
So that my application is registered and the platform team can review and approve the provisioning.

**Acceptance Criteria:**

**Given** the developer confirms the provisioning plan
**When** the "Confirm & Create PR" button is clicked
**Then** the OnboardingPrBuilder executes the following sequence:
1. Creates a new branch in the infra repo named `onboard/<team>-<app>`
2. Commits all generated manifest files (namespace YAMLs + ArgoCD Application YAMLs) to the branch
3. Creates a pull request from the branch to the infra repo's main branch
**And** the PR title follows the format: "Onboard <team>/<app> — <N> namespaces, <M> ArgoCD applications"
**And** the PR description lists all resources being created

**Given** the wizard is on step 4 (PR creation)
**When** the PR creation is in progress
**Then** the ProvisioningProgressTracker component shows the steps:
1. "Creating branch in infra repo" — pending/in-progress/completed
2. "Committing manifests" — pending/in-progress/completed
3. "Creating pull request" — pending/in-progress/completed
**And** each step transitions through states: ○ pending → ⟳ in-progress → ✓ completed or ✕ failed
**And** the progress counter updates in real time (e.g., "2/3 complete")
**And** the tracker uses aria-live="polite" for screen reader announcements

**Given** the PR is created successfully
**When** the backend processes the completion
**Then** the Application entity is saved to the database with name, team, gitRepoUrl, runtimeType, and onboardingPrUrl set to the PR URL
**And** Environment entities are created for each environment in the promotion chain with the correct cluster, namespace, and promotion_order
**And** onboardedAt is set to the current timestamp

**Given** the PR creation succeeds
**When** the wizard advances to step 5 (completion)
**Then** a success state is displayed with the message "Application onboarded successfully"
**And** a link to the PR is shown: "View onboarding PR ↗" (opens in new tab)
**And** a primary action "View <app-name>" navigates to the application overview
**And** a secondary link "Open in DevSpaces ↗" is shown (placeholder — functional in Epic 3)

**Given** any step in the PR creation fails
**When** the failure occurs
**Then** the failed step shows ✕ with an error message in developer language
**And** a "Retry" button is available to retry from the failed step
**And** completed steps remain ✓ and are not re-executed
**And** a deep link to the Git server is shown if applicable

**Given** the onboarding endpoint is called
**When** a developer with "member" or "lead" role submits the request
**Then** the Casbin permission check allows the operation
**And** the TeamContext ensures the application is scoped to the developer's team

### Story 2.6: Application List & Team Navigation

As a developer,
I want to see all my team's onboarded applications in the sidebar and navigate to any of them,
So that I can quickly access the applications I work on.

**Acceptance Criteria:**

**Given** a developer is authenticated and has applications onboarded for their team
**When** GET `/api/v1/teams/{teamId}/applications` is called
**Then** all applications belonging to the team are returned, ordered by name
**And** each application includes: id, name, runtimeType, onboardedAt, onboardingPrUrl

**Given** the sidebar renders the application list
**When** the developer's team has onboarded applications
**Then** each application appears as a clickable item in the sidebar below the team selector
**And** the currently selected application is visually highlighted
**And** clicking an application navigates to its overview page (ApplicationOverviewPage)

**Given** the sidebar shows the application list
**When** the developer's team has no onboarded applications
**Then** the sidebar shows only the "+ Onboard Application" button
**And** the main content area shows the EmptyState: "No applications onboarded yet" with the onboard CTA

**Given** a developer navigates to an application
**When** the ApplicationOverviewPage loads
**Then** the breadcrumbs update to: Team Name → Application Name → Overview
**And** the application-context tab bar is visible (Overview, Builds, Releases, Environments, Health, Settings)
**And** the Overview tab is active by default

**Given** the application list API is called
**When** the TeamContext filter processes the request
**Then** only applications belonging to the authenticated user's team are returned
**And** applications from other teams are never visible (404 if accessed directly by ID)

### Story 2.7: ArgoCD Adapter & Environment Status

As a developer on the portal team,
I want an ArgoCD adapter that fetches application sync and health status from the ArgoCD API,
So that the portal can display live environment state without exposing ArgoCD concepts to developers.

**Acceptance Criteria:**

**Given** the ArgoCdAdapter is an @ApplicationScoped CDI bean
**When** it needs to query an ArgoCD instance
**Then** it uses the ArgoCD REST API at the URL configured via `portal.argocd.url`
**And** it authenticates using the configured ArgoCD credentials or token

**Given** the adapter is asked for the status of an application's environments
**When** `getEnvironmentStatuses(String appName, List<Environment> environments)` is called
**Then** it queries ArgoCD for each ArgoCD Application matching the naming convention (e.g., `<app>-run-<env>`)
**And** it returns a list of EnvironmentStatusDto objects in portal domain language

**Given** the ArgoCD API returns sync and health status for an application
**When** the adapter translates the response
**Then** ArgoCD "Synced" + "Healthy" maps to portal status "Healthy"
**And** ArgoCD "Synced" + "Degraded" or "Missing" maps to portal status "Unhealthy"
**And** ArgoCD "OutOfSync" or "Progressing" maps to portal status "Deploying"
**And** ArgoCD application not found maps to portal status "Not Deployed"
**And** the deployed image tag / version is extracted from the ArgoCD application status

**Given** the adapter queries ArgoCD for multiple environments
**When** parallel calls are needed for performance
**Then** calls to ArgoCD are executed in parallel using CompletableFuture
**And** results are aggregated before returning

**Given** ArgoCD is unreachable
**When** the adapter attempts to query it
**Then** a PortalIntegrationException is thrown with system="argocd" and a message: "Deployment status unavailable — ArgoCD is unreachable"
**And** the deep link to ArgoCD UI is included if the URL is configured

**Given** the adapter returns environment status
**When** reviewing the EnvironmentStatusDto
**Then** it contains: environmentName, status (Healthy/Unhealthy/Deploying/NotDeployed), deployedVersion, lastDeployedAt, argocdAppName, and a deep link URL to the ArgoCD Application UI

### Story 2.8: Environment Chain Visualization

As a developer,
I want to see my application's environment promotion chain as a visual card row showing health, version, and status per environment,
So that I can instantly understand where my application stands across all environments.

**Acceptance Criteria:**

**Given** GET `/api/v1/teams/{teamId}/applications/{appId}/environments` is called
**When** the backend processes the request
**Then** it reads the Environment entities for the application (ordered by promotion_order)
**And** it calls the ArgoCdAdapter in parallel to fetch live status for each environment
**And** it returns a combined response with both stored environment data (name, cluster, namespace, promotionOrder) and live status (health, deployedVersion, lastDeployedAt)
**And** the response respects the data-driven promotion chain — rendering whatever environments are defined, not a hardcoded three

**Given** the EnvironmentChain Card Row component renders
**When** the environment data is loaded
**Then** one card is displayed per environment in the application's promotion chain, ordered left to right by promotion_order
**And** arrow connectors (→) are displayed between adjacent cards
**And** the chain renders dynamically based on the number of environments (2, 3, 4, or more) — not hardcoded to three

**Given** an environment card renders with status "Healthy"
**When** reviewing the card
**Then** a green top border (3px) is displayed
**And** a PatternFly Label with success variant shows "✓ Healthy"
**And** the deployed version is displayed (e.g., "v1.4.2")
**And** the last deployment timestamp is shown (e.g., "2h ago, Marco")
**And** a "Promote to [next env]" button is visible (placeholder — functional in Epic 5)

**Given** an environment card renders with status "Unhealthy"
**When** reviewing the card
**Then** a red top border (3px) is displayed
**And** a PatternFly Label with danger variant shows "✕ Unhealthy"
**And** the promote button is disabled
**And** a deep link "Open in ArgoCD ↗" is visible

**Given** an environment card renders with status "Deploying"
**When** reviewing the card
**Then** a yellow/gold top border (3px) is displayed
**And** a PatternFly Label with warning variant shows "⟳ Deploying v1.4.2..."
**And** no action buttons are shown (deployment in progress)

**Given** an environment card renders with status "Not Deployed"
**When** reviewing the card
**Then** a grey top border (3px) is displayed
**And** a PatternFly Label with custom/grey variant shows "— Not deployed"
**And** a "Deploy" button is visible if a release exists (placeholder — functional in Epic 5)

**Given** the ApplicationOverviewPage renders
**When** the environment data has loaded
**Then** the Environment Chain Card Row is displayed at the top of the page as the primary view element
**And** below the chain, a two-column grid shows a "Recent Builds" section (placeholder — populated in Epic 4) and an "Activity" section (placeholder — populated in Epic 7)

**Given** the environment chain cards are displayed
**When** a developer hovers over a card
**Then** a subtle shadow elevation appears and cursor changes to pointer
**And** clicking the card body expands it to show: deployment history placeholder, environment details (namespace, cluster), and deep links to ArgoCD ↗ and Grafana ↗ (placeholder — functional in Epics 3/6)

**Given** the environment chain component
**When** navigating with keyboard
**Then** arrow keys move focus between chain cards (left/right)
**And** each card has an aria-label: "[Env name] environment, version [X], [status]"
**And** status is communicated via color + icon + text label (never color alone)

**Given** the viewport is narrower than the chain's natural width
**When** the chain would overflow
**Then** horizontal scrolling is enabled
**And** each card maintains a minimum width of 180px
**And** the chain never stacks vertically

**Given** ArgoCD is unreachable when the page loads
**When** the environment status cannot be fetched
**Then** environment cards render with the data available from the database (name, cluster, namespace)
**And** a PatternFly inline Alert (warning) appears: "Deployment status unavailable — ArgoCD is unreachable"
**And** health badges show "Status unavailable" in grey

---

## Epic 3: Developer Tooling & Deep Links

Developers can launch a DevSpaces web IDE scoped to their application, navigate to Vault for secret management, and deep-link from any portal view to the corresponding native tool UI (Tekton, ArgoCD, Grafana, Vault) scoped to the exact resource in context.

### Story 3.1: Deep Link Service & Shared Component

As a developer,
I want contextual deep links to native platform tools that land me in the exact right place,
So that I can seamlessly transition from the portal's abstraction to the native tool when I need deeper investigation.

**Acceptance Criteria:**

**Given** the DeepLinkService CDI bean is configured with base URLs for all native tools
**When** reviewing the configuration
**Then** the following properties are read from application configuration:
- `portal.argocd.url` — ArgoCD UI base URL
- `portal.tekton.dashboard-url` — Tekton Dashboard base URL
- `portal.grafana.url` — Grafana base URL
- `portal.grafana.dashboard-id` — Grafana dashboard ID for application health
- `portal.devspaces.url` — DevSpaces base URL
- `portal.vault.url` — Vault UI base URL (for secret management navigation)

**Given** the DeepLinkService is called for an ArgoCD deep link
**When** `generateArgoCdLink(String argocdAppName)` is called
**Then** it returns `{argocdUrl}/applications/{argocdAppName}`

**Given** the DeepLinkService is called for a Tekton deep link
**When** `generateTektonLink(String pipelineRunId)` is called
**Then** it returns `{tektonDashboardUrl}/#/pipelineruns/{pipelineRunId}`

**Given** the DeepLinkService is called for a Grafana deep link
**When** `generateGrafanaLink(String namespace)` is called
**Then** it returns `{grafanaUrl}/d/{dashboardId}?var-namespace={namespace}`

**Given** the DeepLinkService is called for a DevSpaces deep link
**When** `generateDevSpacesLink(String gitRepoUrl)` is called
**Then** it returns `{devspacesUrl}/#/{gitRepoUrl}`

**Given** the DeepLinkService is called for a Vault deep link
**When** `generateVaultLink(String team, String app, String env)` is called
**Then** it returns `{vaultUrl}/ui/vault/secrets/applications/{team}/{team}-{app}-{env}/static-secrets`

**Given** a deep link URL needs to be included in an API response
**When** the backend assembles DTOs that reference native tools
**Then** the DeepLinkService is injected and used to generate the link
**And** the link is included in the DTO's `deepLink` or `links` field

**Given** the DeepLinkButton React component is used in the frontend
**When** it renders
**Then** it displays as a PatternFly Button with link variant
**And** the label includes the target tool name with ↗ suffix (e.g., "Open in ArgoCD ↗")
**And** clicking opens the URL in a new browser tab (target="_blank", rel="noopener noreferrer")
**And** the component accepts props: href (string), label (string), toolName (string)

**Given** a deep link URL is not available (tool not configured or context insufficient)
**When** the DeepLinkButton would render
**Then** it is not rendered — no broken or empty links are shown

### Story 3.2: DevSpaces Launch & Vault Secret Navigation

As a developer,
I want to launch a DevSpaces web IDE for my application and navigate to my application's secrets in Vault directly from the portal,
So that I can start coding or manage secrets without manually finding the right URL.

**Acceptance Criteria:**

**Given** a developer is viewing the ApplicationOverviewPage for an onboarded application
**When** the page renders
**Then** an "Open in DevSpaces ↗" DeepLinkButton is displayed in the application header area
**And** the link is generated by DeepLinkService using the application's Git repository URL
**And** clicking it opens DevSpaces in a new tab scoped to the application's repository

**Given** DevSpaces is not configured (portal.devspaces.url is empty)
**When** the ApplicationOverviewPage renders
**Then** the "Open in DevSpaces ↗" button is not shown

**Given** a developer navigates to the Application Settings tab
**When** the ApplicationSettingsPage renders
**Then** a "Secrets Management" section is displayed
**And** for each environment in the application's promotion chain, a Vault deep link is shown
**And** each link is labeled "Manage secrets in Vault ↗" with the environment name (e.g., "dev — Manage secrets in Vault ↗")
**And** each link is scoped to the Vault path: `/applications/<team>/<team>-<app>-<env>/static-secrets`

**Given** the Vault URL is not configured
**When** the Application Settings page renders
**Then** the Secrets Management section shows a message: "Vault URL not configured — contact your platform administrator"
**And** no deep link buttons are shown

**Given** the onboarding wizard completion step (Story 2.5)
**When** the "Open in DevSpaces ↗" placeholder link was shown
**Then** it is now functional, using DeepLinkService to generate the correct URL

### Story 3.3: Deep Links on Environment Chain

As a developer,
I want each environment card in the chain to link directly to the ArgoCD Application for that environment,
So that I can investigate deployment details in ArgoCD when needed without searching for the right resource.

**Acceptance Criteria:**

**Given** an environment chain card is displayed with any status (Healthy, Unhealthy, Deploying, or Not Deployed)
**When** the card is expanded (clicked)
**Then** a "Open in ArgoCD ↗" DeepLinkButton is displayed in the expanded detail area
**And** the link is scoped to the ArgoCD Application for that specific environment (e.g., `<app>-run-<env>`)
**And** clicking opens the ArgoCD Application UI in a new tab

**Given** an environment card has status "Unhealthy"
**When** the card renders in its collapsed state
**Then** the "Open in ArgoCD ↗" deep link is also visible directly on the card (not only in expanded detail)
**And** this provides immediate access to investigation without an extra click

**Given** the backend returns environment status DTOs (from Story 2.7)
**When** the DTO includes deep link URLs
**Then** the argocdDeepLink field is populated using DeepLinkService.generateArgoCdLink()
**And** the grafanaDeepLink field is populated using DeepLinkService.generateGrafanaLink() (will be surfaced in Epic 6 when health views are built)

**Given** the ArgoCD URL is not configured
**When** environment cards render
**Then** no ArgoCD deep link buttons are shown
**And** no error is displayed — deep links are optional enhancements

**Given** a developer navigates the environment chain with keyboard
**When** a card is focused and expanded
**Then** the deep link buttons are reachable via Tab key within the expanded card
**And** each deep link button has an aria-label: "Open [environment name] in ArgoCD"

---

## Epic 4: CI Pipeline, Build & Release Management

Developers can trigger CI pipelines, monitor build execution with developer-friendly status, view logs, see resulting artifacts, and create versioned releases from successful builds.

### Story 4.1: Tekton Adapter & Pipeline Triggering

As a developer,
I want to trigger a CI build for my application from the portal,
So that I can start a pipeline without opening the Tekton UI or using kubectl.

**Acceptance Criteria:**

**Given** the TektonAdapter is an @ApplicationScoped CDI bean
**When** it needs to interact with Tekton
**Then** it uses the Kubernetes API (via the kubernetes-client extension) to create and query PipelineRun resources
**And** it authenticates to the target cluster using credentials from SecretManagerCredentialProvider (Vault-issued)
**And** the target cluster is determined from the application's build environment (the cluster assigned to the build namespace)

**Given** a developer triggers a build
**When** POST `/api/v1/teams/{teamId}/applications/{appId}/builds` is called
**Then** the TektonAdapter creates a PipelineRun in the application's build namespace
**And** the PipelineRun references the Tekton pipeline configured during onboarding
**And** a BuildSummaryDto is returned with: buildId, status ("Building"), startedAt, and the application context

**Given** the Casbin permission check runs
**When** a developer with "member" or "lead" role triggers a build
**Then** the request is permitted
**And** the TeamContext ensures the application belongs to the developer's team

**Given** the target cluster is unreachable or Tekton returns an error
**When** the build trigger is attempted
**Then** a PortalIntegrationException is thrown with system="tekton"
**And** the error message is in developer language: "Build could not be started — the build cluster is unreachable"

**Given** the TektonAdapter translates Tekton concepts
**When** returning data to the service layer
**Then** PipelineRun is translated to "Build" in all portal domain types
**And** no Tekton-specific terminology (PipelineRun, TaskRun, Step) appears in API responses or UI

### Story 4.2: Build Monitoring & Log Retrieval

As a developer,
I want to monitor my build's progress with clear status and view logs when something fails,
So that I can understand what happened without switching to the Tekton UI.

**Acceptance Criteria:**

**Given** a build has been triggered
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/builds` is called
**Then** a list of builds is returned, ordered by startedAt descending (most recent first)
**And** each build includes: buildId, status, startedAt, completedAt (if finished), duration, and artifact reference (if successful)

**Given** the TektonAdapter queries a PipelineRun status
**When** translating to portal domain
**Then** PipelineRun "Running" maps to build status "Building"
**And** PipelineRun "Succeeded" maps to "Passed"
**And** PipelineRun "Failed" maps to "Failed"
**And** PipelineRun "Cancelled" maps to "Cancelled"

**Given** a build has status "Passed"
**When** the build details are returned
**Then** the resulting container image reference is included (e.g., `registry.example.com/team/app:commit-sha`)
**And** the image reference is extracted from the PipelineRun results or the build namespace's image stream

**Given** a build has status "Failed"
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}` is called
**Then** the response includes the failed stage name in developer language (e.g., "Unit Tests", "Image Build")
**And** a summary error message is included (e.g., "Test failure in ProcessorTest.testNullRefHandling")

**Given** a developer requests build logs
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs` is called
**Then** the TektonAdapter retrieves logs from the PipelineRun's TaskRun steps
**And** logs are returned as a text stream
**And** the response is fast enough that the developer does not need to switch to the Tekton UI (NFR4)

**Given** a build is currently in progress ("Building")
**When** the status is queried
**Then** the current stage is indicated in developer language (e.g., "Building... Running unit tests")
**And** the elapsed duration is included

**Given** a Tekton deep link is available
**When** build details are returned
**Then** the tektonDeepLink field is populated using DeepLinkService.generateTektonLink(pipelineRunId)

### Story 4.3: Builds Page & Build Table

As a developer,
I want a builds page showing all my application's builds with inline status, expandable failure details, and a one-click build trigger,
So that I can manage my CI workflow efficiently from a single view.

**Acceptance Criteria:**

**Given** a developer navigates to the Builds tab for an application
**When** the ApplicationBuildsPage renders
**Then** a "Trigger Build" primary button is displayed in the page header
**And** a PatternFly Table (compact variant) lists all builds for the application

**Given** the build table renders
**When** reviewing each row
**Then** each row shows: build number/ID, status badge, start time, duration, and artifact reference (if passed)
**And** status badges use the portal status vocabulary: "✓ Passed" (success/green), "✕ Failed" (danger/red), "⟳ Building..." (warning/yellow), "Cancelled" (grey)

**Given** the developer clicks "Trigger Build"
**When** the build is initiated
**Then** a new row appears at the top of the table with status "⟳ Building..."
**And** the status updates as the build progresses (no page reload required — re-fetch on refresh button click)

**Given** a build row has status "Failed"
**When** the row is displayed
**Then** the row has a subtle red background tint
**And** the row is expandable (PatternFly expandable table row)

**Given** a developer expands a failed build row
**When** the detail area renders
**Then** it shows: the failed stage name, the error summary message, a "View Logs" link that loads the full log output, and a "Open in Tekton ↗" DeepLinkButton scoped to the PipelineRun

**Given** a build row has status "Passed"
**When** the row is displayed
**Then** the artifact reference (container image) is shown in the row
**And** a "Create Release" button is displayed inline (covered in Story 4.4)

**Given** no builds exist for the application
**When** the builds page renders
**Then** a PatternFly EmptyState is shown: "No builds yet"
**And** description: "Trigger your first build or push code to start a CI pipeline."
**And** primary action: "Trigger Build" button
**And** secondary link: "Open in DevSpaces ↗"

**Given** the build table is navigated with keyboard
**When** a user tabs through rows
**Then** expandable rows can be toggled with Enter/Space
**And** action buttons within rows are reachable via Tab

### Story 4.4: Release Creation from Successful Builds

As a developer,
I want to create a versioned release from a successful build by tagging the Git commit and associating the container image,
So that I have a named, deployable artifact ready for promotion through environments.

**Acceptance Criteria:**

**Given** a build has status "Passed" and has not yet been released
**When** the build row renders in the table
**Then** an inline "Create Release" primary button is displayed in the row's action column

**Given** a developer clicks "Create Release" on a passed build
**When** the release creation dialog appears
**Then** the developer can enter a version tag (e.g., "v1.4.2")
**And** the dialog shows the build number, commit SHA, and container image that will be associated
**And** a "Create" primary button and "Cancel" secondary button are available

**Given** the developer confirms release creation
**When** POST `/api/v1/teams/{teamId}/applications/{appId}/releases` is called with the version and buildId
**Then** the backend calls GitProvider.createTag() to tag the Git commit with the version
**And** the RegistryAdapter records the association between the version tag and the container image reference
**And** a 201 Created response is returned with the release details

**Given** the RegistryAdapter is an @ApplicationScoped CDI bean
**When** it queries or records image references
**Then** it uses the OCI Distribution API at the URL configured via `portal.registry.url`
**And** it translates registry concepts to portal domain types (releases, not tags/manifests)

**Given** a release is created successfully
**When** the build table updates
**Then** the "Create Release" button is replaced with a release badge: "Released v1.4.2"
**And** no toast or modal — the inline badge change IS the success confirmation (UX-DR19)

**Given** release creation fails (Git tag already exists, registry unreachable)
**When** the error is returned
**Then** an inline error is displayed on the build row with the error message
**And** the "Create Release" button remains available for retry

**Given** the Casbin permission check runs
**When** a developer with "member" or "lead" role creates a release
**Then** the request is permitted

### Story 4.5: Releases Page & Release List

As a developer,
I want to view all releases for my application with their version, creation date, associated build, and image reference,
So that I can see what's available for deployment and track my release history.

**Acceptance Criteria:**

**Given** a developer navigates to the Releases tab for an application
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/releases` is called
**Then** all releases for the application are returned, ordered by creation date descending
**And** each release includes: id, version, createdAt, buildId, commitSha, imageReference

**Given** the ApplicationReleasesPage renders
**When** releases exist
**Then** a PatternFly Table (compact variant) displays all releases
**And** each row shows: version tag, creation date, associated build number, commit SHA (monospace font), and container image reference (monospace font)

**Given** a release row is displayed
**When** the developer reviews it
**Then** the version tag is the primary identifier (e.g., "v1.4.2")
**And** the commit SHA is truncated to 7 characters with full SHA available on hover/tooltip
**And** the image reference shows the full registry/repository:tag format

**Given** no releases exist for the application
**When** the releases page renders
**Then** a PatternFly EmptyState is shown: "No releases yet"
**And** description: "Create a release from a successful build to start deploying."

**Given** the release list is populated
**When** Epic 5 (Deployment) is implemented
**Then** the release list provides the data needed for the "Deploy release" action — each release is a deployable artifact with a known version and image reference

---

## Epic 5: Deployment & Environment Promotion

Developers can deploy a specific release to any environment and promote it through the chain (dev → qa → prod). Production deployments are gated to team leads. ArgoCD and Argo Rollouts execute the mechanics invisibly.

### Story 5.1: Deploy Release to Environment

As a developer,
I want to deploy a specific release to any environment in my application's promotion chain,
So that I can get my code running in the target environment without using ArgoCD directly.

**Acceptance Criteria:**

**Given** the ArgoCdAdapter is extended with deployment operations
**When** `deployRelease(String argocdAppName, String imageReference, String version)` is called
**Then** it updates the ArgoCD Application's target revision or image override to reference the specified release's container image
**And** it triggers an ArgoCD sync operation on the target Application
**And** the sync is performed via the ArgoCD REST API

**Given** a developer deploys a release
**When** POST `/api/v1/teams/{teamId}/applications/{appId}/deployments` is called with `{releaseId, environmentId}`
**Then** the DeploymentService resolves the release's image reference and the environment's ArgoCD Application name
**And** the ArgoCdAdapter triggers the deployment
**And** a 201 Created response is returned with: deploymentId, releaseVersion, environmentName, status ("Deploying"), startedAt

**Given** the deployment is triggered successfully
**When** ArgoCD begins the sync
**Then** the environment card in the chain updates to show "⟳ Deploying v1.4.2..." (handled by existing status polling from Story 2.7/2.8)

**Given** the target cluster is unreachable or ArgoCD returns an error
**When** the deployment is attempted
**Then** a PortalIntegrationException is thrown with system="argocd"
**And** the error message is developer-friendly: "Deployment to [env] failed — ArgoCD could not be reached"
**And** a deep link to ArgoCD is included

**Given** the Casbin permission check runs for a non-production environment
**When** a developer with "member" or "lead" role deploys
**Then** the request is permitted

**Given** an Argo Rollouts progressive delivery strategy is configured for the target application
**When** the ArgoCD sync triggers the rollout
**Then** Argo Rollouts manages the progressive delivery invisibly
**And** the portal reflects the rollout status through the ArgoCD Application health (which incorporates rollout state)

### Story 5.2: Deployment Status & History

As a developer,
I want to see the deployment status of each environment and a history of past deployments,
So that I can track what's deployed where and when.

**Acceptance Criteria:**

**Given** a deployment has been triggered to an environment
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/deployments?environmentId={envId}` is called
**Then** a list of deployments for that environment is returned, ordered by startedAt descending
**And** each deployment includes: deploymentId, releaseVersion, status, startedAt, completedAt, deployedBy

**Given** the ArgoCdAdapter queries deployment status
**When** translating ArgoCD sync state to portal domain
**Then** ArgoCD sync "Progressing" maps to deployment status "Deploying"
**And** ArgoCD sync "Synced" + health "Healthy" maps to "Deployed"
**And** ArgoCD sync "Failed" or health "Degraded"/"Missing" maps to "Failed"
**And** Argo Rollouts status is incorporated: a rollout in progress maps to "Deploying" even if ArgoCD shows "Synced"

**Given** a deployment completes (success or failure)
**When** the environment chain re-fetches status
**Then** the environment card transitions from "⟳ Deploying v1.4.2..." to either "✓ Healthy — v1.4.2" (green) or "✕ Unhealthy" (red) with an error summary
**And** the transition is the success/failure confirmation — no separate notification (UX-DR19)

**Given** a deployment fails
**When** the environment card shows the failure
**Then** an inline error summary is displayed below the status badge (e.g., "Health check timeout after 120s")
**And** deep links to ArgoCD ↗ are available for investigation

**Given** a developer clicks on an environment card to expand it
**When** the deployment history is loaded
**Then** a list of recent deployments is shown with version, status, timestamp, and who deployed
**And** the most recent deployment is highlighted

### Story 5.3: Environment Chain Deploy & Promote Actions

As a developer,
I want deploy and promote buttons directly on the environment chain cards that appear when they're relevant,
So that I can move releases through environments with minimal clicks.

**Acceptance Criteria:**

**Given** an environment card has status "Not Deployed" and releases exist for the application
**When** the card renders
**Then** a "Deploy" button is displayed on the card
**And** clicking "Deploy" opens a dropdown/popover listing available releases (version + creation date)
**And** selecting a release initiates the deployment (calls POST /deployments)

**Given** an environment card has status "Healthy"
**When** the environment is NOT the last in the promotion chain
**Then** a "Promote to [next env name]" button is displayed on the card
**And** clicking it initiates a deployment of the same release to the next environment in the chain (by promotion_order)

**Given** an environment card has status "Healthy"
**When** the environment IS the last in the promotion chain (e.g., prod)
**Then** no promote button is displayed (there is no next environment)

**Given** an environment card has status "Unhealthy" or "Deploying"
**When** the card renders
**Then** the promote button is NOT displayed (cannot promote from an unhealthy or in-progress state)

**Given** a deployment is in progress on an environment
**When** the card shows "⟳ Deploying..."
**Then** no deploy or promote buttons are shown on that card
**And** the developer waits for the deployment to complete before further action

**Given** progressive action revelation (UX-DR18)
**When** reviewing the chain after a fresh build and release
**Then** the deploy button appears on the first environment only after a release exists
**And** the promote button appears on an environment only after a successful deployment to it
**And** actions guide the natural left-to-right flow through the chain

### Story 5.4: Promotion Confirmation & Production Gating

As a team lead,
I want production deployments to require explicit confirmation and be restricted to my role,
So that production changes are intentional and authorized.

**Acceptance Criteria:**

**Given** a developer clicks "Promote to [non-production env]" (e.g., QA)
**When** the confirmation appears
**Then** a PatternFly Popover is displayed (lightweight, attached to the promote button)
**And** it shows: "Promote v1.4.2 to QA?" with the target namespace and cluster (e.g., "→ orders-orders-api-qa on ocp-qa-01")
**And** "Cancel" and "Promote" buttons are available
**And** clicking "Promote" initiates the deployment
**And** clicking "Cancel" or clicking outside dismisses the popover

**Given** a developer clicks "Promote to Prod" (or the last environment if it's the production environment)
**When** the confirmation appears
**Then** a PatternFly Modal (warning variant) is displayed
**And** it shows: "⚠ Deploy to PRODUCTION" as the title
**And** the body shows: Version, Target namespace, Target cluster
**And** the text "This will deploy to production." is displayed
**And** "Cancel" and "Deploy to Prod" (danger variant) buttons are available

**Given** the production confirmation modal is displayed
**When** reviewing accessibility
**Then** focus is trapped within the modal
**And** the primary action ("Deploy to Prod") is auto-focused
**And** pressing Escape dismisses the modal
**And** focus returns to the promote button after dismissal

**Given** a developer with "member" role views a production environment card
**When** the card shows a healthy deployment in the previous environment
**Then** the "Promote to Prod" button is visible but disabled
**And** a tooltip on hover explains: "Production deployments require team lead approval"

**Given** a developer with "lead" role views the same production environment card
**When** the card shows a healthy deployment in the previous environment
**Then** the "Promote to Prod" button is visible and enabled

**Given** a developer with "member" role attempts to bypass the frontend and call the deployment API directly for production
**When** the backend processes the request
**Then** the Casbin PermissionFilter rejects the request with a 403 Forbidden response
**And** production authorization is enforced server-side, not just via frontend button state (NFR7)

**Given** a production deployment succeeds
**When** the production environment card updates
**Then** it transitions to "✓ Healthy — v1.4.2" (green)
**And** the entire chain shows the release flowing from left to right — all environments green with the same version indicates a complete promotion

---

## Epic 6: Observability & Health Monitoring

Developers can view application health status per environment, DORA metric trends over time, golden signal metrics, and deep-link to Grafana dashboards scoped to the specific application and environment.

### Story 6.1: Prometheus Adapter & Health Signals

As a developer,
I want the portal to retrieve golden signal metrics for my application from Prometheus,
So that I can see health indicators per environment without opening Grafana.

**Acceptance Criteria:**

**Given** the PrometheusAdapter is an @ApplicationScoped CDI bean
**When** it needs to query metrics
**Then** it uses the Prometheus HTTP API at the URL configured via `portal.prometheus.url`
**And** it executes PromQL queries to retrieve metric values

**Given** the adapter is asked for golden signals for an application in a specific environment
**When** `getGoldenSignals(String namespace)` is called
**Then** it queries Prometheus for the four golden signals scoped to the namespace:
- **Latency:** request duration percentiles (p50, p95, p99)
- **Traffic:** request rate (requests per second)
- **Errors:** error rate (percentage of 5xx responses)
- **Saturation:** CPU and memory utilization percentages
**And** each metric is returned with its current value and unit

**Given** the adapter translates metrics to portal domain types
**When** returning a HealthStatusDto
**Then** it includes: overall health status (Healthy/Unhealthy/Degraded), individual golden signal values, and the namespace used for scoping
**And** overall health is derived from golden signals: error rate > threshold → Unhealthy, saturation > threshold → Degraded, otherwise → Healthy

**Given** Prometheus is unreachable
**When** health signals are requested
**Then** a PortalIntegrationException is thrown with system="prometheus"
**And** the error message: "Health data unavailable — metrics system is unreachable"

**Given** no metrics exist for a namespace (new deployment, no traffic)
**When** the adapter queries Prometheus
**Then** it returns a HealthStatusDto with status "No Data" and empty metric values
**And** this is not treated as an error

**Given** the health endpoint aggregates data from multiple environments
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/health` is called
**Then** the backend queries Prometheus in parallel for each environment (using CompletableFuture)
**And** returns health status per environment as a list of HealthStatusDto objects

### Story 6.2: Application Health Page

As a developer,
I want a health page showing golden signal metrics per environment and direct links to Grafana,
So that I can verify my application is running well and investigate further when it's not.

**Acceptance Criteria:**

**Given** a developer navigates to the Health tab for an application
**When** the ApplicationHealthPage renders
**Then** it displays a section for each environment in the promotion chain (ordered by promotion_order)
**And** each section shows the environment name, overall health status badge, and golden signal metrics

**Given** the GoldenSignalsPanel component renders for an environment
**When** health data is available
**Then** it displays four metric cards in a row:
- Latency: p95 value with unit (e.g., "245ms")
- Traffic: request rate (e.g., "42 req/s")
- Errors: error percentage (e.g., "0.3%")
- Saturation: CPU/memory (e.g., "CPU 45%, Mem 62%")
**And** each metric card uses color coding: green for healthy range, yellow for warning range, red for critical range

**Given** an environment has status "Unhealthy" or "Degraded"
**When** the health section renders
**Then** the offending metrics are highlighted in red or yellow
**And** the overall status badge shows "✕ Unhealthy" (danger) or "⟳ Degraded" (warning)

**Given** the Grafana deep link is configured
**When** each environment's health section renders
**Then** a "View in Grafana ↗" DeepLinkButton is displayed
**And** the link is generated by DeepLinkService.generateGrafanaLink() scoped to the environment's namespace
**And** clicking opens the Grafana dashboard in a new tab pre-filtered to that namespace

**Given** the environment chain cards (from Epic 2)
**When** health data is available from this epic
**Then** the environment chain card health badges now reflect live golden signal data from Prometheus (in addition to the ArgoCD sync/health status)
**And** the Grafana ↗ deep link on expanded cards is now functional

**Given** Prometheus is unreachable for a specific environment
**When** the health page renders
**Then** that environment's section shows "Health data unavailable" in grey
**And** a PatternFly inline Alert (warning) identifies the affected system
**And** other environments with available data render normally

**Given** no traffic has reached a newly deployed application
**When** the health page renders
**Then** the environment section shows "No Data" with a message: "Metrics will appear once the application receives traffic"

### Story 6.3: DORA Metrics Retrieval & Display

As a developer,
I want to view DORA metric trends for my application over time,
So that I can understand my team's delivery performance and track improvement.

**Acceptance Criteria:**

**Given** the PrometheusAdapter is extended for DORA metrics
**When** `getDoraMetrics(String appName, String timeRange)` is called
**Then** it queries Prometheus for the four DORA metrics scoped to the application:
- **Deployment Frequency:** number of deployments per week over the time range
- **Lead Time for Changes:** median time from commit to production deployment
- **Change Failure Rate:** percentage of deployments that caused incidents or rollbacks
- **Mean Time to Recovery (MTTR):** median time from failure detection to resolution
**And** each metric is returned with: current value, previous period value, trend direction (improving/stable/declining), and time series data points for charting

**Given** a developer navigates to the Health tab
**When** GET `/api/v1/teams/{teamId}/applications/{appId}/dora` is called
**Then** DORA metrics are returned for the default time range (30 days)
**And** the response includes current values, trends, and time series data

**Given** the DORA metrics section renders on the ApplicationHealthPage (below golden signals)
**When** data is available
**Then** four DoraStatCard components are displayed in a row
**And** each card shows: the metric name, current value with unit (e.g., "4.2/wk", "2.1h", "2.3%", "45m"), trend arrow (↑ or ↓), percentage change from previous period (e.g., "+18% from last month")

**Given** a DoraStatCard shows an improving trend
**When** the metric direction is favorable (higher deploy freq, lower lead time/CFR/MTTR)
**Then** the trend arrow and percentage are displayed in green

**Given** a DoraStatCard shows a declining trend
**When** the metric direction is unfavorable
**Then** the trend arrow and percentage are displayed in red

**Given** a DoraStatCard has insufficient data
**When** fewer than 7 days of activity exist
**Then** the card shows "—" as the value
**And** "Insufficient data" as the label in grey
**And** trend text: "Available after 7 days of activity"

**Given** the DORA stat cards are displayed
**When** reviewing accessibility
**Then** each card has an aria-label combining metric name, value, and trend (e.g., "Deployment frequency, 4.2 per week, up 18 percent from last month")

**Given** below the stat cards
**When** time series data is available
**Then** PatternFly Chart components display trend lines for each DORA metric over the selected time range
**And** charts use the PatternFly chart theme and Victory-based rendering

**Given** Prometheus is unreachable
**When** DORA metrics are requested
**Then** the DORA section shows "Delivery metrics unavailable — metrics system is unreachable"
**And** this does not affect the golden signals section (they fail independently)

---

## Epic 7: Team Portfolio Dashboard

Team leads can view a team-level dashboard with all applications, health across environments, recent activity feed (builds, deployments, releases), aggregated DORA metrics, and drill down to any application's detail view.

### Story 7.1: Team Dashboard Backend & Aggregation

As a team lead,
I want a single API that returns all the data I need for my team's portfolio view,
So that the dashboard loads efficiently with one request instead of many.

**Acceptance Criteria:**

**Given** a team lead requests the dashboard
**When** GET `/api/v1/teams/{teamId}/dashboard` is called
**Then** a TeamDashboardDto is returned containing:
- List of ApplicationHealthSummary objects (one per team app, with per-environment health dots)
- Aggregated DORA metrics across all team applications (four metrics with trends)
- Recent activity list (last 20 events across all team apps)

**Given** the DashboardService assembles the response
**When** it needs data from multiple sources
**Then** it queries in parallel using CompletableFuture:
- Database: all applications for the team (Panache)
- ArgoCD: environment health status per application (ArgoCdAdapter)
- Prometheus: golden signal health per environment (PrometheusAdapter)
- Prometheus: aggregated DORA metrics for the team (PrometheusAdapter)
- Tekton + ArgoCD + Git: recent activity events (builds, deployments, releases)
**And** results are aggregated into the TeamDashboardDto before returning

**Given** each ApplicationHealthSummary in the response
**When** reviewing its structure
**Then** it includes: applicationId, applicationName, runtimeType, and an ordered list of environment health entries
**And** each environment health entry includes: environmentName, status (Healthy/Unhealthy/Deploying/NotDeployed), deployedVersion
**And** the environment list is ordered by promotion_order (the data-driven chain)

**Given** the aggregated DORA metrics
**When** computing team-level values
**Then** Deployment Frequency is summed across all team apps
**And** Lead Time is the median across all team apps
**And** Change Failure Rate is the weighted average across all team apps
**And** MTTR is the median across all team apps
**And** each metric includes current value, previous period value, and trend direction

**Given** the recent activity list
**When** assembling events
**Then** each event includes: eventType (build/deployment/release), applicationName, version or build number, timestamp, status, and actor (who triggered it)
**And** events are ordered by timestamp descending (most recent first)
**And** a maximum of 20 events are returned

**Given** one or more platform systems are unreachable
**When** the dashboard is assembled
**Then** available data is still returned — partial failure does not block the entire dashboard
**And** sections with unavailable data include an error indicator (e.g., health dots show grey with "Status unavailable")

**Given** any authenticated team member calls the dashboard endpoint
**When** the TeamContext scopes the request
**Then** only applications belonging to the user's team are included
**And** the dashboard is available to all roles (member, lead, admin) — not restricted to leads

### Story 7.2: Team Dashboard Page & Application Health Grid

As a team lead,
I want to see all my team's applications with their health across environments at a glance and drill into any app,
So that I can quickly spot problems and take action.

**Acceptance Criteria:**

**Given** a team lead navigates to the team dashboard
**When** the TeamDashboardPage renders
**Then** it is the default landing view when selecting a team (before selecting a specific application)
**And** the page layout follows UX-DR10: DORA stat cards at top, health grid in middle, two-column section at bottom

**Given** the DORA stat cards row renders at the top of the dashboard
**When** aggregated DORA data is available
**Then** four DoraStatCard components are displayed (reusing the component from Story 6.3)
**And** each shows the team-aggregated metric value, trend arrow, and percentage change
**And** the cards reflect team-wide performance, not a single application

**Given** the application health grid renders
**When** team applications exist
**Then** a PatternFly Table displays one row per application
**And** each row shows: application name, and one environment health dot per environment in the chain
**And** environment health dots are compact colored circles (8px): green (Healthy), red (Unhealthy), yellow (Deploying), grey (Not Deployed/Unknown)
**And** each dot has the deployed version displayed beside it
**And** a deployment activity sparkline chart is displayed at the end of the row (showing deployment frequency over the last 30 days)

**Given** the Application Health Grid Row component
**When** an environment dot is hovered
**Then** a tooltip shows: environment name, full status text, deployed version, and last deployment timestamp

**Given** a developer clicks on any application row in the health grid
**When** the click is processed
**Then** navigation occurs to that application's overview page (ApplicationOverviewPage with environment chain)
**And** breadcrumbs update to: Team → Application → Overview

**Given** an application in the grid has an unhealthy environment
**When** the row renders
**Then** the red dot is immediately visible without interaction
**And** the row does not require expansion or drilling in to discover the problem

**Given** no applications exist for the team
**When** the dashboard renders
**Then** the health grid section shows a PatternFly EmptyState: "No applications onboarded yet"
**And** a primary "Onboard Application" button is displayed

**Given** the health grid is navigated with keyboard
**When** tabbing through rows
**Then** each row is focusable
**And** Enter/Space on a row navigates to the application overview
**And** the sparkline chart has an aria-label describing the trend (e.g., "12 deployments in the last 30 days")

### Story 7.3: Activity Feed & Aggregated DORA Trends

As a team lead,
I want to see recent activity across all my team's applications and DORA trend charts,
So that I have temporal context on what's happening and how delivery performance is trending.

**Acceptance Criteria:**

**Given** the bottom section of the TeamDashboardPage renders
**When** data is available
**Then** a two-column grid layout is displayed:
- Left column: Aggregated DORA trend charts
- Right column: Activity feed

**Given** the Activity Feed component renders
**When** recent events exist
**Then** a PatternFly DataList displays events in chronological order (most recent at top)
**And** each event item shows:
- Event type icon: build (hammer), deployment (rocket), release (tag)
- Application name
- Version or build number
- Status badge (Passed/Failed/Deployed/etc.)
- Timestamp (relative, e.g., "5 minutes ago")
- Actor name (who triggered the action)

**Given** an activity feed item is displayed
**When** the developer clicks on it
**Then** navigation occurs to the relevant context:
- Build event → ApplicationBuildsPage for that app
- Deployment event → ApplicationOverviewPage (environment chain) for that app
- Release event → ApplicationReleasesPage for that app
**And** breadcrumbs update accordingly

**Given** no recent activity exists
**When** the activity feed renders
**Then** a message is shown: "No recent activity across team applications"

**Given** the aggregated DORA trend charts render in the left column
**When** time series data is available
**Then** four PatternFly Chart components display trend lines over the last 30 days:
- Deployment Frequency (line chart, deployments per day)
- Lead Time for Changes (line chart, hours)
- Change Failure Rate (line chart, percentage)
- MTTR (line chart, minutes/hours)
**And** charts use the PatternFly chart theme with consistent colors

**Given** insufficient data for DORA trend charts
**When** fewer than 7 days of data exist
**Then** the chart area shows: "Trend data available after 7 days of activity"

**Given** the activity feed and DORA charts
**When** reviewing accessibility
**Then** DataList items are keyboard-navigable (Tab between items, Enter to navigate)
**And** each item has an aria-label combining event type, app name, status, and time (e.g., "Build, checkout-api, passed, 5 minutes ago")
**And** charts have aria-labels describing the metric and trend summary
