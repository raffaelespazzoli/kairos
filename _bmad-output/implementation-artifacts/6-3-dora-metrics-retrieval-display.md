# Story 6.3: DORA Metrics Retrieval & Display

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to view DORA metric trends for my application over time,
so that I can understand my team's delivery performance and track improvement.

## Acceptance Criteria

1. **PrometheusAdapter extended with getDoraMetrics()**
   - **Given** the PrometheusAdapter is extended for DORA metrics
   - **When** `getDoraMetrics(String appName, String timeRange)` is called
   - **Then** it queries Prometheus for the four DORA metrics scoped to the application:
     - **Deployment Frequency:** number of deployments per week over the time range
     - **Lead Time for Changes:** median time from commit to production deployment
     - **Change Failure Rate:** percentage of deployments that caused incidents or rollbacks
     - **Mean Time to Recovery (MTTR):** median time from failure detection to resolution
   - **And** each metric is returned with: current value, previous period value, trend direction (improving/stable/declining), and time series data points for charting
   - **And** PromQL queries come from `portal.prometheus.dora-queries.*` configuration keys (same externalized pattern as golden signals but in a separate DORA namespace)
   - **And** query templates use `{appName}` and `{range}` placeholders substituted at runtime

2. **DORA REST endpoint returns metrics for default time range**
   - **Given** a developer navigates to the Health tab
   - **When** `GET /api/v1/teams/{teamId}/applications/{appId}/dora` is called
   - **Then** DORA metrics are returned for the default time range (30 days)
   - **And** the response includes current values, trends, and time series data
   - **And** optional query parameter `timeRange` overrides the default (e.g., `?timeRange=90d`)

3. **Four DoraStatCard components displayed on health page**
   - **Given** the DORA metrics section renders on the ApplicationHealthPage (below golden signals)
   - **When** data is available
   - **Then** four DoraStatCard components are displayed in a row
   - **And** each card shows: metric name, current value with unit (e.g., "4.2/wk", "2.1h", "2.3%", "45m"), trend arrow (↑ or ↓), percentage change from previous period (e.g., "+18% from last month")

4. **Improving trend shown in green**
   - **Given** a DoraStatCard shows an improving trend
   - **When** the metric direction is favorable (higher deploy freq, lower lead time/CFR/MTTR)
   - **Then** the trend arrow and percentage are displayed in green

5. **Declining trend shown in red**
   - **Given** a DoraStatCard shows a declining trend
   - **When** the metric direction is unfavorable
   - **Then** the trend arrow and percentage are displayed in red

6. **Insufficient data empty state**
   - **Given** a DoraStatCard has insufficient data
   - **When** fewer than 7 days of activity exist (determined by time series having fewer than 7 data points with step=1d)
   - **Then** the card shows "—" as the value
   - **And** "Insufficient data" as the label in grey
   - **And** trend text: "Available after 7 days of activity"
   - **And** the backend sets `hasData = false` on `DoraMetricsDto` when all metrics have fewer than 7 time series points OR all return empty vectors

7. **Accessibility — aria-labels on stat cards**
   - **Given** the DORA stat cards are displayed
   - **When** reviewing accessibility
   - **Then** each card has an `aria-label` combining metric name, value, and trend (e.g., "Deployment frequency, 4.2 per week, up 18 percent from last month")

8. **PatternFly Chart trend lines below stat cards**
   - **Given** below the stat cards
   - **When** time series data is available
   - **Then** PatternFly Chart components display trend lines for each DORA metric over the selected time range
   - **And** charts use the PatternFly chart theme and Victory-based rendering
   - **And** import from `@patternfly/react-charts/victory`: `Chart`, `ChartAxis`, `ChartGroup`, `ChartLine`, `ChartVoronoiContainer`

9. **Prometheus unreachable — isolated failure**
   - **Given** Prometheus is unreachable
   - **When** DORA metrics are requested
   - **Then** the DORA section shows "Delivery metrics unavailable — metrics system is unreachable"
   - **And** this does NOT affect the golden signals section (they fail independently)

10. **Resource ownership validation (mandatory scoping pattern)**
    - **Given** a request targets a resource outside the caller's team scope
    - **Then** 404 is returned (never 403)
    - **Given** a request targets a non-existent application
    - **Then** 404 is returned
    - **Given** a request targets an application in another team
    - **Then** 404 is returned

11. **Casbin authorization permits all authenticated roles**
    - **Given** the Casbin permission check runs for a DORA request
    - **When** a developer with `member`, `lead`, or `admin` role requests DORA metrics
    - **Then** the request is permitted (read operation on dora resource)

12. **Dev-mode adapter returns mock DORA data**
    - **Given** `portal.prometheus.provider=dev` is configured (dev profile)
    - **When** DORA metrics are requested
    - **Then** `DevPrometheusAdapter` returns realistic mock DORA data with trend values and time series
    - **And** mirrors the existing dev adapter pattern for deterministic dev/test behavior

## Tasks / Subtasks

### Backend Tasks

