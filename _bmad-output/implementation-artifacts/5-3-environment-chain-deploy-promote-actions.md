# Story 5.3: Environment Chain Deploy & Promote Actions

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want deploy and promote buttons directly on the environment chain cards that appear when they're relevant,
so that I can move releases through environments with minimal clicks.

## Acceptance Criteria

1. **Deploy button on NOT_DEPLOYED environments with available releases**
   - **Given** an environment card has status "NOT_DEPLOYED" and releases exist for the application
   - **When** the card renders
   - **Then** a "Deploy" button is displayed on the card
   - **And** clicking "Deploy" opens a PF6 Select dropdown listing available releases (version + creation date)
   - **And** selecting a release initiates the deployment by calling `POST /api/v1/teams/{teamId}/applications/{appId}/deployments` with `{ releaseVersion, environmentId }`
   - **And** the button shows a Spinner during the API call
   - **And** on success, the card transitions to "Deploying" status (via environment chain re-fetch)
   - **And** on failure, an inline PF6 Alert (danger variant) shows the error message

2. **Promote button on HEALTHY environments (not last in chain)**
   - **Given** an environment card has status "HEALTHY"
   - **When** the environment is NOT the last in the promotion chain
   - **Then** a "Promote to [next env name]" button is displayed on the card
   - **And** clicking it initiates a deployment of the currently deployed version (`entry.deployedVersion`) to the next environment in the chain (by `promotionOrder`)
   - **And** the button shows a Spinner during the API call
   - **And** on success, the next environment card transitions to "Deploying" status (via chain re-fetch)

3. **No promote button on last environment in chain**
   - **Given** an environment card has status "HEALTHY"
   - **When** the environment IS the last in the promotion chain (e.g., prod)
   - **Then** no promote button is displayed (there is no next environment)

4. **No action buttons during DEPLOYING or UNHEALTHY states**
   - **Given** an environment card has status "DEPLOYING"
   - **When** the card renders
   - **Then** no deploy or promote buttons are shown on that card
   - **Given** an environment card has status "UNHEALTHY"
   - **When** the card renders
   - **Then** the promote button is NOT displayed (cannot promote from an unhealthy state)
   - **And** the existing ArgoCD deep link remains visible for investigation

5. **Progressive action revelation (UX design principle)**
   - **Given** the environment chain renders after a fresh build and release
   - **When** reviewing the chain
   - **Then** the deploy button appears on the first NOT_DEPLOYED environment only when releases exist
   - **And** the promote button appears on an environment only after a successful deployment to it (status HEALTHY)
   - **And** actions guide the natural left-to-right flow through the chain
   - **And** deploy buttons also appear on HEALTHY environments to allow re-deploying a different version

6. **Environment chain passes teamId and appId to cards**
   - **Given** the EnvironmentChain component renders
   - **When** cards need to call the deployments API
   - **Then** `teamId` and `appId` are passed as props from the page-level component through EnvironmentChain to each EnvironmentCard
   - **And** `environmentId` is available on each `EnvironmentChainEntry` (added to backend DTO and TypeScript type)

7. **Resource ownership validation (mandatory scoping pattern)**
   - **Given** a request targets a resource outside the caller's team scope
   - **Then** 404 is returned (never 403)
   - **Given** a request targets an environment that does not belong to the application
   - **Then** 404 is returned
   - **Given** a request targets a non-existent application or environment
   - **Then** 404 is returned

## Tasks / Subtasks

