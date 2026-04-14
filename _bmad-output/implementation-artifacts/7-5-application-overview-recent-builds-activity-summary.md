# Story 7.5: Application Overview — Recent Builds & Activity Summary

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to see a summary of recent builds and activity on my application's Overview page,
so that I have immediate context without switching to the Builds or Activity tabs.

## Acceptance Criteria

1. **Two-column bottom grid replaces placeholders**
   - **Given** the Application Overview page renders its bottom section
   - **When** the two-column grid displays
   - **Then** the left column shows "Recent Builds" with the 5 most recent builds
   - **And** the right column shows "Recent Activity" with the most recent per-app activity events
   - **And** the previous placeholder cards ("Build history coming in Epic 4." / "Activity feed coming in Epic 7.") are completely removed

2. **Recent Builds section**
   - **Given** the Recent Builds section renders
   - **When** build data is available
   - **Then** it reuses the existing `BuildTable` component showing the last 5 builds
   - **And** each build shows: build ID, status badge, started timestamp, and duration
   - **And** a "View all builds" link navigates to the Builds tab

3. **Recent Activity section**
   - **Given** the Recent Activity section renders
   - **When** activity events are available for this application
   - **Then** it reuses the `ActivityFeed` component from Story 7.3
   - **And** events are scoped to the current application (not team-wide)
   - **And** event types include builds, deployments, and releases for this application

4. **Empty states**
   - **Given** no builds exist for the application
   - **When** the Recent Builds section renders
   - **Then** an empty state message is shown: "No builds yet"
   - **Given** no activity events exist for the application
   - **When** the Recent Activity section renders
   - **Then** an empty state message is shown: "No recent activity"

5. **Loading and error states**
   - **Given** build data is loading
   - **When** the Recent Builds card renders
   - **Then** a loading spinner is shown in the card body
   - **Given** activity data is loading
   - **When** the Recent Activity card renders
   - **Then** a loading spinner is shown in the card body
   - **Given** a data fetch fails for builds or activity
   - **When** the respective section renders
   - **Then** an inline warning Alert is shown in that section without blocking other sections

## Tasks / Subtasks

