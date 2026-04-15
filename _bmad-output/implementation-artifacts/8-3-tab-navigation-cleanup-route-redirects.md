# Story 8.3: Tab Navigation Cleanup & Route Redirects

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want old bookmarked URLs for removed tabs to redirect gracefully to their new locations,
So that saved links and browser history don't lead to 404 pages.

## Acceptance Criteria

1. **`/environments` redirects to Overview**
   - **Given** a developer has a bookmarked URL to `/teams/{teamId}/apps/{appId}/environments`
   - **When** they navigate to that URL
   - **Then** they are redirected to `/teams/{teamId}/apps/{appId}` (Overview, which now contains environments)
   - **And** the URL in the browser address bar updates to the Overview URL
   - **And** the Overview tab is highlighted in the tab bar
   - **And** the breadcrumb shows "Overview" (not "Environments")

2. **`/builds` redirects to Delivery**
   - **Given** a developer has a bookmarked URL to `/teams/{teamId}/apps/{appId}/builds`
   - **When** they navigate to that URL
   - **Then** they are redirected to `/teams/{teamId}/apps/{appId}/delivery`
   - **And** the Delivery tab is highlighted in the tab bar
   - **And** the breadcrumb shows "Delivery" (not "Builds")

3. **`/releases` redirects to Delivery**
   - **Given** a developer has a bookmarked URL to `/teams/{teamId}/apps/{appId}/releases`
   - **When** they navigate to that URL
   - **Then** they are redirected to `/teams/{teamId}/apps/{appId}/delivery`
   - **And** the Delivery tab is highlighted in the tab bar
   - **And** the breadcrumb shows "Delivery" (not "Releases")

4. **`/settings` redirects to Overview**
   - **Given** a developer has a bookmarked URL to `/teams/{teamId}/apps/{appId}/settings`
   - **When** they navigate to that URL
   - **Then** they are redirected to `/teams/{teamId}/apps/{appId}` (Overview)
   - **And** the Overview tab is highlighted
   - **And** the breadcrumb shows "Overview" (not "Settings")

5. **All tests pass with zero regressions**
   - **Given** all redirect routes are in place
   - **When** the full test suite runs
   - **Then** all tests pass with zero regressions

6. **Breadcrumbs display correctly for all remaining tabs**
   - **Given** a developer navigates to any active tab (Overview, Delivery, Metrics)
   - **When** the page renders
   - **Then** breadcrumbs show the correct path: Team → Application → [View]

## Tasks / Subtasks

