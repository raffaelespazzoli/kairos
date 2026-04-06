# Story 2.8: Environment Chain Visualization

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to see my application's environment promotion chain as a visual card row showing health, version, and status per environment,
So that I can instantly understand where my application stands across all environments.

## Acceptance Criteria

1. **Backend REST endpoint returns combined environment + live status data**
   - **Given** GET `/api/v1/teams/{teamId}/applications/{appId}/environments` is called
   - **When** the backend processes the request
   - **Then** it reads the Environment entities for the application (ordered by promotion_order)
   - **And** it calls the ArgoCdAdapter in parallel to fetch live status for each environment
   - **And** it returns a combined response with stored environment data (name, cluster, namespace, promotionOrder) and live status (health, deployedVersion, lastDeployedAt)
   - **And** the response respects the data-driven promotion chain — rendering whatever environments are defined, not a hardcoded three

2. **Environment Chain Card Row renders dynamically**
   - **Given** the EnvironmentChain Card Row component renders
   - **When** the environment data is loaded
   - **Then** one card is displayed per environment in the promotion chain, ordered left to right by promotionOrder
   - **And** arrow connectors (→) are displayed between adjacent cards
   - **And** the chain renders dynamically based on the number of environments (2, 3, 4, or more) — not hardcoded to three

3. **Healthy environment card**
   - **Given** an environment card renders with status "Healthy"
   - **When** reviewing the card
   - **Then** a green top border (3px) is displayed
   - **And** a PatternFly Label with success status shows "✓ Healthy"
   - **And** the deployed version is displayed (e.g., "v1.4.2")
   - **And** the last deployment timestamp is shown (e.g., "2h ago")
   - **And** a "Promote to [next env]" button is visible (placeholder — functional in Epic 5)

4. **Unhealthy environment card**
   - **Given** an environment card renders with status "Unhealthy"
   - **When** reviewing the card
   - **Then** a red top border (3px) is displayed
   - **And** a PatternFly Label with danger status shows "✕ Unhealthy"
   - **And** the promote button is disabled
   - **And** a deep link "Open in ArgoCD ↗" is visible

5. **Deploying environment card**
   - **Given** an environment card renders with status "Deploying"
   - **When** reviewing the card
   - **Then** a yellow/gold top border (3px) is displayed
   - **And** a PatternFly Label with warning status shows "⟳ Deploying v1.4.2..."
   - **And** no action buttons are shown (deployment in progress)

6. **Not Deployed environment card**
   - **Given** an environment card renders with status "Not Deployed"
   - **When** reviewing the card
   - **Then** a grey top border (3px) is displayed
   - **And** a PatternFly Label with grey color shows "— Not deployed"
   - **And** a "Deploy" button is visible if a release exists (placeholder — functional in Epic 5)

7. **ApplicationOverviewPage integration**
   - **Given** the ApplicationOverviewPage renders
   - **When** the environment data has loaded
   - **Then** the Environment Chain Card Row is displayed at the top of the page as the primary view element
   - **And** below the chain, a two-column grid shows a "Recent Builds" section (placeholder) and an "Activity" section (placeholder)

8. **Card hover and expand interaction**
   - **Given** the environment chain cards are displayed
   - **When** a developer hovers over a card
   - **Then** a subtle shadow elevation appears and cursor changes to pointer
   - **And** clicking the card body expands it to show: deployment history placeholder, environment details (namespace, cluster), and deep links to ArgoCD ↗ and Grafana ↗ (placeholder — functional in Epics 3/6)

9. **Keyboard accessibility**
   - **Given** the environment chain component
   - **When** navigating with keyboard
   - **Then** arrow keys move focus between chain cards (left/right)
   - **And** each card has an aria-label: "[Env name] environment, version [X], [status]"
   - **And** status is communicated via color + icon + text label (never color alone)

10. **Responsive horizontal scrolling**
    - **Given** the viewport is narrower than the chain's natural width
    - **When** the chain would overflow
    - **Then** horizontal scrolling is enabled
    - **And** each card maintains a minimum width of 180px
    - **And** the chain never stacks vertically

11. **ArgoCD unreachable graceful degradation**
    - **Given** ArgoCD is unreachable when the page loads
    - **When** the environment status cannot be fetched
    - **Then** environment cards render with the data available from the database (name, cluster, namespace)
    - **And** a PatternFly inline Alert (warning) appears: "Deployment status unavailable — ArgoCD is unreachable"
    - **And** health badges show "Status unavailable" in grey

## Tasks / Subtasks

