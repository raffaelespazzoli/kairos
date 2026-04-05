---
stepsCompleted:
  - 'step-01-init'
  - 'step-02-discovery'
  - 'step-02b-vision'
  - 'step-02c-executive-summary'
  - 'step-03-success'
  - 'step-04-journeys'
  - 'step-05-domain'
  - 'step-06-innovation-skipped'
  - 'step-07-project-type'
  - 'step-08-scoping'
  - 'step-09-functional'
  - 'step-10-nonfunctional'
  - 'step-11-polish'
  - 'step-12-complete'
inputDocuments:
  - 'planning-artifacts/product-brief.md'
  - 'planning-artifacts/product-brief-distillate.md'
documentCounts:
  briefs: 2
  research: 0
  brainstorming: 0
  projectDocs: 0
classification:
  projectType: 'web_app_on_prem'
  projectTypeDescription: 'On-premises, disconnected-environment capable, multi-tenant web application'
  domain: 'platform_engineering'
  complexity: 'high'
  projectContext: 'greenfield'
  multiTenantModel: 'internal - tenants are application teams'
workflowType: 'prd'
---

# Product Requirements Document - Internal Developer Portal

**Author:** Raffa
**Date:** 2026-03-30

## Executive Summary

The Internal Developer Portal is a self-service web application that unifies the full application lifecycle — onboarding, coding, building, deploying, and observing — into a single developer-centric interface for organizations running the Red Hat/OpenShift platform stack. It replaces the current experience of navigating six or more disparate tool UIs (ArgoCD, Tekton, Vault, DevSpaces, Grafana, Argo Rollouts) with a coherent abstraction layer that speaks in developer concepts: applications, builds, deployments, and releases — not Kubernetes resources, GitOps repositories, or Vault paths.

The product targets application developers and team leads who today lose significant time to tool-switching, ticket-based onboarding, and the cognitive load of learning each platform tool's vocabulary and navigation. The portal eliminates these friction points through zero-ticket self-service workflows: if your team exists in the OIDC provider and your Git repo follows the defined contract, you onboard and ship code without waiting for anyone.

Deployable on-premises in disconnected customer environments, the portal is a stateless UI layer backed by a database, integrating with platform tools and OpenShift clusters over their APIs. It is not the source of truth — it is a read-write orchestration layer on top of the systems that are.

### What Makes This Special

**Lifecycle-first, not catalog-first.** Existing developer portals (Backstage, Port, Cortex) index services and display metadata. This portal covers the entire developer workflow end-to-end — from zero-ticket onboarding through inner-loop development, CI/CD pipeline execution, environment promotion, and production observability. No other product in the market provides full application lifecycle management natively on the Red Hat/OpenShift stack.

**Built because nothing else exists.** There is no serious contender in this space that is not SaaS-dependent, and the organization will not introduce a new vendor. Backstage was evaluated and rejected — 6-12 month time-to-value, 2-15 FTE maintenance burden, and not turnkey for the OpenShift/Tekton/ArgoCD/DevSpaces ecosystem. This product fills a gap that the market has not addressed.

**Adoption earned, not mandated.** The portal succeeds when developers voluntarily flock to it because the productivity gain is undeniable. The conviction: zero-ticket onboarding is the moment developers realize they can never go back to the old way.

## Project Classification

- **Project Type:** On-premises, disconnected-environment capable, multi-tenant web application
- **Domain:** Platform Engineering / Developer Tooling
- **Complexity:** High — multi-tenant isolation, multi-cluster topology, OIDC identity, GitOps-driven state management, integration with six platform systems
- **Project Context:** Greenfield
- **Multi-Tenant Model:** Internal — tenants are application teams within the organization

## Success Criteria

### User Success

The portal succeeds when a developer can execute the entire application lifecycle without leaving the portal or filing tickets:

