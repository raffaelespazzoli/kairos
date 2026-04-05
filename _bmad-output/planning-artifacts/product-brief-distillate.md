---
title: "Product Brief Distillate: Internal Developer Portal"
type: llm-distillate
source: "product-brief.md"
created: "2026-03-30"
purpose: "Token-efficient context for downstream PRD creation"
---

# Product Brief Distillate: Internal Developer Portal

## Platform Architecture Context

- Platform follows GitOps-everywhere principle: all configuration (infra and tenant) stored in Git, applied via ArgoCD, Kubernetes API is the universal interface
- Namespace provisioning via GitOps: namespaces defined in a Git repo, labels/annotations drive configuration (team, application, size, environment), namespace-configuration-operator applies policies, group-sync-operator maps OIDC groups to OpenShift RBAC
- Credentials management via Vault: per-team/per-namespace secret stores at path `/applications/<team>/<namespace>/static-secrets`, OIDC-based human access, Kubernetes auth for workloads, External Secrets Operator bridges Vault → K8s secrets
- Dynamic credentials via Vault database secret engine: Crossplane provisions external resources (e.g., RDS), root credentials stored in Vault, short-lived least-privilege credentials generated on demand
- Tenant GitOps: centralized multitenant ArgoCD recommended; team-scoped Git repos with convention `/<cluster>/<namespace>/<application>`; ArgoCD Projects isolate tenants; OIDC SSO for ArgoCD UI; Vault Kubernetes secret engine mints scoped service account tokens for cluster connections
- Multi-cluster topology: hub-and-spoke model with Red Hat Advanced Cluster Management; managed clusters connected to central ArgoCD via per-team scoped credentials
- The portal must generate the right Git commits, Kubernetes manifests, and API calls that these patterns expect — it is the developer-friendly front door to the GitOps machinery

## Technical Constraints & Decisions

- Portal is a standalone application, NOT a Kubernetes operator
- Stateless UI layer backed by a database for persistence — HA via multiple stateless instances
- Database HA explicitly deferred from MVP
- Portal is a read-write layer on top of source-of-truth tools (ArgoCD, Tekton, Vault, etc.) — it is NOT the source of truth itself
- State refreshed from source systems on every page load; minor drift acceptable
- Must have network reach to all platform tools and all OpenShift clusters it manages
- OIDC authentication: portal uses the same OIDC provider as all platform tools; single identity across the stack
- Supported runtimes from day one: Quarkus, Node.js, .NET — these cover the bulk of the organization
- Application onboarding requires Git repo to follow a defined contract (to be specified during PRD/architecture) — if contract is met, automation wires up everything
- Build decision: custom build, not Backstage or other existing portal — Red Hat lacks a compelling developer portal; Backstage failed to deliver for this stack

## Developer Domain Model

- Portal translates infrastructure abstractions into developer concepts:
  - **Application** → maps to a set of ArgoCD Applications, namespaces, pipelines, and observability targets
  - **Application Component** → a deployable unit within an application
  - **Build** → a Tekton PipelineRun producing a container image pushed to the OCI registry
  - **Deployment** → an ArgoCD sync operation placing a build into an environment
  - **Release** → an Argo Rollouts progressive delivery to production
  - **Environment** → a configured namespace (or set of namespaces) in a specific cluster, with associated secrets, quotas, and RBAC
- This domain model is the core design principle — all UI, API, and data structures should speak this language

## MVP Phasing

- Phase 1 — Onboarding: team onboarding (OIDC-backed), developer onboarding, application onboarding (Git contract)
- Phase 2 — Inner Loop: web IDE via DevSpaces launched from portal context
- Phase 3 — CI/CD: Tekton pipeline triggering and visibility; ArgoCD + Argo Rollouts environment modeling and deployment
- Phase 4 — Observability: DORA metrics, golden signals per app per environment, Grafana deep links
- Cross-cutting from phase 1: multi-tenant, multi-cluster, multi-environment support
- Each phase should deliver standalone value — teams can adopt incrementally

## Scope Signals

### In for MVP
- Team/developer/app onboarding, inner loop, CI, CD, observability (6 capabilities, 4 phases)
- Quarkus, Node.js, .NET runtimes
- Multi-tenant, multi-cluster, multi-environment from day one
- Configurable environment promotion chains (e.g., dev → qa → staging → prod)
- Progressive disclosure: deep links to native tool UIs (Tekton, ArgoCD, Grafana, Vault) for debugging and edge cases
- DORA metrics and golden signals in-portal

### Explicitly Out for MVP
- Platform infrastructure management (cluster provisioning, operator lifecycle, upgrades)
- Cost show-back / charge-back
- Compliance and security dashboards
- Golden path authoring and management
- Middleware-as-a-service provisioning
- CLI or API access for portal operations
- Database high availability
- Runtimes beyond Quarkus/Node.js/.NET

### Future Roadmap Items
- Cost show-back and charge-back (FinOps)
- Compliance visualization and audit trails
- Golden paths as a product (opinionated paths to production, opt-in with visibility for deviations)
- Middleware-as-a-service (databases, messaging, beyond stateless PaaS-like deployments)
- Application quality scores (code quality, test coverage, security posture, operational maturity)
- Extended runtime support
- CLI/API for automation and power users

