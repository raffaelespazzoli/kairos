# Story 6.1: Prometheus Adapter & Health Signals

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want the portal to retrieve golden signal metrics for my application from Prometheus,
so that I can see health indicators per environment without opening Grafana.

## Acceptance Criteria

1. **PrometheusAdapter is an @ApplicationScoped CDI bean using externalized PromQL configuration**
   - **Given** the PrometheusAdapter is an @ApplicationScoped CDI bean
   - **When** it needs to query metrics
   - **Then** it uses the Prometheus HTTP API at the URL configured via `portal.prometheus.url`
   - **And** it executes PromQL queries read from `portal.prometheus.queries.*` configuration properties
   - **And** query templates use `{namespace}` and `{interval}` placeholders substituted at runtime
   - **And** the adapter does NOT contain hardcoded PromQL — it is a generic query executor

2. **Golden signals retrieved for a namespace via getGoldenSignals()**
   - **Given** the adapter is asked for golden signals for an application in a specific environment
   - **When** `getGoldenSignals(String namespace)` is called
   - **Then** it queries Prometheus for the four golden signals scoped to the namespace:
     - **Latency:** request duration percentiles (p50, p95, p99)
     - **Traffic:** request rate (requests per second)
     - **Errors:** error rate (percentage of 5xx responses)
     - **Saturation:** CPU and memory utilization percentages
   - **And** each metric is returned with its current value and unit
   - **And** the PromQL for each signal comes from configuration keys: `portal.prometheus.queries.latency-p50`, `latency-p95`, `latency-p99`, `traffic-rate`, `error-rate`, `saturation-cpu`, `saturation-memory`

3. **HealthStatusDto translates golden signals to portal health status**
   - **Given** the adapter translates metrics to portal domain types
   - **When** returning a `HealthStatusDto`
   - **Then** it includes: overall health status (`Healthy`/`Unhealthy`/`Degraded`), individual golden signal values, and the namespace used for scoping
   - **And** overall health is derived: error rate > threshold → `Unhealthy`, saturation > threshold → `Degraded`, otherwise → `Healthy`
   - **And** thresholds are configurable via `portal.prometheus.thresholds.error-rate` (default: 5.0) and `portal.prometheus.thresholds.saturation` (default: 90.0)

4. **Prometheus unreachable throws PortalIntegrationException**
   - **Given** Prometheus is unreachable
   - **When** health signals are requested
   - **Then** a `PortalIntegrationException` is thrown with `system="prometheus"`, `operation="getGoldenSignals"`
   - **And** the error message: "Health data unavailable — metrics system is unreachable"

5. **No-data namespace returns HealthStatusDto with status "No Data"**
   - **Given** no metrics exist for a namespace (new deployment, no traffic)
   - **When** the adapter queries Prometheus
   - **Then** it returns a `HealthStatusDto` with status `NO_DATA` and empty/zero metric values
   - **And** this is NOT treated as an error

6. **Health endpoint aggregates across environments in parallel**
   - **Given** the health endpoint aggregates data from multiple environments
   - **When** `GET /api/v1/teams/{teamId}/applications/{appId}/health` is called
   - **Then** the backend resolves all environments for the application (ordered by `promotion_order`)
   - **And** queries Prometheus in parallel for each environment using `CompletableFuture` (same pattern as `ArgoCdRestAdapter`)
   - **And** returns health status per environment as a list of `EnvironmentHealthDto` objects
   - **And** each entry includes: environment name, `HealthStatusDto`, and Grafana deep link (via `DeepLinkService.generateGrafanaLink(namespace)`)

7. **Per-environment failure isolation**
   - **Given** Prometheus returns data for some namespaces but fails for others
   - **When** the health endpoint processes results
   - **Then** environments with successful data return their `HealthStatusDto` normally
   - **And** environments where the query failed return an `EnvironmentHealthDto` with `error` field populated and `healthStatus` as null
   - **And** a single failing environment does NOT fail the entire request

