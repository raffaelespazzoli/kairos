# Story 5.4: Promotion Confirmation & Production Gating

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a team lead,
I want production deployments to require explicit confirmation and be restricted to my role,
so that production changes are intentional and authorized.

## Acceptance Criteria

1. **Non-production promotions show Popover confirmation**
   - **Given** a developer clicks "Promote to [non-production env]" (e.g., QA)
   - **When** the confirmation appears
   - **Then** a PF6 Popover is displayed attached to the promote button
   - **And** it shows: "Promote [version] to [envName]?" with the target namespace and cluster (e.g., "→ orders-orders-api-qa on ocp-qa-01")
   - **And** "Cancel" and "Promote" buttons are available
   - **And** clicking "Promote" initiates the deployment
   - **And** clicking "Cancel" or clicking outside dismisses the popover

2. **Production promotions show Modal confirmation (warning variant)**
   - **Given** a developer clicks "Promote to Prod" (or Deploy on a production environment)
   - **When** the confirmation appears
   - **Then** a PF6 Modal (warning variant) is displayed
   - **And** title shows: "Deploy to PRODUCTION"
   - **And** body shows: Version, Target namespace, Target cluster
   - **And** "This will deploy to production." text is displayed
   - **And** "Cancel" and "Deploy to Prod" (danger variant) buttons are available

3. **Production modal accessibility**
   - **Given** the production confirmation modal is displayed
   - **When** reviewing accessibility
   - **Then** focus is trapped within the modal
   - **And** pressing Escape dismisses the modal
   - **And** focus returns to the promote button after dismissal

4. **Member role sees disabled production promote button with tooltip**
   - **Given** a developer with "member" role views a production environment card
   - **When** the card shows a healthy deployment in the previous environment
   - **Then** the "Promote to Prod" button is visible but disabled
   - **And** a tooltip on hover explains: "Production deployments require team lead approval"

5. **Lead role can promote to production**
   - **Given** a developer with "lead" role views the same production environment card
   - **When** the card shows a healthy deployment in the previous environment
   - **Then** the "Promote to Prod" button is visible and enabled
   - **And** clicking it opens the production Modal confirmation

6. **Backend rejects member production deployments with 403**
   - **Given** a developer with "member" role attempts to call the deployment API for a production environment
   - **When** the request includes `?env=prod` query parameter
   - **Then** the Casbin PermissionFilter maps to `deploy-prod` action
   - **And** the request is rejected with 403 Forbidden
   - **And** production authorization is enforced server-side, not just via frontend button state

7. **Defense-in-depth: DeploymentService validates production flag**
   - **Given** a deployment request targets an environment with `isProduction = true`
   - **When** the DeploymentService processes the request
   - **Then** it checks that `teamContext.getRole()` is `lead` or `admin`
   - **And** if the role is `member`, it throws `PortalAuthorizationException` (403)
   - **And** this is a defense-in-depth check — the PermissionFilter should have already blocked it

8. **Environment entity carries isProduction flag**
   - **Given** environments are stored in the database
   - **When** an environment's `is_production` flag is set to `true`
   - **Then** the flag propagates to `EnvironmentChainEntryDto.isProduction`
   - **And** the frontend receives `isProduction: true` in the environment chain response
   - **And** both frontend and backend use this flag to determine production gating behavior

9. **Frontend sends ?env=prod for production deployments**
   - **Given** the frontend triggers a deployment to a production environment
   - **When** calling `POST /api/v1/teams/{teamId}/applications/{appId}/deployments`
   - **Then** the URL includes `?env=prod` query parameter
   - **And** the PermissionFilter maps to `deploy-prod` action for Casbin enforcement

10. **Deploy dropdown on production environments also requires confirmation**
    - **Given** a production environment card shows the Deploy dropdown (status HEALTHY or NOT_DEPLOYED)
    - **When** a lead-role user selects a release from the dropdown
    - **Then** the production Modal confirmation is shown before executing the deployment
    - **And** a member-role user cannot deploy to production (dropdown not shown or disabled)

11. **Resource ownership validation (mandatory scoping pattern)**
    - **Given** a request targets a resource outside the caller's team scope
    - **Then** 404 is returned (never 403)
    - **Given** a request targets an environment that does not belong to the application
    - **Then** 404 is returned

## Tasks / Subtasks