- **Zero-ticket onboarding:** A new team onboards in minutes, a new application goes from Git repo to running in dev in under one hour — no platform team intervention required
- **CI execution:** Trigger and monitor CI pipelines from the portal, with build status, logs, and artifact references surfaced in developer-friendly terms
- **Release creation:** Tag a Git commit and produce a corresponding container image in the registry, all initiated from the portal
- **Environment deployment:** Deploy a release to any environment in the promotion chain, with ArgoCD and Argo Rollouts executing the mechanics invisibly
- **Health verification:** View application health status across every environment where it is deployed, from a single view
- **DORA metrics visibility:** Access DORA metric trends per application to understand delivery performance over time

The "aha" moment: a developer onboards, writes code, builds, releases, deploys, and verifies health — all from one place, with zero tickets filed.

### Business Success

| Metric | Target |
|---|---|
| Self-service rate | >90% of onboarding and deployment actions without platform team tickets |
| Developer satisfaction (NPS) | Positive trend, tracked quarterly |
| DORA metric improvement | At least one order of magnitude improvement on a key DORA metric |
| Adoption model | Voluntary — teams adopt because the productivity gain is undeniable |

### Technical Success

- Meets all performance targets defined in Non-Functional Requirements
- Deployable on-premises in disconnected customer environments
- Stateless application tier with horizontal scaling capability

### Measurable Outcomes

| Outcome | Measurement | Target |
|---|---|---|
| Team onboarding time | Time from OIDC group exists to team active in portal | Minutes |
| Application onboarding time | Time from Git repo to running in dev environment | < 1 hour |
| Daily developer tool-switching | Number of external tool UIs opened for standard workflows | Zero for covered lifecycle actions |
| Platform team ticket volume | Tickets for onboarding and deployment actions | Reduced by >90% |

## User Journeys

### Journey 1: Application Onboarding — "From Git Repo to Running in Dev"

**Persona:** Priya, senior backend developer on the Payments team. Her team just built a new microservice and needs it on the platform. In the old world, this meant filing tickets to the platform team and waiting days for namespaces, pipelines, secrets, and deployments to be wired up.

**Opening Scene:** Priya logs into the portal with her existing OIDC credentials. Her team — Payments — is already recognized because their OIDC group carries the right metadata. She sees her team's existing applications listed, and a clear path to onboard a new one.

**Rising Action:** She selects "Onboard Application" and points at the Git repository for the new service. The repo follows the defined contract — it has the expected file structure, CI configuration, and Dockerfile conventions. The portal detects the runtime (Quarkus), validates the contract, and presents the onboarding plan: namespaces to be created across the environment chain (dev, qa, staging, prod), Tekton pipelines to be wired, ArgoCD applications to be configured, Vault secret stores to be provisioned.

**Climax:** Priya confirms. The portal generates the necessary Git commits, Kubernetes manifests, and API calls that the GitOps machinery expects. Within minutes, the application exists in dev — namespace provisioned, pipeline ready, ArgoCD watching, secrets store available. No tickets filed. No platform engineer involved.

**Resolution:** Priya opens DevSpaces from the portal, makes a small code change, triggers the CI pipeline, and watches her first build succeed — all before lunch. What used to take days took minutes.

**Capabilities revealed:** Application registration, Git contract validation, runtime detection, automated namespace provisioning (via GitOps), pipeline wiring (Tekton), deployment configuration (ArgoCD), secrets provisioning (Vault), environment chain modeling.

### Journey 2: Daily Development Workflow — "Build, Release, Deploy, Verify"

**Persona:** Marco, mid-level developer on the Orders team. His team's application has been onboarded for weeks. He's shipping a new feature today.

**Opening Scene:** Marco logs in and navigates to his team's application. The dashboard shows him the current state: what's deployed where, the last build status, and health across environments.

**Rising Action:** Marco has already pushed his code. He triggers the CI pipeline from the portal. The build runs — he watches the status, sees logs in developer-friendly terms ("Building... Testing... Image pushed"), and gets a clear pass/fail result with the artifact reference.

