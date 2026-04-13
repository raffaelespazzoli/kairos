# Story 7.3: Activity Feed & Aggregated DORA Trends

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a team lead,
I want to see recent activity across all my team's applications and DORA trend charts,
so that I have temporal context on what's happening and how delivery performance is trending.

## Acceptance Criteria

1. **Two-column bottom section replaces the Story 7.2 placeholder**
   - **Given** the bottom section of the `TeamDashboardPage` renders
   - **When** data is available
   - **Then** a two-column grid layout is displayed:
     - Left column: Aggregated DORA trend charts
     - Right column: Activity feed
   - **And** the layout follows UX-DR10 (two-column grid below the health grid)

2. **Activity Feed renders as a PatternFly DataList with navigable items**
   - **Given** the Activity Feed component renders
   - **When** recent events exist in `recentActivity` from the dashboard API
   - **Then** a PatternFly `DataList` displays events in chronological order (most recent at top)
   - **And** each event item shows:
     - Event type icon: `BuildIcon` (build), `RocketIcon` (deployment), `TagIcon` (release)
     - Application name
     - Version or build number (from `reference`)
     - Status badge (using PatternFly `Label` with status variant)
     - Relative timestamp (e.g., "5 minutes ago")
     - Actor name (from `actor`)
     - Environment name for deployment events (from `environmentName`)

3. **Activity feed items are clickable and navigate to the correct context**
   - **Given** an activity feed item is displayed
   - **When** the user clicks on it
   - **Then** navigation occurs to the relevant context:
     - Build event → `/teams/{teamId}/apps/{appId}/builds`
     - Deployment event → `/teams/{teamId}/apps/{appId}/overview`
     - Release event → `/teams/{teamId}/apps/{appId}/releases`
   - **And** breadcrumbs update accordingly

4. **Activity feed empty state**
   - **Given** no recent activity exists (`recentActivity` is empty)
   - **When** the activity feed renders
   - **Then** a message is shown: "No recent activity across team applications"

5. **Aggregated DORA trend charts render in the left column**
   - **Given** the aggregated DORA trend charts render
   - **When** time series data is available from `dora.metrics` in the dashboard response
   - **Then** four line charts display trend lines over the last 30 days:
     - Deployment Frequency (deployments per day)
     - Lead Time for Changes (hours)
     - Change Failure Rate (percentage)
     - MTTR (minutes/hours)
   - **And** charts reuse the existing `DoraTrendChart` component from Story 6.3
   - **And** charts use the PatternFly chart theme with consistent colors

6. **DORA trend charts handle insufficient data**
   - **Given** insufficient data for DORA trend charts
   - **When** `dora.hasData` is `false` or no metrics have time series data
   - **Then** the chart area shows: "Trend data available after 7 days of activity"

7. **Section-level error handling**
   - **Given** the dashboard API returns `activityError` or `doraError`
   - **When** the bottom section renders
   - **Then** affected columns show a PatternFly inline `Alert` (warning variant) explaining the issue
   - **And** the unaffected column renders normally

8. **Activity feed accessibility and keyboard navigation**
   - **Given** the activity feed and DORA charts
   - **When** reviewing accessibility
   - **Then** DataList items are keyboard-navigable (Tab between items, Enter to navigate)
   - **And** each item has an `aria-label` combining event type, app name, status, and time (e.g., "Build, checkout-api, passed, 5 minutes ago")
   - **And** DORA trend charts have `aria-label` describing the metric and trend summary (already handled by `DoraTrendChart`)

9. **Loading state**
   - **Given** the dashboard API is still loading
   - **When** the bottom section renders
   - **Then** the DORA trends column and activity feed column each show independent loading states (skeleton/spinner)

## Tasks / Subtasks

