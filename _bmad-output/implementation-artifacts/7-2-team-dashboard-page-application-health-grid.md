# Story 7.2: Team Dashboard Page & Application Health Grid

Status: done

## Story

As a team lead,
I want to see all my team's applications with their health across environments at a glance and drill into any app,
so that I can quickly spot problems and take action.

## Acceptance Criteria

1. **Given** a team lead navigates to the team dashboard, **When** the `TeamDashboardPage` renders, **Then** it is the default landing view when selecting a team and the page layout follows UX-DR10: DORA stat cards at top, health grid in middle, placeholder bottom section (completed by Story 7.3).

2. **Given** the DORA stat cards row renders at the top, **When** aggregated DORA data is available from the dashboard API, **Then** four `DoraStatCard` components display team-aggregated metric values, trend arrows, and percentage changes. **When** DORA data is unavailable or the `doraError` section error is present, **Then** each card shows the "Insufficient data" state.

3. **Given** the application health grid renders, **When** team applications exist, **Then** a PatternFly `Table` (compact variant) displays one row per application. Each row shows: application name, one environment health dot per environment in the chain (ordered by `promotion_order`), deployed version beside each dot, and a deployment activity sparkline at the end.

4. **Given** environment health dots in the grid, **Then** they are compact colored circles (8px): green (`HEALTHY`), red (`UNHEALTHY`), yellow (`DEPLOYING`), grey (`NOT_DEPLOYED`/`UNKNOWN`). Status never relies on color alone — dots include accessible text via `aria-label` and tooltip.

5. **Given** an environment dot is hovered, **When** a tooltip appears, **Then** it shows: environment name, full status text, deployed version, and last deployment timestamp.

6. **Given** a developer clicks on any application row in the health grid, **When** the click is processed, **Then** navigation occurs to `/teams/{teamId}/apps/{appId}/overview` and breadcrumbs update to: Team → Application → Overview.

7. **Given** an application has an unhealthy environment, **When** the row renders, **Then** the red dot is immediately visible without interaction — no expansion or drilling required.

8. **Given** no applications exist for the team, **When** the dashboard renders, **Then** the health grid section shows `NoApplicationsEmptyState` with an "Onboard Application" button (reuse existing component).

9. **Given** the health grid is navigated with keyboard, **When** tabbing through rows, **Then** each row is focusable, Enter/Space navigates to the application overview, and sparkline charts have `aria-label` describing the trend (e.g. "12 deployments in the last 30 days").

10. **Given** the dashboard API returns section-level errors (`healthError`, `doraError`, `activityError`), **When** partial data is available, **Then** affected sections show a PatternFly inline `Alert` (warning variant) explaining the issue; available sections render normally.

11. **Given** the dashboard API is loading, **When** the page renders, **Then** each section shows independent loading states (DORA cards show loading state, grid shows `Skeleton`/`Spinner`) — no global loading overlay.

## Tasks / Subtasks

