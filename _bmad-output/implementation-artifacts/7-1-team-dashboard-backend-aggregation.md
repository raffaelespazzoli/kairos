# Story 7.1: Team Dashboard Backend & Aggregation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a team lead,
I want a single API that returns all the data I need for my team's portfolio view,
so that the dashboard loads efficiently with one request instead of many.

## Acceptance Criteria

1. **Team dashboard endpoint returns the full aggregate payload**
   - **Given** a team lead requests the dashboard
   - **When** `GET /api/v1/teams/{teamId}/dashboard` is called
   - **Then** a `TeamDashboardDto` is returned containing:
     - a list of `ApplicationHealthSummaryDto` records (one per team application)
     - aggregated team-level DORA metrics
     - a recent activity list limited to 20 events
   - **And** the payload is shaped for later reuse by Stories 7.2 and 7.3, so the endpoint does not need a second contract redesign for charts or activity navigation

2. **DashboardService aggregates data in parallel without bypassing existing domain patterns**
   - **Given** the dashboard is assembled from multiple backend sources
   - **When** `DashboardService` builds the response
   - **Then** it fans out work in parallel using `CompletableFuture`
   - **And** it reuses existing domain services where they already encapsulate scope, mapping, and error behavior:
     - `ApplicationService` for team-scoped application discovery
     - `EnvironmentService` for ordered environment chain + ArgoCD/UNKNOWN behavior
     - `DoraService` and/or `PrometheusAdapter`-backed DORA logic for metric retrieval semantics
     - `BuildService`, `ReleaseService`, and `DeploymentService` for activity source data
   - **And** it unwraps `CompletionException` consistently, matching existing health and DORA patterns

3. **Application health summaries are compact, ordered, and tooltip-ready**
   - **Given** each application summary in the response
   - **When** its structure is reviewed
   - **Then** it includes `applicationId`, `applicationName`, `runtimeType`, and an ordered list of environment summary entries
   - **And** each environment entry includes:
     - `environmentName`
     - `status`
     - `deployedVersion`
     - `lastDeploymentAt`
     - `statusDetail`
   - **And** `status` uses the existing frontend-compatible vocabulary:
     - `HEALTHY`
     - `UNHEALTHY`
     - `DEPLOYING`
     - `NOT_DEPLOYED`
     - `UNKNOWN`
   - **And** the environment list is ordered by `promotion_order`

4. **Environment status aggregation follows explicit merge rules**
   - **Given** the compact dashboard health dot must summarize both deployment state and live health
   - **When** ArgoCD and Prometheus data are combined
   - **Then** these merge rules are applied consistently:
     - `DEPLOYING` and `NOT_DEPLOYED` from the environment chain remain unchanged
     - `UNKNOWN` from the environment chain remains the fallback when ArgoCD status is unavailable
     - if the base status is `HEALTHY` or `UNHEALTHY` and Prometheus reports `UNHEALTHY`, the final status is `UNHEALTHY`
     - if the base status is `HEALTHY` and Prometheus reports `DEGRADED`, the compact dashboard status is downgraded to `UNHEALTHY` and `statusDetail` records that the underlying signal was degraded
     - if Prometheus reports `NO_DATA`, the ArgoCD-derived status is preserved
   - **And** the implementation does not introduce a new competing status vocabulary for the dashboard

5. **Aggregated DORA metrics are computed from per-application data using explicit math**
   - **Given** the team dashboard needs a single team-level DORA section
   - **When** DORA values are aggregated across applications
   - **Then** Deployment Frequency is the sum across applications
   - **And** Lead Time is the median across applications with data
   - **And** Change Failure Rate is the weighted average across applications using deployment frequency as the weight
   - **And** MTTR is the median across applications with data
   - **And** each aggregated metric includes `currentValue`, `previousValue`, `trend`, `percentageChange`, `unit`, and aggregated `timeSeries`
   - **And** the response reuses `DoraMetricsDto` / `DoraMetricDto` instead of creating a second dashboard-only DORA contract