- [x] Task 1: Create `ActivityFeed` component (AC: #2, #3, #4, #8)
  - [x] 1.1 Create `src/main/webui/src/components/dashboard/ActivityFeed.tsx`
  - [x] 1.2 Implement PatternFly `DataList` with `DataListItem` per event from `TeamActivityEventDto[]`
  - [x] 1.3 Render event type icon: `BuildIcon` for `build`, `RocketIcon` for `deployment`, `TagIcon` for `release`
  - [x] 1.4 Render application name, reference (version/build number), status `Label` with appropriate variant, relative timestamp, actor name
  - [x] 1.5 Render `environmentName` inline for deployment events (e.g. "→ qa")
  - [x] 1.6 Implement `formatRelativeTime` helper for relative timestamps (e.g. "5 minutes ago", "2 hours ago", "3 days ago")
  - [x] 1.7 Implement `onSelectDataListItem` to navigate: build → `builds`, deployment → `overview`, release → `releases`
  - [x] 1.8 Add `aria-label` on each `DataListItem` combining event type, app name, status, and relative time
  - [x] 1.9 Handle empty state: show "No recent activity across team applications" when `recentActivity` is empty

- [x] Task 2: Create `DoraTrendsSection` wrapper (AC: #5, #6)
  - [x] 2.1 Create a section wrapper in `TeamDashboardPage` (or inline) that passes `dora.metrics` and `dora.timeRange` to existing `DoraTrendChart`
  - [x] 2.2 Handle `dora.hasData === false` or all time series empty: show "Trend data available after 7 days of activity"

- [x] Task 3: Update `TeamDashboardPage` bottom section (AC: #1, #7, #9)
  - [x] 3.1 Replace the Story 7.3 placeholder in `TeamDashboardPage.tsx` with a two-column `Grid` layout (`span={6}` per column)
  - [x] 3.2 Left column: DORA trend charts via `DoraTrendChart` with team-aggregated data from `useDashboard` response
  - [x] 3.3 Right column: `ActivityFeed` component with `recentActivity` from `useDashboard` response
  - [x] 3.4 Handle `doraError`: show inline `Alert` (warning) in left column, still render activity feed
  - [x] 3.5 Handle `activityError`: show inline `Alert` (warning) in right column, still render DORA trends
  - [x] 3.6 Handle loading state: show skeleton/spinner per column while dashboard is loading

- [x] Task 4: Tests (all AC)
  - [x] 4.1 Create `src/main/webui/src/components/dashboard/ActivityFeed.test.tsx`
    - [x] Renders correct number of DataList items
    - [x] Shows correct icon per event type
    - [x] Shows application name, reference, status label, relative time, actor
    - [x] Shows environment name for deployment events
    - [x] Click on build event navigates to builds page
    - [x] Click on deployment event navigates to overview page
    - [x] Click on release event navigates to releases page
    - [x] Keyboard: Enter on item triggers navigation
    - [x] Empty state message when no events
    - [x] Each item has correct `aria-label`
  - [x] 4.2 Update `src/main/webui/src/routes/TeamDashboardPage.test.tsx`
    - [x] Bottom section renders two-column layout with DORA trends and activity feed
    - [x] DORA trends show charts when `dora.hasData` is true
    - [x] DORA trends show insufficient data message when `dora.hasData` is false
    - [x] Activity feed renders events from dashboard response
    - [x] Activity feed shows empty state when no events
    - [x] `doraError` shows inline Alert in DORA column, activity feed still renders
    - [x] `activityError` shows inline Alert in activity column, DORA trends still render
    - [x] Mock `@patternfly/react-charts/victory` per existing pattern

### Review Findings

- [x] [Review][Patch] Use a collision-safe identity for activity rows and selection lookup [developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.tsx:82]
- [x] [Review][Patch] Harden `formatRelativeTime()` against invalid and future timestamps [developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.tsx:30]
- [x] [Review][Patch] Add a keyboard activation test for activity items to cover the AC8 Enter-path [developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.test.tsx:105]

## Dev Notes

### API Contract (from Story 7.1 backend, consumed via Story 7.2 hook)

This story consumes the same `GET /api/v1/teams/{teamId}/dashboard` endpoint and `useDashboard` hook established by Stories 7.1 and 7.2. No new API calls or hooks needed.

Relevant response fields for this story:

```json
{
  "dora": {
    "metrics": [DoraMetricDto],
    "timeRange": "30d",
    "hasData": true
  },
  "recentActivity": [
    {
      "eventType": "build" | "release" | "deployment",
      "applicationId": 1,
      "applicationName": "checkout-api",
      "reference": "v2.1.0",
      "timestamp": "2026-04-10T14:30:00Z",
      "status": "Passed",
      "actor": "Marco",
      "environmentName": "qa"
    }
  ],
  "doraError": "string | null",
  "activityError": "string | null"
}
```

`DoraMetricDto` includes `timeSeries: TimeSeriesPoint[]` (each point has `timestamp` as Unix seconds and `value` as number), which `DoraTrendChart` already consumes.

### Existing Code to Reuse

| What | Location | How |
|------|----------|-----|
| `DoraTrendChart` | `components/dashboard/DoraTrendChart.tsx` | Pass `metrics` from `dora.metrics` and `timeRange` from `dora.timeRange` — renders a 2×2 grid of line charts; returns `null` if no metric has time series data |
| `DoraMetricDto`, `DoraMetricsResponse`, `TimeSeriesPoint` | `types/dora.ts` | Existing DORA types — the dashboard `dora` field matches `DoraMetricsResponse` shape |
| `TeamActivityEventDto`, `TeamDashboardResponse` | `types/dashboard.ts` | Created by Story 7.2 — includes `eventType`, `applicationId`, `applicationName`, `reference`, `timestamp`, `status`, `actor`, `environmentName` |
| `useDashboard` | `hooks/useDashboard.ts` | Created by Story 7.2 — returns `{ data, error, isLoading }` for the dashboard endpoint; already used by `TeamDashboardPage` |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | For column-level loading states |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | For fatal errors |
| PatternFly `Alert` | `@patternfly/react-core` | For section-level error banners (warning variant) |
| PatternFly `DataList` family | `@patternfly/react-core` | `DataList`, `DataListItem`, `DataListItemRow`, `DataListItemCells`, `DataListCell` |
| PF icons | `@patternfly/react-icons/dist/esm/icons/*` | `BuildIcon`, `RocketIcon`, `TagIcon` |
| `useNavigate`, `useParams` | `react-router-dom` | For activity item navigation |
| PF chart mock pattern | `ApplicationHealthPage.test.tsx` or `ApplicationHealthGrid.test.tsx` | `vi.mock('@patternfly/react-charts/victory', ...)` for chart tests |

### DO NOT Create / Reuse Incorrectly

- Do **not** create a new API call or hook — the dashboard endpoint already returns `recentActivity` and `dora`
- Do **not** create a new DORA chart component — reuse `DoraTrendChart` directly
- Do **not** add polling, auto-refresh, or client-side caching
- Do **not** modify any backend Java files — this is a frontend-only story
- Do **not** modify `types/dashboard.ts` or `types/dora.ts` — they already have the correct types from Stories 7.1/7.2
- Do **not** modify `DoraStatCard.tsx` or `DoraTrendChart.tsx` — reuse as-is
- Do **not** modify `ApplicationHealthGrid.tsx` — it was completed in Story 7.2

### ActivityFeed Component Implementation

**Component structure:**

```tsx
// src/main/webui/src/components/dashboard/ActivityFeed.tsx
import {
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Label,
} from '@patternfly/react-core';
import BuildIcon from '@patternfly/react-icons/dist/esm/icons/build-icon';
import RocketIcon from '@patternfly/react-icons/dist/esm/icons/rocket-icon';
import TagIcon from '@patternfly/react-icons/dist/esm/icons/tag-icon';
```

**Event type icon mapping:**

```typescript
const EVENT_ICONS: Record<string, React.ComponentType> = {
  build: BuildIcon,
  deployment: RocketIcon,
  release: TagIcon,
};
```

**Status label variant mapping — match existing status vocabulary (UX-DR7):**

```typescript
const STATUS_LABEL_COLORS: Record<string, 'green' | 'red' | 'blue' | 'gold' | 'grey'> = {
  Passed: 'green',
  Failed: 'red',
  Released: 'blue',
  Deployed: 'green',
  'In Progress': 'gold',
};
```

Default to `'grey'` for unrecognized statuses.

**Relative timestamp implementation — compute relative time from ISO string:**

```typescript
function formatRelativeTime(isoTimestamp: string): string {
  const now = Date.now();
  const then = new Date(isoTimestamp).getTime();
  const diffMs = now - then;
  const diffMinutes = Math.floor(diffMs / 60000);
  if (diffMinutes < 1) return 'just now';
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
}
```

**Navigation on item click — use `onSelectDataListItem` callback:**

```typescript
function getNavigationPath(event: TeamActivityEventDto, teamId: string): string {
  const base = `/teams/${teamId}/apps/${event.applicationId}`;
  switch (event.eventType) {
    case 'build': return `${base}/builds`;
    case 'deployment': return `${base}/overview`;
    case 'release': return `${base}/releases`;
    default: return `${base}/overview`;
  }
}
```

**Item aria-label format:** `"Build, checkout-api, passed, 5 minutes ago"` or `"Deployment, checkout-api, deployed, → qa, 2 hours ago"`

**Empty state:** When `recentActivity` is empty, render a centered text message: "No recent activity across team applications" using PatternFly `EmptyState` or simple text.

### DoraTrendChart Integration

The existing `DoraTrendChart` component accepts `metrics: DoraMetricDto[]` and `timeRange: string`. It renders a 2×2 grid of line charts and returns `null` if no metric has time series data.

**Integration in TeamDashboardPage:**

```tsx
{dashboard.dora.hasData && dashboard.dora.metrics.some(m => m.timeSeries.length > 0) ? (
  <DoraTrendChart
    metrics={dashboard.dora.metrics}
    timeRange={dashboard.dora.timeRange}
  />
) : (
  <div>Trend data available after 7 days of activity</div>
)}
```

If `doraError` is set, show an inline `Alert` above the chart area.

### Page Layout (bottom section)

The bottom section follows UX-DR10: two-column grid below the health grid.

```
└── PageSection (bottom — Story 7.3)
    └── Grid (hasGutter)
        ├── GridItem span={6} (DORA Trends)
        │   ├── Card
        │   │   ├── CardTitle: "DORA Trends"
        │   │   ├── Alert (warning) — if doraError
        │   │   └── CardBody
        │   │       └── DoraTrendChart (or insufficient data message)
        └── GridItem span={6} (Activity Feed)
            ├── Card
            │   ├── CardTitle: "Recent Activity"
            │   ├── Alert (warning) — if activityError
            │   └── CardBody
            │       └── ActivityFeed (or empty state)
```

### File Structure (new/modified)

**New files:**
```
src/main/webui/src/components/dashboard/ActivityFeed.tsx
src/main/webui/src/components/dashboard/ActivityFeed.test.tsx
```

**Modified files:**
```
src/main/webui/src/routes/TeamDashboardPage.tsx          (replace 7.3 placeholder with two-column layout)
src/main/webui/src/routes/TeamDashboardPage.test.tsx     (add bottom-section tests)
```

**Should NOT touch:**
- Backend Java files
- `types/dashboard.ts` (already has `TeamActivityEventDto` from Story 7.2)
- `types/dora.ts` (already complete)
- `DoraTrendChart.tsx` (reuse as-is)
- `DoraStatCard.tsx` (reuse as-is)
- `ApplicationHealthGrid.tsx` (completed in Story 7.2)
- `useDashboard.ts` (already provides all needed data)
- `App.tsx` routing (routes already configured)

### Testing Approach

**Component tests (`ActivityFeed.test.tsx`):**
- Renders correct number of DataList items for given events
- Shows `BuildIcon` for build events, `RocketIcon` for deployment events, `TagIcon` for release events
- Shows application name, reference, status label with correct color, relative time, actor
- Shows environment name ("→ qa") for deployment events, omits for build/release
- Click on build event calls `navigate` with correct builds URL
- Click on deployment event calls `navigate` with correct overview URL
- Click on release event calls `navigate` with correct releases URL
- Keyboard: Enter on focused item triggers navigation
- Empty state: shows "No recent activity" message when events array is empty
- Each DataListItem has correct `aria-label`

**Page tests (`TeamDashboardPage.test.tsx` updates):**
- Bottom section renders two-column layout
- DORA trends column renders `DoraTrendChart` when `dora.hasData` is true
- DORA trends column shows "Trend data available after 7 days of activity" when `dora.hasData` is false
- Activity feed column renders DataList items from dashboard response
- Activity feed shows empty state message when `recentActivity` is empty
- `doraError` → inline Alert (warning) in DORA column; activity feed still renders
- `activityError` → inline Alert (warning) in activity column; DORA trends still render

**Mock `@patternfly/react-charts/victory`** — follow existing pattern:
```typescript
vi.mock('@patternfly/react-charts/victory', () => ({
  Chart: ({ children, ...props }: any) => <div data-testid="chart" {...props}>{children}</div>,
  ChartAxis: () => <div />,
  ChartGroup: ({ children }: any) => <div>{children}</div>,
  ChartLine: () => <div data-testid="chart-line" />,
  ChartVoronoiContainer: () => <div />,
}));
```

**Mock `react-router-dom` navigation** — use `vi.mock` with `useNavigate` returning a mock function.

### Accessibility Requirements

- `DataList` items: each `DataListItem` must have `aria-labelledby` pointing to cells that compose the label, OR a direct `aria-label` on the item
- Recommended `aria-label` format: `"Build, checkout-api, passed, 5 minutes ago"` or `"Deployment, checkout-api, deployed, → qa, 2 hours ago"`
- `DataList` items must be keyboard-navigable: Tab between items, Enter/Space to navigate to the linked page
- DORA trend charts: `DoraTrendChart` already provides `aria-label` on each chart wrapper
- Status labels use PatternFly `Label` component which provides built-in accessibility
- Do **not** rely on color alone — icons differentiate event types, text labels differentiate statuses

### Responsive Behavior

- Desktop-first, desktop-only for MVP
- Two-column grid: `span={6}` for each column; at narrower viewports, PF `Grid` responsive props can collapse to `sm={12}` (single column stacking)
- DORA trend chart 2×2 grid handles its own responsive behavior internally
- DataList is full-width within its grid column

### Previous Story Intelligence

**From Story 7.2 (Team Dashboard Page & Application Health Grid):**
- `TeamDashboardPage` will be rewritten to use `useDashboard` hook instead of `useApplications`
- Page layout: DORA stat cards at top, health grid in middle, placeholder bottom section for Story 7.3
- The placeholder will be a `PageSection` with text like "Activity feed and DORA trends — coming in Story 7.3"
- Types in `types/dashboard.ts` include `TeamActivityEventDto` with all fields this story needs
- `useDashboard` hook provides `{ data, error, isLoading }` — the `data` object is `TeamDashboardResponse`
- Section-level errors (`doraError`, `activityError`) are already in the response type
- Chart mocking pattern: `vi.mock('@patternfly/react-charts/victory', ...)` with stub components

**From Story 7.1 (Team Dashboard Backend & Aggregation):**
- `recentActivity` is pre-sorted by timestamp descending, limited to 20 events
- `actor` is normalized: never null, defaults to `"System"` when no trustworthy actor data exists
- `dora` field reuses `DoraMetricsDto` (matches `DoraMetricsResponse` in TS types) with aggregated team-level time series
- DORA time series timestamps are Unix seconds (matches `DoraTrendChart`'s `timestamp * 1000` conversion)

**From Story 6.3 (DORA Metrics Retrieval & Display):**
- `DoraTrendChart` accepts `metrics: DoraMetricDto[]` and `timeRange: string`
- It renders a 2-column (`span={6}`) grid of `SingleTrendChart` components — one per metric
- Returns `null` if no metric has time series data
- Uses `ChartLine` within `ChartGroup` within `Chart` from `@patternfly/react-charts/victory`
- `formatDate` converts Unix seconds → readable date for chart axis labels

### Git Intelligence

Recent commits reinforce the patterns to follow:

- `6d52694` Epic 6 retrospective: DORA query optimization and new coding rules
- `d2a8d20` Refactor Metrics page: sub-tabs, collapsable environments, Grafana link
- `327bd46` Fix blank page: add missing Victory peer dependencies for PatternFly charts
- `c98cdf5` Story 6.3: DORA Metrics Retrieval & Display
- `57ffdba` Story 6.2: Application Health Page

Key patterns from these commits:
- PatternFly charts require explicit Victory peer dependency (already resolved in 327bd46)
- DORA components factor into `DoraStatCard` (single metric) and `DoraTrendChart` (trend lines) — reuse, don't reinvent
- PF CSS token usage for all colors (no hardcoded hex)
- `useApiFetch` for data hooks, inline `Alert` for errors
- Chart tests mock `@patternfly/react-charts/victory` with stub components

### Project Structure Notes

- All new files follow existing project structure conventions
- Components in `src/components/dashboard/`, tests co-located
- Route component stays in `src/routes/TeamDashboardPage.tsx`
- No new dependencies needed — all PF packages and icons already installed
- No new types needed — `TeamActivityEventDto` and `DoraMetricsResponse` already defined

### References

- [Source: planning-artifacts/epics.md#Epic 7, Story 7.3] — User story, BDD acceptance criteria, activity feed navigation, DORA trend charts
- [Source: planning-artifacts/architecture.md#Project Structure] — File locations, naming conventions
- [Source: planning-artifacts/architecture.md#Frontend] — PatternFly 6, React Router v6, state management, CSS token rules
- [Source: planning-artifacts/architecture.md#REST API] — `GET /api/v1/teams/{teamId}/dashboard`
- [Source: planning-artifacts/ux-design-specification.md#UX-DR10] — Team Dashboard page layout: DORA trend charts + activity feed in two-column bottom grid
- [Source: planning-artifacts/ux-design-specification.md#UX-DR22] — Activity Feed: PatternFly DataList with event type, app name, version, timestamp, status; navigable
- [Source: planning-artifacts/ux-design-specification.md#UX-DR4] — DORA Stat Card (consumed by Story 7.2, referenced for data shape)
- [Source: planning-artifacts/ux-design-specification.md#Activity Feed custom component] — DataList pattern, click → navigation to app context
- [Source: planning-artifacts/ux-design-specification.md#Empty States] — Team dashboard DORA no-data: "Available after 7 days of activity"
- [Source: planning-artifacts/ux-design-specification.md#Loading and Data Freshness Patterns] — Per-section loading, inline Alert for errors
- [Source: planning-artifacts/prd.md#FR33] — Activity feed across team apps
- [Source: planning-artifacts/prd.md#FR34] — Aggregated DORA metrics
- [Source: implementation-artifacts/7-1-team-dashboard-backend-aggregation.md] — Backend API contract, DTO shapes, DORA aggregation rules, activity feed normalization
- [Source: implementation-artifacts/7-2-team-dashboard-page-application-health-grid.md] — TeamDashboardPage rewrite, useDashboard hook, types/dashboard.ts, placeholder for Story 7.3
- [Source: project-context.md] — Coding rules, testing conventions, PF6 mandatory, strict TS, CSS token rules, useEffect cleanup rules

## Dev Agent Record

### Agent Model Used

Claude Opus 4 (Cursor Agent)

### Debug Log References

- Test failures due to duplicate text matches (DORA metric titles appearing in both stat cards and trend charts; "checkout-api" appearing in both health grid and activity feed). Fixed by using `getAllByText` assertions where text appears in multiple components.

### Completion Notes List

- Created `ActivityFeed` component with PatternFly `DataList`, event type icons (BuildIcon/RocketIcon/TagIcon), status labels with color mapping, relative timestamps, actor names, environment names for deployments, click-to-navigate, and aria-labels
- Exported `formatRelativeTime` helper for timestamp display ("just now", "5m ago", "2h ago", "3d ago")
- Integrated `DoraTrendChart` (reused as-is) into the bottom section with `hasData` and `doraError` handling, including "Trend data available after 7 days of activity" insufficient data message
- Replaced Story 7.3 placeholder in `TeamDashboardPage` with two-column `Grid` layout (span={6} each): DORA trends (left) and Activity Feed (right)
- Independent section-level error handling: `doraError` shows warning Alert in DORA column only; `activityError` shows warning Alert in activity column only
- Independent loading states per column with Spinner
- Updated chart mock in `TeamDashboardPage.test.tsx` to include `Chart`, `ChartAxis`, `ChartLine` needed by `DoraTrendChart`
- Added `recentActivity` sample events to `fullDashboardResponse` test fixture
- Updated existing tests to handle duplicate text from new bottom section (stat card + trend chart titles, health grid + activity feed app names)
- All 35 tests in the 2 story-related test files pass; 415/416 full suite tests pass (1 pre-existing failure in `ApplicationTabs.test.tsx` unrelated to this story)

### Change Log

- 2026-04-13: Implemented Story 7.3 — Activity Feed & Aggregated DORA Trends

### File List

**New files:**
- `developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.tsx`
- `developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.test.tsx`

**Modified files:**
- `developer-portal/src/main/webui/src/routes/TeamDashboardPage.tsx`
- `developer-portal/src/main/webui/src/routes/TeamDashboardPage.test.tsx`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
