# Story 7.4: Environments Tab — Promotion Pipeline & Settings Consolidation

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want a dedicated Environments tab that shows the full promotion pipeline with per-environment details, deep links, and secrets access,
so that I can understand my application's deployment path and access all environment-related tooling from one place — replacing the separate Settings tab.

## Acceptance Criteria

1. **Environments tab shows ordered promotion pipeline**
   - **Given** a developer navigates to the Environments tab for an application
   - **When** the page renders
   - **Then** it displays the ordered promotion pipeline (dev → staging → prod, as defined at onboarding via `promotionOrder`)
   - **And** the last environment is visually marked as the production environment

2. **Per-environment details are displayed**
   - **Given** the environments are displayed
   - **When** viewing each environment entry
   - **Then** each shows: environment name, cluster name, namespace, deployed version, deployment status, and last deployment timestamp
   - **And** the production environment is distinguished with a visual indicator (badge or icon)

3. **Deep links per environment**
   - **Given** each environment entry in the pipeline view
   - **When** deep links are available
   - **Then** the following deep links are displayed per environment:
     - ArgoCD deep link (to the environment's ArgoCD application)
     - Vault deep link (to the environment's secrets path)
     - Grafana deep link (to the environment's monitoring dashboard)

4. **Settings tab removed and consolidated**
   - **Given** the Environments tab exists
   - **When** the Settings tab is evaluated
   - **Then** the Settings tab is removed from `ApplicationTabs`
   - **And** the `/settings` route is removed from `App.tsx`
   - **And** the `ApplicationSettingsPage` component is deleted
   - **And** all Vault/environment configuration that was on Settings is now accessible on the Environments tab

5. **Keyboard accessibility**
   - **Given** the Environments tab is navigated with keyboard
   - **When** tabbing through environment entries
   - **Then** each entry and its deep links are keyboard-accessible
   - **And** each environment row has an aria-label including environment name and status

6. **Loading and error states**
   - **Given** environments data is loading
   - **When** the tab first renders
   - **Then** a loading spinner is shown
   - **And** errors from ArgoCD or Prometheus are displayed as inline alerts without blocking the entire page

## Tasks / Subtasks

- [ ] Task 1: Rewrite `ApplicationEnvironmentsPage` with full environment chain and Vault deep links (AC: #1, #2, #3, #5, #6)
  - [ ] 1.1 Replace the placeholder content in `ApplicationEnvironmentsPage.tsx` with a full implementation
  - [ ] 1.2 Use `useEnvironments(teamId, appId)` to fetch environment chain data
  - [ ] 1.3 Use `useReleases(teamId, appId)` and `useHealth(teamId, appId)` for deploy actions and health enrichment
  - [ ] 1.4 Render `EnvironmentChain` component (reuse from Overview page) with all required props
  - [ ] 1.5 Add a "Secrets Management" card below the chain: show Vault deep links per environment using `DeepLinkButton`
  - [ ] 1.6 Handle no-vault-links state with info Alert ("Vault URL not configured")
  - [ ] 1.7 Add loading state (`LoadingSpinner`) and error handling (`ErrorAlert`, inline `Alert`)
  - [ ] 1.8 Add `RefreshButton` for manual refresh of environment + health data

- [ ] Task 2: Add Vault deep link to `EnvironmentCard` expanded view (AC: #3)
  - [ ] 2.1 Add a `FlexItem` with `DeepLinkButton` for Vault between the existing ArgoCD and Grafana deep link items in the expanded card body
  - [ ] 2.2 Use `entry.vaultDeepLink` (already in `EnvironmentChainEntry` type) with `toolName="Vault"` and appropriate `ariaLabel`

- [ ] Task 3: Remove Settings tab and route (AC: #4)
  - [ ] 3.1 Remove `{ key: 'settings', label: 'Settings' }` from `APP_TABS` in `ApplicationTabs.tsx`
  - [ ] 3.2 Remove `<Route path="settings" element={<ApplicationSettingsPage />} />` from `App.tsx`
  - [ ] 3.3 Remove `import { ApplicationSettingsPage }` from `App.tsx`
  - [ ] 3.4 Delete `ApplicationSettingsPage.tsx` and `ApplicationSettingsPage.test.tsx`

- [ ] Task 4: Tests (all AC)
  - [ ] 4.1 Create `ApplicationEnvironmentsPage.test.tsx` in `src/main/webui/src/routes/`
    - [ ] Renders environment chain with environment cards
    - [ ] Shows loading spinner while environments load
    - [ ] Shows error alert on fetch failure
    - [ ] Shows ArgoCD warning alert when `argocdError` is present
    - [ ] Shows Vault deep links per environment in Secrets Management card
    - [ ] Shows info alert when no vault links configured
    - [ ] RefreshButton is rendered
  - [ ] 4.2 Update `ApplicationTabs.test.tsx`
    - [ ] Update "renders all 6 tabs" → "renders all 5 tabs"
    - [ ] Remove assertion for 'Settings' tab
  - [ ] 4.3 Update `App.test.tsx` if it references the settings route
  - [ ] 4.4 Verify `EnvironmentCard` Vault deep link renders in existing `EnvironmentCard.test.tsx` or add test if none exists

## Dev Notes

### What Already Exists — Critical Context

The **EnvironmentChain** + **EnvironmentCard** ecosystem is fully built and battle-tested from Epics 2 and 5. The Environments tab currently renders only a placeholder: `"Coming soon — environment chain detail view."` This story replaces that placeholder with the real implementation.

| What | Location | Status |
|------|----------|--------|
| `EnvironmentChain` | `components/environment/EnvironmentChain.tsx` | Complete — horizontal card chain with arrow connectors, ArrowKey navigation, `role="list"`, `argocdError` Alert |
| `EnvironmentCard` | `components/environment/EnvironmentCard.tsx` | Complete — status badges, deploy/promote actions, deployment history table, ArgoCD + Grafana deep links on expand. **Missing: Vault deep link** |
| `PromotionConfirmation` | `components/environment/PromotionConfirmation.tsx` | Complete — Popover (non-prod) / Modal (prod) confirmation |
| `DeepLinkButton` | `components/shared/DeepLinkButton.tsx` | Complete — PF6 `Button` with `variant="link"`, `target="_blank"`, returns `null` if no `href` |
| `useEnvironments` | `hooks/useEnvironments.ts` | Complete — wraps `useApiFetch` for `GET /api/v1/teams/{teamId}/applications/{appId}/environments` → `EnvironmentChainResponse` |
| `useReleases` | `hooks/useReleases.ts` | Complete — needed for deploy dropdown on EnvironmentCard |
| `useHealth` | `hooks/useHealth.ts` | Complete — needed for Prometheus health enrichment on EnvironmentCard |
| `RefreshButton` | `components/shared/RefreshButton.tsx` | Complete — manual refresh control |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Complete |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Complete |
| `EnvironmentChainEntry` type | `types/environment.ts` | Already includes `argocdDeepLink`, `vaultDeepLink`, `grafanaDeepLink`, `clusterName`, `namespace`, `promotionOrder`, `environmentId`, `isProduction` |
| `ApplicationOverviewPage` | `routes/ApplicationOverviewPage.tsx` | Uses `EnvironmentChain` — **reference implementation** for how to wire the chain, hooks, and refresh |

### What Needs to Change

| Change | File | Details |
|--------|------|---------|
| **Replace** placeholder | `routes/ApplicationEnvironmentsPage.tsx` | Full page with EnvironmentChain + Secrets Management card |
| **Add** Vault deep link | `components/environment/EnvironmentCard.tsx` | One `FlexItem` + `DeepLinkButton` in the expanded card body |
| **Remove** Settings entry | `components/layout/ApplicationTabs.tsx` | Remove from `APP_TABS` array |
| **Remove** Settings route | `App.tsx` | Remove route and import |
| **Delete** Settings page | `routes/ApplicationSettingsPage.tsx` | Entire file |
| **Delete** Settings test | `routes/ApplicationSettingsPage.test.tsx` | Entire file |
| **Update** tabs test | `components/layout/ApplicationTabs.test.tsx` | 6 tabs → 5 tabs |
| **Create** environments test | `routes/ApplicationEnvironmentsPage.test.tsx` | New test file |

### ApplicationEnvironmentsPage Implementation

Model after `ApplicationOverviewPage.tsx` — it already wires `useEnvironments`, `useReleases`, `useHealth`, and `EnvironmentChain` correctly.

```tsx
// routes/ApplicationEnvironmentsPage.tsx
import { useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  PageSection,
  Title,
  Card,
  CardTitle,
  CardBody,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Alert,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { useEnvironments } from '../hooks/useEnvironments';
import { useReleases } from '../hooks/useReleases';
import { useHealth } from '../hooks/useHealth';
import { EnvironmentChain } from '../components/environment/EnvironmentChain';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';
```

**Page structure:**

```
PageSection
  ├── Flex (Title "Environments" + RefreshButton)
PageSection
  ├── LoadingSpinner (when loading)
  ├── ErrorAlert (when envError)
  ├── ErrorAlert (when releasesError)
  └── EnvironmentChain (when envData)
PageSection
  └── Card "Secrets Management"
      └── CardBody
          ├── Info Alert (when envs exist but no vault links)
          └── DescriptionList (env name → Vault DeepLinkButton per env)
```

**Hook wiring — exactly like ApplicationOverviewPage:**

```typescript
const { data: envData, error: envError, isLoading: envLoading, refresh: refreshEnv } = useEnvironments(teamId, appId);
const { data: releases, error: releasesError } = useReleases(teamId, appId);
const { data: healthData, refresh: refreshHealth } = useHealth(teamId, appId);
const refreshAll = useCallback(() => { refreshEnv(); refreshHealth(); }, [refreshEnv, refreshHealth]);
```

**EnvironmentChain props — pass everything:**

```tsx
<EnvironmentChain
  environments={envData.environments}
  argocdError={envData.argocdError}
  teamId={teamId}
  appId={appId}
  releases={releases}
  healthData={healthData}
  onDeploymentInitiated={refreshAll}
/>
```

**Secrets Management card — migrated from ApplicationSettingsPage:**

```tsx
const hasVaultLinks = envData?.environments.some((env) => env.vaultDeepLink);

// Renders as DescriptionList with env name → DeepLinkButton
// Shows info Alert if environments exist but no vault links
```

### EnvironmentCard Vault Deep Link Addition

Add a single `FlexItem` in the expanded `CardBody` section, between the existing ArgoCD and Grafana deep link items:

```tsx
{/* Existing ArgoCD link */}
{entry.argocdDeepLink && (
  <FlexItem>
    <DeepLinkButton href={entry.argocdDeepLink} toolName="ArgoCD" ariaLabel={`Open ${entry.environmentName} in ArgoCD`} />
  </FlexItem>
)}
{/* NEW: Vault link */}
<FlexItem>
  <DeepLinkButton href={entry.vaultDeepLink} toolName="Vault" ariaLabel={`Open ${entry.environmentName} secrets in Vault`} />
</FlexItem>
{/* Existing Grafana link */}
<FlexItem>
  <DeepLinkButton href={entry.grafanaDeepLink} toolName="Grafana" label="View in Grafana ↗" ariaLabel={`Open ${entry.environmentName} in Grafana`} />
</FlexItem>
```

Note: `DeepLinkButton` already returns `null` when `href` is falsy — no conditional wrapper needed.

### ApplicationTabs — Remove Settings

Change `APP_TABS` from 6 entries to 5:

```typescript
const APP_TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'builds', label: 'Builds' },
  { key: 'releases', label: 'Releases' },
  { key: 'environments', label: 'Environments' },
  { key: 'health', label: 'Metrics' },
] as const;
```

### DO NOT Create / Reuse Incorrectly

- Do **not** create a new environment chain component — reuse `EnvironmentChain` + `EnvironmentCard` as-is
- Do **not** create a new API hook — reuse `useEnvironments`, `useReleases`, `useHealth`
- Do **not** create new types — `EnvironmentChainResponse`, `EnvironmentChainEntry` already have all fields
- Do **not** modify the backend — this is a frontend-only story; the API already returns `vaultDeepLink` per environment
- Do **not** add auto-refresh or polling — manual `RefreshButton` only per project conventions
- Do **not** modify `EnvironmentChain.tsx` — it is complete and works correctly
- Do **not** modify any dashboard files (`TeamDashboardPage`, `ActivityFeed`, `ApplicationHealthGrid`, `DoraTrendChart`)
- Do **not** rename the "Metrics" label on the health tab — only Settings is removed
- Do **not** add a redirect from `/settings` to `/environments` — clean removal, no redirect needed

### Testing Approach

**New test file: `ApplicationEnvironmentsPage.test.tsx`**

Mock `useEnvironments`, `useReleases`, `useHealth` following existing patterns. Reference `ApplicationSettingsPage.test.tsx` fixtures for environment data with/without Vault links.

Test cases:
- Loading spinner shown while `isLoading` is true
- Error alert shown when `envError` is truthy
- Environment chain renders (check for `role="list"` with `aria-label="Environment promotion chain"`)
- ArgoCD warning alert shown when `argocdError` is present in response
- Secrets Management card heading renders
- Vault deep links rendered per environment when `vaultDeepLink` is present
- Info alert shown when environments exist but no vault links
- RefreshButton is rendered (check for button with "Refresh" text or appropriate aria-label)

**Mock pattern (from previous stories):**

```typescript
vi.mock('../hooks/useEnvironments', () => ({
  useEnvironments: () => mockEnvResult,
}));
vi.mock('../hooks/useReleases', () => ({
  useReleases: () => mockReleasesResult,
}));
vi.mock('../hooks/useHealth', () => ({
  useHealth: () => mockHealthResult,
}));
```

**ApplicationTabs.test.tsx update:**

```typescript
it('renders all 5 tabs', () => {  // was 6
  renderTabs();
  expect(screen.getByText('Overview')).toBeInTheDocument();
  expect(screen.getByText('Builds')).toBeInTheDocument();
  expect(screen.getByText('Releases')).toBeInTheDocument();
  expect(screen.getByText('Environments')).toBeInTheDocument();
  expect(screen.getByText('Health')).toBeInTheDocument();
  // Settings assertion removed
});
```

**App.test.tsx:** Check if it references Settings route and update accordingly. From Story 7.3 notes, the `App.test.tsx` had changes — verify it doesn't assert Settings route existence.

**EnvironmentCard Vault link test:** Check if `EnvironmentCard.test.tsx` exists; if so add a test that Vault deep link renders when `vaultDeepLink` is present. If no test file exists, the `ApplicationEnvironmentsPage.test.tsx` integration tests will cover it via rendered chain.

### File Structure (new/modified/deleted)

**New files:**
```
src/main/webui/src/routes/ApplicationEnvironmentsPage.test.tsx
```

**Modified files:**
```
src/main/webui/src/routes/ApplicationEnvironmentsPage.tsx     (replace placeholder with full implementation)
src/main/webui/src/components/environment/EnvironmentCard.tsx  (add Vault deep link in expanded view)
src/main/webui/src/components/layout/ApplicationTabs.tsx       (remove Settings from APP_TABS)
src/main/webui/src/components/layout/ApplicationTabs.test.tsx  (6 tabs → 5 tabs)
src/main/webui/src/App.tsx                                     (remove Settings route + import)
src/main/webui/src/App.test.tsx                                (update if references Settings)
```

**Deleted files:**
```
src/main/webui/src/routes/ApplicationSettingsPage.tsx
src/main/webui/src/routes/ApplicationSettingsPage.test.tsx
```

**Should NOT touch:**
- Backend Java files (API already returns vaultDeepLink)
- `EnvironmentChain.tsx` (complete, reuse as-is)
- `DeepLinkButton.tsx` (complete)
- `useEnvironments.ts` (complete)
- `types/environment.ts` (already has vaultDeepLink)
- Any dashboard files (TeamDashboardPage, ActivityFeed, etc.)
- `PromotionConfirmation.tsx`

### Accessibility Requirements

- EnvironmentChain already provides: `role="list"`, `aria-label="Environment promotion chain"`, left/right arrow key navigation between cards
- Each EnvironmentCard already has: `tabIndex={0}`, `aria-label` with environment name + version + status, Enter/Space to expand
- Deep links: `DeepLinkButton` uses PF6 `Button` with link variant — inherently focusable and keyboard-accessible
- Vault deep link `ariaLabel` format: `"Open {envName} secrets in Vault"`
- Status indicators use both color AND text/icons (never color-only per WCAG 2.1 AA)

### Previous Story Intelligence

**From Story 7.3 (Activity Feed & Aggregated DORA Trends):**
- 415/416 full suite tests pass; **1 pre-existing failure in `ApplicationTabs.test.tsx`** — investigate this before assuming all tabs tests pass
- Mock pattern for `useApiFetch`-based hooks is well-established
- Chart mocking (`@patternfly/react-charts/victory`) not needed for this story — no charts involved

**From Story 7.2 (Team Dashboard Page & Application Health Grid):**
- `useDashboard` hook pattern — not needed here, but confirms the `{ data, error, isLoading }` tuple convention
- PatternFly `Table` for grids, `DataList` for feeds — this story uses `Card`/`DescriptionList` patterns instead

**From Story 5.3 (Environment Chain Deploy/Promote Actions):**
- EnvironmentCard deploy/promote actions are fully implemented with role-based gating
- `useAuth` provides `role` for production gating
- `triggerDeployment` API is wired through EnvironmentCard

**From Story 3.2 (DevSpaces Launch & Vault Secret Navigation):**
- Vault deep links were first surfaced on the Settings page
- `DeepLinkButton` pattern established here — exact same component to reuse

### Git Intelligence

Recent commits (last 10):
- `9a728af` Story 7.3: Activity Feed & Aggregated DORA Trends
- `c8ba41d` Story 7.2: Team Dashboard Page & Application Health Grid
- `07700e4` Story 7.1: Team Dashboard Backend & Aggregation
- `b54619b` Add Epic 7 story files and update sprint status
- `6d52694` Epic 6 retrospective — DORA query optimization and coding rules

Key patterns reinforced:
- PatternFly 6 components exclusively — no custom CSS
- PF CSS design tokens for all colors (`var(--pf-t--global--...)`)
- Co-located test files (`.test.tsx` next to component)
- `useApiFetch` wrapper for all data hooks
- Inline `Alert` for section-level errors
- `RefreshButton` for manual refresh (no auto-poll)

### Project Structure Notes

- All files follow existing project structure conventions
- Page component in `src/routes/` with `Page` suffix
- Shared components in `src/components/shared/`
- Environment-specific components in `src/components/environment/`
- Tests co-located with source files
- No new dependencies needed — all PF6 packages and hooks already installed
- No new types needed — `EnvironmentChainEntry` already has all fields including `vaultDeepLink`
- No new API calls needed — `useEnvironments` already returns full environment data with deep links

### References

- [Source: planning-artifacts/epics.md#Epic 7, Story 7.4] — User story, BDD acceptance criteria, Settings removal, Vault consolidation
- [Source: planning-artifacts/architecture.md#Frontend] — PatternFly 6, React Router v6, CSS token rules, component patterns
- [Source: planning-artifacts/architecture.md#Deep Links] — DeepLinkService, Grafana/ArgoCD URL patterns, DeepLinkButton component
- [Source: planning-artifacts/architecture.md#Project Structure] — File locations, naming conventions, domain packages
- [Source: planning-artifacts/ux-design-specification.md#Environment Chain Card Row] — Horizontal card chain with status badges, arrow connectors, click to expand, min 180px cards, horizontal scroll
- [Source: planning-artifacts/ux-design-specification.md#Deep Link Display] — Link button + ↗, new tab, tool name label, never replace portal
- [Source: planning-artifacts/ux-design-specification.md#Application Tabs] — Horizontal tabs: Overview | Builds | Releases | Environments | Health | Settings
- [Source: planning-artifacts/ux-design-specification.md#Loading and Errors] — Spinner on load, inline Alert for errors, "Last refreshed" + manual refresh
- [Source: planning-artifacts/ux-design-specification.md#Accessibility] — WCAG 2.1 AA, arrow keys between stages, aria-label per env, status never color-only
- [Source: project-context.md] — Coding rules, testing conventions, PF6 mandatory, strict TS, CSS token rules, useEffect cleanup
- [Source: implementation-artifacts/7-3-activity-feed-aggregated-dora-trends.md] — Previous story patterns, mock conventions, test counts

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### Change Log

### File List
