---
title: "Product Brief: Internal Developer Portal"
status: "complete"
created: "2026-03-30"
updated: "2026-03-30"
inputs:
  - "Environment-as-a-Service blog series (parts 1-4)"
  - "Platform Engineering presentation deck"
  - "User interviews and elicitation"
---

# Product Brief: Internal Developer Portal

## Executive Summary

Enterprise development teams today face a fragmented platform experience. Despite investing in best-of-breed tools — OpenShift for compute, Tekton for CI, ArgoCD for CD, Vault for secrets, DevSpaces for IDE, and a full OTEL-based observability stack — developers must context-switch between six or more disparate UIs to ship code. Each tool has its own mental model, its own authentication flow, and its own learning curve. The result: slow onboarding, low developer productivity, and a platform team whose headcount grows linearly with the number of teams they support.

The Internal Developer Portal is a unified, self-service UI that abstracts the complexity of the underlying platform into concepts developers already understand: applications, components, builds, deployments, and releases. Developers log in with their existing OIDC identity, onboard their team and applications without tickets, write and test code in a web IDE, trigger and monitor CI pipelines, promote builds through configurable environment chains, and observe their applications' health — all from a single pane of glass.

This MVP covers six foundational capabilities delivered in four phases — onboarding, inner loop, CI/CD, and observability — designed so that a developer can go from "new team member" to "watching my commit flow through environments into production" without leaving the portal. Multi-tenant, multi-cluster, and multi-environment support is baked in from phase one. The roadmap extends toward compliance visualization, cost attribution, golden paths, and middleware-as-a-service.

## The Problem

Application developers on the platform today face compounding friction at every stage of the software delivery lifecycle:

**Fragmented tooling.** Shipping a single feature requires navigating ArgoCD for deployments, Tekton for pipelines, Vault for secrets, Grafana for metrics, and DevSpaces for coding — each with its own UI, concepts, and access patterns. Developers lose significant hours weekly to tool-switching alone.

**Ticket-based onboarding.** New teams and applications require platform team intervention. Getting from "we want to use the platform" to "we're shipping code" takes days or weeks, not minutes.

**Cognitive overload.** Developers must learn the vocabulary and navigation patterns of every underlying tool. "ArgoCD Application," "Tekton PipelineRun," "Vault KV mount path" — these are infrastructure abstractions, not developer concepts.

**Platform team bottleneck.** Every new team, application, and environment adds operational load. The platform team's FTE cost scales linearly with the number of tenants — the exact antipattern a platform should eliminate.

**No unified visibility.** There's no single place to answer "what's the health of my application across environments?" Developers piece together the picture from multiple dashboards.

The status quo works, but it works expensively — in developer time, platform team capacity, and organizational agility.

## The Solution

The Internal Developer Portal provides a single, developer-centric interface to the platform. It doesn't replace the underlying tools — it abstracts them.

**Core abstraction model.** The portal translates platform-native concepts into a developer domain model:

- **Application** and **Application Component** — not ArgoCD Applications or Kubernetes Deployments
- **Build** — not Tekton PipelineRuns
- **Deployment** and **Release** — not ArgoCD sync operations or Argo Rollouts analyses
- **Environment** — not Kubernetes namespaces with labels and annotations

**Six MVP capabilities, delivered in four phases:**

| Phase | Capabilities |
|---|---|
| 1. Onboarding | Team onboarding, developer onboarding, application onboarding |
| 2. Inner Loop | Web IDE integration via DevSpaces |
| 3. CI/CD | CI pipeline visibility (Tekton), CD with environment modeling (ArgoCD + Argo Rollouts) |
| 4. Observability | DORA metrics, golden signals, Grafana deep links |

Multi-tenant, multi-cluster, and multi-environment support is foundational — baked in from phase one, not bolted on later.

1. **Team & Application Onboarding** — If your team exists in the OIDC provider, members log in and start working. Point at a Git repo that follows a defined contract; the portal wires up pipelines, deployments, and monitoring automatically. Zero tickets.
2. **Inner Loop** — Open your application code in DevSpaces directly from the portal. Edit, test, commit — all in-browser.
3. **CI** — Commits trigger Tekton pipelines. The portal surfaces build status, logs, and artifact references in developer-friendly terms.
4. **CD** — Model environments per application (e.g., dev → qa → staging → prod). Deploy tagged builds to any environment. ArgoCD and Argo Rollouts handle the mechanics invisibly.
5. **Observability** — DORA metrics and golden signals per application per environment, with deep links into Grafana for advanced analysis.

**Architecture.** The portal is a stateless UI layer backed by a database for persistence. It integrates with all platform tools and OpenShift clusters over their APIs, refreshing state from the source-of-truth systems on every page load. It is not the source of truth — it is a read-write layer on top of the tools that are. HA is achieved by running multiple stateless instances. Multiple runtimes are supported from day one: Quarkus, Node.js, and .NET, covering the bulk of the organization's application portfolio.