6. **Recent activity feed merges build, release, and deployment events**
   - **Given** the dashboard needs recent events across the whole team
   - **When** build, release, and deployment history are combined
   - **Then** each `TeamActivityEventDto` includes:
     - `eventType` (`build`, `release`, `deployment`)
     - `applicationId`
     - `applicationName`
     - `reference` (build ID or release/deployment version)
     - `timestamp`
     - `status`
     - `actor`
     - optional `environmentName` for deployment events
   - **And** events are sorted by timestamp descending
   - **And** only the most recent 20 events are returned
   - **And** missing actor data is normalized to a non-empty fallback such as `System` rather than returning null

7. **Partial failures do not fail the whole dashboard**
   - **Given** one or more platform systems are unreachable
   - **When** the dashboard is assembled
   - **Then** the endpoint still returns `200` with all available sections populated from successful sources
   - **And** the response exposes section-level error indicators:
     - `healthError`
     - `doraError`
     - `activityError`
   - **And** application environment entries affected by missing live status use `UNKNOWN` with a human-readable `statusDetail`
   - **And** the service never returns stale cached platform state as if it were current

8. **Authorization, scoping, and negative-path behavior follow project rules**
   - **Given** any authenticated `member`, `lead`, or `admin` requests the dashboard
   - **When** Casbin authorization runs
   - **Then** the request is permitted for `dashboard:read`
   - **And** the resource path keeps `dashboard` as the terminal segment so `PermissionFilter` resolves the correct resource
   - **Given** a request targets a non-existent team or a team outside the caller's scope
   - **Then** `404` is returned
   - **And** the response includes only applications belonging to the caller's team

## Tasks / Subtasks