The build passes. Marco creates a release — the portal tags the Git commit and the corresponding container image is pushed to the registry. He now has a named, versioned release ready for deployment.

**Climax:** Marco deploys the release to the dev environment. He verifies health — the portal shows the application is running, healthy, and serving traffic. He promotes to QA. Then staging. At each step, he sees the deployment status and health from the portal. ArgoCD and Argo Rollouts handle the mechanics invisibly — Marco never needs to open those tools.

**Resolution:** The release reaches production via the promotion chain. Marco checks the application health across all four environments from a single view — all green. He pulls up the DORA metrics for his application and sees deployment frequency trending up and lead time trending down. The feature is live.

**Capabilities revealed:** Application dashboard, CI pipeline triggering and monitoring, release creation (Git tag + container image), environment deployment, promotion chain execution, per-environment health status, DORA metric trends, progressive disclosure (deep links to Tekton/ArgoCD/Grafana if needed).

### Journey 3: Team Lead Visibility — "How Is My Team Doing?"

**Persona:** Diana, team lead for the Checkout squad. She manages three applications and eight developers. She needs confidence that the platform accelerates her team rather than constraining it.

**Opening Scene:** Diana logs in and sees a team-level view: all three of her team's applications, their health across environments, and recent activity — builds, deployments, releases.

**Rising Action:** She notices one application has been stuck in QA for several days — no promotion to staging. She drills into it and sees the last deployment to QA is healthy, but no release has been created since. She flags it with the developer.

She checks the DORA metrics for her team's applications — deployment frequency is up across the board since they adopted the portal. Lead time for changes has dropped significantly. She pulls up the trends to include in her upcoming sprint review.

**Climax:** Diana sees a new application her team just onboarded — it's already running in dev with a passing build. She didn't have to file any tickets or wait for platform support. The self-service model is working.

**Resolution:** Diana has the visibility she needs across her team's portfolio without opening ArgoCD, Tekton, or Grafana. She spends her time on team decisions, not tool navigation.

**Capabilities revealed:** Team-level application overview, cross-environment deployment status, activity feed (builds, deployments, releases), DORA metric trends per application, team-level aggregation, drill-down from overview to detail.

### Journey Requirements Summary

| Capability Area | Journey 1 (Onboarding) | Journey 2 (Daily Dev) | Journey 3 (Team Lead) |
|---|---|---|---|
| OIDC authentication & team recognition | x | x | x |
| Application registration & Git contract validation | x | | |
| Automated namespace/pipeline/ArgoCD/Vault provisioning | x | | |
| Environment chain modeling | x | x | x |
| DevSpaces integration | x | | |
| CI pipeline triggering & monitoring | | x | |
| Release creation (Git tag + image) | | x | |
| Environment deployment & promotion | | x | |
| Per-environment health status | | x | x |
| DORA metric trends | | x | x |
| Team-level overview & aggregation | | | x |
| Activity feed (builds, deploys, releases) | | | x |
| Deep links to native tools | | x | |

## Domain-Specific Requirements

### Deployment Model

- The portal is an on-premises application — no cloud dependency assumed
- All platform dependencies (Git server, Vault, OIDC provider, container registry, OpenShift clusters) are reachable from the portal's runtime environment but are not assumed to be cloud-hosted
- The portal itself runs on OpenShift alongside the platform tools it integrates with

### GitOps Contract

- The platform engineering team defines and owns a single, standard GitOps contract applied uniformly across all applications managed by the platform
- The portal is tightly coupled to this contract — it generates Git commits, manifests, and configurations that conform exactly to the contract's expectations
- No abstraction layer over the contract for MVP — the portal knows the contract and writes to it directly
- Contract changes require portal updates and redeployment

### Credential & Cluster Access Model

- The portal retrieves cluster credentials from Vault at path `/infra/<cluster>/kubernetes-secret-engine/<role>`
- This follows the same Vault-based credential pattern used by ArgoCD and other platform tools — the portal is a first-class platform citizen, not a special case
- Credentials are scoped per cluster and per role, limiting the portal's access surface to what it needs