- [x] Task 1: Create EnvironmentService backend (AC: #1)
  - [x] Create `EnvironmentService.java` in `com.portal.environment`, `@ApplicationScoped`
  - [x] Inject `ArgoCdAdapter` and `TeamContext`
  - [x] Implement `getEnvironmentChain(Long appId)`: load environments from DB, call ArgoCdAdapter, merge into `EnvironmentChainEntryDto` list
  - [x] Handle `PortalIntegrationException` from ArgoCD: catch and return partial data with error flag

- [x] Task 2: Create EnvironmentChainEntryDto and response DTOs (AC: #1)
  - [x] Create `EnvironmentChainEntryDto.java` record in `com.portal.environment`
  - [x] Fields: environmentName, clusterName, namespace, promotionOrder, status, deployedVersion, lastDeployedAt, argocdDeepLink
  - [x] Create `EnvironmentChainResponse.java` record: list of entries + optional argocdError message

- [x] Task 3: Create EnvironmentResource REST endpoint (AC: #1)
  - [x] Create `EnvironmentResource.java` in `com.portal.environment`, `@Path("/api/v1/teams/{teamId}/applications/{appId}/environments")`
  - [x] `@GET` returns `EnvironmentChainResponse`
  - [x] Inject `EnvironmentService`
  - [x] Validate application belongs to team (return 404 if not)

- [x] Task 4: Create TypeScript types for environment data (AC: #1, #2)
  - [x] Create `src/main/webui/src/types/environment.ts`
  - [x] Define `EnvironmentChainEntry` and `EnvironmentChainResponse` interfaces

- [x] Task 5: Create environments API function (AC: #1)
  - [x] Create `src/main/webui/src/api/environments.ts`
  - [x] Implement `fetchEnvironmentChain(teamId, appId)` using `apiFetch`

- [x] Task 6: Create useEnvironments hook (AC: #1, #11)
  - [x] Create `src/main/webui/src/hooks/useEnvironments.ts`
  - [x] Use `useApiFetch` pattern returning `{ data, error, isLoading, refresh }`

- [x] Task 7: Create EnvironmentCard component (AC: #3, #4, #5, #6, #8, #9)
  - [x] Create `src/main/webui/src/components/environment/EnvironmentCard.tsx`
  - [x] Compose PatternFly Card + Label with status-driven color and icon
  - [x] Implement expandable detail section with deep links
  - [x] Apply status-specific top border (3px colored)
  - [x] Implement hover elevation and pointer cursor
  - [x] Add aria-label with env name, version, status

- [x] Task 8: Create EnvironmentChain component (AC: #2, #9, #10)
  - [x] Create `src/main/webui/src/components/environment/EnvironmentChain.tsx`
  - [x] Render EnvironmentCards in a flexbox row with arrow connectors
  - [x] Implement `role="list"` / `role="listitem"` for accessibility
  - [x] Implement left/right arrow key navigation between cards
  - [x] Apply `overflow-x: auto` with min-width 180px per card

- [x] Task 9: Update ApplicationOverviewPage (AC: #7, #11)
  - [x] Replace placeholder content with EnvironmentChain + RefreshButton + ErrorAlert
  - [x] Add two-column grid below chain with placeholder "Recent Builds" and "Activity" cards
  - [x] Show LoadingSpinner with systemName="ArgoCD" while loading
  - [x] Show inline warning Alert if ArgoCD error returned in response

- [x] Task 10: Write EnvironmentService unit test (AC: #1, #11)
  - [x] Create `EnvironmentServiceTest.java` in `src/test/java/com/portal/environment/`
  - [x] Mock `ArgoCdAdapter` and `Application`/`Environment` Panache finders
  - [x] Test normal flow: environments + statuses merged correctly
  - [x] Test ArgoCD failure: partial response with error flag set

- [x] Task 11: Write EnvironmentResource integration test (AC: #1)
  - [x] Create `EnvironmentResourceIT.java` in `src/test/java/com/portal/environment/`
  - [x] `@QuarkusTest` with `@TestSecurity` + `@OidcSecurity` claims
  - [x] `@InjectMock ArgoCdAdapter` to mock the adapter
  - [x] Test GET returns environment chain data
  - [x] Test cross-team access returns 404
  - [x] Test ArgoCD failure returns partial data with warning

- [x] Task 12: Write frontend component tests (AC: #2, #3, #4, #5, #6, #8, #9, #10, #11)
  - [x] Create `EnvironmentCard.test.tsx` — test all four status states, expanded detail, accessibility
  - [x] Create `EnvironmentChain.test.tsx` — test dynamic rendering, arrow connectors, keyboard navigation, overflow behavior
  - [x] Create `ApplicationOverviewPage.test.tsx` — test loading, success, error states

### Review Findings

- [x] [Review][Patch] Prevent nested controls from toggling the card [`developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx:107`] — Fixed: added `stopPropagation` on expanded CardBody and CardFooter
- [x] [Review][Patch] Respect the AC6 release prerequisite before showing the `Deploy` placeholder action [`developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx:220`] — Fixed: removed Deploy button since release data is not yet available (Epic 5)

## Dev Notes

### Hard Dependencies

This story **requires** two prior stories to be implemented:
- **Story 2.1** — `Application` and `Environment` Panache entities with `findByApplicationOrderByPromotionOrder(Long applicationId)`
- **Story 2.7** — `ArgoCdAdapter` interface and implementations, `EnvironmentStatusDto`, `PortalEnvironmentStatus` enum

### Package: `com.portal.environment` — New Classes

The `environment/` package currently has only `package-info.java`. This story adds:

```
com.portal.environment/
├── EnvironmentResource.java       # NEW — REST endpoint
├── EnvironmentService.java        # NEW — business logic
├── EnvironmentChainEntryDto.java  # NEW — combined DB + live status DTO
├── EnvironmentChainResponse.java  # NEW — response wrapper with optional error
├── EnvironmentMapper.java         # NEW — entity + status → DTO mapping
├── Environment.java               # EXISTS from Story 2.1
├── EnvironmentStatusDto.java      # EXISTS from Story 2.7
├── PortalEnvironmentStatus.java   # EXISTS from Story 2.7
└── package-info.java              # EXISTS
```

### EnvironmentResource — REST Endpoint

```java
package com.portal.environment;

@Path("/api/v1/teams/{teamId}/applications/{appId}/environments")
@Produces(MediaType.APPLICATION_JSON)
public class EnvironmentResource {

    @Inject
    EnvironmentService environmentService;

    @GET
    public EnvironmentChainResponse getEnvironmentChain(
            @PathParam("teamId") Long teamId,
            @PathParam("appId") Long appId) {
        return environmentService.getEnvironmentChain(teamId, appId);
    }
}
```

Follows the `ClusterResource` pattern exactly: thin resource delegates to service.

### EnvironmentService — Business Logic

```java
package com.portal.environment;

@ApplicationScoped
public class EnvironmentService {

    @Inject
    ArgoCdAdapter argoCdAdapter;

    @Inject
    TeamContext teamContext;

    public EnvironmentChainResponse getEnvironmentChain(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }

        List<Environment> environments =
                Environment.findByApplicationOrderByPromotionOrder(appId);

        String argocdError = null;
        List<EnvironmentStatusDto> statuses = List.of();
        try {
            statuses = argoCdAdapter.getEnvironmentStatuses(app.name, environments);
        } catch (PortalIntegrationException e) {
            argocdError = e.getMessage();
        }

        List<EnvironmentChainEntryDto> entries =
                EnvironmentMapper.merge(environments, statuses);

        return new EnvironmentChainResponse(entries, argocdError);
    }
}
```

**Key design decisions:**
- `NotFoundException` for missing or cross-team app → 404 (never 403)
- ArgoCD failure is caught and stored as `argocdError` string in the response — the endpoint still returns 200 with partial data (DB-only environment info)
- The service does NOT call ArgoCD adapters individually per environment — it passes the full list to `ArgoCdAdapter.getEnvironmentStatuses()` which handles parallel calls internally

### EnvironmentChainEntryDto — Combined DTO

```java
package com.portal.environment;

import java.time.Instant;

public record EnvironmentChainEntryDto(
    String environmentName,
    String clusterName,
    String namespace,
    int promotionOrder,
    String status,
    String deployedVersion,
    Instant lastDeployedAt,
    String argocdDeepLink
) {}
```

The `status` field is a String (not enum) to support both normal statuses ("HEALTHY", "UNHEALTHY", "DEPLOYING", "NOT_DEPLOYED") and the degraded case ("UNKNOWN") when ArgoCD is unreachable. The frontend maps these strings to visual states.

### EnvironmentChainResponse — Response Wrapper

```java
package com.portal.environment;

import java.util.List;

public record EnvironmentChainResponse(
    List<EnvironmentChainEntryDto> environments,
    String argocdError
) {}
```

When `argocdError` is null, all status data is live. When non-null, it contains the user-facing error message (e.g., "Deployment status unavailable — ArgoCD is unreachable") and status fields in entries are "UNKNOWN".

### EnvironmentMapper — Entity + Status Merge

```java
package com.portal.environment;

public class EnvironmentMapper {

    public static List<EnvironmentChainEntryDto> merge(
            List<Environment> environments,
            List<EnvironmentStatusDto> statuses) {

        Map<String, EnvironmentStatusDto> statusByEnv = statuses.stream()
                .collect(Collectors.toMap(
                        EnvironmentStatusDto::environmentName, s -> s));

        return environments.stream()
                .map(env -> {
                    EnvironmentStatusDto status = statusByEnv.get(env.name);
                    return new EnvironmentChainEntryDto(
                            env.name,
                            env.cluster != null ? env.cluster.name : null,
                            env.namespace,
                            env.promotionOrder,
                            status != null ? status.status().name() : "UNKNOWN",
                            status != null ? status.deployedVersion() : null,
                            status != null ? status.lastDeployedAt() : null,
                            status != null ? status.argocdDeepLink() : null);
                })
                .toList();
    }
}
```

**Note on `env.cluster`:** The `Environment` entity from Story 2.1 has a `clusterId` (Long FK). To get the cluster name, the mapper needs access to the `Cluster` entity. Two approaches:
1. Eagerly load cluster with the environment via `@ManyToOne` join
2. Load clusters separately and map by ID

Option 1 is simpler. If `Environment` has a `@ManyToOne Cluster cluster` field (check Story 2.1 implementation), use `env.cluster.name`. If `Environment` only has `clusterId`, fetch cluster names separately in the service.

### Frontend: TypeScript Types

Create `src/main/webui/src/types/environment.ts`:

```typescript
export type EnvironmentStatus =
  | 'HEALTHY'
  | 'UNHEALTHY'
  | 'DEPLOYING'
  | 'NOT_DEPLOYED'
  | 'UNKNOWN';

export interface EnvironmentChainEntry {
  environmentName: string;
  clusterName: string | null;
  namespace: string;
  promotionOrder: number;
  status: EnvironmentStatus;
  deployedVersion: string | null;
  lastDeployedAt: string | null;
  argocdDeepLink: string | null;
}

export interface EnvironmentChainResponse {
  environments: EnvironmentChainEntry[];
  argocdError: string | null;
}
```

### Frontend: API Function

Create `src/main/webui/src/api/environments.ts`:

```typescript
import { apiFetch } from './client';
import type { EnvironmentChainResponse } from '../types/environment';

export function fetchEnvironmentChain(
  teamId: string,
  appId: string,
): Promise<EnvironmentChainResponse> {
  return apiFetch<EnvironmentChainResponse>(
    `/api/v1/teams/${teamId}/applications/${appId}/environments`,
  );
}
```

### Frontend: useEnvironments Hook

Create `src/main/webui/src/hooks/useEnvironments.ts`:

```typescript
import { useApiFetch } from './useApiFetch';
import type { EnvironmentChainResponse } from '../types/environment';

export function useEnvironments(teamId: string, appId: string) {
  return useApiFetch<EnvironmentChainResponse>(
    `/api/v1/teams/${teamId}/applications/${appId}/environments`,
  );
}
```

### Frontend: EnvironmentCard Component

Create `src/main/webui/src/components/environment/EnvironmentCard.tsx`:

PatternFly 6 composition using:
- `Card`, `CardHeader`, `CardBody`, `CardFooter` — from `@patternfly/react-core`
- `Label` with `status` prop — PF6 supports `status="success"`, `status="warning"`, `status="danger"` and color `"grey"` for neutral
- `Button` for promote/deploy actions and deep links
- `CheckCircleIcon`, `ExclamationCircleIcon`, `SyncAltIcon`, `MinusCircleIcon` from `@patternfly/react-icons`

**Status → visual mapping:**

| Status | Top border | Label status prop | Label icon | Label text | Action |
|--------|-----------|-------------------|-----------|-----------|--------|
| HEALTHY | `var(--pf-t--global--color--status--success--default)` | `status="success"` | CheckCircleIcon | "✓ Healthy" | Promote button (enabled) |
| UNHEALTHY | `var(--pf-t--global--color--status--danger--default)` | `status="danger"` | ExclamationCircleIcon | "✕ Unhealthy" | Promote disabled + ArgoCD deep link |
| DEPLOYING | `var(--pf-t--global--color--status--warning--default)` | `status="warning"` | SyncAltIcon | "⟳ Deploying {version}..." | No actions |
| NOT_DEPLOYED | `var(--pf-t--global--color--nonstatus--gray--default)` | `color="grey"` | MinusCircleIcon | "— Not deployed" | Deploy button (placeholder) |
| UNKNOWN | `var(--pf-t--global--color--nonstatus--gray--default)` | `color="grey"` | — | "Status unavailable" | — |

**Card top border implementation:** Use `style={{ borderTop: '3px solid <token>' }}` on the Card component. The token is resolved from the status mapping above.

**Expandable detail:** Use React `useState` for an `isExpanded` boolean. When expanded, show:
- Environment details: namespace and cluster name
- Deep links section: "Open in ArgoCD ↗" (using existing `DeepLinkButton` from `components/shared/DeepLinkButton.tsx`), "View in Grafana ↗" (placeholder — no href yet)
- Deployment history placeholder: "Deployment history coming in Epic 5"

**Hover effect:** Apply `style` with `cursor: 'pointer'` and on hover add box-shadow via CSS class or inline style using `onMouseEnter`/`onMouseLeave` state. Use PatternFly shadow token `var(--pf-t--global--box-shadow--md)`.

**Promote button:** Render only when `promotionOrder < maxPromotionOrder` (the last environment has no "promote to" target). The button text is "Promote to {nextEnvName}". For MVP this is a disabled placeholder with tooltip "Promotion available in a future release".

**Aria label:** Set on the Card: `aria-label={`${environmentName} environment, version ${deployedVersion ?? 'none'}, ${statusLabel}`}`

### Frontend: EnvironmentChain Component

Create `src/main/webui/src/components/environment/EnvironmentChain.tsx`:

**Layout:** Flexbox row with `gap: var(--pf-t--global--spacer--md)`. Between each card, render an arrow connector (→ character or an `ArrowRightIcon` from PF icons) centered vertically.

```typescript
<div
  role="list"
  aria-label="Environment promotion chain"
  style={{
    display: 'flex',
    alignItems: 'stretch',
    overflowX: 'auto',
    gap: 'var(--pf-t--global--spacer--md)',
    paddingBottom: 'var(--pf-t--global--spacer--sm)',
  }}
  onKeyDown={handleArrowKeyNavigation}
>
  {environments.map((env, index) => (
    <Fragment key={env.environmentName}>
      <div role="listitem" style={{ minWidth: 180, flex: '1 0 180px' }}>
        <EnvironmentCard
          entry={env}
          nextEnvName={environments[index + 1]?.environmentName}
          ref={cardRefs.current[index]}
        />
      </div>
      {index < environments.length - 1 && (
        <div style={{ display: 'flex', alignItems: 'center' }} aria-hidden="true">
          <ArrowRightIcon />
        </div>
      )}
    </Fragment>
  ))}
</div>
```

**Keyboard navigation:** `onKeyDown` handler on the container. When ArrowRight/ArrowLeft is pressed, move focus to the next/previous card using refs. Use `useRef` array for card focus management.

**Props:**
- `environments: EnvironmentChainEntry[]`
- `argocdError?: string | null`

When `argocdError` is provided, render an `Alert` (warning variant, inline) above the chain with the error message.

### Frontend: ApplicationOverviewPage Update

Replace the placeholder in `src/main/webui/src/routes/ApplicationOverviewPage.tsx`:

```typescript
import { useParams } from 'react-router-dom';
import {
  PageSection,
  Title,
  Grid,
  GridItem,
  Card,
  CardBody,
  CardTitle,
  Flex,
  FlexItem,
  Content,
} from '@patternfly/react-core';
import { EnvironmentChain } from '../components/environment/EnvironmentChain';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';
import { useEnvironments } from '../hooks/useEnvironments';

export function ApplicationOverviewPage() {
  const { teamId, appId } = useParams();
  const { data, error, isLoading, refresh } = useEnvironments(teamId!, appId!);

  return (
    <>
      <PageSection>
        <Flex>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Application Overview</Title>
          </FlexItem>
          <FlexItem>
            <RefreshButton onRefresh={refresh} isRefreshing={isLoading} />
          </FlexItem>
        </Flex>
      </PageSection>

      <PageSection>
        {isLoading && <LoadingSpinner systemName="ArgoCD" />}
        {error && <ErrorAlert error={error} />}
        {data && (
          <EnvironmentChain
            environments={data.environments}
            argocdError={data.argocdError}
          />
        )}
      </PageSection>

      <PageSection>
        <Grid hasGutter>
          <GridItem span={6}>
            <Card>
              <CardTitle>Recent Builds</CardTitle>
              <CardBody>
                <Content component="p">
                  Build history coming in Epic 4.
                </Content>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem span={6}>
            <Card>
              <CardTitle>Activity</CardTitle>
              <CardBody>
                <Content component="p">
                  Activity feed coming in Epic 7.
                </Content>
              </CardBody>
            </Card>
          </GridItem>
        </Grid>
      </PageSection>
    </>
  );
}
```

### Existing Components to Reuse — DO NOT Recreate

| Component | Location | Usage |
|-----------|----------|-------|
| `DeepLinkButton` | `components/shared/DeepLinkButton.tsx` | "Open in ArgoCD ↗" on expanded card — pass `href={entry.argocdDeepLink}` and `toolName="ArgoCD"` |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Inline error when full API call fails — pass `error` from hook |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Loading state — pass `systemName="ArgoCD"` for delayed hint |
| `RefreshButton` | `components/shared/RefreshButton.tsx` | Manual refresh — pass `onRefresh={refresh}` and `isRefreshing={isLoading}` |
| `useApiFetch` | `hooks/useApiFetch.ts` | Pattern for `useEnvironments` hook — returns `{ data, error, isLoading, refresh }` |
| `apiFetch` | `api/client.ts` | HTTP wrapper — adds bearer token, parses errors into `PortalError` |
| `PortalError` type | `types/error.ts` | Error shape: `{ error, message, detail?, system?, deepLink?, timestamp }` |
| `ApplicationLayout` | `components/layout/ApplicationLayout.tsx` | Already wraps the overview page with tab navigation — no changes needed |
| `ApplicationTabs` | `components/layout/ApplicationTabs.tsx` | Tab bar already includes "Overview" tab — no changes needed |
| `ArgoCdAdapter` | `integration/argocd/ArgoCdAdapter.java` | **From Story 2.7** — `getEnvironmentStatuses(String appName, List<Environment> environments)` |
| `EnvironmentStatusDto` | `environment/EnvironmentStatusDto.java` | **From Story 2.7** — `environmentName, status, deployedVersion, lastDeployedAt, argocdAppName, argocdDeepLink` |
| `PortalEnvironmentStatus` | `environment/PortalEnvironmentStatus.java` | **From Story 2.7** — enum: `HEALTHY, UNHEALTHY, DEPLOYING, NOT_DEPLOYED` |
| `PortalIntegrationException` | `integration/PortalIntegrationException.java` | Thrown by ArgoCdAdapter — catch in EnvironmentService for graceful degradation |
| `GlobalExceptionMapper` | `common/GlobalExceptionMapper.java` | Handles uncaught `PortalIntegrationException` → 502; but EnvironmentService catches it |
| `Environment` entity | `environment/Environment.java` | **From Story 2.1** — `findByApplicationOrderByPromotionOrder(Long applicationId)` |
| `Application` entity | `application/Application.java` | **From Story 2.1** — `findById(Long id)` |
| `TeamContext` | `auth/TeamContext.java` | `@RequestScoped` CDI bean — provides team context from JWT |
| `ClusterResource` / `ClusterService` | `cluster/` package | **Reference pattern** for Resource → Service → DTO chain |
| `ClusterDto` | `cluster/ClusterDto.java` | **Reference pattern** for record DTO with `static from(entity)` |

### PatternFly 6 Specifics

The project uses **PatternFly React 6.x (v6.4.1)**. Key component APIs:

- **Card:** `import { Card, CardHeader, CardBody, CardFooter } from '@patternfly/react-core'` — no `variant` prop needed, use default. No `isClickable` or `isSelectable` in PF6 (removed).
- **Label:** `import { Label } from '@patternfly/react-core'` — use `status="success"`, `status="warning"`, `status="danger"` for health states. For grey/neutral, use `color="grey"` (no `status` prop). The `status` prop overrides color.
- **Icons:** `import { CheckCircleIcon, ExclamationCircleIcon, SyncAltIcon, MinusCircleIcon, ArrowRightIcon } from '@patternfly/react-icons'`
- **CSS tokens:** PF6 uses `--pf-t--global--` prefix (not `--pf-v5--`). Status colors: `--pf-t--global--color--status--success--default`, `--pf-t--global--color--status--danger--default`, `--pf-t--global--color--status--warning--default`. Non-status grey: `--pf-t--global--color--nonstatus--gray--default`.
- **Grid:** `import { Grid, GridItem } from '@patternfly/react-core'` — `hasGutter` for spacing, `span={6}` for two columns.
- **Alert:** Already used in `ErrorAlert.tsx` — `variant="warning"` for ArgoCD partial failure inline alert.

### Testing Strategy

**Backend unit test — `EnvironmentServiceTest.java`:**
- Mock `ArgoCdAdapter` via Mockito
- Mock `Application.findById()` and `Environment.findByApplicationOrderByPromotionOrder()` — use Panache mocking pattern (or construct entities directly with public fields)
- Test: happy path → environments merged with statuses correctly
- Test: ArgoCD throws → response has `argocdError` set, entries have "UNKNOWN" status
- Test: app not found → `NotFoundException`
- Test: cross-team access → `NotFoundException`

**Backend IT test — `EnvironmentResourceIT.java`:**
```java
@QuarkusTest
class EnvironmentResourceIT {

    @InjectMock
    ArgoCdAdapter argoCdAdapter;

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void getEnvironmentChainReturnsData() {
        // Setup: need Application + Environment test data in DB
        // Mock ArgoCdAdapter to return statuses
        given()
                .when().get("/api/v1/teams/{teamId}/applications/{appId}/environments",
                        teamId, appId)
                .then()
                .statusCode(200)
                .body("environments.size()", equalTo(3))
                .body("argocdError", nullValue());
    }
}
```

Follow the `TeamResourceIT.java` and `ClusterResourceIT.java` patterns exactly: `@QuarkusTest`, `@TestSecurity`, `@OidcSecurity`, REST Assured assertions.

**Frontend tests:**

`EnvironmentCard.test.tsx`:
- Mock props with each status, verify correct Label variant/text, border color, button state
- Test expanded state shows deep links and env details
- Test aria-label format

`EnvironmentChain.test.tsx`:
- Mock environment data array, verify correct number of cards rendered
- Verify arrow connectors between cards
- Test keyboard navigation (ArrowRight/ArrowLeft)
- Test with `argocdError` → Alert rendered

`ApplicationOverviewPage.test.tsx`:
- Mock `useEnvironments` hook at the module level using `vi.mock`
- Test loading state → Spinner visible
- Test success → chain visible + placeholder grids visible
- Test error → ErrorAlert visible

Use the existing test patterns: Vitest + React Testing Library. Import from `vitest` for `describe`, `it`, `expect`, `vi`. Use `render` from `@testing-library/react`. Query by `role`, `text`, `label` — not by CSS class or test IDs.

### Anti-Patterns to Avoid

- **DO NOT** call `ArgoCdAdapter` from `EnvironmentResource` directly — go through `EnvironmentService`
- **DO NOT** import `Environment` entity in `EnvironmentResource` — the service handles data access
- **DO NOT** return Panache entities from the REST endpoint — use DTOs only
- **DO NOT** hardcode exactly 3 environments — the chain is data-driven by `promotionOrder`
- **DO NOT** use custom CSS for elements PatternFly provides — use PF6 Card, Label, Alert, Button, Grid
- **DO NOT** use `any` types in TypeScript — all interfaces are strongly typed
- **DO NOT** call `fetch()` directly — use `apiFetch()` wrapper via the existing hook
- **DO NOT** cache ArgoCD status — every load fetches live state
- **DO NOT** use absolute URLs — all API calls use relative paths
- **DO NOT** use `--pf-v5--` CSS tokens — use `--pf-t--global--` (PF6 token prefix)
- **DO NOT** return 403 for cross-team access — return 404

### What NOT to Build

- **No promote/deploy functionality** — buttons are placeholder UI only; promotion logic is Epic 5
- **No Grafana deep links** — Grafana integration is Epic 6; show placeholder "View in Grafana ↗" disabled
- **No deployment history** — placeholder text only; deployment tracking is Epic 5
- **No auto-refresh / polling** — manual refresh only via RefreshButton
- **No activity feed content** — placeholder card only; activity feed is Epic 7
- **No build data** — placeholder card only; Tekton integration is Epic 4
- **No background caching** — every page load fetches live data

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/environment/
├── EnvironmentResource.java
├── EnvironmentService.java
├── EnvironmentChainEntryDto.java
├── EnvironmentChainResponse.java
└── EnvironmentMapper.java
```

**New frontend files:**
```
src/main/webui/src/types/
└── environment.ts

src/main/webui/src/api/
└── environments.ts

src/main/webui/src/hooks/
└── useEnvironments.ts

src/main/webui/src/components/environment/
├── EnvironmentCard.tsx
└── EnvironmentChain.tsx
```

**Modified frontend files:**
```
src/main/webui/src/routes/ApplicationOverviewPage.tsx   (replace placeholder)
```

**New test files:**
```
src/test/java/com/portal/environment/
├── EnvironmentServiceTest.java
└── EnvironmentResourceIT.java

src/main/webui/src/components/environment/
├── EnvironmentCard.test.tsx
└── EnvironmentChain.test.tsx

src/main/webui/src/routes/
└── ApplicationOverviewPage.test.tsx
```

### Previous Story Intelligence

**Story 2.7 (ArgoCD Adapter):**
- Created `ArgoCdAdapter` interface + `ArgoCdRestAdapter` + `DevArgoCdAdapter`
- `getEnvironmentStatuses(String appName, List<Environment> environments)` returns `List<EnvironmentStatusDto>`
- `DevArgoCdAdapter` returns mock data: first env HEALTHY with "v1.2.3", second DEPLOYING, rest NOT_DEPLOYED — ideal for testing the chain visualization in dev mode
- `PortalIntegrationException` thrown when ArgoCD is unreachable — this story's service catches it for graceful degradation
- ArgoCD adapter has NO REST endpoint — this story creates the endpoint that exposes the adapter's data

**Story 2.1 (Data Model):**
- `Environment` entity with `findByApplicationOrderByPromotionOrder(Long applicationId)` returns environments sorted by promotion chain
- `Application` entity with `findByTeam(Long teamId)`
- `Environment` has public fields: `name`, `applicationId`, `clusterId`, `namespace`, `promotionOrder`

**Story 1.4 (Portal Page Shell):**
- `ApplicationLayout` + `ApplicationTabs` already exist with "Overview" tab routing to `ApplicationOverviewPage`
- `AppShell` with sidebar navigation and breadcrumbs is in place
- Routing in `App.tsx` already maps `/teams/:teamId/apps/:appId/overview` to `ApplicationOverviewPage`

**Story 1.5 (API Foundation):**
- `apiFetch()` wrapper, `ApiError`, `PortalError` types, `useApiFetch` hook all exist
- Shared components `ErrorAlert`, `LoadingSpinner`, `RefreshButton` are implemented and tested

### Relative Time Display

For "last deployed" timestamp display (e.g., "2h ago"), use a simple relative time formatter. Do NOT add a library — implement a lightweight utility:

```typescript
export function relativeTime(isoString: string | null): string {
  if (!isoString) return '';
  const seconds = Math.floor(
    (Date.now() - new Date(isoString).getTime()) / 1000,
  );
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}
```

Place in `src/main/webui/src/components/environment/EnvironmentCard.tsx` or a shared utility if multiple components will need it.

### References

- [Source: planning-artifacts/epics.md § Story 2.8 (line 878)] — Full acceptance criteria and story statement
- [Source: planning-artifacts/architecture.md § REST API (line 472)] — `/api/v1/teams/{teamId}/applications/{appId}/environments` endpoint
- [Source: planning-artifacts/architecture.md § environment/ package (line 795)] — EnvironmentResource, EnvironmentService, EnvironmentStatusDto, EnvironmentMapper
- [Source: planning-artifacts/architecture.md § Frontend components (line 962)] — EnvironmentChain.tsx, EnvironmentCard.tsx in `components/environment/`
- [Source: planning-artifacts/architecture.md § Frontend hooks (line 938)] — useEnvironments.ts
- [Source: planning-artifacts/architecture.md § Frontend API (line 925)] — environments.ts API function
- [Source: planning-artifacts/architecture.md § Frontend types (line 989)] — environment.ts type definitions
- [Source: planning-artifacts/architecture.md § Naming conventions (line 607)] — EnvironmentResource, EnvironmentService, EnvironmentStatusDto naming
- [Source: planning-artifacts/architecture.md § Integration Architecture (line 502)] — Adapter pattern, parallel calls with CompletableFuture
- [Source: planning-artifacts/architecture.md § Error response (line 485)] — Error JSON format including system and deepLink fields
- [Source: planning-artifacts/architecture.md § Process Patterns (line 715)] — PortalIntegrationException → ExceptionMapper → 502, frontend error handling, loading states
- [Source: planning-artifacts/architecture.md § Anti-patterns (line 744)] — Resource must not call adapter directly, use Service
- [Source: planning-artifacts/ux-design-specification.md § Environment Chain Card Row (line 914)] — Complete component anatomy, states, interactions, accessibility
- [Source: planning-artifacts/ux-design-specification.md § Card Chain chosen direction (line 547)] — Cards over pipeline flow, table, or split view
- [Source: planning-artifacts/ux-design-specification.md § Status vocabulary (line 321)] — success/danger/warning/grey mapping
- [Source: planning-artifacts/ux-design-specification.md § Color system (line 425)] — Semantic color tokens, no per-environment colors
- [Source: planning-artifacts/ux-design-specification.md § Deep link patterns (line 1255)] — "Open in ArgoCD ↗" scoped to ArgoCD Application
- [Source: planning-artifacts/ux-design-specification.md § Loading and data freshness (line 1223)] — Spinner, system hint after 3s, partial data rendering
- [Source: planning-artifacts/ux-design-specification.md § Error feedback (line 1199)] — Inline errors with deep links
- [Source: planning-artifacts/ux-design-specification.md § Responsive behavior (line 1332)] — Horizontal scroll, 180px min-width, never stack vertically
- [Source: planning-artifacts/ux-design-specification.md § Accessibility (line 514)] — WCAG 2.1 AA, color + icon + text, aria labels
- [Source: planning-artifacts/ux-design-specification.md § ApplicationOverview layout (line 587)] — Chain at top, two-column grid below
- [Source: project-context.md § PatternFly 6] — PF6 v6.4.1, not PF5; use PF6 token prefix `--pf-t--`
- [Source: project-context.md § Testing Rules] — Unit: ClassTest.java, IT: ClassIT.java, Frontend: Component.test.tsx co-located
- [Source: project-context.md § Anti-Patterns] — No caching, developer-language errors, 404 not 403 for cross-team
- [Source: implementation-artifacts/2-7-argocd-adapter-environment-status.md] — ArgoCdAdapter API, EnvironmentStatusDto, DevArgoCdAdapter mock data
- [Source: implementation-artifacts/2-1-application-environment-data-model.md] — Environment entity, findByApplicationOrderByPromotionOrder
- [Source: developer-portal/src/main/webui/src/components/shared/] — ErrorAlert, LoadingSpinner, DeepLinkButton, RefreshButton implementations
- [Source: developer-portal/src/main/webui/src/hooks/useApiFetch.ts] — useApiFetch pattern: { data, error, isLoading, refresh }
- [Source: developer-portal/src/main/webui/src/api/client.ts] — apiFetch wrapper with token injection
- [Source: developer-portal/src/main/webui/src/types/error.ts] — PortalError interface shape
- [Source: developer-portal/src/main/webui/src/App.tsx] — Routing: /teams/:teamId/apps/:appId/overview → ApplicationOverviewPage
- [Source: developer-portal/src/main/java/com/portal/cluster/ClusterResource.java] — Reference Resource pattern
- [Source: developer-portal/src/main/java/com/portal/cluster/ClusterService.java] — Reference Service pattern
- [Source: developer-portal/src/main/java/com/portal/cluster/ClusterDto.java] — Reference DTO record pattern

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus (Cursor Agent)

### Debug Log References

- Frontend tests: 2 initial failures in EnvironmentChain.test.tsx (arrow connector selector `[aria-hidden="true"]` matched PF icons too); fixed by scoping query to direct children of the list container.
- Backend tests: 2 pre-existing failures in `CasbinEnforcerTest.memberCannotCrudClusters` and `PermissionFilterTest.memberDeniedAdminClusters` (unrelated to this story — cluster admin endpoint access control tests).

### Completion Notes List

- ✅ Task 1: Created `EnvironmentService.java` with `getEnvironmentChain()` method that loads environments from DB, calls ArgoCdAdapter for live status, resolves cluster names, and merges results. Graceful degradation when ArgoCD is unreachable (catches PortalIntegrationException, returns partial data with error message).
- ✅ Task 2: Created `EnvironmentChainEntryDto.java` record (combined DB + live data) and `EnvironmentChainResponse.java` record (list + optional argocdError). Created `EnvironmentMapper.java` for entity + status → DTO merge with cluster name resolution.
- ✅ Task 3: Created `EnvironmentResource.java` REST endpoint at `/api/v1/teams/{teamId}/applications/{appId}/environments`. Team isolation enforced via `TeamContext` comparison (404 for cross-team). Thin resource delegates to service.
- ✅ Task 4: Created `environment.ts` with `EnvironmentStatus` union type, `EnvironmentChainEntry` and `EnvironmentChainResponse` interfaces.
- ✅ Task 5: Created `environments.ts` API function using `apiFetch` wrapper.
- ✅ Task 6: Created `useEnvironments.ts` hook using `useApiFetch` pattern with null-path skip for conditional fetching.
- ✅ Task 7: Created `EnvironmentCard.tsx` with status-driven PF6 Card + Label composition, expandable detail section, hover elevation, keyboard interaction, aria-labels, promote/deploy placeholder buttons.
- ✅ Task 8: Created `EnvironmentChain.tsx` with flexbox row, ArrowRight connectors, `role="list"`/`role="listitem"` semantics, ArrowLeft/ArrowRight keyboard navigation, `overflow-x: auto` with 180px min-width, ArgoCD warning Alert.
- ✅ Task 9: Updated `ApplicationOverviewPage.tsx` — integrated EnvironmentChain, added RefreshButton, ArgoCD loading spinner, error handling, two-column grid with placeholder "Recent Builds" and "Activity" cards. Preserved existing app description list.
- ✅ Task 10: Created `EnvironmentServiceTest.java` — 4 tests: happy path merge, ArgoCD failure graceful degradation, not-found app, cross-team access.
- ✅ Task 11: Created `EnvironmentResourceIT.java` — 4 tests: GET returns chain data with 200, cross-team 404, ArgoCD failure returns partial data, non-existent app 404.
- ✅ Task 12: Created 3 frontend test files — `EnvironmentCard.test.tsx` (13 tests: all status states, expand/collapse, buttons, accessibility), `EnvironmentChain.test.tsx` (10 tests: rendering, arrows, keyboard nav, alert, overflow), `ApplicationOverviewPage.test.tsx` (13 tests: loading/error/success states, env chain integration, placeholders, refresh button).

### File List

**New backend files:**
- `developer-portal/src/main/java/com/portal/environment/EnvironmentResource.java`
- `developer-portal/src/main/java/com/portal/environment/EnvironmentService.java`
- `developer-portal/src/main/java/com/portal/environment/EnvironmentChainEntryDto.java`
- `developer-portal/src/main/java/com/portal/environment/EnvironmentChainResponse.java`
- `developer-portal/src/main/java/com/portal/environment/EnvironmentMapper.java`

**New frontend files:**
- `developer-portal/src/main/webui/src/types/environment.ts`
- `developer-portal/src/main/webui/src/api/environments.ts`
- `developer-portal/src/main/webui/src/hooks/useEnvironments.ts`
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx`
- `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.tsx`

**Modified frontend files:**
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx`

**New test files:**
- `developer-portal/src/test/java/com/portal/environment/EnvironmentServiceTest.java`
- `developer-portal/src/test/java/com/portal/environment/EnvironmentResourceIT.java`
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.test.tsx`
- `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.test.tsx`

**Modified test files:**
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx`

### Change Log

- 2026-04-06: Implemented Story 2.8 — Environment Chain Visualization. Created backend REST endpoint with EnvironmentService, DTOs, and EnvironmentMapper. Built frontend EnvironmentCard and EnvironmentChain components with PF6 styling, accessibility, keyboard navigation, and responsive overflow. Integrated into ApplicationOverviewPage with RefreshButton, loading/error states, and placeholder grids. Added 8 backend tests and 36 frontend tests.