- [x] Task 1: Create dashboard TypeScript types (AC: #1, #3, #4, #5)
  - [x] 1.1 Create `src/types/dashboard.ts` with `TeamDashboardResponse`, `ApplicationHealthSummaryDto`, `DashboardEnvironmentEntryDto`, `TeamActivityEventDto` types matching the 7.1 backend contract
  - [x] 1.2 Define `EnvironmentDotStatus` union type: `'HEALTHY' | 'UNHEALTHY' | 'DEPLOYING' | 'NOT_DEPLOYED' | 'UNKNOWN'`

- [x] Task 2: Create dashboard API client and hook (AC: #1, #10, #11)
  - [x] 2.1 Create `src/api/dashboard.ts` with `fetchTeamDashboard(teamId)` calling `GET /api/v1/teams/${teamId}/dashboard` via `apiFetch`
  - [x] 2.2 Create `src/hooks/useDashboard.ts` using `useApiFetch<TeamDashboardResponse>` pattern

- [x] Task 3: Create `ApplicationHealthGrid` component (AC: #3, #4, #5, #6, #7, #9)
  - [x] 3.1 Create `src/components/dashboard/ApplicationHealthGrid.tsx` — PatternFly `Table` (compact) with one row per `ApplicationHealthSummaryDto`
  - [x] 3.2 Implement `EnvironmentDot` inline component — 8px colored circle using PF CSS tokens, with `Tooltip` showing env name, status, version, `lastDeploymentAt`
  - [x] 3.3 Implement `DeploymentSparkline` inline component using PatternFly `ChartArea`/`ChartGroup` (sparkline variant) for 30-day deployment activity from DORA `timeSeries`
  - [x] 3.4 Implement row click → `navigate` to application overview
  - [x] 3.5 Implement keyboard navigation: focusable rows, Enter/Space activates

- [x] Task 4: Rewrite `TeamDashboardPage` with full dashboard layout (AC: #1, #2, #8, #10, #11)
  - [x] 4.1 Replace current `TeamDashboardPage.tsx` content: use `useDashboard` hook instead of `useApplications` context
  - [x] 4.2 Add DORA stat cards row (4-column `Grid` of `DoraStatCard` components, reused from Story 6.3)
  - [x] 4.3 Add `ApplicationHealthGrid` in middle section
  - [x] 4.4 Add placeholder bottom section for Story 7.3 (activity feed + DORA trends)
  - [x] 4.5 Handle empty state via `NoApplicationsEmptyState`
  - [x] 4.6 Handle section-level errors with inline `Alert` (warning variant)
  - [x] 4.7 Handle independent loading states per section

- [x] Task 5: Tests (all AC)
  - [x] 5.1 Create `src/components/dashboard/ApplicationHealthGrid.test.tsx` — renders rows, dots with correct colors, tooltips, sparklines, row click navigation, keyboard nav, empty state
  - [x] 5.2 Update `src/routes/TeamDashboardPage.test.tsx` — DORA cards rendering, health grid rendering, loading states, error states, section errors, empty state
  - [x] 5.3 Mock `@patternfly/react-charts/victory` in tests (follow `ApplicationHealthPage.test.tsx` pattern)

### Review Findings

- [x] [Review][Patch] Render four `DoraStatCard` components in insufficient-data state when `doraError` is present, instead of replacing the entire section with a warning alert.
- [x] [Review][Patch] Preserve the health grid when `healthError` is present so partial application data still renders alongside the warning alert.
- [x] [Review][Patch] Replace the full-page initial `LoadingSpinner` with section-level loading states so the dashboard does not use a global loading view on first fetch.
- [x] [Review][Patch] Implement and test explicit keyboard activation for application rows (`Enter`/`Space`) instead of relying on `isClickable` alone.
- [x] [Review][Patch] Include `statusDetail` in the environment tooltip when present so the tooltip exposes the full status text required by the story.
- [x] [Review][Patch] Surface `activityError` in the placeholder bottom section with an inline warning alert so all section-level dashboard errors are represented.

## Dev Notes

### API Contract (from Story 7.1 backend)

Single endpoint: `GET /api/v1/teams/{teamId}/dashboard`

Response shape (`TeamDashboardDto`):
```json
{
  "applications": [ApplicationHealthSummaryDto],
  "dora": DoraMetricsDto,
  "recentActivity": [TeamActivityEventDto],
  "healthError": "string | null",
  "doraError": "string | null",
  "activityError": "string | null"
}
```

`ApplicationHealthSummaryDto`:
```json
{
  "applicationId": 1,
  "applicationName": "checkout-api",
  "runtimeType": "quarkus",
  "environments": [
    {
      "environmentName": "dev",
      "status": "HEALTHY",
      "deployedVersion": "v2.1.0",
      "lastDeploymentAt": "2026-04-10T14:30:00Z",
      "statusDetail": null
    }
  ]
}
```

Environments are ordered by `promotion_order` (the data-driven chain: dev → qa → staging → prod).

Status vocabulary: `HEALTHY`, `UNHEALTHY`, `DEPLOYING`, `NOT_DEPLOYED`, `UNKNOWN`. No new statuses — matches existing env chain vocabulary.

DORA section reuses `DoraMetricsDto` (same shape as `types/dora.ts` `DoraMetricsResponse` — `metrics: DoraMetricDto[]`, `timeRange`, `hasData`). The `DoraMetricDto` includes `timeSeries` which the deployment sparkline can source from `DEPLOYMENT_FREQUENCY` metric.

`TeamActivityEventDto`: `eventType`, `applicationId`, `applicationName`, `reference`, `timestamp`, `status`, `actor`, optional `environmentName`. Activity feed is Story 7.3 scope — ignore for this story.

### Existing Code to Reuse

| What | Location | How |
|------|----------|-----|
| `DoraStatCard` | `components/dashboard/DoraStatCard.tsx` | Render four in a `Grid` row; pass `metric` from dashboard response `.dora.metrics`; pass `type` for each of the four DORA types |
| `NoApplicationsEmptyState` | `components/shared/NoApplicationsEmptyState.tsx` | Reuse for empty team state |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | For initial full-page load |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | For fatal fetch errors |
| `useApiFetch` | `hooks/useApiFetch.ts` | For `useDashboard` hook — `useApiFetch<TeamDashboardResponse>('/api/v1/teams/${teamId}/dashboard')` |
| `apiFetch` | `api/client.ts` | Already handles OIDC bearer, error parsing to `PortalError`, 502 mapping |
| `formatDoraValue` | `components/dashboard/DoraStatCard.tsx` | Exported utility for DORA value formatting (if needed outside cards) |
| `useTeams` | `contexts/TeamsContext.tsx` | For `activeTeam?.name` in page title |
| `DoraTrendChart` charting pattern | `components/dashboard/DoraTrendChart.tsx` | Import pattern for `@patternfly/react-charts/victory` — `ChartArea`/`ChartGroup` for sparkline |
| `HealthStatusBadge` | `components/health/HealthStatusBadge.tsx` | Reference for status → color mapping pattern (but grid dots are custom 8px circles, not Label badges) |

### DO NOT Reuse / Create

- Do **not** import `useApplications` context in the new `TeamDashboardPage` — the dashboard API replaces the old applications list
- Do **not** create separate API calls for DORA or health per app — the dashboard endpoint returns everything in one request
- Do **not** add polling, auto-refresh, or client-side caching — user-initiated refresh only via `useApiFetch.refresh()`
- Do **not** implement the bottom section (activity feed + DORA trends) — that is Story 7.3

### Environment Dot Implementation

The health dots are **not** PatternFly `Label` or `HealthStatusBadge` — they are compact 8px circles for the table grid.

Color mapping using **PF CSS tokens** (not hardcoded hex):
```typescript
const DOT_COLORS: Record<string, string> = {
  HEALTHY: 'var(--pf-t--global--color--status--success--default)',
  UNHEALTHY: 'var(--pf-t--global--color--status--danger--default)',
  DEPLOYING: 'var(--pf-t--global--color--status--warning--default)',
  NOT_DEPLOYED: 'var(--pf-t--global--color--nonstatus--gray--default)',
  UNKNOWN: 'var(--pf-t--global--color--nonstatus--gray--default)',
};
```

Each dot renders as:
```tsx
<Tooltip content={/* env name, status, version, timestamp */}>
  <span
    style={{
      display: 'inline-block',
      width: '8px',
      height: '8px',
      borderRadius: '50%',
      backgroundColor: DOT_COLORS[status],
    }}
    aria-label={`${envName}: ${status}, ${version}`}
  />
</Tooltip>
```

Version text is displayed beside each dot (not inside the tooltip only).

### Sparkline Implementation

Use PatternFly sparkline chart pattern — `ChartGroup` (no axes for sparklines) with `ChartArea`:

```tsx
import { ChartArea, ChartGroup, ChartVoronoiContainer } from '@patternfly/react-charts/victory';
```

Data source: Extract the `DEPLOYMENT_FREQUENCY` metric from the dashboard response's `dora.metrics`, use its `timeSeries` array. If no deployment frequency data, render empty/dash.

Sparkline sizing: small inline chart, approximately 80px wide × 24px tall, no axes, no labels. Use `ChartVoronoiContainer` for hover tooltips if desired, or keep minimal.

`aria-label` on sparkline: `"${count} deployments in the last 30 days"` where count is derived from summing the time series values.

### Page Layout Structure

```
TeamDashboardPage
├── PageSection (header)
│   └── Title: "{teamName} Dashboard"
├── PageSection (DORA stat cards)
│   └── Grid (4 columns)
│       ├── DoraStatCard (Deploy Frequency)
│       ├── DoraStatCard (Lead Time)
│       ├── DoraStatCard (Change Failure Rate)
│       └── DoraStatCard (MTTR)
├── PageSection (health grid)
│   ├── Alert (warning) — if healthError present
│   └── Card
│       └── ApplicationHealthGrid (Table compact)
│           └── Rows: [AppName] [●v dev] [●v qa] [●v prod] [▃▅▂▆ sparkline]
└── PageSection (placeholder for 7.3)
    └── Content: "Activity feed and DORA trends — coming in Story 7.3"
```

### File Structure (new/modified)

**New files:**
```
src/main/webui/src/types/dashboard.ts
src/main/webui/src/api/dashboard.ts
src/main/webui/src/hooks/useDashboard.ts
src/main/webui/src/components/dashboard/ApplicationHealthGrid.tsx
src/main/webui/src/components/dashboard/ApplicationHealthGrid.test.tsx
```

**Modified files:**
```
src/main/webui/src/routes/TeamDashboardPage.tsx          (rewrite)
src/main/webui/src/routes/TeamDashboardPage.test.tsx     (rewrite)
```

**Should NOT touch:**
- Backend Java files (backend is Story 7.1 scope)
- `App.tsx` routing (routes already map to `TeamDashboardPage`)
- `DoraStatCard.tsx` (reuse as-is)
- `DoraTrendChart.tsx` (Story 7.3 scope)
- Context providers (`TeamsContext`, `ApplicationsContext`)

### Testing Approach

**Component tests (`ApplicationHealthGrid.test.tsx`):**
- Renders correct number of rows for given applications
- Renders environment dots with correct colors for each status
- Renders version text beside each dot
- Shows tooltips on hover with env details
- Row click calls navigate to correct URL
- Keyboard: Enter/Space on row triggers navigation
- Sparkline renders with aria-label
- Empty applications array renders nothing (empty handled at page level)

**Page tests (`TeamDashboardPage.test.tsx`):**
- Loading state: DORA cards show loading, grid shows spinner
- Success: DORA cards render with correct values, grid renders with applications
- Empty team: shows `NoApplicationsEmptyState`
- Fatal error: shows `ErrorAlert`
- Partial error: `healthError` → inline Alert in grid section, DORA cards still render
- Partial error: `doraError` → DORA cards show insufficient, grid still renders

**Mock `@patternfly/react-charts/victory`** — follow the existing pattern from `ApplicationHealthPage.test.tsx`:
```typescript
vi.mock('@patternfly/react-charts/victory', () => ({
  ChartArea: ({ 'aria-label': ariaLabel }: any) => <div data-testid="chart-area" aria-label={ariaLabel} />,
  ChartGroup: ({ children }: any) => <div data-testid="chart-group">{children}</div>,
  ChartVoronoiContainer: () => <div />,
}));
```

**Mock API** at `apiFetch` level per project convention.

### TypeScript Types to Create

```typescript
// src/types/dashboard.ts

import type { DoraMetricsResponse } from './dora';

export type EnvironmentDotStatus =
  | 'HEALTHY'
  | 'UNHEALTHY'
  | 'DEPLOYING'
  | 'NOT_DEPLOYED'
  | 'UNKNOWN';

export interface DashboardEnvironmentEntryDto {
  environmentName: string;
  status: EnvironmentDotStatus;
  deployedVersion: string;
  lastDeploymentAt: string | null;
  statusDetail: string | null;
}

export interface ApplicationHealthSummaryDto {
  applicationId: number;
  applicationName: string;
  runtimeType: string;
  environments: DashboardEnvironmentEntryDto[];
}

export interface TeamActivityEventDto {
  eventType: 'build' | 'release' | 'deployment';
  applicationId: number;
  applicationName: string;
  reference: string;
  timestamp: string;
  status: string;
  actor: string;
  environmentName: string | null;
}

export interface TeamDashboardResponse {
  applications: ApplicationHealthSummaryDto[];
  dora: DoraMetricsResponse;
  recentActivity: TeamActivityEventDto[];
  healthError: string | null;
  doraError: string | null;
  activityError: string | null;
}
```

### Accessibility Requirements

- Environment dots: `aria-label` on each dot with env name + status + version (e.g. "dev: HEALTHY, v2.1.0")
- Sparkline: `aria-label` with deployment count summary
- Table rows: use PF Table `isClickable` prop (already provides focusable rows)
- DORA stat cards: already have comprehensive `aria-label` built in
- Section error Alerts: PF `Alert` provides built-in accessibility
- Do **not** rely on color alone for health status — dots use color + tooltip text + `aria-label`

### Responsive Behavior

- Desktop-first, desktop-only for MVP
- DORA stat cards: 4-column grid; collapse to 2×2 at narrower viewports via PF `Grid` responsive `span` props
- Health grid: PatternFly compact `Table` — horizontal scroll if needed at narrow viewports
- No mobile/tablet optimization required

### Project Structure Notes

- All new files follow existing project structure conventions
- Types in `src/types/`, API in `src/api/`, hooks in `src/hooks/`, components in `src/components/dashboard/`
- Route component stays in `src/routes/TeamDashboardPage.tsx`
- No new dependencies needed — `@patternfly/react-charts` already installed

### References

- [Source: planning-artifacts/epics.md#Epic 7, Story 7.2] — User story, BDD acceptance criteria
- [Source: planning-artifacts/architecture.md#Project Structure] — File locations, naming conventions
- [Source: planning-artifacts/architecture.md#Frontend] — PatternFly 6, React Router v6, state management
- [Source: planning-artifacts/architecture.md#REST API] — `GET /api/v1/teams/{teamId}/dashboard`
- [Source: planning-artifacts/ux-design-specification.md#Direction E] — Team Dashboard layout (four-column DORA row, app grid, two-column bottom)
- [Source: planning-artifacts/ux-design-specification.md#Application Health Grid Row] — 8px dots, version text, sparkline, row click
- [Source: planning-artifacts/ux-design-specification.md#UX-DR5] — Application Health Grid Row pattern
- [Source: planning-artifacts/ux-design-specification.md#UX-DR10] — Team Dashboard page layout
- [Source: planning-artifacts/ux-design-specification.md#Loading and Data Freshness] — Spinner, partial data, inline Alert
- [Source: planning-artifacts/prd.md#FR32-FR35] — Team dashboard, activity feed, aggregated DORA, drill-down
- [Source: implementation-artifacts/7-1-team-dashboard-backend-aggregation.md] — Backend API contract, DTO shapes, partial failure pattern
- [Source: project-context.md] — Coding rules, testing conventions, PF6 mandatory, strict TS

### Previous Story Intelligence (7.1)

Story 7.1 defines the backend contract this story consumes. Key takeaways:

- Single API call for entire dashboard — no per-app fan-out from frontend
- `DoraMetricsDto` is reused (same shape as existing `types/dora.ts` `DoraMetricsResponse`) so `DoraStatCard` works without adaptation
- Section-level error strings (`healthError`, `doraError`, `activityError`) enable partial rendering — handle each independently
- `status` fields are **strings** matching the union type — no competing enum
- Environment entries are pre-ordered by `promotion_order` — no frontend sorting needed
- Activity events are Story 7.3 scope but included in the response — ignore `recentActivity` for now
- `UNKNOWN` status for unreachable ArgoCD/Prometheus — grey dot with tooltip explanation from `statusDetail`

### Git Intelligence

Recent commits show Epic 6 (observability) was the prior work:
- `6d52694` — Epic 6 retro: DORA optimization
- `d2a8d20` — Metrics page refactor (sub-tabs, collapsable envs)
- `327bd46` — Fixed Victory peer deps for PF charts
- `c98cdf5` — Story 6.3 DORA metrics
- `57ffdba` — Story 6.2 Application Health Page

Patterns established: PF chart mocking in tests, `useApiFetch` for data hooks, inline `Alert` for errors, `DoraStatCard` / `DoraTrendChart` component factoring, PF CSS token usage for all colors.

## Dev Agent Record

### Agent Model Used

Cursor Agent (Opus 4.6)

### Debug Log References

- App.test.tsx required update to mock the new `/dashboard` endpoint via `useApiFetch` — the old test relied on `ApplicationsProvider` which the rewritten page no longer uses.
- Pre-existing test failure in `ApplicationTabs.test.tsx` (expects "Health" tab label but component renders "Metrics") — not related to this story.

### Completion Notes List

- Created `types/dashboard.ts` with all DTOs matching Story 7.1 backend contract, including `EnvironmentDotStatus` union type and `TeamDashboardResponse` interface importing `DoraMetricsResponse`.
- Created `api/dashboard.ts` and `hooks/useDashboard.ts` following existing project patterns (`apiFetch` wrapper, `useApiFetch` hook with conditional path).
- Built `ApplicationHealthGrid` component with: PatternFly compact Table, inline `EnvironmentDot` (8px colored circles using PF CSS tokens, Tooltip with env details, aria-label), `DeploymentSparkline` (ChartArea/ChartGroup, 80×24px, aria-label with deployment count), clickable rows via `isClickable` prop with navigate to app overview.
- Rewrote `TeamDashboardPage` with full dashboard layout: DORA stat cards in 4-column Grid, ApplicationHealthGrid in Card, placeholder section for Story 7.3, independent loading/error/empty states per section, inline Alert (warning) for section-level errors.
- Wrote 9 component tests for ApplicationHealthGrid and 12 page tests for TeamDashboardPage covering: loading, success, empty, fatal error, partial errors (doraError, healthError), DORA insufficient data, placeholder section.
- Updated `App.test.tsx` to mock `/dashboard` endpoint for the rewritten page.
- All 390 tests pass; 1 pre-existing failure in ApplicationTabs unrelated to this story.

### Change Log

- 2026-04-13: Story 7.2 implementation complete — Team Dashboard Page with DORA stat cards, Application Health Grid, section-level error handling, and comprehensive test coverage.

### File List

**New files:**
- `developer-portal/src/main/webui/src/types/dashboard.ts`
- `developer-portal/src/main/webui/src/api/dashboard.ts`
- `developer-portal/src/main/webui/src/hooks/useDashboard.ts`
- `developer-portal/src/main/webui/src/components/dashboard/ApplicationHealthGrid.tsx`
- `developer-portal/src/main/webui/src/components/dashboard/ApplicationHealthGrid.test.tsx`

**Modified files:**
- `developer-portal/src/main/webui/src/routes/TeamDashboardPage.tsx` (rewritten)
- `developer-portal/src/main/webui/src/routes/TeamDashboardPage.test.tsx` (rewritten)
- `developer-portal/src/main/webui/src/App.test.tsx` (updated mock for dashboard endpoint)