- [ ] Task 1: Add `environmentId` to backend DTO and frontend type (AC: #6)
  - [ ] Add `Long environmentId` field to `EnvironmentChainEntryDto` Java record (at end of constructor)
  - [ ] Update `EnvironmentMapper.merge()` to include `environment.id` in the DTO
  - [ ] Add `environmentId: number` to `EnvironmentChainEntry` TypeScript interface in `types/environment.ts`

- [ ] Task 2: Create frontend deployment API and types (AC: #1, #2)
  - [ ] Create `src/main/webui/src/types/deployment.ts`:
    - [ ] `DeployRequest` interface: `{ releaseVersion: string; environmentId: number }`
    - [ ] `DeploymentResponse` interface: `{ deploymentId: string; releaseVersion: string; environmentName: string; status: string; startedAt: string }`
  - [ ] Create `src/main/webui/src/api/deployments.ts`:
    - [ ] `triggerDeployment(teamId: string, appId: string, request: DeployRequest): Promise<DeploymentResponse>` using `apiFetch` with `POST` method

- [ ] Task 3: Update EnvironmentChain props to pass teamId, appId, releases, and callbacks (AC: #6)
  - [ ] Add `teamId: string`, `appId: string` to `EnvironmentChainProps` interface
  - [ ] Add `releases: ReleaseSummary[] | null` to `EnvironmentChainProps`
  - [ ] Add `onDeploymentInitiated?: () => void` callback prop (triggers chain re-fetch after deploy/promote)
  - [ ] Pass `teamId`, `appId`, `releases`, and `onDeploymentInitiated` through to each `EnvironmentCard`

- [ ] Task 4: Update EnvironmentCard props and state (AC: #1, #2, #3, #4)
  - [ ] Add `teamId: string`, `appId: string` to `EnvironmentCardProps`
  - [ ] Add `releases: ReleaseSummary[] | null` to `EnvironmentCardProps`
  - [ ] Add `nextEnvironmentId?: number` to `EnvironmentCardProps` (the environmentId of the next env for promote)
  - [ ] Add `onDeploymentInitiated?: () => void` to `EnvironmentCardProps`
  - [ ] Add component state: `isDeploying` (boolean), `deployError` (string | null), `isSelectOpen` (boolean)

- [ ] Task 5: Implement Deploy button with release selection dropdown (AC: #1, #5)
  - [ ] In `EnvironmentCard` CardFooter, render a "Deploy" PF6 `Dropdown` when:
    - [ ] Status is `NOT_DEPLOYED` AND `releases` is non-null and non-empty, OR
    - [ ] Status is `HEALTHY` AND `releases` is non-null and non-empty (re-deploy different version)
  - [ ] Dropdown toggle shows "Deploy" label with a caret
  - [ ] Dropdown items list available releases: each item shows `version` + relative time of `createdAt`
  - [ ] Selecting a release calls `triggerDeployment(teamId, appId, { releaseVersion: selected.version, environmentId: entry.environmentId })`
  - [ ] During API call: toggle shows `Spinner` + "Deploying..."
  - [ ] On success: call `onDeploymentInitiated()` to trigger chain re-fetch
  - [ ] On failure: set `deployError` state → render PF6 inline `Alert` (danger variant) below the button
  - [ ] Stop event propagation on the dropdown to prevent card expand/collapse

- [ ] Task 6: Implement Promote button (AC: #2, #3, #4)
  - [ ] Replace the existing disabled placeholder promote button in `EnvironmentCard` CardFooter
  - [ ] Render an enabled "Promote to [nextEnvName]" `Button` (variant="secondary", size="sm") when:
    - [ ] Status is `HEALTHY`
    - [ ] `nextEnvName` is defined (not last in chain)
    - [ ] `nextEnvironmentId` is defined
    - [ ] NOT currently deploying (`isDeploying === false`)
  - [ ] Clicking promote calls `triggerDeployment(teamId, appId, { releaseVersion: entry.deployedVersion!, environmentId: nextEnvironmentId })`
  - [ ] During API call: button shows `Spinner` + "Promoting..."
  - [ ] On success: call `onDeploymentInitiated()` to trigger chain re-fetch
  - [ ] On failure: set `deployError` state → render PF6 inline `Alert` below the button
  - [ ] Stop event propagation on the button to prevent card expand/collapse
  - [ ] Do NOT render promote button when status is `DEPLOYING`, `UNHEALTHY`, or `NOT_DEPLOYED`

- [ ] Task 7: Update page-level component to pass required data (AC: #6)
  - [ ] Update `ApplicationEnvironmentsPage.tsx` (or wherever EnvironmentChain is rendered) to:
    - [ ] Fetch releases via `useReleases(teamId, appId)` (already exists)
    - [ ] Pass `teamId`, `appId`, `releases`, and a `refresh` callback to `EnvironmentChain`
  - [ ] The `onDeploymentInitiated` callback should trigger a re-fetch of the environment chain data

- [ ] Task 8: Write frontend tests (AC: #1, #2, #3, #4, #5)
  - [ ] `EnvironmentCard.test.tsx` (extend existing):
    - [ ] NOT_DEPLOYED + releases available → Deploy dropdown renders
    - [ ] NOT_DEPLOYED + no releases → no Deploy button
    - [ ] HEALTHY + nextEnvName → Promote button renders and is enabled
    - [ ] HEALTHY + no nextEnvName (last env) → no Promote button
    - [ ] DEPLOYING → no Deploy or Promote buttons
    - [ ] UNHEALTHY → no Promote button, ArgoCD deep link still shown
    - [ ] Deploy dropdown shows release versions with relative timestamps
    - [ ] Selecting a release triggers deployment API call
    - [ ] Promote button click triggers deployment API call with current version
    - [ ] Loading state shows Spinner during API call
    - [ ] Error state shows inline Alert on failure
    - [ ] HEALTHY + releases available → both Deploy dropdown and Promote button visible
  - [ ] `EnvironmentChain.test.tsx` (extend existing):
    - [ ] Verify teamId, appId, releases passed through to EnvironmentCard
    - [ ] Verify nextEnvironmentId is computed from environments array
  - [ ] `deployments.ts` (new — api test):
    - [ ] `triggerDeployment` sends POST with correct body
    - [ ] Error handling

## Dev Notes

### Prerequisites: Stories 5.1 and 5.2 Must Be Implemented First

This story uses the `POST /api/v1/teams/{teamId}/applications/{appId}/deployments` endpoint created in Story 5.1 and the `environmentId` field added to `EnvironmentChainEntryDto` in Story 5.2. **Do not start 5.3 until 5.1 and 5.2 are implemented and merged.**

If for any reason 5.2 has not added `environmentId` to `EnvironmentChainEntryDto`, Task 1 of this story handles that addition.

### All File Paths Are Relative to `developer-portal/`

Every frontend path (e.g., `src/main/webui/src/...`) and backend path (e.g., `src/main/java/com/portal/...`) is relative to the `developer-portal/` directory at the repository root.

### This Is Primarily a Frontend Story

Story 5.3 is almost entirely frontend work. The only backend change is adding `environmentId` to the EnvironmentChainEntryDto (Task 1), and only if Story 5.2 hasn't done it already. All deploy/promote logic uses the existing `POST /deployments` endpoint from Story 5.1.

### Deploy vs. Promote — Same API, Different UX

Both deploy and promote use the same backend endpoint: `POST /api/v1/teams/{teamId}/applications/{appId}/deployments` with `{ releaseVersion, environmentId }`.

- **Deploy**: User picks a release version from a dropdown → deploys to the current card's environment
- **Promote**: Automatically uses the card's `entry.deployedVersion` → deploys to the **next** environment's `environmentId`

The frontend handles the semantic difference. The backend sees identical requests.

### Deploy Button UX — PF6 Dropdown (Not Select)

Use PF6 `Dropdown` (from `@patternfly/react-core`) for the deploy release picker. This is an **action menu**, not a form input — Dropdown is semantically correct per PF6 guidelines. Each dropdown item shows:

```
v2.1.1 — 2h ago
v2.1.0 — 1d ago
v2.0.9 — 3d ago
```

Format: `{version} — {relativeTime(createdAt)}`

The dropdown toggle uses `MenuToggle` with a `variant="secondary"` and includes a caret. When deploying is in progress, the toggle shows a Spinner.

```tsx
import {
  Dropdown,
  DropdownItem,
  DropdownList,
  MenuToggle,
  Spinner,
} from '@patternfly/react-core';
```

### Progressive Action Revelation

The environment chain guides left-to-right progression:

```
[dev: NOT_DEPLOYED]  →  [qa: NOT_DEPLOYED]  →  [prod: NOT_DEPLOYED]
 Deploy ▼                 (no button)           (no button)

After deploying to dev and it becomes healthy:

[dev: ✓ Healthy v2.1.1] →  [qa: NOT_DEPLOYED]  →  [prod: NOT_DEPLOYED]
 Deploy ▼  Promote to qa →   Deploy ▼              (no button)

After promoting to qa and it becomes healthy:

[dev: ✓ Healthy v2.1.1] →  [qa: ✓ Healthy v2.1.1] →  [prod: NOT_DEPLOYED]
 Deploy ▼  Promote to qa →  Deploy ▼  Promote to prod →  Deploy ▼
```

Note: Even HEALTHY environments show the Deploy dropdown (for re-deploying a different version). The promote button only appears when HEALTHY and not the last environment.

### Action Button States Per Environment Status

| Status | Deploy Dropdown | Promote Button |
|---|---|---|
| NOT_DEPLOYED (releases exist) | Shown | Hidden |
| NOT_DEPLOYED (no releases) | Hidden | Hidden |
| HEALTHY (not last, has nextEnv) | Shown | Shown ("Promote to {nextEnv}") |
| HEALTHY (last env) | Shown | Hidden |
| DEPLOYING | Hidden | Hidden |
| UNHEALTHY | Hidden | Hidden (ArgoCD deep link shown) |
| UNKNOWN | Hidden | Hidden |

### Event Propagation — Critical

The `EnvironmentCard` outer div toggles expand/collapse on click. All buttons, dropdowns, and alerts inside the card footer MUST call `e.stopPropagation()` to prevent triggering the card expand/collapse. The existing code already uses `onClick={(e) => e.stopPropagation()}` on the CardFooter — maintain this pattern.

### Chain Re-fetch After Deployment

After a successful deploy or promote action, the environment chain must re-fetch to show the updated status (the target environment should transition to "Deploying"). This is achieved via the `onDeploymentInitiated` callback:

1. `EnvironmentCard` calls `onDeploymentInitiated()` after successful POST
2. `EnvironmentChain` propagates this to the page component
3. Page component calls `refresh()` on the environment chain data hook (from `useApiFetch`)

The chain will show the target environment as "Deploying" because ArgoCD detects the Git drift within seconds.

### EnvironmentChain — Computing nextEnvironmentId

`EnvironmentChain` must compute and pass `nextEnvironmentId` to each `EnvironmentCard`. It already passes `nextEnvName`:

```tsx
// In EnvironmentChain, when rendering EnvironmentCard:
<EnvironmentCard
  entry={env}
  nextEnvName={environments[index + 1]?.environmentName}
  nextEnvironmentId={environments[index + 1]?.environmentId}
  teamId={teamId}
  appId={appId}
  releases={releases}
  onDeploymentInitiated={onDeploymentInitiated}
  ref={setCardRef(index)}
/>
```

### Existing Placeholder Code to Replace

The current `EnvironmentCard.tsx` CardFooter has placeholder buttons:

```tsx
<CardFooter onClick={(e) => e.stopPropagation()}>
  {entry.status === 'HEALTHY' && nextEnvName && (
    <Tooltip content="Promotion available in a future release">
      <Button variant="secondary" isDisabled size="sm">
        Promote to {nextEnvName}
      </Button>
    </Tooltip>
  )}
  {entry.status === 'UNHEALTHY' && (
    <>
      <Tooltip content="Promotion available in a future release">
        <Button variant="secondary" isDisabled size="sm">
          Promote
        </Button>
      </Tooltip>
      {entry.argocdDeepLink && (
        <DeepLinkButton ... />
      )}
    </>
  )}
</CardFooter>
```

This entire CardFooter content must be replaced with the functional deploy/promote logic. The UNHEALTHY case should ONLY show the ArgoCD deep link (no promote button, disabled or otherwise).

### Where EnvironmentChain Is Rendered

Find where `EnvironmentChain` is used in the page-level routes. It's rendered in the application environments page (likely `ApplicationEnvironmentsPage.tsx` or `ApplicationOverviewPage.tsx`). The page component must:
1. Extract `teamId` and `appId` from URL params
2. Fetch releases via `useReleases(teamId, appId)`
3. Pass both to `EnvironmentChain` along with the refresh callback

### Production Gating — NOT in This Story

Story 5.4 handles production deployment restrictions:
- Popover confirmation for non-prod promotions
- Modal confirmation for prod promotions
- Lead-only Casbin enforcement for prod
- Disabled prod promote button for member role

In Story 5.3, the promote button works for ALL environments including prod, with no confirmation dialog and no role restriction. Story 5.4 will layer those safeguards on top.

### Deployment API Request Shape

From Story 5.1, the POST endpoint expects:

```json
POST /api/v1/teams/{teamId}/applications/{appId}/deployments
Content-Type: application/json

{
  "releaseVersion": "v2.1.1",
  "environmentId": 42
}
```

Response (201 Created):

```json
{
  "deploymentId": "correlation-id",
  "releaseVersion": "v2.1.1",
  "environmentName": "qa",
  "status": "Deploying",
  "startedAt": "2026-04-09T14:30:00Z"
}
```

### Project Structure Notes

```
src/main/webui/src/
├── types/
│   ├── deployment.ts          # NEW — DeployRequest, DeploymentResponse
│   └── environment.ts         # MODIFIED — add environmentId
├── api/
│   └── deployments.ts         # NEW — triggerDeployment()
├── components/environment/
│   ├── EnvironmentCard.tsx     # MODIFIED — functional deploy/promote buttons
│   └── EnvironmentChain.tsx    # MODIFIED — pass teamId/appId/releases/callback
└── routes/
    └── ApplicationEnvironmentsPage.tsx  # MODIFIED — fetch releases, pass to chain
        (or ApplicationOverviewPage.tsx — whichever renders the chain)
```

### File Structure Requirements

**New frontend files:**

```
src/main/webui/src/types/deployment.ts
src/main/webui/src/api/deployments.ts
```

**Modified frontend files:**

```
src/main/webui/src/types/environment.ts                               (add environmentId)
src/main/webui/src/components/environment/EnvironmentCard.tsx          (functional deploy/promote)
src/main/webui/src/components/environment/EnvironmentChain.tsx         (pass teamId/appId/releases)
src/main/webui/src/routes/ApplicationEnvironmentsPage.tsx              (fetch releases, wire callbacks)
```

**Modified backend files (only if 5.2 hasn't already done it):**

```
src/main/java/com/portal/environment/EnvironmentChainEntryDto.java     (add environmentId)
src/main/java/com/portal/environment/EnvironmentMapper.java            (pass environment.id)
```

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `EnvironmentCard.tsx` | `components/environment/` | EXISTS — replace placeholder buttons with functional ones |
| `EnvironmentChain.tsx` | `components/environment/` | EXISTS — add props passthrough |
| `EnvironmentCard.test.tsx` | `components/environment/` | EXISTS — extend with new tests |
| `EnvironmentChain.test.tsx` | `components/environment/` | EXISTS — extend with new tests |
| `environment.ts` (types) | `types/` | EXISTS — add environmentId |
| `release.ts` (types) | `types/` | EXISTS — `ReleaseSummary` interface used for dropdown |
| `releases.ts` (api) | `api/` | EXISTS — `fetchReleases()` |
| `useReleases.ts` (hook) | `hooks/` | EXISTS — `useReleases(teamId, appId)` |
| `client.ts` (api) | `api/` | EXISTS — `apiFetch()` wrapper |
| `useApiFetch.ts` (hook) | `hooks/` | EXISTS — generic fetch hook with `refresh` |
| `DeepLinkButton.tsx` | `components/shared/` | EXISTS — used for ArgoCD links |
| `PermissionFilter.java` | `com.portal.auth` | EXISTS — maps POST deployments to `deploy` action |
| `policy.csv` | `resources/casbin/` | EXISTS — `member` can `deploy`, `lead` can `deploy-prod` |
| POST `/deployments` endpoint | `com.portal.deployment` | FROM 5.1 — the backend API this story calls |

### What NOT to Build

- **No backend deployment logic** — Story 5.1 handles the POST /deployments endpoint
- **No deployment history UI** — Story 5.2 handles the GET /deployments and history table
- **No promotion confirmation dialogs** — Story 5.4 adds popover for non-prod, modal for prod
- **No production gating** — Story 5.4 adds lead-only enforcement for prod deploys
- **No Casbin policy changes** — existing policies sufficient
- **No new REST endpoints** — uses existing POST /deployments from 5.1
- **No database changes** — no Flyway migrations
- **No backend service changes** — purely frontend (except possible environmentId DTO addition)
- **No polling or SSE** — deploy triggers a manual chain re-fetch via callback

### Anti-Patterns to Avoid

- **DO NOT** call the deployments API from `EnvironmentChain` — each `EnvironmentCard` makes its own API call via `triggerDeployment()`
- **DO NOT** manage deployment state in EnvironmentChain — each card manages its own `isDeploying`/`deployError` state
- **DO NOT** use `fetch` directly — use the `apiFetch` wrapper from `api/client.ts`
- **DO NOT** hardcode absolute API URLs — use relative paths (`/api/v1/teams/...`)
- **DO NOT** add confirmation dialogs — that's Story 5.4
- **DO NOT** check user role for production deploys — that's Story 5.4
- **DO NOT** use PF6 `Select` for the release picker — use PF6 `Dropdown` (this is an action menu, not a form field)
- **DO NOT** import entities from other packages on the backend
- **DO NOT** create new custom CSS — use PF6 components and tokens exclusively
- **DO NOT** use `@ts-ignore` — type everything properly

### Architecture Compliance

- Frontend calls backend via relative URLs through `apiFetch()` wrapper
- All API types defined in `types/` directory
- API function in `api/deployments.ts` follows naming pattern (`triggerDeployment`)
- Component state managed via React hooks (`useState`) — no Redux, no external state
- PF6 components used exclusively: `Dropdown`, `DropdownItem`, `DropdownList`, `MenuToggle`, `Button`, `Alert`, `Spinner`
- No direct platform system calls from frontend — all through `/api/v1/` endpoints
- Event propagation stopped on interactive elements inside cards
- Error states shown inline with PF6 `Alert` variant="danger"
- Loading states use PF6 `Spinner`
- All text uses developer domain language (deploy, promote, release, environment)

### Testing Requirements

**Frontend tests (extend existing):**

`EnvironmentCard.test.tsx`:
- NOT_DEPLOYED + releases → Deploy dropdown renders with release versions
- NOT_DEPLOYED + no releases → no Deploy button
- HEALTHY + nextEnvName + nextEnvironmentId → Promote button renders enabled
- HEALTHY + no nextEnvName → no Promote button
- HEALTHY + releases → Deploy dropdown also renders (re-deploy scenario)
- DEPLOYING → no Deploy or Promote buttons
- UNHEALTHY → no Promote button, ArgoCD deep link visible
- Deploy: selecting release calls `triggerDeployment` with correct params
- Promote: clicking calls `triggerDeployment` with `entry.deployedVersion` and `nextEnvironmentId`
- Loading: Spinner shown during API call
- Error: Alert shown on deployment failure
- Button clicks don't toggle card expand/collapse (event propagation)

`EnvironmentChain.test.tsx`:
- `teamId`, `appId`, `releases` passed through to cards
- `nextEnvironmentId` computed correctly from `environments[index + 1]`

**No backend tests** — backend changes (if any) are minimal DTO field additions covered by existing integration tests.

### Previous Story Intelligence

**Story 5.1 (Deploy Release to Environment):**
- Creates `POST /deployments` endpoint: `DeploymentResource`, `DeploymentService`, `DeployRequest`, `DeploymentStatusDto`
- Request body: `{ releaseVersion, environmentId }`
- Response: 201 Created with `{ deploymentId, releaseVersion, environmentName, status: "Deploying", startedAt }`
- Git-based deployment mechanism (commit to values file, ArgoCD auto-sync)
- `require*` ownership validation methods

**Story 5.2 (Deployment Status & History):**
- Adds `environmentId` to `EnvironmentChainEntryDto` and TypeScript type
- Creates `types/deployment.ts`, `api/deployments.ts`, `hooks/useDeployments.ts` for GET history
- Adds `teamId` and `appId` props to `EnvironmentCardProps` for history API calls
- Updates `EnvironmentChain.tsx` to pass teamId/appId to cards

**Story 4.5 (Releases Page):**
- Established `useReleases(teamId, appId)` hook — returns `{ data: ReleaseSummary[], error, isLoading }`
- `ReleaseSummary`: `{ version, createdAt, buildId, commitSha, imageReference }`
- `fetchReleases()` API function in `api/releases.ts`

**Story 2.7/2.8 (ArgoCD + Environment Chain):**
- Established `EnvironmentChain` and `EnvironmentCard` components
- Status mapping: HEALTHY, UNHEALTHY, DEPLOYING, NOT_DEPLOYED, UNKNOWN
- Card expand/collapse interaction with stopPropagation pattern
- Arrow key keyboard navigation between cards

**Epic 4 Retrospective:**
- `require*` naming convention for ownership validation
- Standard Authorization AC template (AC #7 in this story)
- Zero-failure test gate — all tests must pass
- Model selection impacts finding count — use opus-high-thinking recommended

### Data Flow

```
User clicks "Deploy" dropdown on dev card → selects v2.1.1
  → EnvironmentCard.handleDeploy("v2.1.1")
  → triggerDeployment(teamId, appId, { releaseVersion: "v2.1.1", environmentId: 10 })
  → POST /api/v1/teams/{teamId}/applications/{appId}/deployments
    { releaseVersion: "v2.1.1", environmentId: 10 }
  → 201 Created
  → onDeploymentInitiated() → page re-fetches environment chain
  → dev card shows "⟳ Deploying v2.1.1..."

User clicks "Promote to qa" on healthy dev card
  → EnvironmentCard.handlePromote()
  → triggerDeployment(teamId, appId, { releaseVersion: "v2.1.1", environmentId: 20 })
    (v2.1.1 = entry.deployedVersion, 20 = nextEnvironmentId)
  → POST /api/v1/teams/{teamId}/applications/{appId}/deployments
    { releaseVersion: "v2.1.1", environmentId: 20 }
  → 201 Created
  → onDeploymentInitiated() → page re-fetches environment chain
  → qa card shows "⟳ Deploying v2.1.1..."
```

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` § Story 5.3 — Environment Chain Deploy & Promote Actions]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR23 — Deploy a specific release to any environment]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR24 — Promote a release from one environment to the next]
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Environment Chain Card Row] — action states per status
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Progressive action revelation] — "Actions appear only when they're relevant"
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Anti-Patterns] — "Reserve modals for genuinely destructive actions"
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Inline Promotion Confirmation] — popover/modal (Story 5.4)
- [Source: `_bmad-output/planning-artifacts/architecture.md` § REST API Structure] — `/deployments` endpoint
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Frontend Architectural Decisions] — PF6, React Context + hooks, fetch wrapper
- [Source: `_bmad-output/project-context.md` § Deployment Mechanism] — Git-based, POST /deployments
- [Source: `_bmad-output/project-context.md` § Resource Ownership Validation] — require* pattern
- [Source: `_bmad-output/implementation-artifacts/5-1-deploy-release-to-environment.md`] — POST /deployments API shape
- [Source: `_bmad-output/implementation-artifacts/5-2-deployment-status-history.md`] — environmentId addition, deployment types
- [Source: `_bmad-output/implementation-artifacts/epic-4-retro-2026-04-09.md`] — Authorization pattern, require* convention
- [Source: `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx`] — current placeholder buttons
- [Source: `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.tsx`] — current chain component
- [Source: `developer-portal/src/main/webui/src/types/environment.ts`] — EnvironmentChainEntry type
- [Source: `developer-portal/src/main/webui/src/types/release.ts`] — ReleaseSummary type
- [Source: `developer-portal/src/main/webui/src/api/releases.ts`] — fetchReleases()
- [Source: `developer-portal/src/main/webui/src/hooks/useReleases.ts`] — useReleases hook
- [Source: `developer-portal/src/main/webui/src/api/client.ts`] — apiFetch wrapper
- [Source: `developer-portal/src/main/webui/src/hooks/useApiFetch.ts`] — useApiFetch hook pattern
- [Source: `developer-portal/src/main/java/com/portal/auth/PermissionFilter.java`] — deploy/deploy-prod action mapping
- [Source: `developer-portal/src/main/resources/casbin/policy.csv`] — deployments permissions

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### Change Log

### File List