## Adoption Strategy

- Voluntary adoption, not mandated — teams flock to the portal because they see undeniable value
- Empirical threshold: adoption accelerates when at least one DORA metric (or org-important metric) improves by an order of magnitude
- NPS tracked quarterly as developer satisfaction signal
- No forced timeline — organic adoption driven by demonstrated value

## Competitive Intelligence

- **Backstage (CNCF):** Open-source plugin ecosystem, de facto OSS standard. 6-12 month time-to-value, 2-15 FTE maintenance burden. Not turnkey for OpenShift/Tekton/ArgoCD/DevSpaces. Maintenance fatigue widely reported.
- **Port:** Hosted IDP, no-code blueprints, fast rollout. SaaS-centric; deep on-prem OpenShift and air-gapped integration lags.
- **Cortex:** Enterprise catalog, scorecards, governance. Premium per-seat pricing; weak on inner-loop/IDE and cluster self-service.
- **OpsLevel:** Service catalog and ownership graphs. Strong on catalog; weak on CI/CD execution, secrets, and IDE inner loop.
- **Roadie (managed Backstage):** Same Backstage-shaped limitations with vendor pace constraints.
- **Humanitec / Kratix:** Platform orchestration and resources-as-code. Developer-facing portal UI often DIY.
- **Key differentiator vs all:** Full lifecycle coverage (inner loop through observability), Red Hat stack-native, developer-language abstraction, self-service by design. Competitors are catalog-first; this is workflow-first.
- Market context: ~80% of large engineering orgs expected to have platform teams by 2026. Next wave of IDPs emphasizes golden paths, DORA, and environment modeling — exactly this product's feature set.

## User Sentiment Themes (from research)

- Onboarding friction and unclear ownership are top developer complaints
- Production debugging across 5+ tools feels broken; portals that don't unify observability feel cosmetic
- Mandated platforms without self-service create "golden cage" resentment — portal must earn adoption
- Stale catalogs erode trust; if CI/CD data isn't live, adoption collapses toward shadow workflows
- Cognitive load dominates: developers want portals that measurably shrink tool-switching overhead
- Heavy DIY Backstage deployments associated with maintenance fatigue

## Platform Engineering Framework (from organization's deck)

- Four pillars of developer experience: Code Time, Build Time, Run Time, Onboarding — portal must cover all four
- FTE cost model: good platform keeps FTE sublinear with growth; known linear-cost drivers are cluster, team, user, application, namespace counts
- Sublinearity achieved via: declarative automation and farming out work to users (self-service)
- Product operating model: continuous improvement, continuous review of user needs, requests managed automatically, problems pre-emptively engineered out
- Golden path maturity: no portal → platform with no golden path → golden path → golden path as product
- Team topologies: platform teams serve value-stream-aligned delivery teams; portal is the interface between them

## EaaS Patterns the Portal Must Drive

- **Namespace provisioning:** Portal generates PRs to namespace Git repo with correct labels (team, application, size, environment); merged by platform team or automated; ArgoCD applies; namespace-configuration-operator configures quotas/RBAC
- **Secrets setup:** Vault KV stores auto-provisioned per namespace; developers set secrets via portal (or deep link to Vault); ESO or direct Vault API delivers secrets to workloads
- **Tenant GitOps:** Portal manages team Git repos; ArgoCD Applications created in team-scoped namespaces; AppProjects enforce isolation
- **Environment promotion:** Portal models environment chains; deployments flow through them; ArgoCD syncs to target clusters/namespaces
- **External resources (future):** Crossplane Claims for databases, etc. — portal could generate Claims as part of middleware-as-a-service feature

## Open Questions for PRD

- What exactly constitutes the "Git repo contract" for application onboarding? (file structure, required manifests, CI config, Dockerfile conventions)
- How does the portal handle applications that span multiple components across multiple repos?
- What is the authorization model within the portal? (who can deploy to production, who can create environments, who can onboard apps)
- How are environment promotion rules configured? (manual approval gates, automated quality gates, rollback policies)
- What level of Vault integration is in MVP? (read-only visibility, secret creation, rotation, or just deep links?)
- How does the portal discover existing applications already running on the platform (brownfield onboarding)?
- What happens when a developer makes changes directly in ArgoCD or Tekton outside the portal? (reconciliation strategy beyond "refresh on page load")
- What are the specific DORA metric collection and calculation approaches? (pipeline instrumentation, deployment tracking)
- How does the portal handle multi-component applications where components may be at different stages of promotion across environments?
- What is the portal's own operational model? (who builds it, who maintains it, SLOs, on-call)

## Rejected Alternatives

- **Backstage adoption/extension:** Rejected — long time-to-value (6-12 months), heavy maintenance (2-15 FTE), not native to OpenShift/Tekton/ArgoCD stack, "failed to deliver" per organizational assessment
- **Port / Cortex / OpsLevel (buy):** Rejected — SaaS-centric, not deep enough on Red Hat stack, catalog-first not lifecycle-first
- **Operator-based portal:** Rejected — portal is a standalone application, not tied to Kubernetes operator lifecycle
- **Mandated adoption:** Rejected — voluntary adoption driven by demonstrated order-of-magnitude improvement on key metrics