### Integration Surface

The portal integrates with the following platform systems, all assumed reachable on-prem:

| System | Integration Purpose |
|---|---|
| OIDC Provider | Authentication, team identity, group metadata |
| Git Server | Repository contract validation, commit generation, tagging |
| Vault | Secrets provisioning, cluster credentials, developer secret stores |
| Tekton | CI pipeline triggering and status monitoring |
| ArgoCD | Deployment configuration, sync operations, application status |
| Argo Rollouts | Progressive delivery execution and status |
| Container Registry | Image references, release artifact tracking |
| Grafana / OTEL stack | Health signals, DORA metrics, deep links |
| DevSpaces | Inner-loop web IDE launch |

## Web Application Specific Requirements

### Project-Type Overview

The Internal Developer Portal is a single-page application (SPA) serving as the unified interface to the platform engineering stack. It is an internal enterprise tool deployed on-premises — no SEO, no public-facing concerns. The focus is on information density, fast navigation between platform concepts, and clear presentation of live system state.

### Technical Architecture Considerations

**Frontend:**
- Single-page application architecture
- PatternFly preferred as the UI component framework (aligns with Red Hat ecosystem conventions)
- Modern evergreen browsers only (Chrome, Firefox, Edge) — no legacy browser support required
- WCAG 2.1 AA accessibility compliance (standard enterprise level)
- No real-time push required — standard request/response with user-initiated refresh is sufficient for all views (pipeline status, deployment state, health checks)

**Backend:**
- Quarkus preferred as the backend framework
- Stateless application tier — all session state in the client or database, enabling horizontal scaling via multiple instances
- REST API layer between frontend SPA and backend services
- Backend orchestrates all platform tool integrations (Vault, ArgoCD, Tekton, Git, OIDC, etc.)
- Database-backed persistence for portal-specific state (application registry, environment chain configuration, onboarding metadata)

**Deployment:**
- Containerized, deployed on OpenShift
- No external/cloud dependencies — all integrations are with on-prem platform tools
- HA via multiple stateless pod replicas behind a service/route

### Responsive Design

- Desktop-first — this is a developer productivity tool used on workstations
- Responsive layout not a priority for MVP; functional at standard desktop and laptop resolutions
- No mobile or tablet optimization required

### Performance Targets

- Page load: <5 seconds including live state retrieval from platform tools
- Navigation between views: near-instant (SPA client-side routing)
- API responses from backend: <3 seconds for standard operations, <10 seconds for complex orchestration operations (e.g., application onboarding that triggers multiple downstream systems)

### Implementation Considerations

- PatternFly provides a mature component library with built-in accessibility, reducing custom UI work
- Quarkus offers fast startup, low memory footprint, and native compilation options — well-suited for on-prem deployments with resource constraints
- The SPA + REST API architecture enables independent frontend/backend development and testing
- All platform tool integrations are backend-side — the frontend never communicates directly with ArgoCD, Tekton, Vault, etc.

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Platform MVP — deliver the full application lifecycle experience across four phases, each providing standalone value. The MVP is complete when a developer can onboard an application, develop in a web IDE, build and release through CI/CD, deploy across environments, and verify health with DORA metrics — all without leaving the portal.

**Resource Model:** Side project of the platform engineering team. This constrains velocity but aligns incentives — the builders are also the operators of the underlying platform, giving them direct insight into what developers need and how the GitOps machinery works.

### MVP Feature Set (Phase 1-4)

**Core User Journeys Supported:**
- Application Onboarding (Journey 1) — Phase 1
- Daily Development Workflow (Journey 2) — Phases 2, 3, 4
- Team Lead Visibility (Journey 3) — Phase 4

**Must-Have Capabilities by Phase:**

