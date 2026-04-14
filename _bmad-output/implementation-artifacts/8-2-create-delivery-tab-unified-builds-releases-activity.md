# Story 8.2: Create Delivery Tab — Unified Builds, Releases & Activity View

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want a single Delivery tab that shows my builds, releases, and recent activity in a 3-column layout,
So that I can see the full delivery pipeline — from CI output to versioned releases to deployment events — in one view.

## Acceptance Criteria

1. **3-column Delivery page with independent data**
   - **Given** a developer navigates to the Delivery tab for an application
   - **When** the page renders
   - **Then** a 3-column PatternFly Grid layout displays:
     - Left column (span 4): full build list using `BuildTable` with Create Release flow intact
     - Middle column (span 4): full release list using `ReleaseTable`
     - Right column (span 4): recent activity using `ActivityFeed` with per-app events

2. **Builds and Releases tabs removed, Delivery tab added**
   - **Given** the Builds tab and Releases tab currently exist as separate pages
   - **When** this story is implemented
   - **Then** the `builds` and `releases` entries are removed from `APP_TABS` in `ApplicationTabs.tsx`
   - **And** a new `delivery` entry is added to `APP_TABS`
   - **And** the `<Route path="builds" ... />` and `<Route path="releases" ... />` are removed from `App.tsx`
   - **And** a new `<Route path="delivery" ... />` is added to `App.tsx`
   - **And** `ApplicationBuildsPage.tsx`, `ApplicationBuildsPage.test.tsx`, `ApplicationReleasesPage.tsx`, and `ApplicationReleasesPage.test.tsx` are deleted
   - **And** the tab bar shows exactly 3 tabs: Overview | Delivery | Metrics

3. **Independent loading, error, and empty states per column**
   - **Given** the 3-column layout renders
   - **When** each column has its own data source
   - **Then** each column shows its own loading spinner independently
   - **And** each column shows its own inline warning Alert on error without blocking sibling columns
   - **And** empty states are handled per column: "No builds yet", "No releases yet", "No recent activity"

4. **Cross-column release creation coordination**
   - **Given** a developer creates a release from a successful build in the Builds column
   - **When** the `CreateReleaseModal` completes successfully
   - **Then** the new release appears in the Releases column via automatic data refresh

5. **Trigger Build preserved**
   - **Given** the Delivery page renders
   - **When** the Builds column Card header is visible
   - **Then** a "Trigger Build" button is present in the Builds Card header
   - **And** triggering a build prepends the new row to the Builds column (existing `prepend` behavior)

6. **RefreshButton behavior**
   - **Given** the Delivery page is displayed
   - **When** the RefreshButton is clicked
   - **Then** all three data sources (builds, releases, activity) are refreshed

7. **All tests pass with zero regressions**
   - **Given** all changes are implemented
   - **When** the full frontend test suite runs
   - **Then** all tests pass with zero regressions

## Tasks / Subtasks