8. **Resource ownership validation (mandatory scoping pattern)**
   - **Given** a request targets a resource outside the caller's team scope
   - **Then** 404 is returned (never 403)
   - **Given** a request targets a non-existent application
   - **Then** 404 is returned
   - **Given** a request targets an application in another team
   - **Then** 404 is returned

9. **Casbin authorization permits all authenticated roles**
   - **Given** the Casbin permission check runs for a health request
   - **When** a developer with `member`, `lead`, or `admin` role requests health
   - **Then** the request is permitted (read operation on health resource)

10. **Dev-mode adapter returns mock golden signal data**
    - **Given** `portal.prometheus.provider=dev` is configured (dev profile)
    - **When** health signals are requested
    - **Then** `DevPrometheusAdapter` returns realistic mock data: first environment `Healthy` with sample values, second `Degraded` with elevated saturation, rest `Healthy`
    - **And** this mirrors the `DevArgoCdAdapter` pattern for deterministic dev/test behavior

## Tasks / Subtasks

- [ ] Task 1: Create PrometheusAdapter interface (AC: #1, #2)
  - [ ] Create `com.portal.integration.prometheus` package
  - [ ] Create `PrometheusAdapter` interface with method: `HealthSignalsResult getGoldenSignals(String namespace)`
  - [ ] `HealthSignalsResult` is a record containing the list of `GoldenSignal` values and a boolean `hasData` flag

- [ ] Task 2: Create Prometheus model types (AC: #2, #3)
  - [ ] Create `com.portal.integration.prometheus.model.GoldenSignal` record: `String name`, `double value`, `String unit`, `GoldenSignalType type`
  - [ ] Create `com.portal.integration.prometheus.model.GoldenSignalType` enum: `LATENCY_P50`, `LATENCY_P95`, `LATENCY_P99`, `TRAFFIC_RATE`, `ERROR_RATE`, `SATURATION_CPU`, `SATURATION_MEMORY`
  - [ ] Create `com.portal.integration.prometheus.model.HealthSignalsResult` record: `List<GoldenSignal> signals`, `boolean hasData`

- [ ] Task 3: Create PrometheusConfig (AC: #1, #2, #3)
  - [ ] Create `com.portal.integration.prometheus.PrometheusConfig` using `@ConfigMapping(prefix = "portal.prometheus")`
  - [ ] Properties: `provider()` (String, default "prometheus"), `url()` (String), `queries()` (nested interface with methods for each query key: `latencyP50()`, `latencyP95()`, `latencyP99()`, `trafficRate()`, `errorRate()`, `saturationCpu()`, `saturationMemory()`)
  - [ ] Thresholds: nested `thresholds()` interface with `errorRate()` (default 5.0), `saturation()` (default 90.0)
  - [ ] Query interval: `queryInterval()` (default "5m") — the `{interval}` placeholder value

- [ ] Task 4: Create PrometheusRestAdapter (AC: #1, #2, #4, #5)
  - [ ] Create `com.portal.integration.prometheus.PrometheusRestAdapter` as `@ApplicationScoped` with `@IfBuildProperty(name = "portal.prometheus.provider", stringValue = "prometheus", enableIfMissing = true)`
  - [ ] Inject `PrometheusConfig`
  - [ ] Use Quarkus `rest-client-jackson` via a `@RegisterRestClient(configKey = "prometheus-api")` REST client interface `PrometheusRestClient` with a `@GET @Path("/api/v1/query")` method accepting `@QueryParam("query") String query`
  - [ ] `getGoldenSignals(namespace)` implementation:
    - [ ] For each of the 7 query keys, read the PromQL template from config, substitute `{namespace}` and `{interval}`, call Prometheus instant query endpoint
    - [ ] Parse JSON response: navigate `data.result[0].value[1]` for scalar value (Prometheus instant query vector result format)
    - [ ] Build `GoldenSignal` record for each with appropriate name, value, unit
    - [ ] If all results are empty vectors (`data.result` is empty array), return `HealthSignalsResult(signals, false)` — "No Data"
    - [ ] If any query succeeds, return `HealthSignalsResult(signals, true)`
  - [ ] Wrap HTTP/connection failures in `PortalIntegrationException(system="prometheus", operation="getGoldenSignals", message="Health data unavailable — metrics system is unreachable")`
  - [ ] Handle Prometheus 400/422 errors (bad query) differently — log warning with the query that failed, return zero for that signal rather than failing the whole call

- [ ] Task 5: Create DevPrometheusAdapter (AC: #10)
  - [ ] Create `com.portal.integration.prometheus.DevPrometheusAdapter` as `@ApplicationScoped` with `@IfBuildProperty(name = "portal.prometheus.provider", stringValue = "dev")`
  - [ ] Returns mock golden signal data: latency p50=45ms, p95=245ms, p99=890ms, traffic=42.5 req/s, errors=0.3%, saturation CPU=45%, memory=62%
  - [ ] `hasData = true` for all calls

- [ ] Task 6: Create health domain DTOs (AC: #3, #6)
  - [ ] Create `com.portal.health.HealthStatus` enum: `HEALTHY`, `UNHEALTHY`, `DEGRADED`, `NO_DATA`
  - [ ] Create `com.portal.health.HealthStatusDto` record: `HealthStatus status`, `List<GoldenSignal> goldenSignals`, `String namespace`
  - [ ] Create `com.portal.health.EnvironmentHealthDto` record: `String environmentName`, `HealthStatusDto healthStatus`, `String grafanaDeepLink`, `String error`
  - [ ] Create `com.portal.health.HealthResponse` record: `List<EnvironmentHealthDto> environments`

- [ ] Task 7: Create HealthService (AC: #3, #5, #6, #7, #8)
  - [ ] Create `com.portal.health.HealthService` as `@ApplicationScoped`
  - [ ] Inject `PrometheusAdapter`, `DeepLinkService`, `PrometheusConfig` (for thresholds)
  - [ ] `getApplicationHealth(Long teamId, Long appId)` method:
    - [ ] `requireTeamApplication(teamId, appId)` — Application lookup with team ownership validation → 404
    - [ ] Load all environments: `Environment.findByApplicationOrderByPromotionOrder(appId)`
    - [ ] For each environment, submit a `CompletableFuture` calling `prometheusAdapter.getGoldenSignals(env.namespace)`
    - [ ] Unwrap `CompletionException` to extract real cause (same pattern as `ArgoCdRestAdapter`)
    - [ ] Per-environment failure isolation: catch `PortalIntegrationException` per future, set `error` field on `EnvironmentHealthDto`
    - [ ] For each successful result, derive `HealthStatus` from golden signals using configurable thresholds
    - [ ] Generate Grafana deep link via `deepLinkService.generateGrafanaLink(env.namespace)`
    - [ ] Return `HealthResponse` with all `EnvironmentHealthDto` entries

- [ ] Task 8: Create HealthResource REST endpoint (AC: #6, #8, #9)
  - [ ] Create `com.portal.health.HealthResource` with `@Path("/api/v1/teams/{teamId}/applications/{appId}/health")`
  - [ ] `@GET` method returning `HealthResponse`
  - [ ] Path params: `@PathParam("teamId") Long teamId`, `@PathParam("appId") Long appId`
  - [ ] Inject `HealthService`, delegate to `getApplicationHealth(teamId, appId)`

- [ ] Task 9: Create PrometheusRestClient interface (AC: #1)
  - [ ] Create `com.portal.integration.prometheus.PrometheusRestClient` interface with `@RegisterRestClient(configKey = "prometheus-api")`
  - [ ] Method: `@GET @Path("/api/v1/query") JsonNode query(@QueryParam("query") String query)`
  - [ ] The Quarkus REST client URL is configured via `quarkus.rest-client.prometheus-api.url=${portal.prometheus.url}`

- [ ] Task 10: Update Casbin policy (AC: #9)
  - [ ] Add policy line: `p, member, health, read` in `src/main/resources/casbin/policy.csv`
  - [ ] `member` inherits to `lead` and `admin` via existing role hierarchy

- [ ] Task 11: Update configuration files (AC: #1, #2, #3, #10)
  - [ ] `application.properties`:
    - [ ] Add `portal.prometheus.provider=${PROMETHEUS_PROVIDER:prometheus}`
    - [ ] Add `portal.prometheus.url=${PROMETHEUS_URL:}`
    - [ ] Add `portal.prometheus.query-interval=${PROMETHEUS_QUERY_INTERVAL:5m}`
    - [ ] Add `portal.prometheus.thresholds.error-rate=5.0`
    - [ ] Add `portal.prometheus.thresholds.saturation=90.0`
    - [ ] Add default PromQL query templates under `portal.prometheus.queries.*` (placeholder values that platform teams will customize)
    - [ ] Add `quarkus.rest-client.prometheus-api.url=${portal.prometheus.url}`
  - [ ] `application-dev.properties` / dev profile: `%dev.portal.prometheus.provider=dev`
  - [ ] `application-test.properties`: `portal.prometheus.provider=prometheus` (tests mock the adapter via `@InjectMock`)

- [ ] Task 12: Write PrometheusRestAdapter unit tests (AC: #1, #2, #4, #5)
  - [ ] Create `src/test/java/com/portal/integration/prometheus/PrometheusRestAdapterTest.java`
  - [ ] Test successful golden signal parsing from Prometheus JSON response
  - [ ] Test empty result set → `hasData = false` (No Data case)
  - [ ] Test Prometheus unreachable → `PortalIntegrationException` with `system="prometheus"`
  - [ ] Test bad PromQL response (400/422) → graceful degradation, zero value for that signal
  - [ ] Test `{namespace}` and `{interval}` substitution in query templates
  - [ ] Mock `PrometheusRestClient` via `@InjectMock`

- [ ] Task 13: Write HealthService unit tests (AC: #3, #5, #6, #7, #8)
  - [ ] Create `src/test/java/com/portal/health/HealthServiceTest.java`
  - [ ] Test health status derivation: low error + low saturation → `HEALTHY`
  - [ ] Test health status derivation: high error rate → `UNHEALTHY`
  - [ ] Test health status derivation: high saturation → `DEGRADED`
  - [ ] Test no data → `NO_DATA`
  - [ ] Test parallel environment querying returns correct per-environment results
  - [ ] Test per-environment failure isolation — one environment fails, others succeed
  - [ ] Test resource ownership: wrong team → 404
  - [ ] Test resource ownership: non-existent application → 404

- [ ] Task 14: Write HealthResource integration test (AC: #6, #8, #9)
  - [ ] Create `src/test/java/com/portal/health/HealthResourceIT.java`
  - [ ] `@QuarkusTest` with `@InjectMock PrometheusAdapter`
  - [ ] Test `GET /api/v1/teams/{teamId}/applications/{appId}/health` returns 200 with expected structure
  - [ ] Test cross-team access returns 404
  - [ ] Test non-existent application returns 404
  - [ ] Test Casbin permits member, lead, admin roles

## Dev Notes

### Architecture Compliance

- **Adapter pattern:** Follow exact same pattern as `ArgoCdAdapter` / `ArgoCdRestAdapter` / `DevArgoCdAdapter` / `ArgoCdConfig`. The `PrometheusAdapter` interface lives in `com.portal.integration.prometheus`, with `PrometheusRestAdapter` (real) and `DevPrometheusAdapter` (dev mode) as implementations. Build-time selection via `@IfBuildProperty(name = "portal.prometheus.provider", ...)`.
- **Health domain package:** `com.portal.health` contains `HealthResource`, `HealthService`, `HealthStatusDto`, `HealthStatus`, `EnvironmentHealthDto`, `HealthResponse`. This follows the domain-centric package organization — health has its own Resource/Service/DTOs.
- **No cross-package entity imports:** `HealthService` references `Environment` and `Application` by calling their static Panache finders — do NOT import entities from other packages; use the ID-based lookup pattern already established.
- **REST resource → Service → Adapter:** `HealthResource` delegates to `HealthService` which calls `PrometheusAdapter` and `DeepLinkService`. The resource never touches the adapter directly.

### PromQL as Configuration (Design Decision 2026-04-11)

This is the most important architectural constraint for this story. From `project-context.md`:

> Prometheus queries are externalized as configuration under `portal.prometheus.queries.*` — the PrometheusAdapter is a generic query executor, not a PromQL author. Query templates use `{namespace}` and `{interval}` placeholders that the adapter substitutes at runtime.

The adapter's responsibility is strictly: HTTP call to Prometheus API → parameter substitution → response parsing into portal DTOs. It does NOT need to understand PromQL semantics.

**Default query templates** (platform teams customize for their metric names):

```properties
portal.prometheus.queries.latency-p50=histogram_quantile(0.50, rate(http_request_duration_seconds_bucket{namespace="{namespace}"}[{interval}]))
portal.prometheus.queries.latency-p95=histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{namespace="{namespace}"}[{interval}]))
portal.prometheus.queries.latency-p99=histogram_quantile(0.99, rate(http_request_duration_seconds_bucket{namespace="{namespace}"}[{interval}]))
portal.prometheus.queries.traffic-rate=sum(rate(http_requests_total{namespace="{namespace}"}[{interval}]))
portal.prometheus.queries.error-rate=sum(rate(http_requests_total{namespace="{namespace}",code=~"5.."}[{interval}])) / sum(rate(http_requests_total{namespace="{namespace}"}[{interval}])) * 100
portal.prometheus.queries.saturation-cpu=avg(rate(container_cpu_usage_seconds_total{namespace="{namespace}"}[{interval}])) / avg(kube_pod_container_resource_limits{namespace="{namespace}",resource="cpu"}) * 100
portal.prometheus.queries.saturation-memory=avg(container_memory_working_set_bytes{namespace="{namespace}"}) / avg(kube_pod_container_resource_limits{namespace="{namespace}",resource="memory"}) * 100
```

### Prometheus HTTP API Reference

- **Instant query endpoint:** `GET /api/v1/query?query=<PromQL>`
- **Response format for vector result:**
```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": { "namespace": "team-app-dev" },
        "value": [ 1712345678.123, "0.245" ]
      }
    ]
  }
}
```
- `value[0]` = Unix timestamp, `value[1]` = string representation of the numeric result
- Empty `result` array means no matching time series (No Data)
- HTTP 400 = bad query, 422 = cannot execute, 503 = timeout/abort

### Parallel Query Pattern

Follow `ArgoCdRestAdapter.getEnvironmentStatuses()` exactly:
- Submit `CompletableFuture.supplyAsync()` for each environment
- Collect futures into a list
- Join each future, unwrapping `CompletionException` to extract real cause
- For health, isolate failures per-environment (unlike ArgoCD which fails the whole chain): catch exceptions per future and populate the `error` field on `EnvironmentHealthDto`

### Existing Code to Reuse

| What | Location | How |
|---|---|---|
| `PortalIntegrationException` | `com.portal.integration.PortalIntegrationException` | Throw with `system="prometheus"` |
| `DeepLinkService.generateGrafanaLink(namespace)` | `com.portal.deeplink.DeepLinkService` | Already implemented — generates `{grafanaUrl}/d/{dashboardId}?var-namespace={namespace}` |
| `Environment.findByApplicationOrderByPromotionOrder(appId)` | `com.portal.environment.Environment` | Panache static finder for ordered environments |
| `Environment.namespace` field | `com.portal.environment.Environment` | The namespace string to scope Prometheus queries |
| `@IfBuildProperty` pattern | See `ArgoCdRestAdapter` / `DevArgoCdAdapter` | Build-time adapter selection |
| `@ConfigMapping` pattern | See `ArgoCdConfig` | SmallRye config mapping |
| `@RegisterRestClient` pattern | See `ArgoCdRestClient` | MicroProfile REST client for HTTP calls |
| `CompletableFuture` parallel pattern | See `ArgoCdRestAdapter.getEnvironmentStatuses()` | Parallel adapter calls with `CompletionException` unwrapping |
| `requireTeamApplication` pattern | See `DeploymentService` | Ownership validation helper |

### Files NOT in Health Package

The Prometheus adapter classes live in `com.portal.integration.prometheus/`, NOT in `com.portal.health/`. The health package contains the portal domain layer (Resource, Service, DTOs). This follows the same separation as ArgoCD: `integration/argocd/` for the adapter vs. `environment/` for the domain.

### Namespace Derivation

The `Environment` entity already has a `namespace` field (e.g., `checkout-orders-api-dev`). Use `env.namespace` directly — do NOT reconstruct namespace names. The environment chain was seeded during onboarding with the correct namespace.

### Configuration Key Naming Convention

Follow existing pattern in `application.properties`:
```properties
# Prometheus
portal.prometheus.provider=${PROMETHEUS_PROVIDER:prometheus}
portal.prometheus.url=${PROMETHEUS_URL:}
portal.prometheus.query-interval=${PROMETHEUS_QUERY_INTERVAL:5m}
# ... queries and thresholds follow
```

Dev profile override: `%dev.portal.prometheus.provider=dev`

### Grafana Deep Links (Already Implemented)

`DeepLinkService.generateGrafanaLink(namespace)` already exists and generates the correct URL pattern: `{grafanaUrl}/d/{dashboardId}?var-namespace={namespace}`. Configuration is already set up:
- `portal.grafana.url` (prod: env var, dev: `https://dev-grafana.local`)
- `portal.grafana.dashboard-id` (prod: env var, dev: `app-health-overview`)

Do NOT re-implement — inject and call the existing service.

### Health Status Derivation Logic

```
if (!hasData) → NO_DATA
else if (errorRate > thresholds.errorRate) → UNHEALTHY
else if (saturationCpu > thresholds.saturation || saturationMemory > thresholds.saturation) → DEGRADED
else → HEALTHY
```

Thresholds are configurable:
- `portal.prometheus.thresholds.error-rate` (default: `5.0` = 5%)
- `portal.prometheus.thresholds.saturation` (default: `90.0` = 90%)

### Testing Approach

- **Unit tests** (`PrometheusRestAdapterTest`): Mock `PrometheusRestClient` via `@InjectMock`. Test JSON parsing, query substitution, error handling, no-data scenarios.
- **Unit tests** (`HealthServiceTest`): Mock `PrometheusAdapter` via `@InjectMock`. Test health derivation logic, parallel execution, per-environment failure isolation, ownership validation.
- **Integration tests** (`HealthResourceIT`): `@QuarkusTest` with `@InjectMock PrometheusAdapter`. Test full request lifecycle through REST endpoint with REST Assured.
- Mock platform integrations in tests — never call real Prometheus from tests.
- Use `@InjectMock` (Quarkus CDI mock) for adapter mocking — not Mockito standalone.
- Never use `assertInstanceOf` on `@ApplicationScoped` CDI beans — Quarkus wraps them in `ClientProxy`.

### Story 6.2 Forward Compatibility

Story 6.2 (Application Health Page) will build the frontend for this data. Story 6.3 (DORA Metrics) will extend `PrometheusAdapter` with a `getDoraMetrics()` method using the same externalized query pattern. Design the adapter interface with this extensibility in mind — keep `getGoldenSignals()` and the future `getDoraMetrics()` as separate interface methods.

The `HealthResource` endpoint created here will be consumed by the React `ApplicationHealthPage` component in Story 6.2. The response shape (`HealthResponse` → `List<EnvironmentHealthDto>`) should match what the frontend needs for rendering health sections per environment.

### Project Structure Notes

New files this story creates:

```
developer-portal/src/main/java/com/portal/
├── health/
│   ├── HealthResource.java
│   ├── HealthService.java
│   ├── HealthStatus.java                 (enum)
│   ├── HealthStatusDto.java              (record)
│   ├── EnvironmentHealthDto.java         (record)
│   └── HealthResponse.java              (record)
├── integration/prometheus/
│   ├── PrometheusAdapter.java            (interface)
│   ├── PrometheusRestAdapter.java        (@ApplicationScoped, real)
│   ├── DevPrometheusAdapter.java         (@ApplicationScoped, dev-mode)
│   ├── PrometheusConfig.java             (@ConfigMapping)
│   ├── PrometheusRestClient.java         (@RegisterRestClient)
│   └── model/
│       ├── GoldenSignal.java             (record)
│       ├── GoldenSignalType.java         (enum)
│       └── HealthSignalsResult.java      (record)

developer-portal/src/test/java/com/portal/
├── health/
│   ├── HealthServiceTest.java
│   └── HealthResourceIT.java
├── integration/prometheus/
│   └── PrometheusRestAdapterTest.java
```

Modified files:
- `src/main/resources/application.properties` — add Prometheus config block
- `src/main/resources/casbin/policy.csv` — add `p, member, health, read`
- `src/test/resources/application.properties` — add test Prometheus config

### References

- [Source: planning-artifacts/epics.md#Epic 6, Story 6.1] — Full acceptance criteria and BDD scenarios
- [Source: planning-artifacts/architecture.md#Integration Adapters] — PrometheusAdapter in `integration/prometheus/`, Prometheus HTTP API, Phase 4
- [Source: planning-artifacts/architecture.md#API & Communication Patterns] — `/api/v1/teams/{teamId}/applications/{appId}/health` endpoint
- [Source: planning-artifacts/architecture.md#Data Architecture] — Health signals NOT persisted, fetched live
- [Source: planning-artifacts/architecture.md#Configuration Properties] — `portal.prometheus.url`, `portal.grafana.url`, `portal.grafana.dashboard-id`
- [Source: planning-artifacts/architecture.md#Project Structure] — `health/` and `integration/prometheus/` packages
- [Source: planning-artifacts/prd.md#Observability & Health] — FR27 (health per env), FR29 (golden signals)
- [Source: planning-artifacts/ux-design-specification.md#Environment Chain Card Row] — Health badge states: Healthy (green), Unhealthy (red), Degraded (yellow), No Data (grey)
- [Source: project-context.md#Observability Integration] — PromQL as Configuration design decision (2026-04-11)
- [Source: project-context.md#Critical Implementation Rules] — Adapter patterns, CDI beans, error handling, CompletableFuture unwrapping
- [Source: project-context.md#Resource Ownership Validation] — require* pattern, 404 for cross-team access

## Previous Story Intelligence

**From Epic 5 (Story 5.4 — Promotion Confirmation & Production Gating):**

- **Hibernate column mapping gotcha:** When adding boolean fields, explicit `@Column(name = "...")` was needed for `is_production`. Health domain uses records (not entities), so this is less likely to apply, but be aware for any future entity changes.
- **Tooltip on disabled button:** Wrapping disabled PF6 `Button` in a focusable `<span>` was needed for tooltip events — relevant if Story 6.2 adds disabled states to health-related controls.
- **Test churn management:** Adding confirmation flows required updating 4 existing tests. This story creates a new domain package, so test churn should be minimal.
- **`require*` ownership pattern is mandatory:** Established firmly in Epic 4 retrospective, reinforced in Story 5.4. Every service method receiving entity IDs from URL path params MUST validate the full ownership chain.

## Git Intelligence

Recent commits show active development in Epic 5 deployment stories, with consistent patterns:
- `8c0c135` Fix dev-mode team context scoping and environment status mapping
- `7af2734` Epic 5 retrospective: new coding rules, TS build fixes, and design decisions
- `a889c39` Story 5.4: Promotion Confirmation & Production Gating

Patterns established: adapter interface + real + dev implementations, `@IfBuildProperty` switching, `CompletableFuture` parallel calls in adapters, `require*` ownership methods in services, REST Assured integration tests, `@InjectMock` for adapter mocking.

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
