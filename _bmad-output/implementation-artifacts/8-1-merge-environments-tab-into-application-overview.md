# Story 8.1: Merge Environments Tab into Application Overview

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want the Application Overview to be the single view for deployment state — showing the full environment promotion pipeline with all deep links and deploy/promote actions,
So that I don't need to switch between Overview and Environments tabs for the same information.

## Acceptance Criteria

1. **Overview shows full environment promotion pipeline (absorbs Environments page)**
   - **Given** the Application Overview page renders
   - **When** the environment chain section displays
   - **Then** it shows the full promotion pipeline with per-environment details, deep links (ArgoCD, Vault, Grafana), deploy/promote actions, and health status — identical to what the Environments tab currently provides
   - **And** the Vault deep links are accessible from each `EnvironmentCard` expanded view (already present from Story 7.4)

2. **Health error surfaced on Overview**
   - **Given** the Overview page renders the environment chain
   - **When** Prometheus is unreachable or `healthError` is present
   - **Then** an inline warning Alert "Health data unavailable — Prometheus may be unreachable" is displayed above the chain (matching Environments page behavior from Story 7.4)

3. **Recent Builds and Recent Activity cards removed from Overview**
   - **Given** the Overview page previously showed Recent Builds and Recent Activity cards in a bottom grid (Story 7.5)
   - **When** this story is implemented
   - **Then** the Recent Builds card, Recent Activity card, and their associated hooks (`useBuilds`, `useAppActivity`) are removed from the Overview page
   - **And** the imports for `BuildTable`, `ActivityFeed`, `useBuilds`, `useAppActivity`, `Grid`, `GridItem`, and `Link` (if unused elsewhere) are removed
   - **And** the Overview page's bottom section is clean — no placeholder or stub content remains

4. **Environments tab removed**
   - **Given** the Overview page now contains the full environment chain
   - **When** the Environments tab is evaluated
   - **Then** the `environments` entry is removed from `APP_TABS` in `ApplicationTabs.tsx` (5 → 4 tabs)
   - **And** the `<Route path="environments" ... />` is removed from `App.tsx`
   - **And** the `import { ApplicationEnvironmentsPage }` is removed from `App.tsx`
   - **And** `ApplicationEnvironmentsPage.tsx` and `ApplicationEnvironmentsPage.test.tsx` are deleted

5. **RefreshButton behavior preserved**
   - **Given** the Overview page is displayed
   - **When** the RefreshButton is clicked
   - **Then** environment data and health data are refreshed
   - **And** builds and activity refreshes are NO LONGER triggered (those hooks are removed)

6. **All tests pass with zero regressions**
   - **Given** all changes are implemented
   - **When** the full frontend test suite runs
   - **Then** all tests pass with zero regressions

## Tasks / Subtasks