- [ ] Task 1: Create `ApplicationDeliveryPage.tsx` (AC: #1, #3, #5, #6)
  - [ ] 1.1 Create `routes/ApplicationDeliveryPage.tsx`
  - [ ] 1.2 Wire hooks: `useBuilds(teamId, appId)`, `useTriggerBuild(teamId, appId)`, `useReleases(teamId, appId)`, `useAppActivity(teamId, appId)`
  - [ ] 1.3 Create `refreshAll` callback combining `refreshBuilds`, `refreshReleases`, `refreshActivity`
  - [ ] 1.4 Render page header `PageSection` with page title and `RefreshButton` wired to `refreshAll`
  - [ ] 1.5 Render content `PageSection` with `Grid` (`numColumns={12}`)
  - [ ] 1.6 Builds column (`GridItem span={4}`): Card with "Builds" CardTitle + "Trigger Build" button in CardHeader actions, CardBody with `LoadingSpinner`/`ErrorAlert`/EmptyState/`BuildTable`
  - [ ] 1.7 Releases column (`GridItem span={4}`): Card with "Releases" CardTitle, CardBody with `LoadingSpinner`/`ErrorAlert`/EmptyState/`ReleaseTable`
  - [ ] 1.8 Activity column (`GridItem span={4}`): Card with "Recent Activity" CardTitle, CardBody with `LoadingSpinner`/`ErrorAlert`/EmptyState/`ActivityFeed`
  - [ ] 1.9 Wire trigger build: call `trigger()`, on success call `prependBuild(newBuild)`, show `triggerError` as inline danger Alert above BuildTable
  - [ ] 1.10 Wire `onReleaseCreated={refreshReleases}` on `BuildTable` (see Task 2)
  - [ ] 1.11 Pass `emptyMessage="No recent activity"` to `ActivityFeed` (per Story 7.5 pattern)

- [ ] Task 2: Add `onReleaseCreated` callback prop to `BuildTable` (AC: #4)
  - [ ] 2.1 Add optional `onReleaseCreated?: () => void` prop to `BuildTable`'s props interface
  - [ ] 2.2 In `BuildTable`, after successful `CreateReleaseModal` completion, call `onReleaseCreated?.()` alongside the existing inline release badge update
  - [ ] 2.3 Update `BuildTable.test.tsx`: add test that `onReleaseCreated` callback is invoked after successful release creation

- [ ] Task 3: Update `ApplicationTabs.tsx` (AC: #2)
  - [ ] 3.1 Remove `{ key: 'builds', label: 'Builds' }` from `APP_TABS`
  - [ ] 3.2 Remove `{ key: 'releases', label: 'Releases' }` from `APP_TABS`
  - [ ] 3.3 Add `{ key: 'delivery', label: 'Delivery' }` to `APP_TABS` (between Overview and Metrics)

- [ ] Task 4: Update routes in `App.tsx` (AC: #2)
  - [ ] 4.1 Remove `<Route path="builds" element={<ApplicationBuildsPage />} />`
  - [ ] 4.2 Remove `<Route path="releases" element={<ApplicationReleasesPage />} />`
  - [ ] 4.3 Add `<Route path="delivery" element={<ApplicationDeliveryPage />} />`
  - [ ] 4.4 Remove `import { ApplicationBuildsPage }` and `import { ApplicationReleasesPage }`
  - [ ] 4.5 Add `import { ApplicationDeliveryPage }`

- [ ] Task 5: Delete old page files (AC: #2)
  - [ ] 5.1 Delete `routes/ApplicationBuildsPage.tsx`
  - [ ] 5.2 Delete `routes/ApplicationBuildsPage.test.tsx`
  - [ ] 5.3 Delete `routes/ApplicationReleasesPage.tsx`
  - [ ] 5.4 Delete `routes/ApplicationReleasesPage.test.tsx`

- [ ] Task 6: Create `ApplicationDeliveryPage.test.tsx` (AC: #1, #3, #4, #5, #6, #7)
  - [ ] 6.1 Mock `useBuilds`, `useTriggerBuild`, `useReleases`, `useAppActivity` with mutable mock results
  - [ ] 6.2 Test: 3-column grid renders with builds, releases, activity data
  - [ ] 6.3 Test: builds loading spinner shown when buildsLoading
  - [ ] 6.4 Test: releases loading spinner shown when releasesLoading
  - [ ] 6.5 Test: activity loading spinner shown when activityLoading
  - [ ] 6.6 Test: builds error Alert shown when buildsError (sibling columns still render)
  - [ ] 6.7 Test: releases error Alert shown when releasesError (sibling columns still render)
  - [ ] 6.8 Test: activity error Alert shown when activityError (sibling columns still render)
  - [ ] 6.9 Test: empty state "No builds yet" when builds data is empty array
  - [ ] 6.10 Test: empty state "No releases yet" when releases data is empty array
  - [ ] 6.11 Test: empty state "No recent activity" when activity data is empty/null
  - [ ] 6.12 Test: RefreshButton triggers all three refresh functions
  - [ ] 6.13 Test: Trigger Build button renders in Builds Card header
  - [ ] 6.14 Test: trigger error shows danger Alert in Builds column
  - [ ] 6.15 Test: `onReleaseCreated` callback wired (verify `refreshReleases` called — mock BuildTable or use integration test)

- [ ] Task 7: Update tab and routing tests (AC: #2, #7)
  - [ ] 7.1 Update `ApplicationTabs.test.tsx`: change "renders all 4 tabs" → "renders all 3 tabs" — assert Overview, Delivery, Metrics; remove assertions for Builds and Releases
  - [ ] 7.2 Update `Accessibility.test.tsx`: update tab count assertion from 4 → 3 (if it asserts count)
  - [ ] 7.3 Verify `App.test.tsx` does not reference builds or releases routes (remove if present)

## Dev Notes

### What Already Exists — Critical Context

After Story 8.1, the Application tabs are: Overview (4 tabs: Overview, Builds, Releases, Metrics). Story 8.2 consolidates Builds + Releases into a single Delivery tab with Activity, resulting in 3 final tabs.

| What | Location | Status |
|------|----------|--------|
| `BuildTable` | `components/build/BuildTable.tsx` | Complete — full table with expandable failed rows, Create Release flow, per-row release state (idle/creating/released/error), `CreateReleaseModal` integration |
| `BuildStatusBadge` | `components/build/BuildStatusBadge.tsx` | Complete — status badge for build rows |
| `FailedBuildDetail` | `components/build/FailedBuildDetail.tsx` | Complete — expandable detail for failed builds with log retrieval |
| `CreateReleaseModal` | `components/build/CreateReleaseModal.tsx` | Complete — PF6 Modal, version input with semver validation, build context display |
| `ReleaseTable` | `components/release/ReleaseTable.tsx` | Complete — compact table with columns, monospace SHA, truncated with tooltip |
| `ActivityFeed` | `components/dashboard/ActivityFeed.tsx` | Complete — DataList of activity events, accepts optional `emptyMessage` prop |
| `useBuilds` | `hooks/useBuilds.ts` | Complete — returns `{ data, error, isLoading, refresh, prepend }` |
| `useTriggerBuild` | `hooks/useBuilds.ts` | Complete — POST trigger, returns new `BuildSummary` on success |
| `useBuildDetail` | `hooks/useBuilds.ts` | Complete — lazy detail for expanded failed rows (used internally by BuildTable) |
| `useBuildLogs` | `hooks/useBuilds.ts` | Complete — lazy plain-text logs (used internally by BuildTable) |
| `useReleases` | `hooks/useReleases.ts` | Complete — thin `useApiFetch` wrapper, returns `{ data, error, isLoading, refresh }` |
| `useAppActivity` | `hooks/useDashboard.ts` | Complete — per-app activity events, returns `{ data, error, isLoading, refresh }` |
| `ApplicationBuildsPage` | `routes/ApplicationBuildsPage.tsx` | Being DELETED — functionality absorbed into Delivery page |
| `ApplicationReleasesPage` | `routes/ApplicationReleasesPage.tsx` | Being DELETED — functionality absorbed into Delivery page |
| `ApplicationTabs` | `components/layout/ApplicationTabs.tsx` | After 8.1: 4 tabs (Overview, Builds, Releases, Metrics) → After 8.2: 3 tabs |
| `RefreshButton` | `components/shared/RefreshButton.tsx` | Complete — reuse on Delivery page |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Complete |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Complete |
| `EmptyState` | PatternFly 6 component | Use directly from `@patternfly/react-core` |

### What Needs to Change

| Change | File | Details |
|--------|------|---------|
| **Create** Delivery page | `routes/ApplicationDeliveryPage.tsx` | 3-column Grid: Builds \| Releases \| Activity with independent states |
| **Create** Delivery tests | `routes/ApplicationDeliveryPage.test.tsx` | Full coverage: columns, loading, error, empty, refresh, trigger build |
| **Add** `onReleaseCreated` prop | `components/build/BuildTable.tsx` | Optional callback invoked after successful CreateReleaseModal completion |
| **Update** BuildTable tests | `components/build/BuildTable.test.tsx` | Test onReleaseCreated callback fired on release creation |
| **Update** tabs | `components/layout/ApplicationTabs.tsx` | Remove builds + releases entries, add delivery (4 → 3 tabs) |
| **Update** routes | `App.tsx` | Remove builds + releases routes, add delivery route |
| **Delete** builds page | `routes/ApplicationBuildsPage.tsx` | Entire file |
| **Delete** builds tests | `routes/ApplicationBuildsPage.test.tsx` | Entire file |
| **Delete** releases page | `routes/ApplicationReleasesPage.tsx` | Entire file |
| **Delete** releases tests | `routes/ApplicationReleasesPage.test.tsx` | Entire file |
| **Update** tabs test | `components/layout/ApplicationTabs.test.tsx` | 4 tabs → 3 tabs |
| **Update** a11y test | `Accessibility.test.tsx` | 4 tabs → 3 tabs (if applicable) |

### ApplicationDeliveryPage — Layout Structure

```
PageSection 1 (header)
  └── Flex (page Title + RefreshButton)

PageSection 2 (content)
  └── Grid (numColumns={12})
      ├── GridItem (span={4}) — Builds
      │   └── Card
      │       ├── CardHeader with CardTitle "Builds" + Trigger Build button in actions
      │       └── CardBody
      │           ├── LoadingSpinner (when buildsLoading)
      │           ├── Alert warning (when buildsError)
      │           ├── EmptyState "No builds yet" + Trigger Build button (when empty)
      │           └── BuildTable (when buildsData, with onReleaseCreated)
      │
      ├── GridItem (span={4}) — Releases
      │   └── Card
      │       ├── CardHeader with CardTitle "Releases"
      │       └── CardBody
      │           ├── LoadingSpinner (when releasesLoading)
      │           ├── Alert warning (when releasesError)
      │           ├── EmptyState "No releases yet" (when empty)
      │           └── ReleaseTable (when releasesData)
      │
      └── GridItem (span={4}) — Activity
          └── Card
              ├── CardHeader with CardTitle "Recent Activity"
              └── CardBody
                  ├── LoadingSpinner (when activityLoading)
                  ├── Alert warning (when activityError)
                  └── ActivityFeed (when activityData, emptyMessage="No recent activity")
```

### Hooks on the Delivery Page

```tsx
const { teamId, appId } = useParams<{ teamId: string; appId: string }>();
const { data: builds, error: buildsError, isLoading: buildsLoading, refresh: refreshBuilds, prepend: prependBuild } = useBuilds(teamId, appId);
const { trigger, error: triggerError, isTriggering } = useTriggerBuild(teamId, appId);
const { data: releases, error: releasesError, isLoading: releasesLoading, refresh: refreshReleases } = useReleases(teamId, appId);
const { data: activity, error: activityError, isLoading: activityLoading, refresh: refreshActivity } = useAppActivity(teamId, appId);

const refreshAll = useCallback(() => {
  refreshBuilds();
  refreshReleases();
  refreshActivity();
}, [refreshBuilds, refreshReleases, refreshActivity]);
```

**Note:** `useTriggerBuild` returns `{ trigger, error, isTriggering }` — the function is named `trigger`, NOT `triggerBuild`. The `error` field captures trigger-specific failures (shown as inline danger Alert).

### Trigger Build Flow

Replicate from `ApplicationBuildsPage`:
1. "Trigger Build" button in Builds Card header — PF6 `Button` with `variant="primary"`
2. On click: call `trigger()` (NOT `triggerBuild` — the hook returns `trigger`)
3. While triggering: disable button, show `Spinner` inline (or use `isLoading` prop on Button)
4. On success: `prependBuild(newBuild)` — new build appears at top of BuildTable
5. On error: show inline `Alert variant="danger"` with `triggerError.message` in the Builds Card body (above the table, same pattern as `ApplicationBuildsPage`)

The existing `ApplicationBuildsPage` trigger flow:
```tsx
const handleTrigger = async () => {
  const newBuild = await trigger();
  if (newBuild) prependBuild(newBuild);
};

{triggerError && (
  <Alert variant="danger" title={triggerError.message} isInline className="pf-v6-u-mb-md" />
)}
```

### Cross-Column Release Refresh (AC #4)

`BuildTable` currently handles `CreateReleaseModal` internally and tracks per-row release state. To satisfy AC #4 (new release appears in Releases column):

1. Add `onReleaseCreated?: () => void` optional prop to `BuildTable`
2. After successful release creation (when BuildTable updates the row to show "Released vX.Y.Z"), also invoke `onReleaseCreated?.()` 
3. `ApplicationDeliveryPage` passes `onReleaseCreated={refreshReleases}`
4. This triggers `useReleases` to re-fetch, updating the Releases column

This is the **only modification** to `BuildTable.tsx` — adding a single optional callback prop and one invocation line.

### APP_TABS After This Story

```typescript
const APP_TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'delivery', label: 'Delivery' },
  { key: 'health', label: 'Metrics' },
] as const;
```

3 tabs. This is the final tab structure per Epic 8.

### DO NOT Create / Reuse Incorrectly

- Do **not** create new hook files — all hooks already exist (`useBuilds`, `useTriggerBuild`, `useReleases`, `useAppActivity`)
- Do **not** create new API client files — all API functions already exist in `api/builds.ts`, `api/releases.ts`, `api/dashboard.ts`
- Do **not** create new type files — all types exist in `types/build.ts`, `types/release.ts`, `types/dashboard.ts`
- Do **not** modify `useBuilds.ts`, `useReleases.ts`, `useDashboard.ts` — the hooks are complete and return the required interfaces
- Do **not** modify `ActivityFeed.tsx` — it already accepts an `emptyMessage` prop (added in Story 7.5)
- Do **not** modify `ReleaseTable.tsx` — it is a complete read-only component, reuse as-is
- Do **not** modify `CreateReleaseModal.tsx` — it is used internally by BuildTable
- Do **not** modify any backend Java files — this is a frontend-only story (Epic 8 is entirely frontend)
- Do **not** add URL redirects for `/builds` or `/releases` — that is Story 8.3
- Do **not** add auto-refresh or polling — manual `RefreshButton` only per project conventions
- Do **not** use custom CSS — PatternFly 6 components and CSS tokens exclusively
- Do **not** remove `useBuilds.ts`, `useReleases.ts`, or `useDashboard.ts` source files — they are hooks used by the new Delivery page
- Do **not** remove `api/builds.ts`, `api/releases.ts`, `api/dashboard.ts` — they are API functions used by the hooks
- Do **not** modify `EnvironmentChain.tsx`, `EnvironmentCard.tsx`, or `ApplicationOverviewPage.tsx` — those are stable from Story 8.1
- Do **not** modify any dashboard page files (`TeamDashboardPage`, `ApplicationHealthGrid`, etc.)

### Error Handling Pattern — Per-Column Isolation

Each column handles errors independently. A failure in one column does NOT affect the other two. This follows the project's section-level error handling rules from `project-context.md`.

**Load errors** use the shared `ErrorAlert` component (which renders a PF6 Alert with `variant="danger"` and the `PortalError` message). This matches `ApplicationBuildsPage` and `ApplicationReleasesPage` patterns:

```tsx
{buildsError && <ErrorAlert error={buildsError} />}
```

```tsx
{releasesError && <ErrorAlert error={releasesError} />}
```

```tsx
{activityError && (
  <Alert variant="warning" title="Unable to load activity" isInline />
)}
```

**Trigger errors** use a separate inline danger Alert (same as existing Builds page):
```tsx
{triggerError && (
  <Alert variant="danger" title={triggerError.message} isInline className="pf-v6-u-mb-md" />
)}
```

Use `LoadingSpinner` for loading states (not `systemName` — this is a multi-source page). Keep loading spinners inside each Card's body so they don't affect siblings.

### Empty State Pattern

Use PatternFly 6 `EmptyState` for builds and releases columns when data array is empty. For Activity, the `ActivityFeed` component already handles empty state via its `emptyMessage` prop.

Builds empty state should match the existing `ApplicationBuildsPage` pattern. It includes the "Trigger Build" button as primary action, and optionally the DevSpaces deep link if the app has one:

```tsx
<EmptyState headingLevel="h3" titleText="No builds yet">
  <EmptyStateBody>
    Trigger your first build or push code to start a CI pipeline.
  </EmptyStateBody>
  <EmptyStateFooter>
    <EmptyStateActions>
      <Button variant="primary" onClick={handleTrigger} isDisabled={isTriggering}>
        Trigger Build
      </Button>
    </EmptyStateActions>
    {app?.devSpacesDeepLink && (
      <EmptyStateActions>
        <DeepLinkButton href={app.devSpacesDeepLink} toolName="DevSpaces" />
      </EmptyStateActions>
    )}
  </EmptyStateFooter>
</EmptyState>
```

**Note:** `EmptyState` uses `headingLevel`/`titleText` props directly (NOT `EmptyStateHeader` subcomponent) — match the existing codebase pattern from `ApplicationBuildsPage`.

If the Delivery page needs `app` data for the DevSpaces link, destructure from the route context or load via `useApplications`/`useParams` as needed. Check how `ApplicationBuildsPage` obtains this.

Releases empty state:
```tsx
<EmptyState headingLevel="h3" titleText="No releases yet">
  <EmptyStateBody>
    Create a release from a successful build to start deploying.
  </EmptyStateBody>
</EmptyState>
```

### Testing Approach

**ApplicationDeliveryPage.test.tsx — Mocks:**

Use mutable mock pattern (established in Stories 7.4, 7.5, 8.1):

```typescript
let mockBuildsResult = {
  data: [...] as BuildSummary[] | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
  prepend: vi.fn(),
};

let mockTriggerResult = {
  trigger: vi.fn(),
  error: null as PortalError | null,
  isTriggering: false,
};

let mockReleasesResult = {
  data: [...] as ReleaseSummary[] | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

let mockActivityResult = {
  data: { events: [...] } as AppActivityResponse | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

vi.mock('../hooks/useBuilds', () => ({
  useBuilds: () => mockBuildsResult,
  useTriggerBuild: () => mockTriggerResult,
  useBuildDetail: () => ({ data: null, error: null, isLoading: false, fetch: vi.fn() }),
  useBuildLogs: () => ({ data: null, error: null, isLoading: false, fetch: vi.fn() }),
}));

vi.mock('../hooks/useReleases', () => ({
  useReleases: () => mockReleasesResult,
}));

vi.mock('../hooks/useDashboard', () => ({
  useAppActivity: () => mockActivityResult,
}));
```

Reset all in `beforeEach`.

**Critical:** The `useBuilds` module mock MUST include all four exports (`useBuilds`, `useTriggerBuild`, `useBuildDetail`, `useBuildLogs`) because `BuildTable` internally imports `useBuildDetail` and `useBuildLogs` for expandable failed-row detail and log retrieval. If these are missing, the mock factory will fail when `BuildTable` mounts. Copy the mock pattern from the existing `ApplicationBuildsPage.test.tsx`.

**Test fixture uniqueness:** Use distinct test data per column to avoid `getByText` collisions (per project-context.md rule). Example: build fixtures use `"test-build-001"`, release fixtures use `"v9.0.0-test"`, activity fixtures use `"test-activity-deploy"`.

**ApplicationTabs.test.tsx update:**

```typescript
it('renders all 3 tabs', () => {
  renderTabs();
  expect(screen.getByText('Overview')).toBeInTheDocument();
  expect(screen.getByText('Delivery')).toBeInTheDocument();
  expect(screen.getByText('Metrics')).toBeInTheDocument();
  // Builds and Releases tabs removed
});
```

**Accessibility.test.tsx:** Update tab count assertion from 4 → 3 if it exists.

**App.test.tsx:** Verify no references to builds or releases routes remain. Remove if present.

### File Structure (modified/created/deleted)

**New files:**
```
developer-portal/src/main/webui/src/routes/ApplicationDeliveryPage.tsx
developer-portal/src/main/webui/src/routes/ApplicationDeliveryPage.test.tsx
```

**Modified files:**
```
developer-portal/src/main/webui/src/components/build/BuildTable.tsx              (add onReleaseCreated prop)
developer-portal/src/main/webui/src/components/build/BuildTable.test.tsx         (test onReleaseCreated)
developer-portal/src/main/webui/src/components/layout/ApplicationTabs.tsx        (4 → 3 tabs)
developer-portal/src/main/webui/src/components/layout/ApplicationTabs.test.tsx   (4 → 3 tabs)
developer-portal/src/main/webui/src/App.tsx                                      (remove builds/releases routes, add delivery)
developer-portal/src/main/webui/src/components/layout/Accessibility.test.tsx     (4 → 3 tabs, if applicable)
```

**Deleted files:**
```
developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx
developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.test.tsx
developer-portal/src/main/webui/src/routes/ApplicationReleasesPage.tsx
developer-portal/src/main/webui/src/routes/ApplicationReleasesPage.test.tsx
```

**Should NOT touch:**
- Backend Java files (zero backend changes in Epic 8)
- `EnvironmentChain.tsx`, `EnvironmentCard.tsx` (complete, stable from 8.1)
- `ApplicationOverviewPage.tsx` or its tests (stable from 8.1)
- `CreateReleaseModal.tsx` (used internally by BuildTable, no changes needed)
- `BuildStatusBadge.tsx`, `FailedBuildDetail.tsx` (complete, used by BuildTable)
- `ActivityFeed.tsx` (complete, already accepts emptyMessage)
- `ReleaseTable.tsx` (complete, reuse as-is)
- `useBuilds.ts`, `useReleases.ts`, `useDashboard.ts` (complete hooks)
- `api/builds.ts`, `api/releases.ts`, `api/dashboard.ts`, `api/client.ts` (complete API functions)
- `types/*.ts` (no type changes)
- Any dashboard page files
- `DeepLinkButton.tsx`, `PromotionConfirmation.tsx`
- `RefreshButton.tsx`, `LoadingSpinner.tsx`, `ErrorAlert.tsx` (reuse as-is)

### Accessibility Requirements

- All PF6 components (Card, Grid, Alert, EmptyState, Button, Table) are inherently accessible
- `BuildTable` already provides: keyboard navigation, expandable rows via Enter/Space, aria-labels on build rows
- `ReleaseTable` already provides: standard PF6 Table keyboard navigation
- `ActivityFeed` already provides: PF6 DataList accessibility
- Trigger Build button: standard PF6 Button, keyboard accessible
- RefreshButton: already keyboard accessible
- Column Cards should have appropriate `aria-label` attributes (e.g., "Builds", "Releases", "Recent Activity") for screen reader landmarks
- Error Alerts: PF6 `Alert` with `variant="warning"` is inherently accessible with `role="alert"`
- Empty states: PF6 `EmptyState` provides appropriate heading levels
- Tab order: left-to-right through columns, top-to-bottom within each column

### Previous Story Intelligence

**From Story 8.1 (Merge Environments Tab into Application Overview):**
- Status: `ready-for-dev`
- Removes Recent Builds + Activity from Overview, removes Environments tab (5 → 4 tabs)
- After 8.1: APP_TABS = [overview, builds, releases, health] — this is the starting state for 8.2
- Deleted `ApplicationEnvironmentsPage.tsx` + tests — follows the same pattern 8.2 uses for deleting Builds/Releases pages
- Tab removal + route removal in App.tsx pattern is established
- Test update pattern (tab count, route assertions) is established

**From Story 7.5 (Application Overview — Recent Builds & Activity Summary):**
- Status: `done`
- Established the pattern of using `BuildTable` + `ActivityFeed` outside their dedicated pages
- Added `useAppActivity` hook to `hooks/useDashboard.ts`
- Added `emptyMessage` prop to `ActivityFeed`
- Debug note: `getByText` collision between page heading and activity feed item — use unique test fixture data per section
- 435 frontend tests pass (38 files)

**From Story 4.4 (Release Creation from Successful Builds):**
- Status: `done`
- `CreateReleaseModal` is in `components/build/CreateReleaseModal.tsx`
- BuildTable tracks per-row release state: `idle | creating | released | error`
- On success: inline "Released vX.Y.Z" Label replaces button — no toast or modal
- On error: inline danger Alert on build row with retry
- Release state managed inside BuildTable (self-contained, works in any page context per Epic 7 retro)

**From Story 4.3 (Builds Page & Build Table):**
- Status: `done`
- `useBuilds` returns `{ data, error, isLoading, refresh, prepend }`
- `useTriggerBuild` is a separate hook in the same file
- `prepend(newBuild)` inserts new build row at top without full refresh
- `useBuildDetail` and `useBuildLogs` are used internally for expandable rows
- `apiFetchText()` in `api/client.ts` used for build logs (text/plain)
- 220 frontend tests at time of completion

**From Story 4.5 (Releases Page & Release List):**
- Status: `done`
- `useReleases` is a thin `useApiFetch<ReleaseSummary[]>` wrapper
- `ReleaseTable` is a simple read-only PF6 compact table
- Columns: version (tag), commit SHA (monospace, 7-char + tooltip), image reference, created date
- `LoadingSpinner` with `systemName="Git"` on the standalone page — Delivery page uses generic spinner per column
- No primary action on releases (creation is from Builds via modal)

### Git Intelligence

Recent commits (last 5):
- `690dc02` Epic 7 retrospective, process rules, and Epic 8 creation
- `b101ca7` Story 7.5: Application Overview — Recent Builds & Activity Summary
- `b1496c6` Story 7.4: Environments Tab — Promotion Pipeline & Settings Consolidation
- `fc56b47` Add Story 7.4 story file and update backend seed/dashboard data
- `b95b456` Add Story 7.5 story file and update sprint status to ready-for-dev

Key patterns reinforced:
- PatternFly 6 components exclusively — no custom CSS
- PF CSS design tokens for all colors (`var(--pf-t--global--...)`)
- Co-located test files (`.test.tsx` next to component)
- `useApiFetch` wrapper for all data hooks
- Inline `Alert` for section-level errors, `variant="warning"` for degraded data
- `RefreshButton` for manual refresh (no auto-poll)
- Hook mock pattern: `vi.mock('../hooks/useXxx', () => ({ useXxx: () => mockResult }))`
- Tab count updates cascade to: `ApplicationTabs.test.tsx`, `Accessibility.test.tsx`

### Project Structure Notes

- All files follow existing project structure conventions
- Net result: 2 new files, ~6 modified files, 4 deleted files — slight net reduction
- No new hook files, no new API files, no new type files, no new component files (besides the page)
- No new dependencies needed
- Frontend-only: zero backend changes

### References

- [Source: planning-artifacts/epics.md#Epic 8, Story 8.2] — User story, acceptance criteria, 3-column layout, tab restructuring (5→3), design decisions (CreateReleaseModal in any context, Delivery as pipeline activity view)
- [Source: planning-artifacts/epics.md#Epic 8] — Tab consolidation table (Builds+Releases → Delivery), frontend-only scope, component reuse list
- [Source: planning-artifacts/architecture.md#Frontend] — PatternFly 6, React Router v6, CSS token rules, component patterns, Vitest + RTL
- [Source: planning-artifacts/architecture.md#Project Structure] — File locations (routes/, components/, hooks/, api/, types/), naming conventions (PascalCase + Page suffix)
- [Source: planning-artifacts/ux-design-specification.md#Build & Release] — Compact table with inline status, release creation affordance, failed build detail
- [Source: planning-artifacts/ux-design-specification.md#Empty States] — "No builds yet" with Trigger Build action, "No releases yet" with guidance
- [Source: planning-artifacts/ux-design-specification.md#Loading and Data Freshness] — Spinner per section, inline Alert for unreachable systems, manual refresh only
- [Source: planning-artifacts/ux-design-specification.md#Accessibility] — WCAG 2.1 AA via PF6, keyboard navigation, status never color-only
- [Source: planning-artifacts/ux-design-specification.md#Feedback Patterns] — Build/release success as inline state change, error as inline Alert on row
- [Source: project-context.md] — Coding rules, testing conventions, PF6 mandatory, strict TS, CSS token rules, section-level error handling, unique test fixtures
- [Source: implementation-artifacts/8-1-merge-environments-tab-into-application-overview.md] — Tab removal pattern (remove from APP_TABS + App.tsx route + delete page), test update cascade, starting state for 8.2
- [Source: implementation-artifacts/4-3-builds-page-build-table.md] — BuildTable component, useBuilds/useTriggerBuild hooks, prepend pattern, trigger build flow
- [Source: implementation-artifacts/4-4-release-creation-from-successful-builds.md] — CreateReleaseModal, per-row release state in BuildTable, release creation flow
- [Source: implementation-artifacts/4-5-releases-page-release-list.md] — ReleaseTable component, useReleases hook, release list patterns
- [Source: implementation-artifacts/7-5-application-overview-recent-builds-activity-summary.md] — BuildTable + ActivityFeed reuse on Overview, useAppActivity hook, emptyMessage prop, test fixture uniqueness lesson

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