## What Makes This Different

**Full lifecycle, not just a catalog.** Existing developer portals (Backstage, Port, Cortex) are catalog-first — they index services and show metadata. This portal covers the entire developer workflow: from writing code to observing it in production.

**Red Hat stack-native.** Built specifically for the Red Hat/OpenShift ecosystem. No generic plugin scaffolding that requires months of custom development to integrate with Tekton, ArgoCD, DevSpaces, and Vault. Red Hat does not offer a compelling developer portal today — Backstage failed to deliver on that promise. This fills the gap.

**Developer language, not infrastructure language.** The core design principle is abstraction: developers think in applications, builds, and releases — not in Kubernetes resources, GitOps repositories, or Vault paths.

**Self-service by design.** Every capability follows the same pattern: if preconditions are met (team exists in OIDC, repo follows the contract, environments are defined), developers proceed without tickets or waiting.

**Progressive disclosure, not a walled garden.** The portal is the happy path, not a cage. When abstractions aren't enough — a failed pipeline needs task-level logs, an out-of-sync deployment needs a GitOps diff — deep links into the native tool UIs (Tekton, ArgoCD, Grafana, Vault) are first-class features, scoped to the exact resource in context. Developers should never feel trapped.

## Who This Serves

**Primary: Application Developers.** They write code, run tests, and ship features. They want to focus on business logic, not platform mechanics. They need visibility into builds, deployments, and application health. Today they spend significant time context-switching across tools.

**Secondary: Development Team Leads.** They need cross-environment visibility into team activity, deployment status, and application health. They want confidence that the platform accelerates rather than constrains their team.

**Downstream: Platform Engineering Team.** Reduced ticket volume as self-service adoption grows. FTE cost stays sublinear as the number of teams and applications scales.

**Future: Engineering Managers and Finance.** As cost visibility and quality scores mature, the portal becomes a decision-making surface for resource allocation and team health.

## Success Criteria

| Metric | Target |
|---|---|
| Time to onboard a new team | Minutes, not days |
| Time to onboard a new application | Under 1 hour from Git repo to running in dev |
| Self-service rate | >90% of onboarding and deployment actions without platform team tickets |
| Platform team FTE growth | Sublinear: doubling teams does not double platform engineers |
| Developer satisfaction (NPS) | Positive trend, tracked quarterly |
| DORA / key metric improvement | At least one order of magnitude improvement on a DORA metric or other metric the organization cares about |

**Adoption philosophy:** Voluntary, not mandated. Developer teams should flock to the portal because they see undeniable value. Empirical experience shows this happens when at least one key metric (e.g., deployment frequency, lead time for changes) improves by an order of magnitude compared to the tool-by-tool status quo.

## Scope

**MVP includes (phased delivery):**

- Phase 1: Team, developer, and application onboarding via OIDC identity and Git repo contract
- Phase 2: Inner loop via DevSpaces integration
- Phase 3: CI pipeline triggering and visibility (Tekton); CD with configurable environment promotion chains (ArgoCD + Argo Rollouts)
- Phase 4: Observability — DORA metrics, golden signals, Grafana deep links
- Cross-cutting: Multi-tenant, multi-cluster, multi-environment support from phase 1
- Supported runtimes: Quarkus, Node.js, .NET

**Explicitly out of scope for MVP:**

- Platform infrastructure management (cluster provisioning, operator lifecycle)
- Cost show-back / charge-back
- Compliance and security dashboards
- Golden path authoring and management
- Middleware-as-a-service provisioning
- CLI or API access for portal operations (power users and automation)
- Database high availability (stateless layer HA only)

## Vision

If the MVP succeeds, this portal becomes the home base for every developer in the organization. Over 2–3 years:

- **Cost visibility** — Show-back and charge-back give teams ownership of their resource consumption.
- **Compliance and security** — Vulnerability scanning, policy compliance visualization, and audit trails integrated into the developer workflow.
- **Golden paths as a product** — Opinionated, pre-engineered paths to production that teams can adopt or intentionally deviate from with full visibility.
- **Middleware-as-a-service** — Move beyond stateless applications to support databases, messaging, and other dependencies as self-service platform capabilities.
- **Application quality scores** — Aggregate code quality, test coverage, security posture, and operational maturity into actionable dashboards.
- **Extended runtime support** — Expand beyond the initial three runtimes to cover the full range the organization needs.

The OIDC-centric identity model established in MVP becomes the spine for all of this: every action is identity-bound, creating a natural audit trail and control surface for compliance features without retrofitting.

The maturity arc: *no portal → portal with no golden paths → portal with golden paths → golden paths as a product.*