- [x] Task 1: Create dashboard domain DTOs in `com.portal.dashboard` (AC: #1, #3, #6, #7)
  - [x] Create `TeamDashboardDto.java`
  - [x] Create `ApplicationHealthSummaryDto.java`
  - [x] Create `DashboardEnvironmentEntryDto.java`
  - [x] Create `TeamActivityEventDto.java`
  - [x] Shape `TeamDashboardDto` to include `applications`, `dora`, `recentActivity`, `healthError`, `doraError`, and `activityError`
  - [x] Reuse `DoraMetricsDto` for the team DORA section instead of inventing a parallel metrics DTO

- [x] Task 2: Create `DashboardService` scaffold and team-scoped loading flow (AC: #1, #2, #8)
  - [x] Create `DashboardService.java` in `com.portal.dashboard`
  - [x] Inject `ApplicationService`, `EnvironmentService`, `DoraService` or equivalent DORA helper, `BuildService`, `ReleaseService`, and `DeploymentService`
  - [x] Load team applications through `ApplicationService.getApplicationsForTeam(teamId)` rather than querying `Application.findByTeam(...)` directly inside the dashboard package
  - [x] Fan out application-level aggregation work with `CompletableFuture.supplyAsync(...)`
  - [x] Add a single `joinSafely`/unwrap helper matching the existing `HealthService` pattern

- [x] Task 3: Implement compact application health aggregation (AC: #2, #3, #4, #7)
  - [x] Reuse `EnvironmentService.getEnvironmentChain(teamId, appId)` to obtain ordered environments, existing `UNKNOWN` fallback behavior, deployed version, and last deployment timestamp
  - [x] For environments whose base status is `HEALTHY` or `UNHEALTHY`, enrich with Prometheus golden-signal health using the existing `PrometheusAdapter`
  - [x] Apply the merge rules from AC #4 exactly; do not create a second dashboard-specific status vocabulary
  - [x] Populate `statusDetail` with useful tooltip text such as `Healthy`, `Unhealthy`, `Degraded`, `Not deployed`, `Deploying`, or `Status unavailable`
  - [x] If ArgoCD status retrieval failed for an application, keep the environment entries but leave them as `UNKNOWN` with the source error reflected in `statusDetail`
  - [x] If Prometheus enrichment fails for an environment, preserve the base environment-chain status and capture the missing health signal in `statusDetail` without failing the whole application summary

- [x] Task 4: Implement aggregated DORA metric composition (AC: #1, #2, #5, #7)
  - [x] Gather per-application DORA data in parallel for the default 30-day range already used by the existing DORA flow
  - [x] Aggregate `currentValue` and `previousValue` per metric using the required math:
    - [x] Deployment Frequency: sum
    - [x] Lead Time: median
    - [x] Change Failure Rate: weighted average using deployment frequency as the weight
    - [x] MTTR: median
  - [x] Aggregate `timeSeries` as well so Story 7.3 can render charts without needing a contract change:
    - [x] align points by timestamp bucket
    - [x] sum deployment frequency points
    - [x] median lead-time points
    - [x] weighted-average change-failure-rate points using deployment-frequency points as weights when available
    - [x] median MTTR points
  - [x] Reuse the existing DORA trend semantics and percentage-change calculation rules from the current DORA implementation
  - [x] If some applications fail DORA retrieval, aggregate from successful applications and set `doraError`
  - [x] If all applications fail or all return insufficient data, return a valid `DoraMetricsDto` with `hasData = false` and a non-empty `doraError`

- [x] Task 5: Implement team activity aggregation (AC: #1, #2, #6, #7)
  - [x] For each application, fetch builds, releases, and deployments in parallel through the existing services
  - [x] Trim each source per application before global merge to keep the fan-out bounded (for example, take the most recent handful from each source, then merge and cap the final list at 20)
  - [x] Normalize source DTOs into `TeamActivityEventDto`
  - [x] Map fields explicitly:
    - [x] build event: `reference = buildId`, `timestamp = startedAt`, `status = status`
    - [x] release event: `reference = version`, `timestamp = createdAt`, `status = "Released"`
    - [x] deployment event: `reference = releaseVersion`, `timestamp = startedAt`, `status = status`, `environmentName = environmentName`
  - [x] Set `actor` from the best available source:
    - [x] deployment: existing `deployedBy`
    - [x] build: any available Tekton metadata if present, otherwise `System`
    - [x] release: any available Git/tag metadata if present, otherwise `System`
  - [x] Sort the merged activity list by timestamp descending and return the top 20 entries
  - [x] If one activity source fails for one or more applications, keep successful events and set `activityError`

- [x] Task 6: Add dashboard REST resource and wire authorization correctly (AC: #1, #8)
  - [x] Create `DashboardResource.java` with `@Path("/api/v1/teams/{teamId}/dashboard")`
  - [x] Follow the same team-access validation approach used by existing resources (`BuildResource`, `ReleaseResource`, `DeploymentResource`)
  - [x] Delegate all orchestration to `DashboardService`
  - [x] Verify the terminal path segment remains `dashboard` so the current `PermissionFilter` maps the resource correctly
  - [x] Verify `p, member, dashboard, read` already exists in `policy.csv`; only modify policy if it is missing

- [x] Task 7: Add focused backend tests for the dashboard contract and aggregation math (AC: #2, #4, #5, #6, #7, #8)
  - [x] Create `src/test/java/com/portal/dashboard/DashboardServiceTest.java`
  - [x] Test application summaries preserve environment `promotion_order`
  - [x] Test status merge rules, especially:
    - [x] `DEGRADED` Prometheus health downgrades `HEALTHY` to compact `UNHEALTHY`
    - [x] `NO_DATA` does not override ArgoCD status
    - [x] missing ArgoCD data yields `UNKNOWN`
  - [x] Test DORA aggregation math for sum, median, weighted average, and time-series alignment
  - [x] Test recent activity sort order and 20-item cap across mixed event types
  - [x] Test partial-failure behavior returns section data plus the correct error field instead of throwing
  - [x] Create `src/test/java/com/portal/dashboard/DashboardResourceIT.java`
  - [x] Test `GET /api/v1/teams/{teamId}/dashboard` returns `200` with the expected JSON shape
  - [x] Test non-existent team and cross-team access both return `404`
  - [x] Test `member`, `lead`, and `admin` can all read the dashboard endpoint

### Review Findings

- [x] [Review][Patch] Shared `errors` lists are mutated from multiple threads without consistent synchronization — **Fixed:** replaced `ArrayList` with `CopyOnWriteArrayList`, removed `synchronized` blocks
- [x] [Review][Patch] ArgoCD failures are reduced to generic `"Status unavailable"` instead of surfacing the upstream error in `statusDetail` — **Fixed:** appends actual ArgoCD error text
- [x] [Review][Patch] Prometheus outages preserve the base status but do not record that live health enrichment was unavailable in `statusDetail` — **Fixed:** appends "(live health unavailable)" when Prometheus is down
- [x] [Review][Patch] Team-level DORA trends reimplement trend logic and drop the existing 5% stability threshold used by the current DORA flow — **Fixed:** aligned with `PrometheusRestAdapter.calculateTrend` (5% threshold + `higherIsBetter` semantics)
- [x] [Review][Patch] Release and deployment trimming is not sorted by recency before `limit(10)`, so older events can displace newer ones in the merged activity feed — **Fixed:** added `.sorted(Comparator.comparing(..., reverseOrder()))` before `.limit()`
- [x] [Review][Patch] CFR weighting rewrites zero/negative deployment frequency weights to `1.0`, which breaks the specified weighted-average math — **Fixed:** excludes zero-DF apps from weighted average, falls back to simple average when all weights are zero

## Dev Notes

### Architecture Compliance

- **Backend-only story:** Do not replace or redesign `src/main/webui/src/routes/TeamDashboardPage.tsx` in this story. That placeholder page is handled by Story 7.2.
- **Package placement is fixed by architecture:** Create backend files under `developer-portal/src/main/java/com/portal/dashboard/`.
- **Stay inside the existing stack:** This story should not add new dependencies, schedulers, caches, persistence tables, or background jobs. The dashboard remains request-scoped live aggregation.
- **Service layering matters:** Prefer `DashboardResource -> DashboardService -> existing domain services`. Do not bypass service boundaries unless there is no reusable service path.

### Recommended Response Contract

Use a compact dashboard contract shaped for Stories 7.2 and 7.3:

```java
public record TeamDashboardDto(
        List<ApplicationHealthSummaryDto> applications,
        DoraMetricsDto dora,
        List<TeamActivityEventDto> recentActivity,
        String healthError,
        String doraError,
        String activityError) {}

public record ApplicationHealthSummaryDto(
        Long applicationId,
        String applicationName,
        String runtimeType,
        List<DashboardEnvironmentEntryDto> environments) {}

public record DashboardEnvironmentEntryDto(
        String environmentName,
        String status,
        String deployedVersion,
        Instant lastDeploymentAt,
        String statusDetail) {}

public record TeamActivityEventDto(
        String eventType,
        Long applicationId,
        String applicationName,
        String reference,
        Instant timestamp,
        String status,
        String actor,
        String environmentName) {}
```

**Important contract rule:** keep `status` as a string matching the existing frontend union (`HEALTHY`, `UNHEALTHY`, `DEPLOYING`, `NOT_DEPLOYED`, `UNKNOWN`) rather than introducing a competing dashboard enum that the frontend would have to remap.

### Existing Code to Reuse

| What | Location | How to reuse it |
|---|---|---|
| Team-scoped application list | `com.portal.application.ApplicationService` | Use `getApplicationsForTeam(teamId)` instead of duplicating team-scoped app queries |
| Ordered environment chain + UNKNOWN fallback | `com.portal.environment.EnvironmentService`, `EnvironmentChainResponse`, `EnvironmentMapper` | Reuse to preserve `promotion_order`, deployed version, last deployed time, and current UNKNOWN behavior |
| Existing environment status contract | `com.portal.environment.EnvironmentChainEntryDto`, `src/main/webui/src/types/environment.ts` | Match the existing string-based status vocabulary, including `UNKNOWN` |
| Prometheus health semantics | `com.portal.health.HealthService`, `com.portal.integration.prometheus.PrometheusAdapter` | Reuse current `CompletableFuture` and health-derivation approach instead of inventing a new Prometheus flow |
| DORA DTO contract and display semantics | `com.portal.health.DoraMetricsDto`, `DoraMetricDto`, `DoraService` | Reuse the current DTOs and trend/percentage/unit semantics so `DoraStatCard` can consume team metrics later without a second adapter |
| Build activity source | `com.portal.build.BuildService`, `BuildSummaryDto` | Use existing build list endpoint/service behavior; do not query Tekton directly from the resource |
| Release activity source | `com.portal.release.ReleaseService`, `ReleaseSummaryDto` | Reuse existing release list behavior; normalize into team activity events |
| Deployment activity source | `com.portal.deployment.DeploymentService`, `DeploymentHistoryDto` | Reuse existing deployment history parsing, especially `deployedBy` |
| Dashboard route placeholder | `src/main/webui/src/routes/TeamDashboardPage.tsx` | Treat as a consumer for 7.2, not a place to move backend concerns into |

### Health Summary Merge Rules

Follow the current environment-chain vocabulary and the compact dashboard requirements:

1. Start from the `EnvironmentService` response for ordering, version, and base status.
2. If the base status is `DEPLOYING`, `NOT_DEPLOYED`, or `UNKNOWN`, preserve it.
3. If the base status is `HEALTHY` or `UNHEALTHY`, query Prometheus for that environment namespace.
4. Prometheus `UNHEALTHY` always downgrades the compact status to `UNHEALTHY`.
5. Prometheus `DEGRADED` downgrades compact status to `UNHEALTHY`, while `statusDetail` keeps the richer text (`Degraded`) for tooltip/detail use.
6. Prometheus `NO_DATA` does not override deployment state.
7. ArgoCD outages should surface as `UNKNOWN`; Prometheus outages should not crash the application summary.

This preserves the existing app-level mental model while fitting the 4-color compact dashboard row required by UX-DR5.

### Aggregated DORA Guidance

- Do **not** invent a separate dashboard-only DORA payload. Reuse `DoraMetricsDto`.
- The current per-application DORA implementation already provides:
  - `currentValue`
  - `previousValue`
  - `trend`
  - `percentageChange`
  - `unit`
  - `timeSeries`
- Aggregate these outputs instead of creating a second Prometheus query dialect for teams in this story.
- Keep the default range aligned with the existing 30-day DORA behavior so Story 7.3 trend charts can render without backend churn.
- Weighted CFR guidance:
  - use each application's deployment frequency as the weight
  - compute current and previous weighted averages separately
  - apply the same idea to aligned time-series buckets where possible

### Activity Feed Guidance

- This codebase already has per-application history services, not a team-wide activity endpoint.
- Build the team feed by reusing those existing per-app services and normalizing them.
- Keep the initial aggregation bounded:
  - collect recent items per source per app
  - merge
  - sort
  - truncate to 20
- Actor normalization rules:
  - deployment events already have `deployedBy`
  - build and release actors are not reliably available in the current DTO contracts
  - when no trustworthy actor exists, return `System` instead of null

### Partial Failure Rules

- The dashboard endpoint should remain usable when one source is down.
- Prefer section-level error strings over hard request failure:
  - `healthError` when one or more application health summaries were degraded by missing upstream data
  - `doraError` when some/all DORA aggregation inputs are unavailable
  - `activityError` when some activity sources fail
- Do not silently substitute stale data for failed live calls.
- Error strings should be human-readable and safe for UI display; do not leak stack traces or raw internal exception classes.

### Latest Technical Information

- No new external libraries are required for this story.
- Stay within the pinned project stack from `project-context.md`: `Quarkus 3.34.x`, `Java 17`, existing Prometheus/Tekton/Git/Argo adapters, and the current PatternFly/React frontend contract that will consume this payload later.
- Because the repo already standardizes on `CompletableFuture` fan-out for multi-source views, follow that pattern here instead of introducing Mutiny, schedulers, or background aggregation.

### Testing Approach

- **Service tests:** mock the reused services/adapters and verify aggregation logic, not framework internals.
- **Resource tests:** use `@QuarkusTest` + `@InjectMock` and verify the full REST lifecycle and authorization behavior.
- **High-value cases to cover:**
  - mixed successful + failing upstream sources still returning `200`
  - CFR weighted-average math
  - odd and even median calculations for Lead Time and MTTR
  - activity feed sort/limit across mixed event types
  - `UNKNOWN` status output for missing ArgoCD data
  - `member` access allowed on `/dashboard`

### Previous Story Intelligence

**From Epic 6 (Stories 6.1-6.3):**

- The project already established the pattern of request-scoped live aggregation through Prometheus-backed services. Reuse that pattern; do not add persistence or polling.
- `DoraMetricsDto` is already the frontend-ready contract for `DoraStatCard` and `DoraTrendChart`. Reusing it here prevents a second dashboard-specific DORA model.
- `EnvironmentCard.tsx` already codifies how Prometheus health can worsen ArgoCD health while preserving `DEPLOYING`, `NOT_DEPLOYED`, and `UNKNOWN`. Mirror that logic conceptually in the compact backend summary instead of inventing a different precedence rule.
- Story 6.3 already added DORA trend charts and confirmed the UI wants time-series data. Include aggregated `timeSeries` now so Story 7.3 stays frontend-focused.

### Git Intelligence

Recent commits reinforce the patterns to follow:

- `6d52694` Epic 6 retrospective: DORA query optimization and new coding rules
- `d2a8d20` Refactor Metrics page: sub-tabs, collapsable environments, Grafana link
- `327bd46` Fix blank page: add missing Victory peer dependencies for PatternFly charts
- `c98cdf5` Story 6.3: DORA Metrics Retrieval & Display
- `57ffdba` Story 6.2: Application Health Page

What this means for Story 7.1:

- favor additive reuse over new parallel abstractions
- preserve DTO compatibility for the existing DORA UI components
- keep backend aggregation explicit and testable
- be careful with partial data behavior, because the repo recently tightened those guardrails

### Project Structure Notes

**New files expected in this story:**

```text
developer-portal/src/main/java/com/portal/dashboard/
├── DashboardResource.java
├── DashboardService.java
├── TeamDashboardDto.java
├── ApplicationHealthSummaryDto.java
├── DashboardEnvironmentEntryDto.java
└── TeamActivityEventDto.java

developer-portal/src/test/java/com/portal/dashboard/
├── DashboardServiceTest.java
└── DashboardResourceIT.java
```

**Likely modified files:**

```text
developer-portal/src/main/resources/casbin/policy.csv           # only if dashboard read policy is actually missing
```

**Files that should remain untouched in this story unless strictly required:**

- `developer-portal/src/main/webui/src/routes/TeamDashboardPage.tsx`
- `developer-portal/src/main/webui/src/components/dashboard/`
- existing app-level `health/`, `build/`, `release/`, and `deployment/` endpoint contracts

### References

- [Source: planning-artifacts/epics.md#Epic 7, Story 7.1] - endpoint purpose, payload scope, DORA aggregation rules, recent activity requirements
- [Source: planning-artifacts/epics.md#Story 7.2] - team dashboard page will consume this endpoint for DORA stat cards and the application health grid
- [Source: planning-artifacts/epics.md#Story 7.3] - aggregated DORA trend charts and activity feed should reuse this backend contract
- [Source: planning-artifacts/architecture.md#API & Communication Patterns] - `/api/v1/teams/{teamId}/dashboard`
- [Source: planning-artifacts/architecture.md#Project Structure] - `com.portal.dashboard` package location
- [Source: planning-artifacts/architecture.md#Requirements to Structure Mapping] - Team Dashboard (FR32-35) maps to `dashboard/`
- [Source: planning-artifacts/ux-design-specification.md#DORA Stat Card] - existing DORA card contract reused by team-level metrics
- [Source: planning-artifacts/ux-design-specification.md#Application Health Grid Row] - compact environment dot row expectations
- [Source: planning-artifacts/ux-design-specification.md#Loading and Data Freshness Patterns] - render available data with inline unavailable-state messaging
- [Source: planning-artifacts/ux-design-specification.md#Empty States] - team dashboard DORA no-data behavior
- [Source: project-context.md#Critical Implementation Rules] - Quarkus REST/CDI rules, DTO-only resources, `CompletableFuture` guidance
- [Source: project-context.md#Resource Ownership Validation] - 404 rules and scoping expectations
- [Source: project-context.md#Data Model - Portal Is Not the Source of Truth] - live platform data only, no persistence/caching for dashboard state
- [Source: project-context.md#Observability Integration - PromQL as Configuration] - reuse existing DORA and health query patterns
- [Source: implementation-artifacts/6-1-prometheus-adapter-health-signals.md] - Prometheus health semantics and partial-failure behavior
- [Source: implementation-artifacts/6-2-application-health-page.md] - health status merge semantics and existing environment vocabulary
- [Source: implementation-artifacts/6-3-dora-metrics-retrieval-display.md] - `DoraMetricsDto` contract, trend semantics, and time-series expectations

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (Cursor)

### Debug Log References

None — clean implementation with all tests passing on first run.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Task 1: Created 4 dashboard DTOs as Java records in `com.portal.dashboard` package. `TeamDashboardDto` reuses `DoraMetricsDto` from the health package for DORA section.
- Task 2: Built `DashboardService` with `CompletableFuture`-based parallel fan-out across health, DORA, and activity aggregation. Uses `joinSafely` pattern from `HealthService`. Delegates to `ApplicationService.getApplicationsForTeam()` for team-scoped discovery.
- Task 3: Implemented health merge rules per AC #4. `DEGRADED` Prometheus status downgrades to `UNHEALTHY` in compact view with `statusDetail = "Degraded"`. `NO_DATA` preserves ArgoCD status. Missing ArgoCD yields `UNKNOWN`. `DEPLOYING`/`NOT_DEPLOYED` pass through unchanged.
- Task 4: DORA aggregation uses sum for deployment frequency, median for lead time and MTTR, weighted average (by DF) for CFR. Time-series aggregation aligns by timestamp bucket with matching math per metric type.
- Task 5: Activity feed merges builds, releases, and deployments from per-app services. Each source limited to 10 per app, merged and sorted by timestamp descending, capped at 20. Actor normalized to `System` when unavailable.
- Task 6: Created `DashboardResource` at `/api/v1/teams/{teamId}/dashboard`. Uses same `validateTeamAccess` pattern as `BuildResource`/`ReleaseResource`. Casbin policy `p, member, dashboard, read` already existed.
- Task 7: 17 unit tests in `DashboardServiceTest` covering merge rules, DORA math (sum, odd/even median, weighted CFR), time-series alignment, activity sort/cap, and partial failures. 6 integration tests in `DashboardResourceIT` covering JSON shape, 404 for missing/cross-team, and member/lead/admin access.
- Full regression suite: 548 tests, 0 failures, 0 errors.

### File List

- `developer-portal/src/main/java/com/portal/dashboard/TeamDashboardDto.java` (new)
- `developer-portal/src/main/java/com/portal/dashboard/ApplicationHealthSummaryDto.java` (new)
- `developer-portal/src/main/java/com/portal/dashboard/DashboardEnvironmentEntryDto.java` (new)
- `developer-portal/src/main/java/com/portal/dashboard/TeamActivityEventDto.java` (new)
- `developer-portal/src/main/java/com/portal/dashboard/DashboardService.java` (new)
- `developer-portal/src/main/java/com/portal/dashboard/DashboardResource.java` (new)
- `developer-portal/src/test/java/com/portal/dashboard/DashboardServiceTest.java` (new)
- `developer-portal/src/test/java/com/portal/dashboard/DashboardResourceIT.java` (new)

### Change Log

- 2026-04-13: Story 7.1 implemented — Team dashboard backend aggregation endpoint with health, DORA, and activity sections. 8 new files, 23 new tests, 0 regressions across 548-test suite.
- 2026-04-13: Applied 6 code-review patches — thread-safe error lists (CopyOnWriteArrayList), richer ArgoCD/Prometheus statusDetail, aligned DORA trend threshold (5%), sorted activity sources before limiting, corrected zero-weight CFR exclusion. 548 tests pass, 0 regressions.