- [x] Task 1: Remove Recent Builds and Recent Activity from `ApplicationOverviewPage.tsx` (AC: #3, #5)
  - [x] 1.1 Remove `useBuilds` import and hook call
  - [x] 1.2 Remove `useAppActivity` import and hook call (`useDashboard` module)
  - [x] 1.3 Remove `BuildTable` import
  - [x] 1.4 Remove `ActivityFeed` import
  - [x] 1.5 Remove `Grid`, `GridItem` imports (if unused after removal)
  - [x] 1.6 Remove `Link` import from `react-router-dom` (if unused after removal)
  - [x] 1.7 Remove `refreshBuilds` and `refreshActivity` from `refreshAll` callback and its dependency array
  - [x] 1.8 Delete the entire bottom `PageSection` containing the two-column grid with Recent Builds and Recent Activity cards
  - [x] 1.9 Remove unused PF6 imports: `Card`, `CardBody`, `CardTitle`, `Flex`, `FlexItem`, `Alert`, `Spinner` — BUT verify each is genuinely unused (some may be used in the header section)

- [x] Task 2: Add `healthError` inline warning to Overview page (AC: #2)
  - [x] 2.1 Destructure `error: healthError` from `useHealth(teamId, appId)` (currently only `data` and `refresh` are destructured)
  - [x] 2.2 Add inline `Alert` with `variant="warning"` and `title="Health data unavailable — Prometheus may be unreachable"` in the environment chain `PageSection`, below `envError`/`releasesError` alerts and above `EnvironmentChain`
  - [x] 2.3 Ensure `Alert` uses `isInline` prop (matching pattern from Environments page)

- [x] Task 3: Remove Environments tab and route (AC: #4)
  - [x] 3.1 Remove `{ key: 'environments', label: 'Environments' }` from `APP_TABS` in `ApplicationTabs.tsx`
  - [x] 3.2 Remove `<Route path="environments" element={<ApplicationEnvironmentsPage />} />` from `App.tsx`
  - [x] 3.3 Remove `import { ApplicationEnvironmentsPage } from './routes/ApplicationEnvironmentsPage'` from `App.tsx`
  - [x] 3.4 Delete `routes/ApplicationEnvironmentsPage.tsx`
  - [x] 3.5 Delete `routes/ApplicationEnvironmentsPage.test.tsx`

- [x] Task 4: Update tests (AC: #6)
  - [x] 4.1 Update `ApplicationOverviewPage.test.tsx`:
    - Remove `useBuilds` mock and `mockBuildsResult`
    - Remove `useDashboard` / `useAppActivity` mock and `mockActivityResult`
    - Remove all test cases for: BuildTable, "View all builds" link, ActivityFeed, empty builds, empty activity, loading builds, loading activity, build error, activity error, partial activity warning
    - Add test: health warning Alert renders when `healthError` is present
    - Add test: health warning Alert does not render when `healthError` is null
    - Verify remaining tests still pass: env chain, env loading, env error, releases error, app metadata, refresh button, DevSpaces link
  - [x] 4.2 Update `ApplicationTabs.test.tsx`:
    - Change "renders all 5 tabs" → "renders all 4 tabs"
    - Remove assertion for 'Environments' tab
    - Keep assertions for: Overview, Builds, Releases, Metrics
  - [x] 4.3 Update `Accessibility.test.tsx` if it asserts tab count (was updated 6→5 in Story 7.4; now 5→4)
  - [x] 4.4 Verify `App.test.tsx` does not reference the environments route (if it does, remove)

## Dev Notes

### What Already Exists — Critical Context

The Overview page already renders `EnvironmentChain` with full deploy/promote actions, deep links (ArgoCD, Vault, Grafana), and health enrichment. The only gap between Overview and the Environments page is:

1. **`healthError` inline warning** — Environments page shows `"Health data unavailable — Prometheus may be unreachable"` Alert; Overview page does not surface `healthError`
2. **Secrets Management card** — Environments page has a card showing Vault deep links per environment in a `DescriptionList`; **this is intentionally NOT migrated** — Epic 8 design decision says "Vault deep links stay in `EnvironmentCard` expanded view — the separate Secrets Management card from the Environments page is redundant"

| What | Location | Status |
|------|----------|--------|
| `EnvironmentChain` | `components/environment/EnvironmentChain.tsx` | Complete — horizontal card chain with ArgoCD error Alert, arrow connectors, arrow-key navigation |
| `EnvironmentCard` | `components/environment/EnvironmentCard.tsx` | Complete — expand to see deploy history, ArgoCD + Vault + Grafana deep links, deploy/promote actions |
| `BuildTable` | `components/build/BuildTable.tsx` | Complete — being REMOVED from Overview (moves to Delivery tab in Story 8.2) |
| `ActivityFeed` | `components/dashboard/ActivityFeed.tsx` | Complete — being REMOVED from Overview (moves to Delivery tab in Story 8.2) |
| `useEnvironments` | `hooks/useEnvironments.ts` | Complete — stays on Overview |
| `useReleases` | `hooks/useReleases.ts` | Complete — stays on Overview (needed for EnvironmentCard deploy dropdown) |
| `useHealth` | `hooks/useHealth.ts` | Complete — stays on Overview; **needs `error` destructured** |
| `useBuilds` | `hooks/useBuilds.ts` | Complete — being REMOVED from Overview |
| `useAppActivity` | `hooks/useDashboard.ts` | Complete — being REMOVED from Overview |
| `ApplicationOverviewPage` | `routes/ApplicationOverviewPage.tsx` | Current: env chain + Recent Builds + Recent Activity bottom grid |
| `ApplicationEnvironmentsPage` | `routes/ApplicationEnvironmentsPage.tsx` | Being DELETED — content absorbed into Overview |
| `ApplicationTabs` | `components/layout/ApplicationTabs.tsx` | Current: 5 tabs (Overview, Builds, Releases, Environments, Metrics) |
| `RefreshButton` | `components/shared/RefreshButton.tsx` | Complete — stays on Overview |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Complete |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Complete |
| `DeepLinkButton` | `components/shared/DeepLinkButton.tsx` | Complete |

### What Needs to Change

| Change | File | Details |
|--------|------|---------|
| **Remove** Recent Builds + Activity | `routes/ApplicationOverviewPage.tsx` | Delete bottom PageSection grid, remove `useBuilds`, `useAppActivity`, `BuildTable`, `ActivityFeed` imports and hooks |
| **Add** healthError Alert | `routes/ApplicationOverviewPage.tsx` | Destructure `error: healthError` from `useHealth`, add inline warning Alert |
| **Simplify** refreshAll | `routes/ApplicationOverviewPage.tsx` | Remove `refreshBuilds` and `refreshActivity` from callback |
| **Remove** Environments entry | `components/layout/ApplicationTabs.tsx` | Remove from `APP_TABS` (5 → 4) |
| **Remove** route + import | `App.tsx` | Remove environments route and ApplicationEnvironmentsPage import |
| **Delete** page | `routes/ApplicationEnvironmentsPage.tsx` | Entire file |
| **Delete** tests | `routes/ApplicationEnvironmentsPage.test.tsx` | Entire file |
| **Update** tests | `routes/ApplicationOverviewPage.test.tsx` | Remove build/activity tests, add healthError tests |
| **Update** tabs test | `components/layout/ApplicationTabs.test.tsx` | 5 tabs → 4 tabs |
| **Update** a11y test | `Accessibility.test.tsx` | 5 tabs → 4 tabs (if it asserts count) |

### ApplicationOverviewPage — After This Story

The page structure simplifies to:

```
PageSection 1 (header)
  ├── Flex (app Title + DevSpaces DeepLinkButton + RefreshButton)
  └── DescriptionList (Runtime, Onboarded, PR link)

PageSection 2 (environment chain)
  ├── LoadingSpinner (when envLoading)
  ├── ErrorAlert (when envError)
  ├── Alert warning (when releasesError) — already exists
  ├── Alert warning (when healthError) — NEW
  └── EnvironmentChain (when envData) — with full deploy/promote/deep links
```

No third PageSection. The bottom grid is gone.

### Hooks After This Story

```tsx
const { data: envData, error: envError, isLoading: envLoading, refresh: refreshEnv } = useEnvironments(teamId, appId);
const { data: releases, error: releasesError } = useReleases(teamId, appId);
const { data: healthData, error: healthError, refresh: refreshHealth } = useHealth(teamId, appId);

const refreshAll = useCallback(() => {
  refreshEnv();
  refreshHealth();
}, [refreshEnv, refreshHealth]);
```

`useBuilds` and `useAppActivity` are completely removed.

### healthError Alert — Exact Pattern

Copy from `ApplicationEnvironmentsPage.tsx`:

```tsx
{healthError && (
  <Alert
    variant="warning"
    title="Health data unavailable — Prometheus may be unreachable"
    isInline
  />
)}
```

Place it in the environment chain `PageSection`, after the existing `releasesError` Alert and before the `EnvironmentChain` render.

### APP_TABS After This Story

```typescript
const APP_TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'builds', label: 'Builds' },
  { key: 'releases', label: 'Releases' },
  { key: 'health', label: 'Metrics' },
] as const;
```

4 tabs. Story 8.2 will further reduce to 3 (Overview | Delivery | Metrics).

### DO NOT Create / Reuse Incorrectly

- Do **not** migrate the Secrets Management card from Environments page to Overview — it is intentionally redundant per Epic 8 design decision (Vault links are already in `EnvironmentCard` expanded view)
- Do **not** create any new components — this story only removes and simplifies
- Do **not** modify `EnvironmentChain.tsx` or `EnvironmentCard.tsx` — they are complete
- Do **not** modify any backend Java files — this is a frontend-only story
- Do **not** modify `BuildTable.tsx` or `ActivityFeed.tsx` — they are being removed from this page, not changed
- Do **not** add any redirect for `/environments` URL in this story — that is Story 8.3
- Do **not** modify the Delivery-related files — that is Story 8.2
- Do **not** modify `useEnvironments.ts`, `useReleases.ts`, `useHealth.ts` — they are unchanged
- Do **not** modify any dashboard page files (`TeamDashboardPage`, `ApplicationHealthGrid`, etc.)
- Do **not** add auto-refresh or polling — manual `RefreshButton` only per project conventions
- Do **not** remove `useBuilds.ts` or `useAppActivity` from their source files — they are still used by other pages (Builds page uses `useBuilds`; Delivery tab will use both in Story 8.2)

### Testing Approach

**ApplicationOverviewPage.test.tsx — Remove these test cases:**

All tests related to the bottom grid that is being removed:
- BuildTable rendering (data available, max 5, etc.)
- "View all builds" link
- ActivityFeed rendering
- Empty states for builds and activity
- Loading states for builds and activity
- Error states for builds and activity
- Partial activity warning

**ApplicationOverviewPage.test.tsx — Add these test cases:**

```typescript
it('shows health warning when healthError is present', () => {
  mockHealthResult = {
    data: null,
    error: { message: 'Prometheus unreachable' } as PortalError,
    isLoading: false,
    refresh: vi.fn(),
  };
  renderPage();
  expect(screen.getByText('Health data unavailable — Prometheus may be unreachable')).toBeInTheDocument();
});

it('does not show health warning when no healthError', () => {
  mockHealthResult = {
    data: null,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
  renderPage();
  expect(screen.queryByText('Health data unavailable — Prometheus may be unreachable')).not.toBeInTheDocument();
});
```

**Mock changes for `useHealth`:**

The current Overview test has a static mock for `useHealth`:
```typescript
vi.mock('../hooks/useHealth', () => ({
  useHealth: () => ({ data: null, error: null, isLoading: false, refresh: vi.fn() }),
}));
```

Change to a mutable mock (like `useEnvironments`):
```typescript
let mockHealthResult = {
  data: null as HealthData | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

vi.mock('../hooks/useHealth', () => ({
  useHealth: () => mockHealthResult,
}));
```

Reset in `beforeEach`.

**ApplicationTabs.test.tsx update:**

```typescript
it('renders all 4 tabs', () => {
  renderTabs();
  expect(screen.getByText('Overview')).toBeInTheDocument();
  expect(screen.getByText('Builds')).toBeInTheDocument();
  expect(screen.getByText('Releases')).toBeInTheDocument();
  expect(screen.getByText('Metrics')).toBeInTheDocument();
  // Environments tab removed
});
```

**Accessibility.test.tsx:** Update tab count assertion from 5 → 4 if it exists.

**App.test.tsx:** Verify it does not reference the environments route. From Story 7.4 notes, App.test.tsx did not reference Settings — check for environments reference and remove if present.

### File Structure (modified/deleted)

**Modified files:**
```
developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx         (remove builds/activity, add healthError)
developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx    (remove builds/activity tests, add healthError tests, make useHealth mock mutable)
developer-portal/src/main/webui/src/components/layout/ApplicationTabs.tsx       (remove Environments from APP_TABS)
developer-portal/src/main/webui/src/components/layout/ApplicationTabs.test.tsx  (5 tabs → 4 tabs)
developer-portal/src/main/webui/src/App.tsx                                     (remove environments route + import)
developer-portal/src/main/webui/src/components/layout/Accessibility.test.tsx    (5 tabs → 4 tabs, if applicable)
```

**Deleted files:**
```
developer-portal/src/main/webui/src/routes/ApplicationEnvironmentsPage.tsx
developer-portal/src/main/webui/src/routes/ApplicationEnvironmentsPage.test.tsx
```

**Should NOT touch:**
- Backend Java files (zero backend changes in Epic 8)
- `EnvironmentChain.tsx`, `EnvironmentCard.tsx` (complete, reuse as-is)
- `BuildTable.tsx`, `ActivityFeed.tsx` (complete, still used by other pages)
- `useEnvironments.ts`, `useReleases.ts`, `useHealth.ts` (complete)
- `useBuilds.ts`, `useDashboard.ts` (still used by Builds page and will be used by Delivery tab)
- `api/dashboard.ts`, `api/builds.ts` (still used)
- `types/*.ts` (no type changes)
- Any dashboard page files
- `DeepLinkButton.tsx`, `PromotionConfirmation.tsx`

### Accessibility Requirements

- `EnvironmentChain` already provides: `role="list"`, `aria-label="Environment promotion chain"`, left/right arrow key navigation between cards
- Each `EnvironmentCard` already has: `tabIndex={0}`, `aria-label` with environment name + version + status, Enter/Space to expand, deep links keyboard accessible
- Health warning Alert: PF6 `Alert` with `variant="warning"` is inherently accessible with `role="alert"`
- Removing the bottom grid reduces page complexity and improves focus
- All remaining interactive elements (RefreshButton, DevSpaces link, EnvironmentCard actions) are already keyboard accessible

### Previous Story Intelligence

**From Story 7.5 (Application Overview — Recent Builds & Activity Summary):**
- Status: `done`
- Added `useBuilds` and `useAppActivity` hooks to Overview page — **these are being removed in this story**
- Added `refreshBuilds` and `refreshActivity` to `refreshAll` — **being removed**
- Bottom PageSection with `Grid` two-column layout: Recent Builds (left) + Recent Activity (right) — **being deleted**
- 435 frontend tests pass (38 files), 552 backend tests pass
- Debug note: `getByText` collision between page heading and activity feed item — fixed by using unique reference values in test data
- Review finding: App activity endpoint masks per-source failures — frontend surfaces inline warning via `AppActivityResponse.error` field

**From Story 7.4 (Environments Tab — Promotion Pipeline & Settings Consolidation):**
- Status: `done`
- Rewrote `ApplicationEnvironmentsPage` with full EnvironmentChain + Secrets Management card
- Added Vault deep link to `EnvironmentCard` expanded view (ArgoCD, **Vault**, Grafana)
- Removed Settings tab (6→5 tabs), deleted `ApplicationSettingsPage`
- Added `healthError` inline warning Alert to Environments page — **this pattern is being copied to Overview**
- 424/424 frontend tests pass (38 files)
- The `ApplicationEnvironmentsPage` and its tests are the files being deleted in this story

**From Story 7.3 (Activity Feed & Aggregated DORA Trends):**
- 1 pre-existing failure in `ApplicationTabs.test.tsx` — investigate if still present
- Mock pattern: `vi.mock('../hooks/useXxx', () => ({ useXxx: () => mockResult }))`

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

### Project Structure Notes

- All files follow existing project structure conventions
- This story is primarily a **removal/simplification** — net lines of code decrease
- No new component files, no new hooks, no new types, no new API calls
- No new dependencies needed
- Frontend-only: zero backend changes

### References

- [Source: planning-artifacts/epics.md#Epic 8, Story 8.1] — User story, acceptance criteria, tab restructuring table, design decisions (Secrets Management redundant, RefreshButton preserved)
- [Source: planning-artifacts/epics.md#Epic 8] — Tab consolidation overview (5→3 tabs across 3 stories), frontend-only scope, component reuse list
- [Source: planning-artifacts/architecture.md#Frontend] — PatternFly 6, React Router v6, CSS token rules, component patterns
- [Source: planning-artifacts/architecture.md#Project Structure] — File locations, naming conventions, domain packages
- [Source: planning-artifacts/ux-design-specification.md#Environment Chain Card Row] — Horizontal card chain with status badges, arrow connectors, click to expand, min 180px cards
- [Source: planning-artifacts/ux-design-specification.md#Application Overview] — Originally showed chain + two-column bottom grid; Epic 8 overrides to chain-only
- [Source: planning-artifacts/ux-design-specification.md#Accessibility] — WCAG 2.1 AA, arrow keys between stages, aria-label per env, status never color-only
- [Source: planning-artifacts/ux-design-specification.md#Loading and Errors] — Spinner on load, inline Alert for errors, manual refresh only
- [Source: project-context.md] — Coding rules, testing conventions, PF6 mandatory, strict TS, CSS token rules, section-level error handling rules
- [Source: implementation-artifacts/7-5-application-overview-recent-builds-activity-summary.md] — Current Overview implementation details, hooks wired, bottom grid structure, test patterns, review findings
- [Source: implementation-artifacts/7-4-environments-tab-promotion-pipeline-settings-consolidation.md] — Environments page implementation, Secrets Management card, healthError pattern, Vault deep link in EnvironmentCard, tab removal pattern (6→5)

## Dev Agent Record

### Agent Model Used

GPT-5.4

### Implementation Plan

- Simplify `ApplicationOverviewPage` to keep only the application header and environment chain sections, remove builds/activity hooks and UI, preserve refresh for environments and health, and surface the inline `healthError` warning above the chain.
- Remove the Environments tab and route, then delete the redundant `ApplicationEnvironmentsPage` implementation and its tests.
- Update frontend tests to reflect the reduced tab count, removed Overview bottom grid, and new Overview health warning behavior before validating the frontend suite.

### Debug Log References

- Updated Overview tests first and confirmed red-phase failures for the missing health warning and still-present Environments tab/5-tab navigation.
- Validated the implementation with `npm test -- src/routes/ApplicationOverviewPage.test.tsx src/components/layout/ApplicationTabs.test.tsx src/components/layout/Accessibility.test.tsx` and then `npm test` from `developer-portal/src/main/webui`.
- Ran `npm run lint`; failures were pre-existing in unrelated files (`ActivityFeed.tsx`, `ApplicationHealthGrid.test.tsx`, `DoraStatCard.tsx`, `GoldenSignalsPanel.tsx`, `AppShell.tsx`, `TeamDashboardPage.test.tsx`). `ReadLints` reported no new lint issues in the files changed for this story.

### Completion Notes List

- Removed Recent Builds and Recent Activity from `ApplicationOverviewPage`, including the `useBuilds`/`useAppActivity` hooks, associated imports, and the bottom grid section, leaving Overview focused on the application header and full environment promotion chain.
- Added the inline warning `Alert` for `healthError` on the Overview page and preserved manual refresh behavior for environments and health only.
- Removed the Environments tab from application navigation, removed the `/environments` route from `App.tsx`, and deleted the obsolete `ApplicationEnvironmentsPage` implementation and test files.
- Updated the affected frontend tests for the new Overview and 4-tab navigation, and verified the full frontend suite passes: 37 files, 416 tests.

### File List

- `_bmad-output/implementation-artifacts/8-1-merge-environments-tab-into-application-overview.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `developer-portal/src/main/webui/src/App.tsx`
- `developer-portal/src/main/webui/src/components/layout/Accessibility.test.tsx`
- `developer-portal/src/main/webui/src/components/layout/ApplicationTabs.test.tsx`
- `developer-portal/src/main/webui/src/components/layout/ApplicationTabs.tsx`
- `developer-portal/src/main/webui/src/routes/ApplicationEnvironmentsPage.test.tsx` (deleted)
- `developer-portal/src/main/webui/src/routes/ApplicationEnvironmentsPage.tsx` (deleted)
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx`
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx`

### Change Log

- 2026-04-14: Merged the Environments tab content into Overview, removed Overview's builds/activity section, deleted the Environments route/page, and updated frontend tests for the 4-tab navigation model.