- [x] Task 1: Add redirect routes to `App.tsx` (AC: #1, #2, #3, #4)
  - [x] 1.1 Add `<Route path="environments" element={<Navigate to=".." replace />} />` inside the `ApplicationLayout` nested routes
  - [x] 1.2 Add `<Route path="builds" element={<Navigate to="../delivery" replace />} />` inside the `ApplicationLayout` nested routes
  - [x] 1.3 Add `<Route path="releases" element={<Navigate to="../delivery" replace />} />` inside the `ApplicationLayout` nested routes
  - [x] 1.4 Add `<Route path="settings" element={<Navigate to=".." replace />} />` inside the `ApplicationLayout` nested routes
  - [x] 1.5 Remove stale imports from `App.tsx` if any remain from previous stories (verify `ApplicationEnvironmentsPage`, `ApplicationBuildsPage`, `ApplicationReleasesPage` imports are already removed by 8.1 and 8.2)

- [x] Task 2: Add redirect tests to `App.test.tsx` (AC: #1, #2, #3, #4, #5)
  - [x] 2.1 Add `ApplicationDeliveryPage` import and route to the test's `renderApp` function (if not already added by Story 8.2)
  - [x] 2.2 Add redirect routes to `renderApp` matching production `App.tsx` (environments, builds, releases, settings all use `<Navigate>`)
  - [x] 2.3 Add test: `/environments` redirects to Overview — navigating to `/teams/1/apps/my-app/environments` renders the Overview page content
  - [x] 2.4 Add test: `/builds` redirects to Delivery — navigating to `/teams/1/apps/my-app/builds` renders the Delivery page content
  - [x] 2.5 Add test: `/releases` redirects to Delivery — navigating to `/teams/1/apps/my-app/releases` renders the Delivery page content
  - [x] 2.6 Add test: `/settings` redirects to Overview — navigating to `/teams/1/apps/my-app/settings` renders the Overview page content
  - [x] 2.7 Remove stale builds route test if it still references `ApplicationBuildsPage` directly (should have been updated by 8.2)

- [x] Task 3: Verify breadcrumbs work correctly (AC: #6)
  - [x] 3.1 Verify `AppBreadcrumb.tsx` requires NO changes — `deriveViewFromPath` reads the URL path after redirect, so it will show "Overview" or "Delivery" correctly since `Navigate replace` updates the URL before rendering
  - [x] 3.2 Verify no breadcrumb test updates are needed (the redirect updates the URL, so breadcrumbs see the final URL)

- [x] Task 4: Verify existing tests still pass (AC: #5)
  - [x] 4.1 Run full frontend test suite and confirm zero regressions
  - [x] 4.2 Verify `ApplicationTabs.test.tsx` passes (already 3 tabs after 8.2 — no changes needed)
  - [x] 4.3 Verify `Accessibility.test.tsx` passes (already 3 tabs after 8.2 — no changes needed)

### Review Findings

- [x] [Review][Patch] Redirect tests do not verify post-redirect URL or breadcrumb text [`developer-portal/src/main/webui/src/App.test.tsx`:138-162] — Fixed: added breadcrumb assertions using `within(screen.getByRole('navigation', { name: /breadcrumb/i }))` to verify post-redirect view name in all 4 redirect tests.

## Dev Notes

### What Already Exists — Critical Context

After Stories 8.1 and 8.2 are complete, the application state is:

| What | Location | Status |
|------|----------|--------|
| `App.tsx` | `App.tsx` | Routes: `index` (Overview), `overview`, `delivery`, `health` — environments/builds/releases routes REMOVED by 8.1/8.2; settings removed by 7.4 |
| `ApplicationTabs` | `components/layout/ApplicationTabs.tsx` | 3 tabs: Overview, Delivery, Metrics |
| `ApplicationLayout` | `components/layout/ApplicationLayout.tsx` | Nested layout with tabs + Outlet — unchanged |
| `AppBreadcrumb` | `components/layout/AppBreadcrumb.tsx` | Derives view name from URL path segment — works correctly after redirect |
| `Navigate` | `react-router-dom` | Already imported in `App.tsx` for the root `/` → `/teams/default` redirect |
| `ApplicationOverviewPage` | `routes/ApplicationOverviewPage.tsx` | Full env chain + health (from 8.1) |
| `ApplicationDeliveryPage` | `routes/ApplicationDeliveryPage.tsx` | 3-column Builds/Releases/Activity (from 8.2) |
| `ApplicationHealthPage` | `routes/ApplicationHealthPage.tsx` | Metrics page — unchanged |

### What Needs to Change

| Change | File | Details |
|--------|------|---------|
| **Add** 4 redirect routes | `App.tsx` | `<Navigate>` for environments, builds, releases, settings → their new locations |
| **Add** 4 redirect tests | `App.test.tsx` | Test that old URLs redirect to correct pages |
| **Verify** (no change) | `AppBreadcrumb.tsx` | Confirm breadcrumbs work correctly — `Navigate replace` updates URL before render |
| **Verify** (no change) | `ApplicationTabs.tsx` | Already 3 tabs — no changes |
| **Verify** (no change) | `ApplicationTabs.test.tsx` | Already testing 3 tabs — no changes |
| **Verify** (no change) | `Accessibility.test.tsx` | Already testing 3 tabs — no changes |

### App.tsx — After This Story

The `ApplicationLayout` nested routes section:

```tsx
<Route
  path="/teams/:teamId/apps/:appId"
  element={<ApplicationLayout />}
>
  <Route index element={<ApplicationOverviewPage />} />
  <Route path="overview" element={<ApplicationOverviewPage />} />
  <Route path="delivery" element={<ApplicationDeliveryPage />} />
  <Route path="health" element={<ApplicationHealthPage />} />

  {/* Legacy URL redirects — removed tabs */}
  <Route path="environments" element={<Navigate to=".." replace />} />
  <Route path="builds" element={<Navigate to="../delivery" replace />} />
  <Route path="releases" element={<Navigate to="../delivery" replace />} />
  <Route path="settings" element={<Navigate to=".." replace />} />
</Route>
```

### React Router v6 Relative Path Resolution

These redirect routes are children of `/teams/:teamId/apps/:appId`. React Router v6 resolves relative paths from the route's own path:

| Route Path | `Navigate to` | Resolves To | Result |
|------------|---------------|-------------|--------|
| `environments` | `".."` | `/teams/:teamId/apps/:appId` | Overview (index route) |
| `builds` | `"../delivery"` | `/teams/:teamId/apps/:appId/delivery` | Delivery page |
| `releases` | `"../delivery"` | `/teams/:teamId/apps/:appId/delivery` | Delivery page |
| `settings` | `".."` | `/teams/:teamId/apps/:appId` | Overview (index route) |

The `replace` prop ensures the redirect replaces the current history entry — the back button won't return to the old URL. This is correct behavior for permanent tab removals.

### Breadcrumb Behavior — No Changes Needed

`AppBreadcrumb.tsx` uses `deriveViewFromPath(location.pathname, hasAppId)` which reads the current URL path after React Router processes all `Navigate` components. Since `Navigate replace` updates the URL **before** rendering the target page (and therefore before `AppBreadcrumb` reads `location.pathname`):

- `/environments` → redirect → URL becomes `/teams/{teamId}/apps/{appId}` → `deriveViewFromPath` returns `"Overview"` ✓
- `/builds` → redirect → URL becomes `.../delivery` → `deriveViewFromPath` returns `"Delivery"` ✓
- `/releases` → redirect → URL becomes `.../delivery` → `deriveViewFromPath` returns `"Delivery"` ✓
- `/settings` → redirect → URL becomes `/teams/{teamId}/apps/{appId}` → `deriveViewFromPath` returns `"Overview"` ✓

### Tab Highlighting — No Changes Needed

`ApplicationTabs.tsx` uses `deriveTabFromPath(location.pathname)` to determine the active tab, reading from the same post-redirect URL. After redirect:

- `/environments` → URL becomes `.../` (no segment) → returns `"overview"` → Overview tab highlighted ✓
- `/builds` → URL becomes `.../delivery` → returns `"delivery"` → Delivery tab highlighted ✓
- `/releases` → URL becomes `.../delivery` → returns `"delivery"` → Delivery tab highlighted ✓
- `/settings` → URL becomes `.../` (no segment) → returns `"overview"` → Overview tab highlighted ✓

### DO NOT Create / Reuse Incorrectly

- Do **not** modify `ApplicationTabs.tsx` — already has 3 tabs after 8.2
- Do **not** modify `AppBreadcrumb.tsx` — works correctly with redirects
- Do **not** modify `ApplicationLayout.tsx` — unchanged wrapper
- Do **not** create any new components — this story only adds `<Navigate>` route entries
- Do **not** create any new hooks, API files, or type files
- Do **not** modify any page components (`ApplicationOverviewPage`, `ApplicationDeliveryPage`, `ApplicationHealthPage`)
- Do **not** modify any backend Java files — this is a frontend-only story (Epic 8 is entirely frontend)
- Do **not** modify `Accessibility.test.tsx` or `ApplicationTabs.test.tsx` — they should already pass after 8.2
- Do **not** add custom redirect logic or a redirect component — use React Router's built-in `<Navigate>` component
- Do **not** add server-side redirects (no Quarkus/Quinoa changes) — all redirects are client-side SPA routing
- Do **not** add auto-refresh or polling — manual `RefreshButton` only per project conventions
- Do **not** modify `EnvironmentChain.tsx`, `EnvironmentCard.tsx`, `BuildTable.tsx`, `ActivityFeed.tsx`, `ReleaseTable.tsx` — all complete

### Testing Approach

**App.test.tsx — Add redirect tests:**

After Stories 8.1 and 8.2, `App.test.tsx` will have been updated to use `ApplicationDeliveryPage` instead of `ApplicationBuildsPage`. The redirect routes need to be added to the test's `renderApp` function to match production `App.tsx`.

Add redirect routes to `renderApp`'s route tree (inside the `ApplicationLayout` nested routes):

```tsx
<Route path="environments" element={<Navigate to=".." replace />} />
<Route path="builds" element={<Navigate to="../delivery" replace />} />
<Route path="releases" element={<Navigate to="../delivery" replace />} />
<Route path="settings" element={<Navigate to=".." replace />} />
```

**New test cases:**

```typescript
it('redirects /environments to Overview', () => {
  renderApp('/teams/1/apps/my-app/environments');
  const overviewTab = screen.getByRole('tab', { name: 'Overview' });
  expect(overviewTab).toHaveAttribute('aria-selected', 'true');
});

it('redirects /builds to Delivery', () => {
  renderApp('/teams/1/apps/my-app/builds');
  const deliveryTab = screen.getByRole('tab', { name: 'Delivery' });
  expect(deliveryTab).toHaveAttribute('aria-selected', 'true');
});

it('redirects /releases to Delivery', () => {
  renderApp('/teams/1/apps/my-app/releases');
  const deliveryTab = screen.getByRole('tab', { name: 'Delivery' });
  expect(deliveryTab).toHaveAttribute('aria-selected', 'true');
});

it('redirects /settings to Overview', () => {
  renderApp('/teams/1/apps/my-app/settings');
  const overviewTab = screen.getByRole('tab', { name: 'Overview' });
  expect(overviewTab).toHaveAttribute('aria-selected', 'true');
});
```

These tests verify the redirect works correctly by checking that the destination tab becomes active. Since `Navigate replace` updates the URL, `ApplicationTabs`'s `deriveTabFromPath` will return the correct active tab, confirming the redirect resolved correctly.

**Alternative verification approach:** If the tab assertion is insufficient, verify page content renders. For example:
- Overview redirect: assert the environment chain or app metadata renders
- Delivery redirect: assert the 3-column layout or "Builds" card renders

However, testing tab selection is sufficient because it confirms URL resolution and is less fragile than asserting page content that depends on mock data.

**No need for breadcrumb-specific tests.** The breadcrumb derives from `location.pathname`, which is updated by `Navigate replace`. Existing breadcrumb tests (if any) cover the path derivation logic. The redirect is transparent to the breadcrumb component.

**Mocks required for redirect tests:**

The existing `App.test.tsx` mocks (`useApiFetch`, `useBuilds`) should be sufficient. The redirect tests don't render page content in detail — they verify the URL resolved correctly via tab highlighting.

If Story 8.2 added mocks for `useReleases`, `useDashboard`, etc. to support the Delivery page route, those mocks will already be in place. If not, the Delivery page's hooks need to be mocked to prevent render errors when the redirect resolves to the Delivery page.

Check the mock list in `App.test.tsx` after 8.2 and ensure all hooks used by `ApplicationDeliveryPage` are mocked:

```typescript
vi.mock('./hooks/useBuilds', () => ({
  useBuilds: () => ({ data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() }),
  useTriggerBuild: () => ({ trigger: vi.fn().mockResolvedValue(null), error: null, isTriggering: false }),
  useBuildDetail: () => ({ data: null, error: null, isLoading: false, fetch: vi.fn() }),
  useBuildLogs: () => ({ data: null, error: null, isLoading: false, fetch: vi.fn() }),
}));

vi.mock('./hooks/useReleases', () => ({
  useReleases: () => ({ data: [], error: null, isLoading: false, refresh: vi.fn() }),
}));

vi.mock('./hooks/useDashboard', () => ({
  useAppActivity: () => ({ data: null, error: null, isLoading: false, refresh: vi.fn() }),
}));
```

If these mocks are missing after 8.2, add them as part of this story to support the redirect tests that resolve to the Delivery page.

### File Structure (modified)

**Modified files:**
```
developer-portal/src/main/webui/src/App.tsx           (add 4 redirect routes)
developer-portal/src/main/webui/src/App.test.tsx       (add 4 redirect tests + redirect routes in renderApp)
```

**Should NOT touch:**
- Backend Java files (zero backend changes in Epic 8)
- `ApplicationTabs.tsx` or its tests (stable after 8.2)
- `AppBreadcrumb.tsx` (works correctly with redirects)
- `ApplicationLayout.tsx` (unchanged wrapper)
- `Accessibility.test.tsx` (stable after 8.2)
- Any page components (`ApplicationOverviewPage`, `ApplicationDeliveryPage`, `ApplicationHealthPage`)
- Any hook files, API files, type files
- Any component files (`EnvironmentChain`, `BuildTable`, `ActivityFeed`, etc.)
- Any dashboard page files

### Accessibility Requirements

- No new interactive elements are introduced — redirects are transparent to the user
- Tab highlighting works correctly after redirect (verified via `deriveTabFromPath`)
- Breadcrumbs show the correct view name after redirect (verified via `deriveViewFromPath`)
- Screen readers: `Navigate replace` updates the URL silently; the destination page's ARIA roles and landmarks apply normally
- Keyboard navigation: unaffected — redirects happen before page render

### Previous Story Intelligence

**From Story 8.1 (Merge Environments Tab into Application Overview):**
- Status: `ready-for-dev`
- Removes Environments tab and `/environments` route from `App.tsx` — but does NOT add a redirect (explicitly deferred to Story 8.3)
- Dev notes state: "Do not add any redirect for `/environments` URL in this story — that is Story 8.3"
- After 8.1: 4 tabs (Overview, Builds, Releases, Metrics)

**From Story 8.2 (Create Delivery Tab — Unified Builds, Releases & Activity):**
- Status: `ready-for-dev`
- Removes Builds and Releases tabs and their routes from `App.tsx` — but does NOT add redirects (explicitly deferred to Story 8.3)
- Dev notes state: "Do not add URL redirects for `/builds` or `/releases` — that is Story 8.3"
- After 8.2: 3 tabs (Overview, Delivery, Metrics)

**From Story 7.4 (Environments Tab — Promotion Pipeline & Settings Consolidation):**
- Status: `done`
- Removed Settings tab (6→5 tabs) and `/settings` route — did NOT add a redirect
- `App.test.tsx` was NOT updated for settings removal (the test file had no settings route)
- Pattern: "clean removal" without redirect — Story 8.3 retroactively adds the redirect for settings too

**From Story 1.4 (Portal Page Shell & Navigation):**
- Status: `done`
- Established `AppBreadcrumb` with `deriveViewFromPath` — reads URL segment after `apps/:appId/` and capitalizes
- Established `ApplicationTabs` with `deriveTabFromPath` — reads URL segment to determine active tab
- Established `Navigate` usage for root redirect (`/` → `/teams/default`)
- Both `deriveViewFromPath` and `deriveTabFromPath` are purely URL-driven, so they work correctly after any redirect that changes the URL

### Git Intelligence

Recent commits (last 5):
- `690dc02` Epic 7 retrospective, process rules, and Epic 8 creation
- `b101ca7` Story 7.5: Application Overview — Recent Builds & Activity Summary
- `b1496c6` Story 7.4: Environments Tab — Promotion Pipeline & Settings Consolidation
- `fc56b47` Add Story 7.4 story file and update backend seed/dashboard data
- `b95b456` Add Story 7.5 story file and update sprint status to ready-for-dev

Key patterns reinforced:
- `Navigate` from `react-router-dom` already imported and used in `App.tsx`
- React Router v6 nested routes with relative path resolution
- `replace` prop on `Navigate` for permanent redirects
- Co-located test files (`.test.tsx` next to component)
- `App.test.tsx` maintains a slimmer route tree than production for test isolation — add redirect routes to match
- Tab highlighting via URL-driven `deriveTabFromPath`
- Breadcrumbs via URL-driven `deriveViewFromPath`

### Project Structure Notes

- This story only modifies 2 files: `App.tsx` and `App.test.tsx`
- Net code addition: ~4 `<Route>` lines in `App.tsx`, ~4 `<Route>` lines + ~20 test lines in `App.test.tsx`
- No new component files, no new hooks, no new types, no new API calls, no new dependencies
- Frontend-only: zero backend changes

### References

- [Source: planning-artifacts/epics.md#Epic 8, Story 8.3] — User story, acceptance criteria, redirect mapping (environments→Overview, builds→Delivery, releases→Delivery, settings→Overview), breadcrumb verification
- [Source: planning-artifacts/epics.md#Epic 8] — Tab consolidation overview (5→3 tabs), frontend-only scope
- [Source: planning-artifacts/architecture.md#Frontend] — React Router v6, PatternFly 6, Vitest + RTL
- [Source: planning-artifacts/ux-design-specification.md#Navigation] — Breadcrumb pattern (Team → Application → View), tab bar within application context
- [Source: project-context.md] — React Router v6 for client-side routing, `useParams()` for path params, co-located test files
- [Source: implementation-artifacts/8-1-merge-environments-tab-into-application-overview.md] — Explicit deferral of `/environments` redirect to Story 8.3
- [Source: implementation-artifacts/8-2-create-delivery-tab-unified-builds-releases-activity.md] — Explicit deferral of `/builds` and `/releases` redirects to Story 8.3
- [Source: implementation-artifacts/7-4-environments-tab-promotion-pipeline-settings-consolidation.md] — Settings tab removal pattern, no redirect added at that time
- [Source: implementation-artifacts/1-4-portal-page-shell-navigation.md] — AppBreadcrumb and ApplicationTabs URL-driven derivation logic, Navigate usage for root redirect

## Dev Agent Record

### Agent Model Used

Claude Opus 4

### Debug Log References

No issues encountered. Clean implementation with all tests passing on first run.

### Completion Notes List

- Added 4 `<Navigate replace>` redirect routes to `App.tsx` for legacy URLs: `/environments` → Overview, `/builds` → Delivery, `/releases` → Delivery, `/settings` → Overview
- Added matching redirect routes to `App.test.tsx` `renderApp` function to mirror production routing
- Added 4 redirect test cases verifying correct tab activation after redirect (tab aria-selected assertion)
- Verified `AppBreadcrumb.tsx` requires no changes — `deriveViewFromPath` reads post-redirect URL correctly
- Verified `ApplicationTabs.tsx` requires no changes — `deriveTabFromPath` reads post-redirect URL correctly
- No stale imports found in `App.tsx` — 8.1 and 8.2 already cleaned up old page imports
- Full test suite: 390/390 tests pass across 36 files, zero regressions
- No new dependencies, no new files, no backend changes

### Change Log

- 2026-04-15: Story 8.3 implementation — added 4 legacy URL redirect routes and 4 redirect tests

### File List

- `developer-portal/src/main/webui/src/App.tsx` (modified — added 4 redirect routes)
- `developer-portal/src/main/webui/src/App.test.tsx` (modified — added 4 redirect routes to renderApp + 4 redirect test cases)