**Phase 1 — Onboarding:**
- OIDC authentication and team recognition from group metadata
- Application registration with Git contract validation
- Automated provisioning: namespaces (GitOps), pipelines (Tekton), deployments (ArgoCD), secrets (Vault)
- Environment chain modeling per application
- Multi-tenant, multi-cluster, multi-environment from day one

**Phase 2 — Inner Loop:**
- DevSpaces launch from application context in the portal

**Phase 3 — CI/CD:**
- CI pipeline triggering and monitoring (Tekton)
- Release creation (Git tag + container image to registry)
- Environment deployment and promotion chain execution (ArgoCD + Argo Rollouts)

**Phase 4 — Observability:**
- Per-environment application health status
- DORA metric trends per application
- Golden signals
- Deep links to Grafana for advanced analysis

### Post-MVP Features

**Phase 5 (Growth):**
- CLI and API access for portal operations (power users and automation)
- Database high availability
- Extended runtime support beyond Quarkus/Node.js/.NET
- Golden path authoring and management

**Phase 6 (Vision):**
- Cost show-back and charge-back (FinOps)
- Compliance visualization, security dashboards, audit trails
- Golden paths as a product
- Middleware-as-a-service (databases, messaging)
- Application quality scores

### Risk Mitigation Strategy

**Technical Risks:**
- **Integration correctness** is the highest-risk area. The portal must generate Git commits, Kubernetes manifests, Vault configurations, and ArgoCD resources that conform exactly to the platform's GitOps contract. An incorrect write can break the platform for a team. *Mitigation:* Accept this risk. The platform team builds the portal — they own both the contract and the consumer. Tight coupling is a feature, not a bug, at this stage.
- **Integration breadth** — nine platform systems to integrate with. *Mitigation:* Phased delivery. Each phase adds integrations incrementally (Phase 1: Git, Vault, ArgoCD, OIDC; Phase 2: DevSpaces; Phase 3: Tekton, container registry; Phase 4: Grafana/OTEL).

**Market Risks:**
- Minimal. This is an internal tool for a known user base. The risk is adoption, not market fit. *Mitigation:* Voluntary adoption philosophy — the portal must earn usage through demonstrated productivity gain.

**Resource Risks:**
- **Side project velocity.** The platform team has primary operational responsibilities. Portal development competes with platform operations for the same people's time. *Mitigation:* Phased delivery with standalone value at each phase. If velocity slows, each delivered phase still provides value independently. No phase depends on a future phase to be useful.
- **Bus factor.** Small team, side project — key knowledge concentrated in few people. *Mitigation:* The portal is tightly coupled to the GitOps contract the team already maintains. Domain knowledge is shared by virtue of the team's primary role.

## Functional Requirements

### Authentication & Identity

- **FR1:** Developers can authenticate via the organization's OIDC provider using their existing credentials
- **FR2:** The system can recognize a developer's team membership from OIDC group metadata without any portal-specific registration
- **FR3:** Developers can view only the teams and applications they have access to based on their OIDC group membership
- **FR4:** Team leads can see all applications belonging to their team

### Application Onboarding

- **FR5:** Developers can register a new application by providing its Git repository URL
- **FR6:** The system can validate that a Git repository conforms to the platform's GitOps contract
- **FR7:** The system can detect the application's runtime type (Quarkus, Node.js, .NET) from the repository
- **FR8:** The system can present an onboarding plan showing what will be provisioned (namespaces, pipelines, deployments, secrets) before the developer confirms
- **FR9:** Upon confirmation, the system can automatically provision namespaces across the application's environment chain via GitOps commits
- **FR10:** Upon confirmation, the system can automatically configure Tekton pipelines for the application
- **FR11:** Upon confirmation, the system can automatically configure ArgoCD applications for each environment
- **FR12:** Upon confirmation, the system can automatically provision Vault secret stores per namespace for the application
- **FR13:** Developers can view a list of all applications onboarded by their team

### Environment Management

- **FR14:** Developers can define an environment promotion chain for an application (e.g., dev → qa → staging → prod)
- **FR15:** The system can model environments as configured namespaces across specific clusters
- **FR16:** Developers can view the state of an application across all environments in its promotion chain