- [ ] Task 1: Extend PrometheusAdapter interface (AC: #1)
  - [ ] Add `DoraMetricsResult getDoraMetrics(String appName, String timeRange)` to `PrometheusAdapter` interface in `com.portal.integration.prometheus`
  - [ ] This is an additive change — existing `getGoldenSignals()` method is unchanged

- [ ] Task 2: Create DORA model types in Prometheus integration package (AC: #1)
  - [ ] Create `com.portal.integration.prometheus.model.DoraMetric` record: `DoraMetricType type`, `double currentValue`, `double previousValue`, `TrendDirection trend`, `String unit`, `List<TimeSeriesPoint> timeSeries`
  - [ ] Create `com.portal.integration.prometheus.model.DoraMetricType` enum: `DEPLOYMENT_FREQUENCY`, `LEAD_TIME`, `CHANGE_FAILURE_RATE`, `MTTR`
  - [ ] Create `com.portal.integration.prometheus.model.TrendDirection` enum: `IMPROVING`, `STABLE`, `DECLINING`
  - [ ] Create `com.portal.integration.prometheus.model.TimeSeriesPoint` record: `long timestamp`, `double value`
  - [ ] Create `com.portal.integration.prometheus.model.DoraMetricsResult` record: `List<DoraMetric> metrics`, `boolean hasData`

- [ ] Task 3: Add range query to PrometheusRestClient (AC: #1)
  - [ ] Add method to existing `PrometheusRestClient` interface:
    ```java
    @GET @Path("/api/v1/query_range")
    JsonNode queryRange(
        @QueryParam("query") String query,
        @QueryParam("start") String start,
        @QueryParam("end") String end,
        @QueryParam("step") String step
    );
    ```
  - [ ] This supports matrix result type with `data.result[0].values[]` array of `[timestamp, "value"]` pairs

- [ ] Task 4: Add DORA query config to PrometheusConfig (AC: #1)
  - [ ] Extend `PrometheusConfig` `@ConfigMapping` with nested `doraQueries()` interface:
    - `deploymentFrequency()` — PromQL template
    - `leadTime()` — PromQL template
    - `changeFailureRate()` — PromQL template
    - `mttr()` — PromQL template
  - [ ] Add `doraDefaultRange()` property (default: `"30d"`)
  - [ ] Add `doraStepInterval()` property (default: `"1d"`) — range query step for time series resolution

- [ ] Task 5: Implement getDoraMetrics() in PrometheusRestAdapter (AC: #1, #9)
  - [ ] For each of the 4 DORA metrics:
    - Read PromQL template from config, substitute `{appName}` and `{range}` placeholders
    - **Current value:** instant query (`/api/v1/query`) with the template as-is
    - **Previous period value:** instant query with Prometheus `offset` modifier appended (offset = timeRange duration, e.g., `offset 30d`)
    - **Time series:** range query (`/api/v1/query_range`) with start=now-timeRange, end=now, step=doraStepInterval
  - [ ] Parse range query response: `data.result[0].values[]` → list of `TimeSeriesPoint`
  - [ ] Calculate trend: compare currentValue vs previousValue with 5% threshold
    - Deploy Frequency: higher is better → current > previous*1.05 = IMPROVING
    - Lead Time / CFR / MTTR: lower is better → current < previous*0.95 = IMPROVING
    - Within 5% = STABLE
  - [ ] Calculate `percentageChange`: `((current - previous) / abs(previous)) * 100`
  - [ ] If all 4 metrics return empty results → `DoraMetricsResult(metrics, false)` (no data)
  - [ ] Wrap HTTP/connection failures in `PortalIntegrationException(system="prometheus", operation="getDoraMetrics", message="Delivery metrics unavailable — metrics system is unreachable")`
  - [ ] Handle Prometheus 400/422 (bad query): log warning, return zero for that metric, don't fail entire call

- [ ] Task 6: Extend DevPrometheusAdapter with mock DORA data (AC: #12)
  - [ ] Add `getDoraMetrics()` to `DevPrometheusAdapter`:
    - Deploy Frequency: current=4.2/wk, previous=3.6/wk, trend=IMPROVING, +16.7%
    - Lead Time: current=2.1h, previous=2.8h, trend=IMPROVING, -25.0%
    - Change Failure Rate: current=2.3%, previous=3.1%, trend=IMPROVING, -25.8%
    - MTTR: current=45m, previous=48m, trend=STABLE, -6.3%
  - [ ] Generate 30 synthetic time series data points per metric (daily values with minor variance)

- [ ] Task 7: Create DORA domain DTOs in health package (AC: #2)
  - [ ] Create `com.portal.health.DoraMetricsDto` record: `List<DoraMetricDto> metrics`, `String timeRange`, `boolean hasData`
  - [ ] Create `com.portal.health.DoraMetricDto` record: `DoraMetricType type`, `double currentValue`, `double previousValue`, `TrendDirection trend`, `double percentageChange`, `String unit`, `List<TimeSeriesPointDto> timeSeries`
  - [ ] Create `com.portal.health.TimeSeriesPointDto` record: `long timestamp`, `double value`
  - [ ] The domain DTOs translate from adapter model types (same as HealthStatusDto translates from GoldenSignal)

- [ ] Task 8: Create DoraService (AC: #2, #10)
  - [ ] Create `com.portal.health.DoraService` as `@ApplicationScoped`
  - [ ] Inject `PrometheusAdapter`, `PrometheusConfig`
  - [ ] `getDoraMetrics(Long teamId, Long appId, String timeRange)` method:
    - `requireTeamApplication(teamId, appId)` — ownership validation → 404
    - Resolve application name from the Application entity
    - Default timeRange to `doraDefaultRange` config if null/empty
    - Call `prometheusAdapter.getDoraMetrics(appName, timeRange)`
    - Map adapter result to domain DTOs
    - Assign units: `/wk` for deploy freq, `h` or `m` for lead time, `%` for CFR, `h` or `m` for MTTR
  - [ ] Unit formatting: if value ≥ 1 hour express in hours (e.g., "2.1h"), if < 1 hour express in minutes (e.g., "45m")

- [ ] Task 9: Create DoraResource REST endpoint (AC: #2, #10, #11)
  - [ ] Create `com.portal.health.DoraResource` with `@Path("/api/v1/teams/{teamId}/applications/{appId}/dora")`
  - [ ] `@GET` method returning `DoraMetricsDto`
  - [ ] Path params: `@PathParam("teamId") Long teamId`, `@PathParam("appId") Long appId`
  - [ ] Optional query param: `@QueryParam("timeRange") String timeRange`
  - [ ] Inject `DoraService`, delegate to `getDoraMetrics(teamId, appId, timeRange)`

- [ ] Task 10: Update Casbin policy (AC: #11)
  - [ ] Add policy line: `p, member, dora, read` in `src/main/resources/casbin/policy.csv`
  - [ ] Verify `dora` as a resource string matches what `PermissionFilter` extracts from the request path (follow the same approach as `health` resource added in Story 6.1)
  - [ ] `member` inherits to `lead` and `admin` via existing role hierarchy — no `model.conf` changes needed (string matching model supports any resource name)

- [ ] Task 11: Update configuration files (AC: #1, #4, #12)
  - [ ] `application.properties`: add DORA query configuration block:
    ```properties
    portal.prometheus.dora-queries.deployment-frequency=sum(increase(deployments_total{application="{appName}"}[{range}])) / 4
    portal.prometheus.dora-queries.lead-time=histogram_quantile(0.50, rate(lead_time_seconds_bucket{application="{appName}"}[{range}])) / 3600
    portal.prometheus.dora-queries.change-failure-rate=(sum(increase(deployment_failures_total{application="{appName}"}[{range}])) / clamp_min(sum(increase(deployments_total{application="{appName}"}[{range}])), 1)) * 100
    portal.prometheus.dora-queries.mttr=histogram_quantile(0.50, rate(recovery_time_seconds_bucket{application="{appName}"}[{range}])) / 60
    portal.prometheus.dora-default-range=30d
    portal.prometheus.dora-step-interval=1d
    ```
  - [ ] Dev profile: DORA queries are irrelevant in dev mode (`DevPrometheusAdapter` returns mock data)
  - [ ] Test profile: tests mock the adapter via `@InjectMock`

- [ ] Task 12: Write PrometheusRestAdapter DORA unit tests (AC: #1, #9)
  - [ ] Add to `src/test/java/com/portal/integration/prometheus/PrometheusRestAdapterTest.java`
  - [ ] Test successful DORA metric parsing from Prometheus instant and range query JSON
  - [ ] Test `{appName}` and `{range}` placeholder substitution
  - [ ] Test trend calculation: improving, stable, declining for each metric type
  - [ ] Test percentage change calculation
  - [ ] Test empty result set → `hasData = false`
  - [ ] Test Prometheus unreachable → `PortalIntegrationException` with `system="prometheus"`, `operation="getDoraMetrics"`
  - [ ] Test bad PromQL (400/422) → graceful degradation, zero for that metric
  - [ ] Test range query response parsing (`data.result[0].values[]` → TimeSeriesPoint list)

- [ ] Task 13: Write DoraService unit tests (AC: #2, #10)
  - [ ] Create `src/test/java/com/portal/health/DoraServiceTest.java`
  - [ ] Test successful DORA metrics retrieval and DTO mapping
  - [ ] Test default time range applied when parameter is null
  - [ ] Test custom time range passed through
  - [ ] Test unit formatting (hours vs minutes)
  - [ ] Test resource ownership: wrong team → 404
  - [ ] Test resource ownership: non-existent application → 404
  - [ ] Mock `PrometheusAdapter` via `@InjectMock`

- [ ] Task 14: Write DoraResource integration test (AC: #2, #10, #11)
  - [ ] Create `src/test/java/com/portal/health/DoraResourceIT.java`
  - [ ] `@QuarkusTest` with `@InjectMock PrometheusAdapter`
  - [ ] Test `GET /api/v1/teams/{teamId}/applications/{appId}/dora` returns 200 with expected structure
  - [ ] Test with `?timeRange=90d` parameter
  - [ ] Test cross-team access returns 404
  - [ ] Test non-existent application returns 404
  - [ ] Test Casbin permits member, lead, admin roles

### Frontend Tasks

- [ ] Task 15: Create DORA TypeScript types (AC: #3, #4, #5, #6)
  - [ ] Create `src/main/webui/src/types/dora.ts`:
    ```typescript
    type DoraMetricType = 'DEPLOYMENT_FREQUENCY' | 'LEAD_TIME' | 'CHANGE_FAILURE_RATE' | 'MTTR';
    type TrendDirection = 'IMPROVING' | 'STABLE' | 'DECLINING';

    interface TimeSeriesPoint {
      timestamp: number;
      value: number;
    }

    interface DoraMetricDto {
      type: DoraMetricType;
      currentValue: number;
      previousValue: number;
      trend: TrendDirection;
      percentageChange: number;
      unit: string;
      timeSeries: TimeSeriesPoint[];
    }

    interface DoraMetricsResponse {
      metrics: DoraMetricDto[];
      timeRange: string;
      hasData: boolean;
    }
    ```

- [ ] Task 16: Create DORA API client function (AC: #2)
  - [ ] Create `src/main/webui/src/api/dora.ts`
  - [ ] `fetchDora(teamId, appId, timeRange?)` using `apiFetch<DoraMetricsResponse>`
  - [ ] Path: `/api/v1/teams/${teamId}/applications/${appId}/dora`
  - [ ] Append `?timeRange=${timeRange}` if provided

- [ ] Task 17: Create useDora hook (AC: #2)
  - [ ] Create `src/main/webui/src/hooks/useDora.ts`
  - [ ] Use `useApiFetch<DoraMetricsResponse>` with conditional path
  - [ ] Returns `{ data, error, isLoading, refresh }`

- [ ] Task 18: Create DoraStatCard component (AC: #3, #4, #5, #6, #7)
  - [ ] Create `src/main/webui/src/components/dashboard/DoraStatCard.tsx`
  - [ ] Props: `metric: DoraMetricDto | null`, `isLoading?: boolean`
  - [ ] Card anatomy (matches UX-DR4):
    ```
    ┌──────────────────────┐
    │      4.2/wk          │  ← current value + unit, large text
    │  Deploy Frequency    │  ← metric display name
    │  ↑ +18% from last mo │  ← trend arrow + percentage change
    └──────────────────────┘
    ```
  - [ ] Use PatternFly `Card` + custom stat layout
  - [ ] Trend color: green for IMPROVING, red for DECLINING, grey for STABLE
  - [ ] "Higher is better" vs "lower is better" awareness:
    - Deploy Frequency: ↑ green, ↓ red
    - Lead Time: ↓ green, ↑ red
    - Change Failure Rate: ↓ green, ↑ red
    - MTTR: ↓ green, ↑ red
  - [ ] Insufficient data state: "—" value, "Insufficient data" grey text, "Available after 7 days of activity"
  - [ ] `aria-label`: e.g., "Deployment frequency, 4.2 per week, up 18 percent from last month"

- [ ] Task 19: Create DoraTrendChart component (AC: #8)
  - [ ] Create `src/main/webui/src/components/dashboard/DoraTrendChart.tsx`
  - [ ] Props: `metrics: DoraMetricDto[]`, `timeRange: string`
  - [ ] Use PatternFly Victory charts: `Chart`, `ChartAxis`, `ChartGroup`, `ChartLine`, `ChartVoronoiContainer`
  - [ ] Import from `@patternfly/react-charts/victory`
  - [ ] Display a 2×2 grid of individual line charts (one per metric — different units/scales require separate charts)
  - [ ] X-axis: dates over the time range, formatted as "Apr 1", "Apr 8", etc.
  - [ ] Y-axis: metric values with appropriate units
  - [ ] Tooltip on hover via `ChartVoronoiContainer` showing date + value
  - [ ] Apply PatternFly chart theme — no custom colors
  - [ ] Responsive container for different screen sizes

- [ ] Task 20: Add DORA section to ApplicationHealthPage (AC: #3, #8, #9)
  - [ ] Modify `src/main/webui/src/routes/ApplicationHealthPage.tsx`
  - [ ] Add `useDora(teamId, appId)` hook call alongside existing `useHealth`
  - [ ] Add DORA section BELOW the golden signals environment sections:
    - Section header: "Delivery Metrics (DORA)" with separator
    - Loading: `LoadingSpinner` with `systemName="Prometheus"`
    - Error: inline `Alert` with "Delivery metrics unavailable — metrics system is unreachable"
    - No data (`hasData === false`): four `DoraStatCard` cards in grey insufficient-data state (NOT a PF6 EmptyState — use the cards themselves)
    - Success (`hasData === true`): four `DoraStatCard` cards in a `Grid` row + `DoraTrendChart` below
  - [ ] DORA error does NOT affect golden signals rendering (independent fetch, independent error)
  - [ ] DORA loading does NOT block golden signals rendering

- [ ] Task 21: Write DoraStatCard tests (AC: #3, #4, #5, #6, #7)
  - [ ] Create `src/main/webui/src/components/dashboard/DoraStatCard.test.tsx`
  - [ ] Test renders metric name, value, unit
  - [ ] Test improving trend shows green arrow and positive percentage
  - [ ] Test declining trend shows red arrow and negative percentage
  - [ ] Test stable trend shows grey
  - [ ] Test insufficient data shows "—" and grey label
  - [ ] Test aria-label correctness for each metric type

- [ ] Task 22: Write DoraTrendChart tests (AC: #8)
  - [ ] Create `src/main/webui/src/components/dashboard/DoraTrendChart.test.tsx`
  - [ ] Test renders chart component when data provided
  - [ ] Test renders nothing or empty state when no time series data
  - [ ] Test chart container is responsive

- [ ] Task 23: Write ApplicationHealthPage DORA section tests (AC: #3, #9)
  - [ ] Add tests to `src/main/webui/src/routes/ApplicationHealthPage.test.tsx`
  - [ ] Test DORA section renders when data available
  - [ ] Test DORA loading state independent of health loading
  - [ ] Test DORA error displays inline Alert without affecting golden signals
  - [ ] Test no DORA data shows insufficient data state

## Dev Notes

### Architecture Compliance

- **Backend extends existing patterns from Story 6.1.** The `PrometheusAdapter` interface gains `getDoraMetrics()` alongside existing `getGoldenSignals()`. Both adapter implementations (`PrometheusRestAdapter`, `DevPrometheusAdapter`) must implement the new method.
- **DORA domain classes live in `com.portal.health/`** — `DoraResource`, `DoraService`, `DoraMetricsDto`, `DoraMetricDto`, `TimeSeriesPointDto`. This follows the architecture mapping: DORA Metrics (FR28, FR34) → `health/` package.
- **DORA integration model types live in `com.portal.integration.prometheus.model/`** — `DoraMetric`, `DoraMetricType`, `TrendDirection`, `TimeSeriesPoint`, `DoraMetricsResult`. Adapter layer returns these; the service layer maps to domain DTOs.
- **REST resource → Service → Adapter chain enforced.** `DoraResource` delegates to `DoraService` which calls `PrometheusAdapter`.
- **No cross-package entity imports.** `DoraService` references `Application` by ID lookup only via `requireTeamApplication`.
- **Frontend DoraStatCard in `components/dashboard/`** (NOT `components/health/`). The architecture places it here because it's reused on both the health page (this story) and team dashboard (Epic 7).

### PromQL as Configuration (Critical Design Constraint)

Same pattern as Story 6.1 golden signals. From `project-context.md`:

> DORA metric queries follow the same pattern — externalized as configuration, parameterized by application and time range

**Placeholder naming distinction from golden signals:** Golden signal queries use `{namespace}` and `{interval}` (per-environment scoping). DORA queries use `{appName}` and `{range}` (per-application, longer time windows). This is intentional — DORA metrics span all environments for an application, not a single namespace.

The adapter does NOT author PromQL. It:
1. Reads configured query template for each DORA metric type
2. Substitutes `{appName}` and `{range}` placeholders
3. Executes the query against Prometheus HTTP API
4. Parses the numeric result

**Default DORA query templates** (platform teams customize for their metric names):

```properties
portal.prometheus.dora-queries.deployment-frequency=sum(increase(deployments_total{application="{appName}"}[{range}])) / 4
portal.prometheus.dora-queries.lead-time=histogram_quantile(0.50, rate(lead_time_seconds_bucket{application="{appName}"}[{range}])) / 3600
portal.prometheus.dora-queries.change-failure-rate=(sum(increase(deployment_failures_total{application="{appName}"}[{range}])) / clamp_min(sum(increase(deployments_total{application="{appName}"}[{range}])), 1)) * 100
portal.prometheus.dora-queries.mttr=histogram_quantile(0.50, rate(recovery_time_seconds_bucket{application="{appName}"}[{range}])) / 60
```

### Prometheus HTTP API — Range Queries

Story 6.1 used only instant queries (`GET /api/v1/query`). This story adds range queries for time series data.

**Range query endpoint:** `GET /api/v1/query_range?query=<PromQL>&start=<rfc3339|unix>&end=<rfc3339|unix>&step=<duration>`

**Response format (matrix result):**
```json
{
  "status": "success",
  "data": {
    "resultType": "matrix",
    "result": [
      {
        "metric": { "application": "checkout-api" },
        "values": [
          [1712345678, "4.2"],
          [1712432078, "3.8"],
          [1712518478, "4.5"]
        ]
      }
    ]
  }
}
```

- `values` is an array of `[unix_timestamp, "string_value"]` pairs (values are strings, parse with `Double.parseDouble()`)
- Empty `result` array = no matching time series → treat as no data for that metric
- `result` with length > 1 → use `result[0]` only (multiple series not expected for these aggregate queries; log warning if >1)
- `result[0].values` may contain `"NaN"` strings → treat as 0.0 and skip from time series chart points
- HTTP 400 = bad query, 422 = cannot execute, 503 = timeout

### DORA Metric Query Strategy

For each of the 4 DORA metrics, execute 3 queries (12 total, parallelizable per metric):

1. **Current value:** Instant query (`/api/v1/query`) with template as-is (covers last `{range}`)
2. **Previous period value:** Instant query with `offset {range}` appended to the PromQL (e.g., if range=30d, appends `offset 30d` to shift the window back)
3. **Time series:** Range query (`/api/v1/query_range`) with start=now-range, end=now, step=doraStepInterval

To append `offset`, wrap the original query: `({originalQuery}) offset {range}` — this is valid PromQL syntax for subquery offset.

**Parallelization:** Use `CompletableFuture` to execute all 12 queries concurrently (3 per metric × 4 metrics). Same pattern as `ArgoCdRestAdapter.getEnvironmentStatuses()`. Unwrap `CompletionException` to get real cause.

### Trend Calculation Logic

```java
double changeRatio = (previous != 0) ? (current - previous) / Math.abs(previous) : 0;
double percentageChange = changeRatio * 100;

boolean higherIsBetter = (type == DEPLOYMENT_FREQUENCY);
// For Lead Time, CFR, MTTR: lower is better

if (Math.abs(changeRatio) < 0.05) {
    trend = STABLE;
} else if (higherIsBetter) {
    trend = (current > previous) ? IMPROVING : DECLINING;
} else {
    trend = (current < previous) ? IMPROVING : DECLINING;
}
```

**Edge cases:**
- Previous = 0, current > 0 → trend = IMPROVING, percentageChange = 100.0 (cap at 100%)
- Both = 0 → trend = STABLE, percentageChange = 0
- Current = 0, previous > 0 → trend = direction-dependent (DECLINING for deploy freq, IMPROVING for lower-is-better), percentageChange = -100.0
- Previous = 0, current = 0 → percentageChange display: "No change"
- percentageChange > 999 → cap display at "999%+" to prevent UI overflow

### Insufficient Data Detection

Backend determines insufficient data via two checks:
1. **Empty vectors:** If all 4 DORA range queries return empty `result` arrays → `hasData = false`
2. **7-day minimum rule:** If the range query returns time series data but with fewer than 7 data points (at step=1d), the metric is marked as insufficient. When ALL 4 metrics are insufficient → `hasData = false`

Per-metric insufficient data is indicated by setting `currentValue = 0`, `previousValue = 0`, `trend = STABLE`, `percentageChange = 0`, and `timeSeries` as the sparse points received (or empty list). The frontend renders "Insufficient data" when `hasData === false` (all four cards show "—" state).

**API behavior distinction:**
- Prometheus **unreachable** → `PortalIntegrationException` → global mapper returns **502** with error JSON
- Prometheus **reachable but no/insufficient data** → returns **200** with `hasData: false` and zero-valued metrics
- Frontend distinguishes: HTTP error → `ErrorAlert`; 200 with `hasData: false` → four `DoraStatCard` in insufficient-data state (NOT a PF6 `EmptyState` component — use the stat cards themselves in grey state)

### Existing Code to Reuse

| What | Location | How |
|---|---|---|
| `PrometheusAdapter` interface | `com.portal.integration.prometheus.PrometheusAdapter` | Add `getDoraMetrics()` method |
| `PrometheusRestAdapter` | `com.portal.integration.prometheus.PrometheusRestAdapter` | Implement `getDoraMetrics()` |
| `DevPrometheusAdapter` | `com.portal.integration.prometheus.DevPrometheusAdapter` | Add mock DORA data |
| `PrometheusConfig` | `com.portal.integration.prometheus.PrometheusConfig` | Extend with `doraQueries()` |
| `PrometheusRestClient` | `com.portal.integration.prometheus.PrometheusRestClient` | Add `queryRange()` method |
| `PortalIntegrationException` | `com.portal.integration.PortalIntegrationException` | Throw with `system="prometheus"`, `operation="getDoraMetrics"` |
| `requireTeamApplication` | Service pattern from `DeploymentService` | Ownership validation → 404 |
| `CompletableFuture` parallel pattern | `ArgoCdRestAdapter.getEnvironmentStatuses()` | Parallel query execution |
| `apiFetch<T>` / `useApiFetch<T>` | `src/api/client.ts` / `src/hooks/useApiFetch.ts` | Typed fetch + hook |
| `DeepLinkButton` | `src/components/shared/DeepLinkButton.tsx` | N/A for DORA (no deep links) |
| `ErrorAlert` | `src/components/shared/ErrorAlert.tsx` | Display DORA fetch errors |
| `LoadingSpinner` | `src/components/shared/LoadingSpinner.tsx` | DORA loading state |
| `ApplicationHealthPage` | `src/routes/ApplicationHealthPage.tsx` | Add DORA section below golden signals |
| `HealthStatusBadge` pattern | `src/components/health/HealthStatusBadge.tsx` | Reference for PF6 Label color/icon approach |
| `GoldenSignalsPanel` pattern | `src/components/health/GoldenSignalsPanel.tsx` | Reference for metric card grid layout |

### Frontend — DoraStatCard Component Design (UX-DR4)

**Component location:** `src/main/webui/src/components/dashboard/DoraStatCard.tsx`

Composed from PatternFly `Card` + custom stat layout. Matches UX specification UX-DR4:

```
┌──────────────────────┐
│      4.2/wk          │  ← large value text (pf-v6-u-font-size-2xl)
│  Deploy Frequency    │  ← metric name (pf-v6-u-color-200)
│  ↑ +18% from last mo │  ← trend arrow + change % (green/red/grey)
└──────────────────────┘
```

**Metric display names and value formatting:**

| Type | Display Name | Value Format | Unit | Direction |
|------|-------------|-------------|------|-----------|
| `DEPLOYMENT_FREQUENCY` | Deploy Frequency | `X.X` | `/wk` | Higher = better |
| `LEAD_TIME` | Lead Time | `X.Xh` or `XXm` | (in value) | Lower = better |
| `CHANGE_FAILURE_RATE` | Change Failure Rate | `X.X` | `%` | Lower = better |
| `MTTR` | MTTR | `X.Xh` or `XXm` | (in value) | Lower = better |

**Value formatting function:**

```typescript
function formatDoraValue(metric: DoraMetricDto): string {
  switch (metric.type) {
    case 'DEPLOYMENT_FREQUENCY':
      return `${metric.currentValue.toFixed(1)}${metric.unit}`;
    case 'LEAD_TIME':
    case 'MTTR':
      // Backend sends hours or minutes with appropriate unit
      return metric.currentValue >= 1 && metric.unit === 'h'
        ? `${metric.currentValue.toFixed(1)}h`
        : `${Math.round(metric.currentValue)}m`;
    case 'CHANGE_FAILURE_RATE':
      return `${metric.currentValue.toFixed(1)}%`;
    default:
      return `${metric.currentValue}`;
  }
}
```

**Trend arrow and color:**

```typescript
const TREND_CONFIG = {
  IMPROVING: { arrow: '↑', color: 'var(--pf-t--global--color--status--success--default)' },
  DECLINING: { arrow: '↓', color: 'var(--pf-t--global--color--status--danger--default)' },
  STABLE:    { arrow: '—', color: 'var(--pf-t--global--color--nonstatus--gray--default)' },
};
```

Direction-aware arrows (lower-is-better metrics flip the arrow):
- Deploy Freq IMPROVING (current > previous): ↑ green
- Lead Time IMPROVING (current < previous): ↓ green
- CFR IMPROVING (current < previous): ↓ green
- MTTR IMPROVING (current < previous): ↓ green

### Frontend — DoraTrendChart Component Design

**Component location:** `src/main/webui/src/components/dashboard/DoraTrendChart.tsx`

**Definitive layout:** 2×2 grid of individual line charts (one per metric). A single multi-line chart is NOT suitable because metrics have incompatible units and scales (deployments/week vs hours vs percentages vs minutes).

**PatternFly Victory chart imports:**
```typescript
import {
  Chart,
  ChartAxis,
  ChartGroup,
  ChartLine,
  ChartVoronoiContainer,
} from '@patternfly/react-charts/victory';
```

**Chart configuration per metric:**
```typescript
<Chart
  containerComponent={<ChartVoronoiContainer labels={({ datum }) => `${datum.y}`} />}
  height={200}
  padding={{ top: 20, bottom: 40, left: 60, right: 20 }}
>
  <ChartAxis
    tickFormat={(t) => new Date(t * 1000).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
  />
  <ChartAxis dependentAxis tickFormat={(t) => `${t}${unit}`} />
  <ChartGroup>
    <ChartLine data={timeSeries.map(p => ({ x: p.timestamp, y: p.value }))} />
  </ChartGroup>
</Chart>
```

**Responsive:** Wrap each chart in a `div` with `style={{ height: '200px' }}` and use PatternFly `Grid`/`GridItem` with `span={6}` for 2-column layout on lg screens, `span={12}` on md/sm.

### Frontend — Health Page DORA Section Layout

```
[Existing golden signals sections per environment]

─── Divider ───

PageSection: "Delivery Metrics (DORA)"
├─ [If loading]: LoadingSpinner systemName="Prometheus"
├─ [If error]: Alert variant="warning" "Delivery metrics unavailable — metrics system is unreachable"
├─ [If no data]: Grid of 4 DoraStatCard with insufficient data state
└─ [If data]:
   ├─ Grid (4-col lg, 2-col md, 1-col sm): 4 × DoraStatCard
   └─ Grid (2-col lg, 1-col sm): 4 × DoraTrendChart (2×2 grid)
```

DORA section uses its own `useDora` hook — completely independent from the `useHealth` hook. If DORA fetch fails, golden signals still render normally. If golden signals fail, DORA still renders normally.

### PatternFly 6 Component Usage

- **Stat cards:** PF6 `Card` with `CardBody`. No `CardTitle` for the stat layout — use custom text elements inside `CardBody`.
- **Grid layout:** PF6 `Grid` with `GridItem span={3}` for 4-column stat cards, `GridItem span={6}` for 2-column charts.
- **Section divider:** PF6 `Divider` between golden signals and DORA sections.
- **Charts:** `@patternfly/react-charts/victory` — `Chart`, `ChartAxis`, `ChartLine`, `ChartGroup`, `ChartVoronoiContainer`.
- **Error display:** PF6 `Alert variant="warning" isInline` for DORA errors.
- **Empty state:** PF6 `EmptyState` with `EmptyStateBody` for no-data.
- **Styling:** PatternFly CSS tokens ONLY — `--pf-t--global--color--status--success--default`, `--pf-t--global--color--status--danger--default`, `--pf-t--global--color--nonstatus--gray--default`.

### Accessibility Requirements

- Each `DoraStatCard` has `aria-label` combining metric name + value + trend
  - Example: `"Deployment frequency, 4.2 per week, up 18 percent from last month"`
  - Example: `"Lead time for changes, 2.1 hours, down 25 percent from last month, improving"`
  - Example: `"Change failure rate, insufficient data, available after 7 days of activity"`
- Chart components should have `aria-label` on the container (e.g., "Deployment frequency trend over last 30 days")
- Color coding never relies on color alone — trend arrow direction (↑/↓/—) and text convey meaning
- Heading hierarchy: DORA section header as `h2` or appropriate level below the page title

### Testing Approach

**Backend:**
- `PrometheusRestAdapterTest`: add tests for `getDoraMetrics()` — mock `PrometheusRestClient`, test instant + range query parsing, offset query construction, trend calculation, error handling
- `DoraServiceTest`: mock `PrometheusAdapter` via `@InjectMock`, test DTO mapping, ownership validation, default time range
- `DoraResourceIT`: `@QuarkusTest` with `@InjectMock PrometheusAdapter`, test full REST lifecycle with REST Assured
- Never call real Prometheus; use `@InjectMock` for adapter mocking
- Never use `assertInstanceOf` on CDI beans (Quarkus ClientProxy issue)

**Frontend:**
- `DoraStatCard.test.tsx`: test all visual states (improving/declining/stable/insufficient), value formatting, aria-labels
- `DoraTrendChart.test.tsx`: test chart renders, empty state
- `ApplicationHealthPage.test.tsx`: add DORA section tests — independent loading/error from golden signals
- Mock at `apiFetch()` level using Vitest mocks
- Follow `GoldenSignalsPanel.test.tsx` and `ApplicationHealthPage.test.tsx` patterns from Story 6.2

### Previous Story Intelligence

**From Story 6.1 (Prometheus Adapter & Health Signals):**
- `PrometheusAdapter` interface + `PrometheusRestAdapter` + `DevPrometheusAdapter` established
- `PrometheusConfig` with `@ConfigMapping` established — extend with DORA config
- `PrometheusRestClient` with `@RegisterRestClient` established — add `queryRange()` method
- `@IfBuildProperty(name = "portal.prometheus.provider", ...)` for adapter selection
- Forward compatibility note from 6.1: "Story 6.3 will extend PrometheusAdapter with a `getDoraMetrics()` method using the same externalized query pattern"
- PromQL substitution pattern: read template → substitute placeholders → execute → parse `value[1]`
- Error handling: `PortalIntegrationException` for unreachable, log+zero for bad queries
- `DevPrometheusAdapter` returns mock golden signal data — extend with DORA mock data

**From Story 6.2 (Application Health Page):**
- `ApplicationHealthPage.tsx` implemented with golden signals per environment
- `useHealth` hook pattern established — `useDora` follows the same `useApiFetch` pattern
- `GoldenSignalsPanel` uses PF6 `Card` + `Grid` for metric cards — reuse pattern for `DoraStatCard`
- Health page layout: `PageSection` + title + environment sections — add DORA section below
- Per-section independent loading — DORA section loads independently from golden signals
- "Charts are for Story 6.3 (DORA trends)" — this story delivers charts
- `HealthStatusBadge` color/icon vocabulary — reference for trend color approach

**From Epic 5 (Story 5.4):**
- `require*` ownership pattern is mandatory — every service method must validate full ownership chain
- `CompletableFuture` unwrapping: always handle `CompletionException` to extract real cause
- Test with ownership violations (wrong team → 404)

### Git Intelligence

Recent commits show consistent patterns:
- `8c0c135` Fix dev-mode team context scoping and environment status mapping
- `7af2734` Epic 5 retrospective: new coding rules, TS build fixes, and design decisions
- `a889c39` Story 5.4: Promotion Confirmation & Production Gating

Established patterns: adapter interface + real + dev implementations, `@IfBuildProperty` switching, `CompletableFuture` parallel calls, `require*` ownership methods, REST Assured integration tests, `@InjectMock` for adapter mocking, `useApiFetch` for frontend hooks, PF6 `Card`/`Grid`/`Label`/`Alert` for components, PatternFly CSS tokens for styling.

### Project Structure Notes

**New files this story creates:**

```
developer-portal/src/main/java/com/portal/
├── health/
│   ├── DoraResource.java                  (NEW)
│   ├── DoraService.java                   (NEW)
│   ├── DoraMetricsDto.java                (NEW — record)
│   ├── DoraMetricDto.java                 (NEW — record)
│   └── TimeSeriesPointDto.java            (NEW — record)
├── integration/prometheus/
│   └── model/
│       ├── DoraMetric.java                (NEW — record)
│       ├── DoraMetricType.java            (NEW — enum)
│       ├── TrendDirection.java            (NEW — enum)
│       ├── TimeSeriesPoint.java           (NEW — record)
│       └── DoraMetricsResult.java         (NEW — record)

developer-portal/src/test/java/com/portal/
├── health/
│   ├── DoraServiceTest.java               (NEW)
│   └── DoraResourceIT.java               (NEW)

developer-portal/src/main/webui/src/
├── types/
│   └── dora.ts                            (NEW)
├── api/
│   └── dora.ts                            (NEW)
├── hooks/
│   └── useDora.ts                         (NEW)
├── components/dashboard/
│   ├── DoraStatCard.tsx                   (NEW)
│   ├── DoraStatCard.test.tsx              (NEW)
│   ├── DoraTrendChart.tsx                 (NEW)
│   └── DoraTrendChart.test.tsx            (NEW)
```

**Modified files:**

```
developer-portal/src/main/java/com/portal/
├── integration/prometheus/
│   ├── PrometheusAdapter.java             (MODIFIED — add getDoraMetrics)
│   ├── PrometheusRestAdapter.java         (MODIFIED — implement getDoraMetrics)
│   ├── DevPrometheusAdapter.java          (MODIFIED — add mock DORA data)
│   ├── PrometheusConfig.java              (MODIFIED — add DORA config)
│   └── PrometheusRestClient.java          (MODIFIED — add queryRange)

developer-portal/src/test/java/com/portal/
├── integration/prometheus/
│   └── PrometheusRestAdapterTest.java     (MODIFIED — add DORA tests)

developer-portal/src/main/webui/src/
├── routes/
│   ├── ApplicationHealthPage.tsx          (MODIFIED — add DORA section)
│   └── ApplicationHealthPage.test.tsx     (MODIFIED — add DORA tests)

developer-portal/src/main/resources/
├── application.properties                 (MODIFIED — add DORA config)
├── casbin/policy.csv                      (MODIFIED — add dora read)
```

### References

- [Source: planning-artifacts/epics.md#Epic 6, Story 6.3] — Full acceptance criteria and BDD scenarios
- [Source: planning-artifacts/architecture.md#Integration Adapters] — PrometheusAdapter: health signals AND DORA metrics via PromQL, Phase 4
- [Source: planning-artifacts/architecture.md#API & Communication Patterns] — `/api/v1/teams/{teamId}/applications/{appId}/dora` endpoint
- [Source: planning-artifacts/architecture.md#Data Architecture] — DORA metrics NOT persisted, fetched live from Prometheus
- [Source: planning-artifacts/architecture.md#Project Structure] — `health/DoraResource.java`, `DoraService.java`, `DoraMetricsDto.java`, `integration/prometheus/model/DoraMetric.java`
- [Source: planning-artifacts/architecture.md#Frontend Structure] — `components/dashboard/DoraStatCard.tsx`, `api/dora.ts`, `hooks/useDora.ts`, `types/dora.ts`
- [Source: planning-artifacts/architecture.md#Frontend Decisions] — PatternFly Charts (Victory-based) for DORA metrics
- [Source: planning-artifacts/architecture.md#Requirements Mapping] — DORA Metrics (FR28, FR34) → `health/` backend, `components/dashboard/` frontend, `prometheus/` adapter
- [Source: planning-artifacts/architecture.md#Gap Analysis] — "Prometheus PromQL queries: Specific queries for DORA metrics to be defined during Phase 4 implementation"
- [Source: planning-artifacts/prd.md#Observability & Health] — FR28 (DORA metric trends), FR34 (team-level DORA aggregation)
- [Source: planning-artifacts/ux-design-specification.md#DORA Stat Card] — UX-DR4: card anatomy, variants, states, trend interpretation, accessibility
- [Source: planning-artifacts/ux-design-specification.md#Team Dashboard] — Direction E: DORA stat cards + trend charts (reused component)
- [Source: planning-artifacts/ux-design-specification.md#Charts] — Victory-based PatternFly charts for DORA trend lines
- [Source: planning-artifacts/ux-design-specification.md#Empty States] — "Team dashboard, no DORA data" empty state specification
- [Source: project-context.md#Observability Integration] — PromQL as Configuration, DORA queries follow same pattern
- [Source: project-context.md#Critical Rules] — Adapter patterns, ownership validation, error handling, CompletableFuture unwrapping
- [Source: implementation-artifacts/6-1-prometheus-adapter-health-signals.md] — PrometheusAdapter, PrometheusConfig, PrometheusRestClient, DevPrometheusAdapter, externalized PromQL pattern
- [Source: implementation-artifacts/6-2-application-health-page.md] — ApplicationHealthPage layout, GoldenSignalsPanel pattern, useHealth hook, metric card design

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