- [x] Task 1: Add `is_production` column to environments table (AC: #8)
  - [x] Create `V6__add_is_production_to_environments.sql` in `src/main/resources/db/migration/`
  - [x] `ALTER TABLE environments ADD COLUMN is_production BOOLEAN NOT NULL DEFAULT FALSE;`
  - [x] Convention: the last environment in the promotion chain is typically production, but this is explicitly set per-environment rather than inferred

- [x] Task 2: Add `isProduction` field to Environment entity (AC: #8)
  - [x] Add `public Boolean isProduction;` field to `com.portal.environment.Environment`
  - [x] Map to `is_production` column — requires explicit `@Column(name = "is_production")` annotation (Hibernate naming strategy in this project does not auto-convert)

- [x] Task 3: Add `isProduction` to EnvironmentChainEntryDto and EnvironmentMapper (AC: #8)
  - [x] Add `boolean isProduction` field to `EnvironmentChainEntryDto` record (at end of constructor)
  - [x] Update `EnvironmentMapper.merge()` to include `environment.isProduction` when building the DTO
  - [x] Ensure the existing `EnvironmentChainEntryDto` constructor call sites are updated with the new parameter

- [x] Task 4: Add `isProduction` to frontend EnvironmentChainEntry type (AC: #8)
  - [x] Add `isProduction: boolean` to `EnvironmentChainEntry` interface in `types/environment.ts`

- [x] Task 5: Modify triggerDeployment API to append ?env=prod (AC: #9)
  - [x] Update `triggerDeployment()` in `api/deployments.ts` to accept an optional `isProduction: boolean` parameter
  - [x] When `isProduction` is true, append `?env=prod` to the POST URL
  - [x] Non-production deployments do not include the query parameter

- [x] Task 6: Add defense-in-depth production check in DeploymentService (AC: #7)
  - [x] In `DeploymentService.deployRelease()`, after `requireApplicationEnvironment()`, check `env.isProduction`
  - [x] If `env.isProduction` is true, verify `teamContext.getRole()` is `"lead"` or `"admin"`
  - [x] If role is `"member"`, throw `PortalAuthorizationException` with message: "Production deployments require team lead role"
  - [x] This is defense-in-depth — PermissionFilter already blocks via Casbin, but the service validates independently

- [x] Task 7: Create PromotionConfirmation component (AC: #1, #2, #3)
  - [x] Create `src/main/webui/src/components/environment/PromotionConfirmation.tsx`
  - [x] Props interface:
    - [x] `version: string` — release version being deployed
    - [x] `targetEnvName: string` — target environment name
    - [x] `targetNamespace: string` — target namespace
    - [x] `targetCluster: string | null` — target cluster name
    - [x] `isProduction: boolean` — determines popover vs modal
    - [x] `isOpen: boolean` — controls visibility
    - [x] `onConfirm: () => void` — called when user confirms
    - [x] `onCancel: () => void` — called when user cancels
    - [x] `triggerRef: React.RefObject<HTMLButtonElement>` — reference to the trigger button (for popover positioning and focus return)
  - [x] **Non-production variant:** PF6 `Popover` attached to the promote button
    - [x] Header: "Promote [version] to [envName]?"
    - [x] Body: "→ [namespace] on [cluster]"
    - [x] Footer: "Cancel" (link variant) and "Promote" (primary variant) buttons
    - [x] `shouldClose` prop set to return true (allows clicking outside to dismiss)
  - [x] **Production variant:** PF6 `Modal` with `variant="warning"`
    - [x] Title: "Deploy to PRODUCTION"
    - [x] Body: PF6 `DescriptionList` with Version, Target, Cluster entries
    - [x] Body text: "This will deploy to production."
    - [x] Actions: "Cancel" (link variant) and "Deploy to Prod" (danger variant)
    - [x] `onEscapePress` dismisses modal
    - [x] Focus trapped within modal (PF6 Modal does this automatically)
    - [x] On close, focus returns to trigger button via `triggerRef.current?.focus()`

- [x] Task 8: Integrate confirmation into EnvironmentCard promote flow (AC: #1, #2, #4, #5)
  - [x] Add `isProduction` prop to `EnvironmentCardProps` (passed from `EnvironmentChain`)
  - [x] Add `role` to component state (from `useAuth()` hook)
  - [x] Add `showConfirmation` boolean state
  - [x] Add `pendingAction` state: `{ type: 'deploy' | 'promote', version: string } | null`
  - [x] Add `promoteButtonRef` using `useRef<HTMLButtonElement>(null)`
  - [x] **Promote button changes:**
    - [x] When target env (next env) is production AND role is `member`: button is disabled, wrapped in PF6 `Tooltip` with "Production deployments require team lead approval"
    - [x] When target env (next env) is production AND role is `lead` or `admin`: button is enabled, onClick sets `showConfirmation=true` and `pendingAction`
    - [x] When target env is NOT production: onClick sets `showConfirmation=true` and `pendingAction`
  - [x] **Deploy dropdown changes:**
    - [x] When current env is production AND role is `member`: deploy dropdown hidden
    - [x] When current env is production AND role is `lead` or `admin`: selecting a release sets `showConfirmation=true` and `pendingAction`
    - [x] When current env is NOT production: selecting a release sets `showConfirmation=true` and `pendingAction`
  - [x] **Confirmation flow:**
    - [x] Render `PromotionConfirmation` with current pending action state
    - [x] `isProduction` passed based on target environment's production flag
    - [x] `onConfirm` calls `triggerDeployment()` with `isProduction` flag, then clears state and calls `onDeploymentInitiated`
    - [x] `onCancel` clears `showConfirmation` and `pendingAction`

- [x] Task 9: Pass isProduction context through EnvironmentChain (AC: #8, #10)
  - [x] EnvironmentChain already passes `nextEnvName` — additionally compute and pass `nextIsProduction: boolean` for the next environment
  - [x] Pass `isProduction: entry.isProduction` to each EnvironmentCard for the current card's production status
  - [x] Pass `nextIsProduction: environments[index + 1]?.isProduction ?? false` for the target of promotion

- [x] Task 10: Update DevGitProvider/dev-mode seeding for production flag (AC: #8)
  - [x] If dev-mode data seeding exists, set the last environment in each chain to `is_production = true`
  - [x] Update any `import.sql` or `DevDataSeeder` to include production flag

- [x] Task 11: Write backend tests (AC: #6, #7, #8, #11)
  - [x] `DeploymentServiceProdGatingTest.java` (`@QuarkusTest` + `@InjectMock`):
    - [x] Member deploys to production env → `PortalAuthorizationException` (defense-in-depth)
    - [x] Lead deploys to production env → succeeds
    - [x] Admin deploys to production env → succeeds
    - [x] Member deploys to non-production env → succeeds (no production gating)
    - [x] Lead deploys to non-production env → succeeds
  - [x] `PermissionFilterTest.java` (extended):
    - [x] POST /deployments?env=prod with member role → 403 (pre-existing)
    - [x] POST /deployments?env=prod with lead role → allowed (pre-existing)
    - [x] POST /deployments?env=prod with admin role → allowed (new)
    - [x] POST /deployments (no ?env=prod) with member role → allowed (pre-existing)
  - [x] EnvironmentChainEntryDto isProduction verified via EnvironmentMapper.merge() in existing EnvironmentServiceTest
  - [x] Flyway migration:
    - [x] Verify migration applies cleanly (run `mvn test` — QuarkusTest auto-runs Flyway)

- [x] Task 12: Write frontend tests (AC: #1, #2, #3, #4, #5, #9, #10)
  - [x] `PromotionConfirmation.test.tsx` (new):
    - [x] Non-production: renders Popover with correct version, namespace, cluster
    - [x] Non-production: "Promote" button calls onConfirm
    - [x] Non-production: "Cancel" button calls onCancel
    - [x] Production: renders Modal with warning variant
    - [x] Production: title is "Deploy to PRODUCTION"
    - [x] Production: body shows version, target, cluster
    - [x] Production: "Deploy to Prod" button has danger variant and calls onConfirm
    - [x] Production: Escape key dismisses modal
    - [x] Production: focus returns to trigger button on dismiss
  - [x] `EnvironmentCard.test.tsx` (extend existing):
    - [x] Production env + member role → promote button disabled with tooltip
    - [x] Production env + lead role → promote button enabled
    - [x] Production env + member role → deploy dropdown hidden
    - [x] Production env + lead role → deploy dropdown shown, selecting release shows Modal confirmation
    - [x] Non-prod env → promote button shows Popover confirmation
    - [x] Confirming popover triggers deployment API call
    - [x] Confirming modal triggers deployment API call with ?env=prod
    - [x] Canceling confirmation does not trigger deployment
  - [x] `EnvironmentChain.test.tsx` (extend existing):
    - [x] Verify `isProduction` and `nextIsProduction` passed through to cards

### Review Findings

- [x] [Review][Patch] Disabled production approval tooltip will not fire on a disabled button — fixed: wrapped disabled Button in focusable `<span>` so Tooltip events fire
- [x] [Review][Patch] Non-production deploy confirmations are mislabeled as "Promote", even for Deploy dropdown actions — fixed: added `actionType` prop; uses "Deploy" vs "Promote" wording accordingly
- [x] [Review][Patch] Production confirmation omits the Cluster row entirely when cluster metadata is unavailable — fixed: always renders Cluster row, shows "Unknown" when null

## Dev Notes

### Prerequisites: Stories 5.1, 5.2, and 5.3 Must Be Implemented First

This story layers confirmation dialogs and production gating on top of the functional deploy/promote buttons from Story 5.3. The `POST /deployments` endpoint (5.1), deployment history (5.2), and working deploy/promote UI (5.3) must all exist. **Do not start 5.4 until 5.1–5.3 are implemented and merged.**

### All File Paths Are Relative to `developer-portal/`

Every backend path (e.g., `src/main/java/com/portal/...`) and frontend path (e.g., `src/main/webui/src/...`) is relative to the `developer-portal/` directory at the repository root.

### Two-Layer Production Gating: PermissionFilter + DeploymentService

Production deployment authorization works at two layers:

**Layer 1 — PermissionFilter (HTTP layer):**
The `PermissionFilter` already handles `deploy-prod` detection. When `POST /api/v1/teams/{teamId}/applications/{appId}/deployments?env=prod` is called:
1. `mapAction()` returns `"deploy"` for POST on `deployments` resource
2. `filter()` checks query param `env=prod` → overrides action to `"deploy-prod"`
3. Casbin evaluates: `member, deployments, deploy-prod` → **DENIED** (403)
4. Casbin evaluates: `lead, deployments, deploy-prod` → ALLOWED (inherits nothing extra, explicit policy)
5. Casbin evaluates: `admin, deployments, deploy-prod` → ALLOWED (inherits from lead)

No changes needed to `PermissionFilter` — the `?env=prod` query parameter mechanism is already implemented.

**Layer 2 — DeploymentService (service layer, defense-in-depth):**
Even if a caller bypasses the `?env=prod` parameter (e.g., calls without it for a production env), the service should catch it:

```java
Environment env = requireApplicationEnvironment(appId, request.environmentId());
if (Boolean.TRUE.equals(env.isProduction)) {
    String role = teamContext.getRole();
    if (!"lead".equals(role) && !"admin".equals(role)) {
        throw new PortalAuthorizationException(role, "deployments", "deploy-prod");
    }
}
```

### Frontend Role Awareness

The `useAuth()` hook (MVP stub) exposes `role: 'member' | 'lead' | 'admin'`. In dev mode, role is switchable via `?role=lead` or `?role=admin` URL parameter (persisted in `sessionStorage`).

The frontend uses the role to:
- Disable the "Promote to Prod" button for `member` role
- Hide the deploy dropdown on production environments for `member` role
- Show appropriate confirmation (Popover vs Modal) based on `isProduction` flag

**The frontend role check is UX-only** — the backend enforces authorization independently via PermissionFilter + DeploymentService.

### Popover vs Modal — UX Design Decision

From the UX design specification:

| Target Environment | Confirmation Type | Rationale |
|---|---|---|
| Non-production (dev, QA, staging) | PF6 Popover | Lightweight, attached to button, easy dismiss — routine action |
| Production | PF6 Modal (warning variant) | Heavier, focus-trapped, explicit confirmation — high-stakes action |

**UX anti-pattern (from spec):** "Reserve modals for genuinely destructive or irreversible actions (production deployment). Routine actions (trigger dev build, promote to QA) should be fast."

### Popover Implementation — PF6 API

```tsx
import { Popover, Button } from '@patternfly/react-core';

<Popover
  headerContent="Promote v1.4.2 to QA?"
  bodyContent="→ orders-orders-api-qa on ocp-qa-01"
  footerContent={
    <>
      <Button variant="link" onClick={onCancel}>Cancel</Button>
      <Button variant="primary" onClick={onConfirm}>Promote</Button>
    </>
  }
  isVisible={isOpen}
  shouldClose={() => { onCancel(); return true; }}
  triggerRef={triggerRef}
/>
```

### Modal Implementation — PF6 API

```tsx
import { Modal, ModalVariant, Button, DescriptionList, DescriptionListGroup, DescriptionListTerm, DescriptionListDescription } from '@patternfly/react-core';

<Modal
  variant={ModalVariant.small}
  title="Deploy to PRODUCTION"
  titleIconVariant="warning"
  isOpen={isOpen}
  onClose={onCancel}
  actions={[
    <Button key="cancel" variant="link" onClick={onCancel}>Cancel</Button>,
    <Button key="confirm" variant="danger" onClick={onConfirm}>Deploy to Prod</Button>,
  ]}
>
  <DescriptionList isHorizontal>
    <DescriptionListGroup>
      <DescriptionListTerm>Version</DescriptionListTerm>
      <DescriptionListDescription>{version}</DescriptionListDescription>
    </DescriptionListGroup>
    <DescriptionListGroup>
      <DescriptionListTerm>Target</DescriptionListTerm>
      <DescriptionListDescription>{namespace}</DescriptionListDescription>
    </DescriptionListGroup>
    <DescriptionListGroup>
      <DescriptionListTerm>Cluster</DescriptionListTerm>
      <DescriptionListDescription>{cluster}</DescriptionListDescription>
    </DescriptionListGroup>
  </DescriptionList>
  <p className="pf-v6-u-mt-md">This will deploy to production.</p>
</Modal>
```

### Confirmation Flow — State Machine

```
User clicks "Promote to QA" (non-prod)
  → EnvironmentCard sets showConfirmation=true, pendingAction={ type:'promote', version:'v1.4.2' }
  → PromotionConfirmation renders Popover (isProduction=false)
  → User clicks "Promote"
  → onConfirm() → triggerDeployment(teamId, appId, { releaseVersion, environmentId })
  → Clear state → onDeploymentInitiated()

User clicks "Promote to Prod" (lead role)
  → EnvironmentCard sets showConfirmation=true, pendingAction={ type:'promote', version:'v1.4.2' }
  → PromotionConfirmation renders Modal (isProduction=true)
  → User clicks "Deploy to Prod"
  → onConfirm() → triggerDeployment(teamId, appId, { releaseVersion, environmentId }, isProduction=true)
  → triggerDeployment appends ?env=prod → POST /deployments?env=prod
  → Clear state → onDeploymentInitiated()

User clicks "Promote to Prod" (member role)
  → Button is disabled → Tooltip: "Production deployments require team lead approval"
  → No action
```

### Deploy Dropdown on Production Environments

For production environments:
- **Member role:** Deploy dropdown is NOT rendered (hidden, not just disabled)
- **Lead/admin role:** Deploy dropdown is rendered, but selecting a release triggers the Modal confirmation before executing

For non-production environments:
- All roles: Deploy dropdown is rendered, selecting a release triggers the Popover confirmation

### Action Button States Per Environment Status (Updated from 5.3)

| Status | Role | Deploy Dropdown | Promote Button |
|---|---|---|---|
| NOT_DEPLOYED (non-prod, releases exist) | Any | Shown → Popover | Hidden |
| NOT_DEPLOYED (prod, releases exist) | member | Hidden | Hidden |
| NOT_DEPLOYED (prod, releases exist) | lead+ | Shown → Modal | Hidden |
| HEALTHY (non-prod, not last, has nextEnv) | Any | Shown → Popover | Shown → Popover |
| HEALTHY (non-prod, last env) | Any | Shown → Popover | Hidden |
| HEALTHY (prod) | member | Hidden | Hidden (last env) |
| HEALTHY (prod) | lead+ | Shown → Modal | Hidden (last env — prod is typically last) |
| HEALTHY (non-prod, next is prod) | member | Shown → Popover | Shown but **disabled** + Tooltip |
| HEALTHY (non-prod, next is prod) | lead+ | Shown → Popover | Shown → Modal |
| DEPLOYING | Any | Hidden | Hidden |
| UNHEALTHY | Any | Hidden | Hidden |

### The `is_production` Flag — Setting It

The `is_production` flag is a persistent property of the environment, set during application onboarding (Epic 2). For MVP, the flag must be populated via:
1. The Flyway migration defaults all existing environments to `FALSE`
2. Dev data seeding should set the last environment (highest `promotion_order`) to `TRUE`
3. Future: the onboarding wizard could let users mark which environments are production

For the purpose of this story's implementation, ensure that dev data has at least one production environment so the UI can be tested.

### PermissionFilter — No Changes Needed

The `PermissionFilter` already:
1. Detects `?env=prod` query parameter on POST to `deployments`
2. Overrides action from `deploy` to `deploy-prod`
3. Casbin enforces: `member` → DENIED, `lead`/`admin` → ALLOWED

The comment in the code says: "the service layer should additionally verify the target environment's is_production flag as a defense-in-depth check." This story implements that defense-in-depth check in `DeploymentService`.

### Project Structure Notes

```
com.portal.environment/
├── Environment.java                  # MODIFIED — add isProduction field
├── EnvironmentChainEntryDto.java     # MODIFIED — add isProduction parameter
├── EnvironmentMapper.java            # MODIFIED — pass isProduction

com.portal.deployment/
├── DeploymentService.java            # MODIFIED — add production gating defense-in-depth

src/main/resources/db/migration/
├── V6__add_is_production_to_environments.sql  # NEW

src/main/webui/src/
├── types/
│   └── environment.ts                # MODIFIED — add isProduction
├── api/
│   └── deployments.ts                # MODIFIED — accept isProduction param
├── components/environment/
│   ├── PromotionConfirmation.tsx      # NEW — Popover + Modal confirmation
│   ├── EnvironmentCard.tsx            # MODIFIED — confirmation integration + role gating
│   └── EnvironmentChain.tsx           # MODIFIED — pass isProduction context
```

### File Structure Requirements

**New backend files:**

```
src/main/resources/db/migration/V6__add_is_production_to_environments.sql
```

**Modified backend files:**

```
src/main/java/com/portal/environment/Environment.java                     (add isProduction)
src/main/java/com/portal/environment/EnvironmentChainEntryDto.java        (add isProduction)
src/main/java/com/portal/environment/EnvironmentMapper.java               (pass isProduction)
src/main/java/com/portal/deployment/DeploymentService.java                (production gating check)
```

**New frontend files:**

```
src/main/webui/src/components/environment/PromotionConfirmation.tsx
```

**Modified frontend files:**

```
src/main/webui/src/types/environment.ts                                   (add isProduction)
src/main/webui/src/api/deployments.ts                                     (add ?env=prod param)
src/main/webui/src/components/environment/EnvironmentCard.tsx              (confirmation + role gating)
src/main/webui/src/components/environment/EnvironmentChain.tsx             (pass isProduction/nextIsProduction)
```

**New test files:**

```
src/test/java/com/portal/deployment/DeploymentServiceProdGatingTest.java
src/main/webui/src/components/environment/PromotionConfirmation.test.tsx
```

**Modified test files:**

```
src/test/java/com/portal/auth/PermissionFilterTest.java                   (extend with prod query tests)
src/main/webui/src/components/environment/EnvironmentCard.test.tsx         (extend with prod gating)
src/main/webui/src/components/environment/EnvironmentChain.test.tsx        (extend with isProduction)
```

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `PermissionFilter.java` | `com.portal.auth` | EXISTS — already handles `?env=prod` → `deploy-prod` mapping |
| `CasbinEnforcer.java` | `com.portal.auth` | EXISTS — enforces role-based access |
| `TeamContext.java` | `com.portal.auth` | EXISTS — `getRole()` returns member/lead/admin |
| `PortalAuthorizationException.java` | `com.portal.auth` | EXISTS — thrown for 403 responses |
| `policy.csv` | `resources/casbin/` | EXISTS — `p, lead, deployments, deploy-prod` already defined |
| `model.conf` | `resources/casbin/` | EXISTS — RBAC model with role inheritance |
| `Environment.java` | `com.portal.environment` | EXISTS — add `isProduction` field |
| `EnvironmentChainEntryDto.java` | `com.portal.environment` | EXISTS — add `isProduction` param |
| `EnvironmentMapper.java` | `com.portal.environment` | EXISTS — update to pass `isProduction` |
| `EnvironmentCard.tsx` | `components/environment/` | EXISTS — add confirmation + role gating |
| `EnvironmentChain.tsx` | `components/environment/` | EXISTS — pass isProduction context |
| `EnvironmentCard.test.tsx` | `components/environment/` | EXISTS — extend |
| `EnvironmentChain.test.tsx` | `components/environment/` | EXISTS — extend |
| `useAuth.ts` | `hooks/` | EXISTS — `role` exposed, `?role=lead` dev override |
| `DeploymentService.java` | `com.portal.deployment` | FROM 5.1 — add production check |
| `DeploymentResource.java` | `com.portal.deployment` | FROM 5.1 — no changes |
| POST `/deployments` endpoint | `com.portal.deployment` | FROM 5.1 — no changes |
| `triggerDeployment()` | `api/deployments.ts` | FROM 5.3 — modify to accept isProduction |
| `DeepLinkButton.tsx` | `components/shared/` | EXISTS — used for ArgoCD links |
| `apiFetch()` | `api/client.ts` | EXISTS — typed fetch wrapper |

### What NOT to Build

- **No new REST endpoints** — uses existing POST /deployments from 5.1
- **No new Casbin policy entries** — `deploy-prod` already exists
- **No PermissionFilter changes** — `?env=prod` detection already implemented
- **No new backend service classes** — modify existing DeploymentService
- **No database table for deployment records** — Git is the ledger (5.1 design)
- **No role management UI** — roles come from JWT via OIDC
- **No rollback functionality** — out of scope
- **No confirmation for the Deploy dropdown on non-prod with NOT_DEPLOYED status** — Popover is only for promote and deploy-to-prod actions. Actually, ALL deployments get confirmation per the UX spec. Non-prod deployments get Popover, prod deployments get Modal.

### Anti-Patterns to Avoid

- **DO NOT** rely solely on frontend role checks — backend MUST enforce production gating independently
- **DO NOT** return 403 for a member trying to deploy to prod via the *frontend* — the button is disabled with a tooltip. The 403 is backend-only defense-in-depth
- **DO NOT** use PF6 `Select` for the confirmation — use `Popover` (non-prod) and `Modal` (prod)
- **DO NOT** add `env=prod` query parameter for non-production deployments
- **DO NOT** modify the Casbin policy file — existing policies are sufficient
- **DO NOT** create a new `isProduction()` method that infers production from the environment name — use the explicit `is_production` database flag
- **DO NOT** add any custom CSS — use PF6 components and tokens exclusively
- **DO NOT** use Spring annotations — use Quarkus/CDI annotations
- **DO NOT** import entities from other packages — use IDs and `findById()` for cross-domain lookups
- **DO NOT** use `@ts-ignore` — type everything properly

### Architecture Compliance

- Two-layer authorization: PermissionFilter (Casbin) + DeploymentService (defense-in-depth)
- `require*` ownership validation methods in DeploymentService
- Team-scoped access: 404 for cross-team or missing resources
- Frontend role check is UX-only — backend enforces independently (NFR7, FR38)
- PF6 components used exclusively: `Popover`, `Modal`, `Button`, `Tooltip`, `DescriptionList`
- No direct platform system calls from frontend
- Error states shown inline with PF6 `Alert` variant="danger"
- Event propagation stopped on interactive elements inside cards
- All text uses developer domain language

### Testing Requirements

**Backend tests:**

`DeploymentServiceProdGatingTest.java` (`@QuarkusTest` + `@InjectMock`):
- Member role deploys to production env (`isProduction=true`) → `PortalAuthorizationException` (403)
- Lead role deploys to production env → succeeds (deployment created)
- Admin role deploys to production env → succeeds
- Member role deploys to non-production env → succeeds (no gating)
- Lead role deploys to non-production env → succeeds

`PermissionFilterTest.java` (extend existing):
- POST /deployments?env=prod with member role → Casbin denies → 403
- POST /deployments?env=prod with lead role → Casbin allows
- POST /deployments (no ?env=prod) with member role → Casbin allows (deploy action)

**Frontend tests:**

`PromotionConfirmation.test.tsx`:
- Non-production: Popover rendered with header, body, footer buttons
- Non-production: "Promote" triggers onConfirm, "Cancel" triggers onCancel
- Production: Modal rendered with warning variant, title, body, actions
- Production: "Deploy to Prod" has danger variant
- Production: Escape key dismisses
- Production: focus returns to trigger ref on dismiss

`EnvironmentCard.test.tsx` (extend):
- Production env + member → promote button disabled with tooltip text
- Production env + lead → promote button enabled, click opens Modal
- Production env + member → deploy dropdown hidden
- Production env + lead → deploy dropdown shown, selection opens Modal
- Non-prod env → promote click opens Popover, confirm triggers deploy
- Non-prod env → deploy dropdown selection opens Popover, confirm triggers deploy
- Confirm popover → `triggerDeployment` called without `?env=prod`
- Confirm modal → `triggerDeployment` called with `isProduction=true` (appends `?env=prod`)
- Cancel confirmation → no API call made

`EnvironmentChain.test.tsx` (extend):
- `isProduction` passed through from entry to card
- `nextIsProduction` computed from next environment in array

### Previous Story Intelligence

**Story 5.1 (Deploy Release to Environment):**
- Creates `POST /deployments` endpoint, `DeploymentService`, `DeploymentResource`, `DeployRequest`
- Git-based deployment mechanism (commit to values file)
- `require*` ownership validation methods
- Note: "No production gating — that's Story 5.4 (Casbin `deploy-prod` gate)"

**Story 5.2 (Deployment Status & History):**
- Adds `environmentId` to `EnvironmentChainEntryDto` and TypeScript type
- Creates deployment history API and UI
- `EnvironmentMapper.merge()` is the method to update for new DTO fields

**Story 5.3 (Environment Chain Deploy & Promote Actions):**
- Creates functional deploy/promote buttons on EnvironmentCard
- `triggerDeployment()` in `api/deployments.ts` — needs `isProduction` parameter
- Passes `teamId`, `appId`, `releases`, `nextEnvironmentId`, `onDeploymentInitiated` through chain
- Note: "No promotion confirmation dialogs — Story 5.4 adds popover for non-prod, modal for prod"
- Note: "No production gating — Story 5.4 adds lead-only enforcement for prod deploys"
- Placeholder note removed: Story 5.3 replaces disabled placeholders with working buttons

**Story 1.3 (Casbin RBAC Authorization Layer):**
- Established PermissionFilter with `?env=prod` query param detection
- Created `policy.csv` with `p, lead, deployments, deploy-prod`
- `PortalAuthorizationException` thrown for 403 responses
- Comment in PermissionFilter: "service layer should additionally verify the target environment's is_production flag as a defense-in-depth check"
- Production deployment detection currently uses `env=prod` query parameter

**Story 2.7/2.8 (ArgoCD + Environment Chain):**
- Established `EnvironmentChain` and `EnvironmentCard` components
- `EnvironmentMapper.merge()` builds `EnvironmentChainEntryDto` from environment + ArgoCD status
- Card expand/collapse with stopPropagation pattern

**Epic 4 Retrospective:**
- `require*` naming convention for ownership validation
- Standard Authorization AC template (AC #11 in this story)
- Zero-failure test gate — all tests must pass
- Model selection impacts finding count — opus-high-thinking recommended

### Data Flow

```
Lead role user clicks "Promote to Prod" on QA card (HEALTHY)
  → EnvironmentCard detects nextIsProduction=true, role=lead → button enabled
  → onClick: showConfirmation=true, pendingAction={ type:'promote', version:'v1.4.2' }
  → PromotionConfirmation renders Modal (isProduction=true):
      "Deploy to PRODUCTION"
      Version: v1.4.2
      Target: orders-orders-api-prod
      Cluster: ocp-prod-01
      "This will deploy to production."
      [Cancel] [Deploy to Prod (danger)]
  → User clicks "Deploy to Prod"
  → onConfirm() → triggerDeployment(teamId, appId,
      { releaseVersion: "v1.4.2", environmentId: 30 }, isProduction=true)
  → POST /api/v1/teams/1/applications/2/deployments?env=prod
      { releaseVersion: "v1.4.2", environmentId: 30 }
  → PermissionFilter: role=lead, resource=deployments, action=deploy-prod → ALLOWED
  → DeploymentService.deployRelease():
      requireTeamApplication(1, 2) → ok
      requireApplicationEnvironment(2, 30) → env (isProduction=true)
      teamContext.getRole() == "lead" → ok (defense-in-depth passes)
      Git commit: deploy: v1.4.2 to prod\n\nDeployed-By: developer
  → 201 Created
  → onDeploymentInitiated() → chain re-fetches
  → Prod card shows "⟳ Deploying v1.4.2..."

Member role user views same QA card:
  → EnvironmentCard detects nextIsProduction=true, role=member
  → "Promote to Prod" button rendered DISABLED
  → Tooltip: "Production deployments require team lead approval"
  → Deploy dropdown on prod env: NOT rendered (member + production)

Member role attempts direct API call (bypassing frontend):
  POST /api/v1/teams/1/applications/2/deployments?env=prod
  → PermissionFilter: role=member, action=deploy-prod → DENIED → 403

Member role omits ?env=prod (trying to bypass):
  POST /api/v1/teams/1/applications/2/deployments
  → PermissionFilter: role=member, action=deploy → ALLOWED
  → DeploymentService: env.isProduction=true, role=member → PortalAuthorizationException → 403
  (defense-in-depth catches the bypass attempt)
```

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` § Story 5.4 — Promotion Confirmation & Production Gating]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR38 — Production deployments restricted to team leads]
- [Source: `_bmad-output/planning-artifacts/prd.md` § NFR Security — Production deployment authorization enforced server-side]
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Authorization Enforcement] — three-tier model
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Casbin RBAC model] — role hierarchy, deploy-prod action
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Inline Promotion Confirmation] — Popover + Modal specs
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Anti-Patterns] — "Reserve modals for genuinely destructive actions"
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Journey 2 Authorization model] — member vs lead for production
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Environment Chain Card Row] — action states per status
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Component Catalog] — Modal for prod, Popover for non-prod
- [Source: `_bmad-output/project-context.md` § Security Rules] — Production deployment authorization enforced server-side
- [Source: `_bmad-output/project-context.md` § Resource Ownership Validation] — require* pattern
- [Source: `_bmad-output/project-context.md` § Deployment Mechanism] — Git-based, production gating via Casbin
- [Source: `_bmad-output/implementation-artifacts/1-3-casbin-rbac-authorization-layer.md`] — PermissionFilter, policy.csv, deploy-prod
- [Source: `_bmad-output/implementation-artifacts/5-1-deploy-release-to-environment.md`] — DeploymentService, POST /deployments
- [Source: `_bmad-output/implementation-artifacts/5-2-deployment-status-history.md`] — EnvironmentChainEntryDto extensions
- [Source: `_bmad-output/implementation-artifacts/5-3-environment-chain-deploy-promote-actions.md`] — Deploy/promote buttons, triggerDeployment
- [Source: `_bmad-output/implementation-artifacts/epic-4-retro-2026-04-09.md`] — Authorization pattern, require* convention
- [Source: `developer-portal/src/main/java/com/portal/auth/PermissionFilter.java`] — ?env=prod detection
- [Source: `developer-portal/src/main/java/com/portal/auth/TeamContext.java`] — getRole()
- [Source: `developer-portal/src/main/java/com/portal/auth/PortalAuthorizationException.java`] — 403 exception
- [Source: `developer-portal/src/main/resources/casbin/policy.csv`] — deploy-prod policy
- [Source: `developer-portal/src/main/java/com/portal/environment/Environment.java`] — entity (needs isProduction)
- [Source: `developer-portal/src/main/java/com/portal/environment/EnvironmentChainEntryDto.java`] — DTO (needs isProduction)
- [Source: `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx`] — current UI
- [Source: `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.tsx`] — current chain
- [Source: `developer-portal/src/main/webui/src/types/environment.ts`] — EnvironmentChainEntry type
- [Source: `developer-portal/src/main/webui/src/hooks/useAuth.ts`] — role exposure
- [Source: `developer-portal/src/main/webui/src/api/deployments.ts`] — triggerDeployment (FROM 5.3)
- [Source: `developer-portal/src/main/resources/db/migration/V4__create_environments.sql`] — current schema

## Dev Agent Record

### Agent Model Used

Claude Opus 4 (Cursor Agent Mode)

### Debug Log References

- Hibernate naming strategy does not auto-convert camelCase to snake_case in this project — all existing fields use explicit `@Column(name=...)` annotations. Added `@Column(name = "is_production", nullable = false)` to the Environment entity.
- Entity field `isProduction` must default to `false` in Java (`= false`) to avoid NOT NULL constraint violations when existing tests create Environment objects without setting the flag.
- Four pre-existing tests in EnvironmentCard.test.tsx needed updating to go through the new confirmation flow (promote/deploy clicks now show confirmation before triggering API call).

### Completion Notes List

- Implemented two-layer production gating: PermissionFilter (Casbin `?env=prod`) + DeploymentService defense-in-depth check
- Created PromotionConfirmation component with dual UX: PF6 Popover for non-prod, PF6 Modal (warning) for production
- EnvironmentCard now integrates role-based gating: member sees disabled promote button with tooltip for prod targets, hidden deploy dropdown for prod environments
- Frontend `triggerDeployment()` conditionally appends `?env=prod` query parameter
- All 471 backend tests pass (including 5 new prod gating tests, 1 new PermissionFilter admin test)
- All 310 frontend tests pass (including 9 new PromotionConfirmation tests, 8 new EnvironmentCard production gating tests, 3 new EnvironmentChain isProduction tests)
- Dev seed data updated to mark "prod" environments with `isProduction=true`

### Change Log

- 2026-04-10: Story 5.4 implemented — promotion confirmation dialogs and production deployment gating

### File List

**New files:**
- `developer-portal/src/main/resources/db/migration/V6__add_is_production_to_environments.sql`
- `developer-portal/src/main/webui/src/components/environment/PromotionConfirmation.tsx`
- `developer-portal/src/main/webui/src/components/environment/PromotionConfirmation.test.tsx`
- `developer-portal/src/test/java/com/portal/deployment/DeploymentServiceProdGatingTest.java`

**Modified files:**
- `developer-portal/src/main/java/com/portal/environment/Environment.java` (added isProduction field)
- `developer-portal/src/main/java/com/portal/environment/EnvironmentChainEntryDto.java` (added isProduction parameter)
- `developer-portal/src/main/java/com/portal/environment/EnvironmentMapper.java` (passes isProduction to DTO)
- `developer-portal/src/main/java/com/portal/deployment/DeploymentService.java` (defense-in-depth production check)
- `developer-portal/src/main/java/com/portal/auth/DevSeedDataLoader.java` (production flag on seed environments)
- `developer-portal/src/main/webui/src/types/environment.ts` (added isProduction to EnvironmentChainEntry)
- `developer-portal/src/main/webui/src/api/deployments.ts` (optional isProduction param, appends ?env=prod)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx` (confirmation flow, role gating)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.tsx` (passes isProduction/nextIsProduction)
- `developer-portal/src/test/java/com/portal/auth/PermissionFilterTest.java` (added admin prod deploy test)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.test.tsx` (production gating tests, updated existing tests for confirmation flow)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.test.tsx` (isProduction data and chain-passing tests)