### CI Pipeline Management

- **FR17:** Developers can trigger a CI pipeline for an application from the portal
- **FR18:** Developers can monitor CI pipeline execution status with developer-friendly terminology
- **FR19:** Developers can view CI pipeline logs
- **FR20:** Developers can see the resulting artifact reference (container image) from a successful build

### Release Management

- **FR21:** Developers can create a release from a successful build, which tags the Git commit and associates the container image
- **FR22:** Developers can view a list of releases for an application with their version, creation date, and associated build

### Deployment & Promotion

- **FR23:** Developers can deploy a specific release to any environment in the application's promotion chain
- **FR24:** Developers can promote a release from one environment to the next in the chain
- **FR25:** Developers can view the deployment status of a release in any environment
- **FR26:** The system can execute deployments via ArgoCD sync operations and Argo Rollouts progressive delivery without exposing these mechanisms to the developer

### Observability & Health

- **FR27:** Developers can view the health status of an application in each environment where it is deployed
- **FR28:** Developers can view DORA metric trends for an application over time
- **FR29:** Developers can view golden signal metrics for an application per environment
- **FR30:** Developers can deep-link from the portal to the relevant Grafana dashboard scoped to the specific application and environment

### Inner Loop Development

- **FR31:** Developers can launch a DevSpaces web IDE session for an application directly from the portal, scoped to the application's repository

### Team & Portfolio Views

- **FR32:** Team leads can view a team-level dashboard showing all applications, their health across environments, and recent activity
- **FR33:** Team leads can view an activity feed of recent builds, deployments, and releases across all team applications
- **FR34:** Team leads can view DORA metric trends aggregated across their team's applications
- **FR35:** Team leads can drill down from the team-level overview to a specific application's detail view

### Progressive Disclosure & Platform Integration

- **FR36:** Developers can deep-link from any portal view to the corresponding native tool UI (Tekton, ArgoCD, Grafana, Vault) scoped to the exact resource in context
- **FR37:** Developers can manage application secrets by navigating to the appropriate Vault path via the portal

### Authorization

- **FR38:** The system can restrict production deployments to team leads, while allowing any team member to deploy to pre-production environments

## Non-Functional Requirements

### Performance

- Page load time: <5 seconds for any view, including live state retrieval from platform tools
- SPA navigation between views: <500ms (client-side routing, no full page reload)
- API responses: <3 seconds for standard read operations, <10 seconds for orchestration operations (onboarding provisioning, deployment triggers)
- CI pipeline log streaming: responsive enough that a developer does not need to switch to the Tekton UI for status

### Security

- All authentication via OIDC — no portal-specific user accounts or passwords
- The portal must never store long-lived credentials for platform tools; Vault-issued credentials are short-lived and may be cached within their TTL if it improves performance
- Production deployment authorization enforced server-side (FR38) — not a frontend-only check
- All communication between the portal and platform tools over TLS
- The portal's own Vault credentials (for cluster access at `/infra/<cluster>/kubernetes-secret-engine/<role>`) scoped to the minimum required permissions
- No audit logging requirement for MVP

### Scalability

- Support up to 50 onboarded teams with their applications before requiring significant refactoring
- Horizontal scaling of the stateless application tier via additional pod replicas
- Database is the single scaling bottleneck by design — acceptable for MVP given the 50-team target
- Database HA deferred from MVP

### Accessibility

- WCAG 2.1 AA compliance (covered in Web Application Specific Requirements)
- PatternFly component library provides built-in accessibility support

### Integration Resilience

- When a platform tool is unreachable (Tekton, ArgoCD, Vault, Grafana, etc.), the portal displays a clear error indicating the affected system — no silent failures
- Graceful degradation not required for MVP — an error message is acceptable
- The portal does not cache or persist state from platform tools beyond the current request; each view fetches live state from the source-of-truth systems