- [x] Task 1: Add backend endpoint for per-app activity (AC: #3)
  - [x] 1.1 Add `getApplicationActivity(Long teamId, Long appId)` method to `DashboardService`
  - [x] 1.2 Widen `DashboardResource` `@Path` from `/api/v1/teams/{teamId}/dashboard` to `/api/v1/teams/{teamId}` and add `@Path("dashboard")` on the existing GET method
  - [x] 1.3 Add `@GET @Path("applications/{appId}/activity")` method to `DashboardResource`
  - [x] 1.4 Add backend test for the new endpoint

- [x] Task 2: Add frontend API function, hook, and types for app activity (AC: #3)
  - [x] 2.1 Add `fetchAppActivity(teamId, appId)` to `api/dashboard.ts`
  - [x] 2.2 Add `useAppActivity(teamId, appId)` hook to `hooks/useDashboard.ts`

- [x] Task 3: Update `ActivityFeed` component for per-app context (AC: #3, #4)
  - [x] 3.1 Add optional `emptyMessage` prop to `ActivityFeed`, defaulting to existing "No recent activity across team applications"
  - [x] 3.2 Update `ActivityFeed.test.tsx` for the new prop

- [x] Task 4: Replace placeholder cards in `ApplicationOverviewPage` (AC: #1, #2, #3, #4, #5)
  - [x] 4.1 Add `useBuilds` and `useAppActivity` hooks to the page
  - [x] 4.2 Replace left placeholder card with Recent Builds card using `BuildTable` (sliced to 5)
  - [x] 4.3 Add "View all builds" link in Recent Builds card header
  - [x] 4.4 Replace right placeholder card with Recent Activity card using `ActivityFeed`
  - [x] 4.5 Pass `emptyMessage="No recent activity"` to `ActivityFeed`
  - [x] 4.6 Add per-section loading spinners and error alerts
  - [x] 4.7 Add empty state for builds ("No builds yet")
  - [x] 4.8 Wire `refreshAll` to include builds and activity refreshes

- [x] Task 5: Tests (all AC)
  - [x] 5.1 Update `ApplicationOverviewPage.test.tsx`:
    - Remove placeholder assertion (the current test checks for "Build history coming in Epic 4.")
    - Add mocks for `useBuilds` and `useAppActivity`
    - Test: BuildTable renders when builds available
    - Test: ActivityFeed renders when events available
    - Test: "View all builds" link renders and points to builds tab
    - Test: Empty state renders when no builds
    - Test: Empty state renders when no activity events
    - Test: Loading spinner shown per section while loading
    - Test: Warning alert shown when build fetch fails
    - Test: Warning alert shown when activity fetch fails
  - [x] 5.2 Add `DashboardResourceIT.java` test for the new `/applications/{appId}/activity` endpoint (or extend existing)

## Dev Notes

### What Already Exists — Critical Context

The bottom section of `ApplicationOverviewPage` currently renders two placeholder cards in a `Grid` with `span={6}` each. This story replaces those placeholders with live data.

| What | Location | Status |
|------|----------|--------|
| `BuildTable` | `components/build/BuildTable.tsx` | Complete — compact PF6 Table with expandable failed rows, Create Release flow, Tekton deep link. Parent supplies `builds: BuildSummary[]`, `teamId`, `appId` |
| `BuildStatusBadge` | `components/build/BuildStatusBadge.tsx` | Complete — used by BuildTable |
| `FailedBuildDetail` | `components/build/FailedBuildDetail.tsx` | Complete — used by BuildTable on expand |
| `CreateReleaseModal` | `components/build/CreateReleaseModal.tsx` | Complete — used by BuildTable for release creation |
| `ActivityFeed` | `components/dashboard/ActivityFeed.tsx` | Complete — PF6 DataList with event type icons, status labels, relative timestamps, click-to-navigate, aria-labels. Takes `events: TeamActivityEventDto[]` |
| `useBuilds` | `hooks/useBuilds.ts` | Complete — returns `{ data: BuildSummary[], error, isLoading, refresh, prepend }` for `GET /api/v1/teams/{teamId}/applications/{appId}/builds` |
| `useDashboard` | `hooks/useDashboard.ts` | Complete — team-level only (`teamId`), no `appId` param |
| `useApiFetch` | `hooks/useApiFetch.ts` | Complete — generic fetcher with `{ data, error, isLoading, refresh }` tuple |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Complete |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Complete |
| `RefreshButton` | `components/shared/RefreshButton.tsx` | Complete |
| `DashboardService` | `com.portal.dashboard.DashboardService` | Has `collectAppActivity(teamId, app, errors)` — **private** method that assembles per-app activity from builds, releases, deployments |
| `DashboardResource` | `com.portal.dashboard.DashboardResource` | Only exposes `GET /api/v1/teams/{teamId}/dashboard` — **no per-app activity endpoint** |
| `TeamActivityEventDto` | `com.portal.dashboard.TeamActivityEventDto` | Record with: eventType, applicationId, applicationName, reference, timestamp, status, actor, environmentName |
| `ApplicationOverviewPage` | `routes/ApplicationOverviewPage.tsx` | Currently wires `useEnvironments`, `useReleases`, `useHealth` for the chain. Bottom grid has placeholder cards |
| `ApplicationBuildsPage` | `routes/ApplicationBuildsPage.tsx` | Full builds page that uses `useBuilds` + `BuildTable` — **reference implementation** for how to wire builds |

### What Needs to Change

| Change | File | Details |
|--------|------|---------|
| **Widen @Path + add endpoint** | `DashboardResource.java` | Change class `@Path` to `/api/v1/teams/{teamId}`, add `@Path("dashboard")` on existing method, add `@Path("applications/{appId}/activity")` method |
| **Add public method** | `DashboardService.java` | `getApplicationActivity(Long teamId, Long appId)` — validates app ownership, calls `collectAppActivity`, returns sorted capped list |
| **Add API function** | `api/dashboard.ts` | `fetchAppActivity(teamId, appId)` |
| **Add hook** | `hooks/useDashboard.ts` | `useAppActivity(teamId, appId)` using `useApiFetch` |
| **Add optional prop** | `components/dashboard/ActivityFeed.tsx` | `emptyMessage?: string` defaulting to current text |
| **Replace placeholders** | `routes/ApplicationOverviewPage.tsx` | Wire `useBuilds`, `useAppActivity`, `BuildTable`, `ActivityFeed`, loading/error/empty states |
| **Update tests** | `routes/ApplicationOverviewPage.test.tsx` | Remove placeholder assertion, add comprehensive tests for new sections |
| **Add backend test** | `DashboardResourceIT.java` | Test the new per-app activity endpoint |
| **Update ActivityFeed test** | `components/dashboard/ActivityFeed.test.tsx` | Test custom `emptyMessage` prop |

### Backend: Per-App Activity Endpoint

The `collectAppActivity` method in `DashboardService` already assembles per-app events from builds, releases, and deployments. Expose it via a new endpoint.

**DashboardResource.java changes:**

```java
@Path("/api/v1/teams/{teamId}")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @GET
    @Path("dashboard")
    public TeamDashboardDto getTeamDashboard(@PathParam("teamId") Long teamId) {
        validateTeamAccess(teamId);
        return dashboardService.getTeamDashboard(teamId);
    }

    @GET
    @Path("applications/{appId}/activity")
    public List<TeamActivityEventDto> getApplicationActivity(
            @PathParam("teamId") Long teamId,
            @PathParam("appId") Long appId) {
        validateTeamAccess(teamId);
        return dashboardService.getApplicationActivity(teamId, appId);
    }
}
```

**DashboardService.java — new public method:**

```java
public List<TeamActivityEventDto> getApplicationActivity(Long teamId, Long appId) {
    Application app = applicationService.requireTeamApplication(teamId, appId);
    List<String> errors = new ArrayList<>();
    List<TeamActivityEventDto> events = collectAppActivity(teamId, app, errors);
    events.sort(Comparator.comparing(TeamActivityEventDto::timestamp).reversed());
    return events.size() > MAX_ACTIVITY_EVENTS
            ? events.subList(0, MAX_ACTIVITY_EVENTS)
            : events;
}
```

**Ownership validation:** Use `applicationService.requireTeamApplication(teamId, appId)` — this is the established pattern. Returns 404 if the app doesn't belong to the team. If `requireTeamApplication` does not exist on `ApplicationService`, look for `findByTeamAndId` or equivalent. The method must verify both that the app exists and that it belongs to the given team.

### Frontend: API Function and Hook

**api/dashboard.ts — add:**

```typescript
export async function fetchAppActivity(
  teamId: string,
  appId: string,
): Promise<TeamActivityEventDto[]> {
  return apiFetch(`/api/v1/teams/${teamId}/applications/${appId}/activity`);
}
```

**hooks/useDashboard.ts — add:**

```typescript
export function useAppActivity(teamId: string | undefined, appId: string | undefined) {
  const path = teamId && appId
    ? `/api/v1/teams/${teamId}/applications/${appId}/activity`
    : null;
  return useApiFetch<TeamActivityEventDto[]>(path);
}
```

### Frontend: ActivityFeed Enhancement

Add optional `emptyMessage` prop to `ActivityFeed`:

```tsx
interface ActivityFeedProps {
  events: TeamActivityEventDto[];
  emptyMessage?: string;
}

export function ActivityFeed({ events, emptyMessage }: ActivityFeedProps) {
  if (events.length === 0) {
    return (
      <Content component="p" style={{ color: 'var(--pf-t--global--text--color--subtle)', textAlign: 'center' }}>
        {emptyMessage ?? 'No recent activity across team applications'}
      </Content>
    );
  }
  // ... rest unchanged
}
```

### Frontend: ApplicationOverviewPage Implementation

Replace the bottom `PageSection` grid. Model the wiring after `ApplicationBuildsPage` for builds and `TeamDashboardPage` for activity.

**New imports needed:**

```tsx
import { useBuilds } from '../hooks/useBuilds';
import { useAppActivity } from '../hooks/useDashboard';
import { BuildTable } from '../components/build/BuildTable';
import { ActivityFeed } from '../components/dashboard/ActivityFeed';
import { Alert, Spinner } from '@patternfly/react-core';
import { Link } from 'react-router-dom';
```

**New hooks in the component:**

```tsx
const { data: builds, error: buildsError, isLoading: buildsLoading } = useBuilds(teamId, appId);
const { data: activity, error: activityError, isLoading: activityLoading } = useAppActivity(teamId, appId);
```

**Note on `useBuilds`:** Check its return type — it returns the full list. Slice to 5 when passing to BuildTable: `builds?.slice(0, 5)`.

**Replace the bottom PageSection grid:**

```tsx
<PageSection>
  <Grid hasGutter>
    <GridItem span={6}>
      <Card>
        <CardTitle>
          <Flex>
            <FlexItem grow={{ default: 'grow' }}>Recent Builds</FlexItem>
            <FlexItem>
              <Button variant="link" component={(props) => <Link {...props} to="builds" />}>
                View all builds
              </Button>
            </FlexItem>
          </Flex>
        </CardTitle>
        <CardBody>
          {buildsLoading && <Spinner size="lg" aria-label="Loading builds" />}
          {buildsError && <Alert variant="warning" title="Could not load builds" isInline />}
          {!buildsLoading && !buildsError && builds && builds.length === 0 && (
            <Content component="p" style={{ color: 'var(--pf-t--global--text--color--subtle)', textAlign: 'center' }}>
              No builds yet
            </Content>
          )}
          {!buildsLoading && builds && builds.length > 0 && teamId && appId && (
            <BuildTable builds={builds.slice(0, 5)} teamId={teamId} appId={appId} />
          )}
        </CardBody>
      </Card>
    </GridItem>
    <GridItem span={6}>
      <Card>
        <CardTitle>Recent Activity</CardTitle>
        <CardBody>
          {activityLoading && <Spinner size="lg" aria-label="Loading activity" />}
          {activityError && <Alert variant="warning" title="Could not load activity" isInline />}
          {!activityLoading && !activityError && activity && (
            <ActivityFeed events={activity} emptyMessage="No recent activity" />
          )}
        </CardBody>
      </Card>
    </GridItem>
  </Grid>
</PageSection>
```

**"View all builds" link:** Uses React Router relative `<Link to="builds">` which navigates to the Builds tab since the Overview is at `/teams/:teamId/apps/:appId` and Builds is at `/teams/:teamId/apps/:appId/builds`. Alternatively, use `../builds` if the overview route has a trailing path segment. Verify the route structure in `App.tsx` — Overview is the index route, so `to="builds"` should resolve to the Builds tab.

**RefreshAll update:** The existing `refreshAll` only refreshes environments and health. Add builds and activity refreshes:

```tsx
const { data: builds, error: buildsError, isLoading: buildsLoading, refresh: refreshBuilds } = useBuilds(teamId, appId);
const { data: activity, error: activityError, isLoading: activityLoading, refresh: refreshActivity } = useAppActivity(teamId, appId);

const refreshAll = useCallback(() => {
  refreshEnv();
  refreshHealth();
  refreshBuilds();
  refreshActivity();
}, [refreshEnv, refreshHealth, refreshBuilds, refreshActivity]);
```

### DO NOT Create / Reuse Incorrectly

- Do **not** create a new build table component — reuse `BuildTable` as-is (it's already `variant="compact"`)
- Do **not** create a new activity feed component — reuse `ActivityFeed` with the new `emptyMessage` prop
- Do **not** use `useDashboard(teamId)` and client-filter by appId — that loads all team data unnecessarily; use the new per-app activity endpoint
- Do **not** assemble activity events client-side from builds/releases/deployments — the backend already does this via `collectAppActivity`
- Do **not** add auto-refresh or polling — manual `RefreshButton` only per project conventions
- Do **not** modify `BuildTable.tsx` — it is complete and works correctly with a sliced array
- Do **not** modify `EnvironmentChain.tsx` or any environment components
- Do **not** modify any dashboard page files (`TeamDashboardPage`, `ApplicationHealthGrid`, `DoraTrendChart`)
- Do **not** create a separate `ActivityResource` — the endpoint belongs on `DashboardResource` since `DashboardService` owns the activity aggregation logic
- Do **not** return errors in the activity response body — let the global `ExceptionMapper` handle `PortalIntegrationException` → 502. Partial failures within `collectAppActivity` are already logged and silently omitted (builds/releases/deployments are independent); the endpoint returns whatever events it could collect

### Testing Approach

**ApplicationOverviewPage.test.tsx updates:**

Add mocks for the two new hooks:

```typescript
let mockBuildsResult = {
  data: null as BuildSummary[] | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
  prepend: vi.fn(),
};

let mockActivityResult = {
  data: null as TeamActivityEventDto[] | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

vi.mock('../hooks/useBuilds', () => ({
  useBuilds: () => mockBuildsResult,
}));

vi.mock('../hooks/useDashboard', () => ({
  useAppActivity: () => mockActivityResult,
}));
```

**Sample test data:**

```typescript
const sampleBuilds: BuildSummary[] = [
  { buildId: 'build-001', status: 'Passed', startedAt: '2026-04-13T10:00:00Z', duration: '2m 15s', imageReference: 'quay.io/org/app:build-001', tektonDeepLink: 'https://tekton/runs/build-001' },
  { buildId: 'build-002', status: 'Failed', startedAt: '2026-04-13T09:00:00Z', duration: '1m 05s', imageReference: null, tektonDeepLink: 'https://tekton/runs/build-002' },
];

const sampleActivity: TeamActivityEventDto[] = [
  { eventType: 'deployment', applicationId: 42, applicationName: 'payments-api', reference: 'v1.4.2', timestamp: '2026-04-13T12:00:00Z', status: 'Deployed', actor: 'dev-user', environmentName: 'dev' },
  { eventType: 'build', applicationId: 42, applicationName: 'payments-api', reference: 'build-001', timestamp: '2026-04-13T10:00:00Z', status: 'Passed', actor: 'System', environmentName: null },
];
```

**Test cases to add/update:**

| Test | What to assert |
|------|----------------|
| Builds table renders when data available | `screen.getByLabelText('Builds table')` present |
| Shows 5 builds max | Pass 7 builds, assert only 5 rows rendered |
| "View all builds" link renders | `screen.getByText('View all builds')` with correct href |
| Activity feed renders when events available | Event text (e.g., "payments-api") visible |
| Empty state: no builds | "No builds yet" text shown |
| Empty state: no activity | "No recent activity" text shown |
| Loading: builds loading | Spinner with "Loading builds" aria-label |
| Loading: activity loading | Spinner with "Loading activity" aria-label |
| Error: builds fetch fails | Warning alert "Could not load builds" shown |
| Error: activity fetch fails | Warning alert "Could not load activity" shown |
| **Remove** placeholder assertion | Delete the test checking for "Build history coming in Epic 4." and "Activity feed coming in Epic 7." |

**Mock pattern for `useBuilds`:** The `useBuilds` hook returns `{ data, error, isLoading, refresh, prepend }`. The `prepend` function is used by `useTriggerBuild` — it's not needed in overview tests but must be in the mock shape to avoid runtime errors.

**Backend test — DashboardResourceIT.java:**

Add test for `GET /api/v1/teams/{teamId}/applications/{appId}/activity`:
- 200: Returns activity events for the given application
- 404: Returns 404 for non-existent app or wrong team
- Verify events are sorted by timestamp descending
- Verify only events for the specified app are returned

### File Structure (new/modified)

**Modified files:**
```
developer-portal/src/main/java/com/portal/dashboard/DashboardResource.java    (widen @Path, add activity endpoint)
developer-portal/src/main/java/com/portal/dashboard/DashboardService.java     (add getApplicationActivity method)
developer-portal/src/main/webui/src/api/dashboard.ts                          (add fetchAppActivity)
developer-portal/src/main/webui/src/hooks/useDashboard.ts                     (add useAppActivity)
developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.tsx      (add emptyMessage prop)
developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.test.tsx (test emptyMessage prop)
developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx        (replace placeholders with live data)
developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx   (comprehensive test updates)
developer-portal/src/test/java/com/portal/dashboard/DashboardResourceIT.java  (add activity endpoint test)
```

**Should NOT touch:**
- `BuildTable.tsx` (complete, reuse as-is with sliced data)
- `BuildStatusBadge.tsx`, `FailedBuildDetail.tsx`, `CreateReleaseModal.tsx` (complete, used by BuildTable)
- `EnvironmentChain.tsx`, `EnvironmentCard.tsx` (complete)
- `TeamDashboardPage.tsx` (team dashboard, separate concern)
- `ApplicationHealthGrid.tsx`, `DoraTrendChart.tsx`, `DoraStatCard.tsx` (dashboard components)
- `types/build.ts`, `types/dashboard.ts` (existing types sufficient)
- `useEnvironments.ts`, `useReleases.ts`, `useHealth.ts` (complete)
- `ApplicationBuildsPage.tsx` (full builds page, unrelated)
- `ApplicationTabs.tsx` (no tab changes in this story)

### Accessibility Requirements

- `BuildTable` already provides: `aria-label="Builds table"`, expandable row ARIA, PF6 Table keyboard navigation
- `ActivityFeed` already provides: `aria-label` per item combining event type, app name, status, and time; DataList keyboard navigation (Tab between items, Enter to navigate)
- Loading spinners: use `aria-label` to distinguish sections ("Loading builds" vs "Loading activity")
- Error alerts: inline PF6 `Alert` with `variant="warning"` — inherently accessible
- "View all builds" link: use PF6 `Button variant="link"` wrapping a React Router `Link` — keyboard focusable

### Previous Story Intelligence

**From Story 7.4 (Environments Tab — Promotion Pipeline & Settings Consolidation):**
- Status: `ready-for-dev` (not yet implemented) — no dev notes or learnings yet
- Removes Settings tab, consolidates Vault links to Environments tab
- No overlap with this story's scope

**From Story 7.3 (Activity Feed & Aggregated DORA Trends):**
- `ActivityFeed` component was created here — reuse directly
- `formatRelativeTime` helper exported from `ActivityFeed.tsx` — used internally for timestamps
- 415/416 full suite tests pass; **1 pre-existing failure in `ApplicationTabs.test.tsx`** unrelated
- Chart mocking pattern (`@patternfly/react-charts/victory`) not needed for this story
- `DataList` with custom item template is the activity feed pattern
- Empty state text ("No recent activity across team applications") is team-focused — needs override for per-app context

**From Story 7.2 (Team Dashboard Page & Application Health Grid):**
- `useDashboard` hook pattern confirms `useApiFetch` returns `{ data, error, isLoading, refresh }`
- `TeamDashboardPage` wires `data?.recentActivity` to `ActivityFeed` with `activityError` — model this for overview but with per-app data

**From Story 7.1 (Team Dashboard Backend & Aggregation):**
- `DashboardService.collectAppActivity` is the per-app activity assembly logic — already handles builds, releases, deployments
- `MAX_EVENTS_PER_SOURCE_PER_APP = 10` limits per source; `MAX_ACTIVITY_EVENTS = 20` limits total
- Errors within `collectAppActivity` are logged and added to errors list but don't prevent other sources from collecting
- `supplyWithContext` pattern required for parallel CDI operations — but for single-app activity endpoint, parallelism within `collectAppActivity` is sequential (build/release/deploy in series), so no CDI context issue

### Git Intelligence

Recent commits (last 3):
- `9a728af` Story 7.3: Activity Feed & Aggregated DORA Trends — created `ActivityFeed.tsx`, integrated DORA charts
- `c8ba41d` Story 7.2: Team Dashboard Page & Application Health Grid — `useDashboard`, `ApplicationHealthGrid`, types
- `07700e4` Story 7.1: Team Dashboard Backend & Aggregation — `DashboardService`, `DashboardResource`, DTOs

Key patterns reinforced:
- PatternFly 6 components exclusively — no custom CSS
- PF CSS design tokens for all colors (`var(--pf-t--global--...)`)
- Co-located test files (`.test.tsx` next to component)
- `useApiFetch` wrapper for all data hooks
- Inline `Alert` for section-level errors, `variant="warning"` for degraded data
- `RefreshButton` for manual refresh (no auto-poll)
- Hook mock pattern: `vi.mock('../hooks/useXxx', () => ({ useXxx: () => mockResult }))`

### Project Structure Notes

- All files follow existing project structure conventions
- No new component files — reusing `BuildTable` and `ActivityFeed` as-is
- New hook (`useAppActivity`) co-located in existing `hooks/useDashboard.ts`
- New API function co-located in existing `api/dashboard.ts`
- Backend changes limited to `DashboardResource` and `DashboardService` — no new Java files
- No new dependencies needed — all PF6 packages, hooks, and types already installed
- No new frontend types needed — `BuildSummary` and `TeamActivityEventDto` already defined

### References

- [Source: planning-artifacts/epics.md#Epic 7, Story 7.5] — User story, BDD acceptance criteria, BuildTable reuse, ActivityFeed reuse, placeholder removal
- [Source: planning-artifacts/architecture.md#Frontend] — PatternFly 6, React Router v6, CSS token rules, component patterns
- [Source: planning-artifacts/architecture.md#Project Structure] — File locations, naming conventions, domain packages
- [Source: planning-artifacts/architecture.md#REST API] — `GET /api/v1/teams/{teamId}/...` pattern, JSON camelCase, ISO 8601 dates
- [Source: planning-artifacts/ux-design-specification.md#Application Overview] — Two-column grid below environment chain: Recent Builds table (left), Activity Feed (right)
- [Source: planning-artifacts/ux-design-specification.md#Build Table] — Compact PF6 Table with expandable rows, inline status badges
- [Source: planning-artifacts/ux-design-specification.md#Activity Feed] — DataList with event type icons, timestamps, click-to-navigate
- [Source: planning-artifacts/ux-design-specification.md#View All Link] — Button (link variant) in card header for summary-to-full navigation
- [Source: planning-artifacts/ux-design-specification.md#Empty States] — "No builds yet" + action, never blank areas
- [Source: planning-artifacts/ux-design-specification.md#Loading and Errors] — Per-section spinner, inline Alert for errors, manual refresh
- [Source: planning-artifacts/ux-design-specification.md#Accessibility] — WCAG 2.1 AA, keyboard navigation, aria-labels, status never color-only
- [Source: project-context.md] — Coding rules, testing conventions, PF6 mandatory, strict TS, CSS token rules, useEffect cleanup
- [Source: implementation-artifacts/7-3-activity-feed-aggregated-dora-trends.md] — ActivityFeed creation, mock patterns, formatRelativeTime, test suite status (415/416 pass)
- [Source: implementation-artifacts/7-1-team-dashboard-backend-aggregation.md] — DashboardService architecture, collectAppActivity, parallel aggregation, TeamActivityEventDto shape

## Dev Agent Record

### Agent Model Used

Cursor Agent (Opus 4.6)

### Debug Log References

- Backend tests initially returned 403 on the new activity endpoint due to the Casbin PermissionFilter extracting "activity" as the resource type. Fixed by adding "activity" to ACTION_SEGMENTS in PermissionFilter so the resource resolves to "applications" (which has read permission).
- Frontend test "renders ActivityFeed when events are available" initially failed because `getByText('payments-api')` matched both the page heading and the activity feed item. Fixed by using a unique reference value (`v2.0.0-rc1`) in the test data.

### Completion Notes List

- Added `getApplicationActivity(teamId, appId)` to `DashboardService` — validates app ownership via `ApplicationService.getApplicationById`, reuses existing `collectAppActivity` logic, returns up to `MAX_ACTIVITY_EVENTS` sorted by timestamp descending
- Widened `DashboardResource` class `@Path` to `/api/v1/teams/{teamId}`, added `@Path("dashboard")` on existing GET method, added new `GET applications/{appId}/activity` endpoint
- Added "activity" to `PermissionFilter.ACTION_SEGMENTS` so the Casbin resource resolves to "applications" (read) instead of an unmapped "activity" resource
- Added `fetchAppActivity` API function and `useAppActivity` hook for per-app activity data
- Added optional `emptyMessage` prop to `ActivityFeed` component for per-app context customization
- Replaced placeholder cards in `ApplicationOverviewPage` with live `BuildTable` (sliced to 5) and `ActivityFeed` with per-section loading, error, and empty states
- Added "View all builds" link in Recent Builds card header navigating to builds tab
- Wired `refreshAll` to include builds and activity refreshes
- Added 10 new frontend tests covering all AC scenarios (builds, activity, empty states, loading, errors)
- Added 4 new backend integration tests (200 with sorted events, 404 for non-existent app, 404 for cross-team access, only-specified-app events)
- All 435 frontend tests pass (38 files), all 552 backend tests pass — zero regressions

### Change Log

- 2026-04-14: Story 7.5 implementation complete — replaced placeholder cards with live Recent Builds and Recent Activity sections on Application Overview page

### File List

- `developer-portal/src/main/java/com/portal/dashboard/DashboardResource.java` (modified — widened @Path, added activity endpoint)
- `developer-portal/src/main/java/com/portal/dashboard/DashboardService.java` (modified — added getApplicationActivity method)
- `developer-portal/src/main/java/com/portal/auth/PermissionFilter.java` (modified — added "activity" to ACTION_SEGMENTS)
- `developer-portal/src/main/webui/src/api/dashboard.ts` (modified — added fetchAppActivity)
- `developer-portal/src/main/webui/src/hooks/useDashboard.ts` (modified — added useAppActivity hook)
- `developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.tsx` (modified — added emptyMessage prop)
- `developer-portal/src/main/webui/src/components/dashboard/ActivityFeed.test.tsx` (modified — added emptyMessage prop test)
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx` (modified — replaced placeholders with live data)
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx` (modified — comprehensive test updates)
- `developer-portal/src/test/java/com/portal/dashboard/DashboardResourceIT.java` (modified — added 4 activity endpoint tests)

### Review Findings

- [x] [Review][Patch] App activity endpoint masks per-source failures as an empty/partial success — fixed: endpoint now returns `AppActivityResponse` wrapper with `error` field; frontend surfaces inline warning
- [x] [Review][Patch] Overview page tests still exercise the real health hook instead of a stable mock — fixed: added `vi.mock('../hooks/useHealth')` to test file
