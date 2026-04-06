# Story 2.6: Application List & Team Navigation

Status: done

## Story

As a developer,
I want to see all my team's onboarded applications in the sidebar and navigate to any of them,
So that I can quickly access the applications I work on.

## Acceptance Criteria

1. **Application list API**
   - **Given** a developer is authenticated and has applications onboarded for their team
   - **When** GET `/api/v1/teams/{teamId}/applications` is called
   - **Then** all applications belonging to the team are returned, ordered by name
   - **And** each application includes: id, name, runtimeType, onboardedAt, onboardingPrUrl

2. **Sidebar application navigation**
   - **Given** the sidebar renders the application list
   - **When** the developer's team has onboarded applications
   - **Then** each application appears as a clickable item in the sidebar below the team selector
   - **And** the currently selected application is visually highlighted
   - **And** clicking an application navigates to its overview page (ApplicationOverviewPage)

3. **Empty state for no applications**
   - **Given** the sidebar shows the application list
   - **When** the developer's team has no onboarded applications
   - **Then** the sidebar shows only the "+ Onboard Application" button
   - **And** the main content area shows the EmptyState: "No applications onboarded yet" with the onboard CTA

4. **Application overview page with breadcrumbs and tabs**
   - **Given** a developer navigates to an application
   - **When** the ApplicationOverviewPage loads
   - **Then** the breadcrumbs update to: Team Name → Application Name → Overview
   - **And** the application-context tab bar is visible (Overview, Builds, Releases, Environments, Health, Settings)
   - **And** the Overview tab is active by default

5. **Team-scoped filtering**
   - **Given** the application list API is called
   - **When** the TeamContext filter processes the request
   - **Then** only applications belonging to the authenticated user's team are returned
   - **And** applications from other teams are never visible (404 if accessed directly by ID)

## Tasks / Subtasks

- [x] Task 1: Create ApplicationSummaryDto (AC: #1)
  - [x] Create `ApplicationSummaryDto.java` record in `com.portal.application` with fields: `Long id`, `String name`, `String runtimeType`, `Instant onboardedAt`, `String onboardingPrUrl`
  - [x] Add static factory method `from(Application app)` for entity-to-DTO mapping

- [x] Task 2: Create ApplicationService (AC: #1, #5)
  - [x] Create `ApplicationService.java` in `com.portal.application`, `@ApplicationScoped`
  - [x] Inject `TeamContext`
  - [x] Method: `List<Application> getTeamApplications()` — calls `Application.findByTeam(teamContext.getTeamId())` and returns the result (already ordered by name from the entity query)
  - [x] Method: `Application getApplicationById(Long appId)` — finds by ID, verifies `app.teamId.equals(teamContext.getTeamId())`, throws `NotFoundException` if missing or cross-team

- [x] Task 3: Create ApplicationResource (AC: #1, #5)
  - [x] Create `ApplicationResource.java` in `com.portal.application`
  - [x] `@Path("/api/v1/teams/{teamId}/applications")`, `@Produces(MediaType.APPLICATION_JSON)`
  - [x] Inject `TeamContext`, `ApplicationService`
  - [x] `@GET` → returns `List<ApplicationSummaryDto>` by mapping service results through `ApplicationSummaryDto.from()`
  - [x] Verify `teamContext.getTeamId().equals(teamId)` — throw `NotFoundException` on mismatch (same pattern as TeamResource)

- [x] Task 4: Create frontend Application type (AC: #1)
  - [x] Create `src/main/webui/src/types/application.ts`
  - [x] Define `ApplicationSummary` interface: `id: number`, `name: string`, `runtimeType: string`, `onboardedAt: string`, `onboardingPrUrl: string`

- [x] Task 5: Create frontend API function (AC: #1)
  - [x] Create `src/main/webui/src/api/applications.ts`
  - [x] `fetchApplications(teamId: string): Promise<ApplicationSummary[]>` using `apiFetch`

- [x] Task 6: Wire AppShell to fetch and pass applications to Sidebar (AC: #2, #3)
  - [x] Modify `AppShell.tsx`: use `useApiFetch` with `/api/v1/teams/${teamId}/applications` to fetch applications
  - [x] Map `ApplicationSummary[]` to `SidebarApp[]` (id → String(id), name → name)
  - [x] Pass `applications` prop to `<Sidebar applications={apps} />`
  - [x] Handle loading/error gracefully — sidebar shows without apps while loading

- [x] Task 7: Update TeamDashboardPage to conditionally render (AC: #3)
  - [x] Fetch applications via `useApiFetch<ApplicationSummary[]>('/api/v1/teams/${teamId}/applications')`
  - [x] If applications array is non-empty, show dashboard content (team name, app count summary for now)
  - [x] If applications array is empty, show `NoApplicationsEmptyState` (existing component)

- [x] Task 8: Update AppBreadcrumb to show application name (AC: #4)
  - [x] Receive applications list (or accept an app name lookup mechanism)
  - [x] When `appId` is present, show the application name instead of the raw ID
  - [x] Approach: accept optional `applicationName` prop or look up from a context/prop passed through ApplicationLayout

- [x] Task 9: Update ApplicationOverviewPage from placeholder (AC: #4)
  - [x] Fetch application details from the list endpoint or by accepting props
  - [x] Display application name as page heading
  - [x] Show basic application info: runtime type, onboarded date, onboarding PR link
  - [x] Keep "Coming soon" messaging for environment chain (Story 2.8) and deeper details

- [x] Task 10: Write ApplicationService unit tests (AC: #1, #5)
  - [x] Create `ApplicationServiceTest.java` in `src/test/java/com/portal/application/`
  - [x] Mock `TeamContext`
  - [x] Test `getTeamApplications()` returns applications for the team from TeamContext
  - [x] Test `getApplicationById()` returns app when teamId matches
  - [x] Test `getApplicationById()` throws NotFoundException for cross-team app
  - [x] Test `getApplicationById()` throws NotFoundException for non-existent ID

- [x] Task 11: Write ApplicationResource integration test (AC: #1, #5)
  - [x] Create `ApplicationResourceIT.java` in `src/test/java/com/portal/application/`
  - [x] `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` with member role
  - [x] Test GET `/api/v1/teams/{teamId}/applications` → 200 + JSON array
  - [x] Verify applications ordered by name
  - [x] Test DTO contains all expected fields: id, name, runtimeType, onboardedAt, onboardingPrUrl
  - [x] Test empty team (no apps) → 200 + empty array
  - [x] Test cross-team access → 404

- [x] Task 12: Write frontend component tests (AC: #2, #3, #4)
  - [x] Update `Sidebar.test.tsx`: verify applications are rendered as nav items when passed
  - [x] Update `TeamDashboardPage.test.tsx`: test conditional rendering — empty state when no apps, dashboard content when apps exist
  - [x] Update `AppBreadcrumb.test.tsx`: test application name shown in breadcrumb when navigating to an app
  - [x] Update `ApplicationOverviewPage.test.tsx`: test page renders with application name and info

### Review Findings
- [x] [Review][Patch] AppShell can stay in a perpetual loading state when no numeric team id is resolved, so dashboard/overview never reach empty or loaded states. **Fixed**: `isLoading` now computed as `teamsLoading || (numericTeamId !== null && appsLoading)`.
- [x] [Review][Patch] Team and application fetch failures are not propagated through `ApplicationsContext`, causing failed loads to appear as empty state or "Application not found" instead of an inline error. **Fixed**: Added `error: PortalError | null` to context; `TeamDashboardPage` and `ApplicationOverviewPage` now show `ErrorAlert` on failure.
- [x] [Review][Patch] `AppShell` fetches applications for `teams[0].id` instead of the current route/auth team, which will show the wrong application list if the URL team differs or multi-team support is added. **Fixed**: `numericTeamId` now resolved from `useParams().teamId` → `useAuth().teamId` → `teams[0]` fallback, matching by numeric ID or `oidcGroupId`.
- [x] [Review][Patch] `ApplicationSummaryDto` exposes extra fields (`teamId`, `gitRepoUrl`, `createdAt`, `updatedAt`) beyond the story contract, weakening the API boundary and leaking data the frontend does not need. **Fixed**: Trimmed DTO to `id, name, runtimeType, onboardedAt, onboardingPrUrl`.
- [x] [Review][Patch] `CreatePrStepBody` can trigger onboarding confirmation twice in React Strict Mode because the mount guard is local component state and resets on remount. **Fixed**: Added `activeTimersRef` with cleanup on unmount; `useRef` values persist across StrictMode remounts so the guard is safe.
- [x] [Review][Patch] The onboarding provisioning-plan step has no recovery path when `fetchClusters()` returns an empty list, leaving the wizard blocked with `Confirm & Create PR` disabled. **Fixed**: Added warning Alert when clusters list is empty after plan step entry.

## Dev Notes

### Hard Dependencies

This story **requires** Story 2.1 to be implemented first:
- **Story 2.1** — Application and Environment entities (Flyway V3/V4), `findByTeam(Long teamId)` query method, `findByApplicationOrderByPromotionOrder(Long applicationId)` method

Stories 2.2–2.5 should also be complete so that onboarded applications actually exist in the database for the list to return results. However, this story can be developed concurrently if test data is seeded manually.

### Package: `com.portal.application` — New Code

The `com.portal.application` package currently has only `package-info.java`. Story 2.1 adds the `Application.java` entity. This story adds the REST layer:

```
com.portal.application/
├── Application.java            # EXISTS after Story 2.1
├── ApplicationResource.java    # NEW — GET list endpoint
├── ApplicationService.java     # NEW — team-scoped business logic
├── ApplicationSummaryDto.java  # NEW — API response DTO
└── package-info.java           # EXISTS
```

The architecture also specifies `ApplicationDetailDto.java` and `ApplicationMapper.java` in this package — those are **not needed** for this story. ApplicationDetailDto is for the single-app GET endpoint (future), and the mapping is simple enough to use a static `from()` method on the DTO record.

### ApplicationResource — Implementation

```java
@Path("/api/v1/teams/{teamId}/applications")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource {

    @Inject
    TeamContext teamContext;

    @Inject
    ApplicationService applicationService;

    @GET
    public List<ApplicationSummaryDto> listApplications(
            @PathParam("teamId") Long teamId) {
        if (!teamContext.getTeamId().equals(teamId)) {
            throw new NotFoundException();
        }
        return applicationService.getTeamApplications().stream()
                .map(ApplicationSummaryDto::from)
                .toList();
    }
}
```

**PermissionFilter path analysis:** The path `teams/{teamId}/applications`:
- PermissionFilter walks segments to find the resource
- Resource: `applications`
- HTTP GET → action: `read`
- Casbin check: `(member, applications, read)` → **ALLOWED** per policy.csv line 3

### ApplicationSummaryDto — Implementation

```java
public record ApplicationSummaryDto(
    Long id,
    String name,
    String runtimeType,
    Instant onboardedAt,
    String onboardingPrUrl
) {
    public static ApplicationSummaryDto from(Application app) {
        return new ApplicationSummaryDto(
            app.id, app.name, app.runtimeType,
            app.onboardedAt, app.onboardingPrUrl
        );
    }
}
```

### ApplicationService — Implementation

```java
@ApplicationScoped
public class ApplicationService {

    @Inject
    TeamContext teamContext;

    public List<Application> getTeamApplications() {
        return Application.findByTeam(teamContext.getTeamId());
    }

    public Application getApplicationById(Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamContext.getTeamId())) {
            throw new NotFoundException();
        }
        return app;
    }
}
```

`Application.findByTeam(Long teamId)` already exists from Story 2.1 — it returns applications ordered by name. Verify the ordering. If `findByTeam()` doesn't sort by name, adjust the service to sort: `.stream().sorted(Comparator.comparing(a -> a.name)).toList()` or modify the entity query.

### Frontend: AppShell Wiring — Critical Gap

`AppShell.tsx` currently renders `<Sidebar />` with **no applications prop**. This is the primary frontend gap.

**Approach:** Add `useApiFetch` in `AppShell` to fetch applications from the team API, then pass to `Sidebar`. The `teamId` is available from `useParams()` or `useAuth().teamId`.

```tsx
// In AppShell.tsx
const { teamId } = useParams<{ teamId: string }>();
const authTeamId = useAuth().teamId;
const resolvedTeamId = teamId ?? authTeamId;

const { data: applications } = useApiFetch<ApplicationSummary[]>(
  `/api/v1/teams/${resolvedTeamId}/applications`
);

const sidebarApps: SidebarApp[] = (applications ?? []).map((app) => ({
  id: String(app.id),
  name: app.name,
}));

// Then in JSX:
<Sidebar applications={sidebarApps} />
```

**Issue with `useParams` in `AppShell`:** `AppShell` is the layout component for all routes. The `teamId` param may not be in the URL for all routes (e.g., `/admin/clusters`). Use `useAuth().teamId` as a fallback. The `useAuth()` hook returns `teamId: 'default'` in dev mode — this resolves to the team ID via the `TeamContextFilter` on the backend.

**Important:** `useApiFetch` fires on mount and re-fires when the path changes. Since `resolvedTeamId` is consistent across team routes, the fetch only happens once on shell mount and when team context changes. This is acceptable — no polling, fresh on mount.

### Frontend: TeamDashboardPage Conditional Rendering

The current `TeamDashboardPage` always shows `NoApplicationsEmptyState`. Update it to fetch applications and conditionally render:

```tsx
export function TeamDashboardPage() {
  const { teamId } = useParams<{ teamId: string }>();
  const {
    data: applications,
    error,
    isLoading,
    refresh,
  } = useApiFetch<ApplicationSummary[]>(
    `/api/v1/teams/${teamId}/applications`
  );

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

  if (!applications || applications.length === 0) {
    return (
      <PageSection>
        <NoApplicationsEmptyState />
      </PageSection>
    );
  }

  return (
    <PageSection>
      <Title headingLevel="h1">
        Team Dashboard
        <RefreshButton onRefresh={refresh} isRefreshing={isLoading} aria-label="Refresh dashboard" />
      </Title>
      <p>{applications.length} application{applications.length !== 1 ? 's' : ''} onboarded</p>
    </PageSection>
  );
}
```

The full Team Dashboard is an Epic 7 feature. For now, show a simple summary and the application count. The sidebar provides the primary navigation to individual apps.

### Frontend: AppBreadcrumb — Show App Name

The current `AppBreadcrumb` displays `appId` (raw numeric ID) when inside an application context. Update to show the human-readable application name.

**Option chosen:** Pass application name through route state or context. The simplest approach:
- `AppShell` already fetches the applications list
- Create a React context (or prop drill through `Outlet` context) to share the applications list with child components
- `AppBreadcrumb` looks up the app name from the applications list by matching `appId`

**Implementation with Outlet context:**

```tsx
// In AppShell.tsx — pass applications via Outlet context
<Outlet context={{ applications: sidebarApps }} />

// In AppBreadcrumb.tsx — consume from nearest Outlet
import { useOutletContext } from 'react-router-dom';

interface OutletContextType {
  applications?: SidebarApp[];
}

export function AppBreadcrumb() {
  const { teamId, appId } = useParams();
  const context = useOutletContext<OutletContextType>() ?? {};
  const { teamName } = useAuth();

  const appName = context.applications?.find(a => a.id === appId)?.name ?? appId;
  // ... render with appName instead of appId
}
```

**Caveat:** `useOutletContext` returns the context from the **nearest parent** `Outlet`. `AppBreadcrumb` is rendered in `AppShell` directly (not inside an Outlet), so this approach won't work as-is. Alternative: lift the applications state into a React Context provider that wraps the entire app.

**Simpler approach — ApplicationContext provider:**

Create a minimal `ApplicationsContext` that AppShell populates and any descendant can consume:

```tsx
// contexts/ApplicationsContext.tsx
const ApplicationsContext = createContext<SidebarApp[]>([]);
export const useApplicationsList = () => useContext(ApplicationsContext);
export const ApplicationsProvider = ApplicationsContext.Provider;
```

AppShell wraps its content in `<ApplicationsProvider value={sidebarApps}>`. AppBreadcrumb uses `useApplicationsList()` to look up the name.

### Frontend: ApplicationOverviewPage

Currently a placeholder. Update to show basic application information:

```tsx
export function ApplicationOverviewPage() {
  const { teamId, appId } = useParams();
  const { data: applications, isLoading, error } = useApiFetch<ApplicationSummary[]>(
    `/api/v1/teams/${teamId}/applications`
  );

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

  const app = applications?.find(a => String(a.id) === appId);
  if (!app) return <ErrorAlert error={{ error: 'not-found', message: 'Application not found', timestamp: new Date().toISOString() }} />;

  return (
    <PageSection>
      <Title headingLevel="h2">{app.name}</Title>
      <DescriptionList isHorizontal>
        <DescriptionListGroup>
          <DescriptionListTerm>Runtime</DescriptionListTerm>
          <DescriptionListDescription>{app.runtimeType}</DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Onboarded</DescriptionListTerm>
          <DescriptionListDescription>{new Date(app.onboardedAt).toLocaleDateString()}</DescriptionListDescription>
        </DescriptionListGroup>
        {app.onboardingPrUrl && (
          <DescriptionListGroup>
            <DescriptionListTerm>Onboarding PR</DescriptionListTerm>
            <DescriptionListDescription>
              <Button variant="link" component="a" href={app.onboardingPrUrl} target="_blank">
                View onboarding PR ↗
              </Button>
            </DescriptionListDescription>
          </DescriptionListGroup>
        )}
      </DescriptionList>
      <p style={{ marginTop: 'var(--pf-v6-global--spacer--lg)' }}>
        Environment chain visualization coming in Story 2.8.
      </p>
    </PageSection>
  );
}
```

**Alternative:** Instead of re-fetching the full list, use the `ApplicationsContext` from AppShell to look up the current app. This avoids a duplicate API call. If more detail is needed later (ApplicationDetailDto with environments), a dedicated GET `/api/v1/teams/{teamId}/applications/{appId}` endpoint can be added — but that is NOT in scope for this story.

### Frontend Types — `types/application.ts`

```tsx
export interface ApplicationSummary {
  id: number;
  name: string;
  runtimeType: string;
  onboardedAt: string;
  onboardingPrUrl: string;
}
```

### Frontend API — `api/applications.ts`

```tsx
import { apiFetch } from './client';
import type { ApplicationSummary } from '../types/application';

export function fetchApplications(teamId: string): Promise<ApplicationSummary[]> {
  return apiFetch<ApplicationSummary[]>(`/api/v1/teams/${teamId}/applications`);
}
```

### Backend Unit Test — ApplicationServiceTest

```java
class ApplicationServiceTest {
    private ApplicationService service;
    private TeamContext mockTeamContext;

    @BeforeEach
    void setUp() throws Exception {
        mockTeamContext = mock(TeamContext.class);
        when(mockTeamContext.getTeamId()).thenReturn(1L);

        service = new ApplicationService();
        Field tcField = ApplicationService.class.getDeclaredField("teamContext");
        tcField.setAccessible(true);
        tcField.set(service, mockTeamContext);
    }

    // Test getTeamApplications delegates to Application.findByTeam
    // Test getApplicationById returns app when team matches
    // Test getApplicationById throws NotFoundException for different team
    // Test getApplicationById throws NotFoundException for null result
}
```

**Note:** Testing Panache static methods in unit tests requires `@QuarkusTest` or PanacheMock. For true unit tests without Quarkus, mock at a higher level or use integration tests for entity queries. Follow the pattern established in earlier stories — check if `ApplicationServiceTest` uses PanacheMock or if all entity-query testing is deferred to IT tests.

### Backend Integration Test — ApplicationResourceIT

```java
@QuarkusTest
class ApplicationResourceIT {

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "default"),
        @Claim(key = "role", value = "member")
    })
    void listApplicationsReturnsTeamApps() {
        // Pre-condition: team and applications seeded via Flyway or test setup
        given()
        .when()
            .get("/api/v1/teams/{teamId}/applications", getTeamId())
        .then()
            .statusCode(200)
            .body("$.size()", greaterThanOrEqualTo(0));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "default"),
        @Claim(key = "role", value = "member")
    })
    void listApplicationsReturnsEmptyForTeamWithNoApps() {
        given()
        .when()
            .get("/api/v1/teams/{teamId}/applications", getTeamId())
        .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "other-team"),
        @Claim(key = "role", value = "member")
    })
    void listApplicationsCrossTeamReturns404() {
        given()
        .when()
            .get("/api/v1/teams/{teamId}/applications", getOtherTeamId())
        .then()
            .statusCode(404);
    }
}
```

**Test data strategy:** The IT test needs at least one Application entity in the database. Either:
1. Use a Flyway test migration (`V999__test_data.sql`) in `src/test/resources/`
2. Use `@Transactional` setup in `@BeforeEach` to persist test entities
3. Call the onboarding endpoint first (requires 2.1–2.5 to be complete)

Option 2 is the most isolated and doesn't depend on other stories.

### UX Specification Compliance

| UX Requirement | Implementation |
|---|---|
| Sidebar: team selector (top), app list (middle), "+ Onboard Application" (bottom) | Already implemented in `Sidebar.tsx` — just needs `applications` prop wired |
| Sidebar highlights current application | Already implemented via `isActive={appId === app.id}` in NavItem |
| Switching apps preserves current tab | Already implemented via `deriveTabFromPath()` in Sidebar |
| Breadcrumbs: Team → Application → View | `AppBreadcrumb` exists — needs app name lookup instead of raw ID |
| Tab bar: Overview, Builds, Releases, Environments, Health, Settings | Already implemented in `ApplicationTabs.tsx` |
| Empty state: "No applications onboarded yet" + CTA | Already implemented in `NoApplicationsEmptyState.tsx` |
| Sidebar: collapsible to icon-only | Already implemented in `AppShell.tsx` with breakpoint logic |
| Sidebar width: 256px | Already set via CSS custom property in `AppShell.tsx` |

### Existing Code to Reuse

| Component | Location | Usage |
|-----------|----------|-------|
| `Application` entity | `application/Application.java` | **From Story 2.1** — `findByTeam(Long teamId)` returns list ordered by name |
| `TeamContext` | `auth/TeamContext.java` | `getTeamId()` for scoping queries, `getTeamIdentifier()` for team name |
| `PermissionFilter` | `auth/PermissionFilter.java` | Already handles `applications` resource with `read` action |
| Casbin policy | `casbin/policy.csv` | `(member, applications, read)` — already allows |
| `GlobalExceptionMapper` | `common/GlobalExceptionMapper.java` | NotFoundException → 404 |
| `Sidebar` | `components/layout/Sidebar.tsx` | Already accepts `SidebarApp[]` prop — ready to wire |
| `AppBreadcrumb` | `components/layout/AppBreadcrumb.tsx` | Needs app name lookup — minor enhancement |
| `ApplicationTabs` | `components/layout/ApplicationTabs.tsx` | Already functional — no changes needed |
| `ApplicationLayout` | `components/layout/ApplicationLayout.tsx` | Already renders tabs + Outlet — no changes needed |
| `NoApplicationsEmptyState` | `components/shared/NoApplicationsEmptyState.tsx` | Already functional — reuse in TeamDashboardPage conditional |
| `useApiFetch` | `hooks/useApiFetch.ts` | Generic data-fetching hook — use for application list |
| `apiFetch` | `api/client.ts` | HTTP wrapper with auth — use in api/applications.ts |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Error display |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Loading state |
| `RefreshButton` | `components/shared/RefreshButton.tsx` | Manual refresh action |

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/application/
├── ApplicationResource.java
├── ApplicationService.java
└── ApplicationSummaryDto.java

src/test/java/com/portal/application/
├── ApplicationServiceTest.java
└── ApplicationResourceIT.java
```

**New frontend files:**
```
src/main/webui/src/types/application.ts
src/main/webui/src/api/applications.ts
src/main/webui/src/contexts/ApplicationsContext.tsx
```

**Modified frontend files:**
```
src/main/webui/src/components/layout/AppShell.tsx         (fetch apps, pass to Sidebar, provide context)
src/main/webui/src/components/layout/AppBreadcrumb.tsx    (show app name from context)
src/main/webui/src/routes/TeamDashboardPage.tsx           (conditional: apps list vs empty state)
src/main/webui/src/routes/ApplicationOverviewPage.tsx     (show app info instead of placeholder)
```

**Modified frontend test files:**
```
src/main/webui/src/components/layout/Sidebar.test.tsx               (verify app list rendering)
src/main/webui/src/components/layout/AppBreadcrumb.test.tsx         (verify app name in breadcrumb)
src/main/webui/src/routes/TeamDashboardPage.test.tsx                (test conditional rendering)
src/main/webui/src/routes/ApplicationOverviewPage.test.tsx          (test app info display)
```

### Previous Story Intelligence

**Story 2.1 (Application & Environment Data Model):**
- Application entity at `com.portal.application.Application` extends `PanacheEntityBase`
- Fields: `id` (Long, `@GeneratedValue(IDENTITY)`), `name`, `teamId` (Long FK → teams), `gitRepoUrl`, `runtimeType`, `onboardingPrUrl` (nullable), `onboardedAt` (nullable Instant), `createdAt`, `updatedAt`
- `findByTeam(Long teamId)` returns `List<Application>` — verify sort order is by name
- Unique constraint `uq_applications_team_id_name` — no duplicate app names per team
- Index `idx_applications_team_id` — efficient team-scoped queries
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` is mandatory for all entities

**Story 2.5 (Onboarding PR Creation & Completion):**
- OnboardingService.confirmOnboarding persists Application entity with name, teamId, gitRepoUrl, runtimeType, onboardingPrUrl, onboardedAt
- This is how applications get into the database — the list endpoint in this story returns what was persisted during onboarding

**Epic 1 patterns:**
- `@ApplicationScoped` for all service beans
- `@RequestScoped` for TeamContext
- Inject via `@Inject` field injection
- Unit tests mock dependencies via reflection (`Field.setAccessible(true)`)
- Integration tests use `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` + REST Assured
- Frontend tests use Vitest + RTL; query by role/label, not CSS class
- PF6 components exclusively; PF CSS tokens for styling

**TeamResource pattern (direct analog for ApplicationResource):**
- `@Path("/api/v1/teams")`, `@Produces(MediaType.APPLICATION_JSON)`
- Inject `TeamContext`, service
- GET returns `List<SummaryDto>`, maps via `SummaryDto.from(entity)`
- Cross-team check: `teamContext.getTeamId().equals(teamId)` → `NotFoundException` on mismatch

### What NOT to Build in This Story

- **No single-app GET endpoint** — GET `/api/v1/teams/{teamId}/applications/{appId}` is architecturally planned but not in the AC for this story. The list endpoint is sufficient for sidebar + overview
- **No application CRUD (update, delete)** — read-only for this story
- **No environment chain visualization** — that's Story 2.8
- **No environment status from ArgoCD** — that's Story 2.7
- **No build/release data** — those are Epic 4 features
- **No team dashboard with DORA metrics** — that's Epic 7; dashboard shows simple app count for now
- **No full application detail page** — overview shows basic info from the list DTO; deeper details in later stories
- **No search/filter/sort on application list** — not in AC; alphabetical order from backend is sufficient

### References

- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.6 (line 803)] — Full acceptance criteria
- [Source: planning-artifacts/architecture.md § REST API resource structure (line 466)] — `/api/v1/teams/{teamId}/applications` endpoint path
- [Source: planning-artifacts/architecture.md § Structure Patterns (line 640)] — `com.portal.application` package: ApplicationResource, ApplicationService, ApplicationSummaryDto
- [Source: planning-artifacts/architecture.md § Frontend organization (line 662)] — routes/ApplicationOverviewPage, api/applications.ts, hooks/useApplications.ts, types/application.ts
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure (line 787)] — application/ package contents, layout/ components (AppShell, Sidebar, AppBreadcrumb)
- [Source: planning-artifacts/architecture.md § HTTP status codes (line 699)] — 404 for both missing AND cross-team resources
- [Source: planning-artifacts/architecture.md § Data Flow (line 1095)] — PermissionFilter → TeamContextFilter → Resource → Service → Entity chain
- [Source: planning-artifacts/ux-design-specification.md § Navigation Layout (line 508)] — Sidebar: team selector, app list, onboard CTA; breadcrumbs: Team → App → View; tab bar within app context
- [Source: planning-artifacts/ux-design-specification.md § Navigation Consistency (line 1277)] — Global sidebar, breadcrumb orientation, tab preservation on app switch
- [Source: planning-artifacts/ux-design-specification.md § Empty States (line 1240)] — "No applications onboarded yet" with onboard CTA
- [Source: planning-artifacts/ux-design-specification.md § Transferable Patterns (line 227)] — Tab-based contextual navigation, sidebar + breadcrumb orientation
- [Source: planning-artifacts/ux-design-specification.md § Application Overview (line 587)] — Env chain card row at top, two-column grid below (future)
- [Source: planning-artifacts/ux-design-specification.md § Design System Components (line 891)] — Page, Nav, Breadcrumb, Tabs, EmptyState PF components
- [Source: planning-artifacts/ux-design-specification.md § Action Hierarchy (line 1150)] — Onboard Application as secondary action in sidebar
- [Source: project-context.md § Technology Stack] — Quarkus 3.34.x, PF6 v6.4.1, React 18, TypeScript strict
- [Source: project-context.md § Framework-Specific Rules] — Domain-centric packages, no cross-package entity imports, REST → Service → Adapter chain
- [Source: project-context.md § Testing Rules] — Unit: Class.Test.java, IT: Class.IT.java, Frontend: Component.test.tsx
- [Source: project-context.md § Anti-Patterns] — Return 404 for cross-team access, no custom CSS, PF components exclusively
- [Source: casbin/policy.csv line 3] — `p, member, applications, read` — permission already exists
- [Source: implementation-artifacts/2-5-onboarding-pr-creation-completion.md] — Application persistence during onboarding confirm
- [Source: implementation-artifacts/2-1-application-environment-data-model.md] — Application entity, findByTeam, unique constraints

## Dev Agent Record

### Agent Model Used
claude-4.6-opus-high-thinking

### Debug Log References
None — clean implementation with no debug cycles required.

### Completion Notes List
- **Task 1**: ApplicationSummaryDto already existed from Story 2.1 with all required fields (plus extras: teamId, gitRepoUrl, createdAt, updatedAt). Kept as-is; AC only requires presence of id, name, runtimeType, onboardedAt, onboardingPrUrl — all present.
- **Task 2**: Created ApplicationService with team-scoped getTeamApplications() and getApplicationById() with cross-team NotFoundException guard.
- **Task 3**: Created ApplicationResource at /api/v1/teams/{teamId}/applications with GET endpoint. TeamContext.getTeamId().equals(teamId) check matches TeamResource pattern.
- **Task 4-5**: Created ApplicationSummary TypeScript interface and fetchApplications API function.
- **Task 6**: Key architectural decision — created ApplicationsContext (React Context) to share application list across components. AppShell fetches teams first (to resolve numeric team ID from "default" route param), then fetches applications, provides via context. Enhanced useApiFetch to support null path for conditional fetching. Sidebar now receives applications prop from AppShell.
- **Task 7**: TeamDashboardPage now consumes ApplicationsContext — shows NoApplicationsEmptyState when empty, team name + app count summary when apps exist. Removed duplicate teams API call; uses context instead.
- **Task 8**: AppBreadcrumb now resolves app name from ApplicationsContext, falling back to raw appId if not found.
- **Task 9**: ApplicationOverviewPage shows app name heading, runtime type, onboarded date, and onboarding PR link (when present). Future environment chain message included.
- **Task 10**: ApplicationServiceTest uses @QuarkusTest + @InjectMock TeamContext pattern. 4 tests covering team apps query, ID lookup, cross-team rejection, and not-found.
- **Task 11**: ApplicationResourceIT uses @QuarkusTest + @TestSecurity + @OidcSecurity. 5 tests: list returns apps, alphabetical order, DTO field verification, empty team returns empty array, cross-team returns 404.
- **Task 12**: Created TeamDashboardPage.test.tsx (5 tests) and ApplicationOverviewPage.test.tsx (8 tests). Updated AppBreadcrumb.test.tsx to test app name resolution from context. Updated App.test.tsx to reflect new ApplicationOverviewPage behavior. Existing Sidebar.test.tsx already covers app list rendering.

### Implementation Plan
- Backend: ApplicationService → ApplicationResource → ApplicationSummaryDto (reused existing)
- Frontend: types/application.ts → api/applications.ts → contexts/ApplicationsContext.tsx → useApiFetch null-path support → AppShell wiring → component updates
- Tests: @QuarkusTest service test → REST Assured IT → Vitest + RTL component tests

### File List

**New backend files:**
- developer-portal/src/main/java/com/portal/application/ApplicationService.java
- developer-portal/src/main/java/com/portal/application/ApplicationResource.java

**New backend test files:**
- developer-portal/src/test/java/com/portal/application/ApplicationServiceTest.java
- developer-portal/src/test/java/com/portal/application/ApplicationResourceIT.java

**New frontend files:**
- developer-portal/src/main/webui/src/types/application.ts
- developer-portal/src/main/webui/src/api/applications.ts
- developer-portal/src/main/webui/src/contexts/ApplicationsContext.tsx

**New frontend test files:**
- developer-portal/src/main/webui/src/routes/TeamDashboardPage.test.tsx
- developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx

**Modified frontend files:**
- developer-portal/src/main/webui/src/hooks/useApiFetch.ts (null-path support for conditional fetching)
- developer-portal/src/main/webui/src/components/layout/AppShell.tsx (fetch teams/apps, provide context, wire Sidebar)
- developer-portal/src/main/webui/src/components/layout/AppBreadcrumb.tsx (app name from context)
- developer-portal/src/main/webui/src/routes/TeamDashboardPage.tsx (conditional: apps list vs empty state)
- developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx (app info display)

**Modified frontend test files:**
- developer-portal/src/main/webui/src/components/layout/AppBreadcrumb.test.tsx (context-aware tests)
- developer-portal/src/main/webui/src/App.test.tsx (updated for new overview page behavior)

## Change Log
- 2026-04-06: Story 2.6 implemented — Application list API, sidebar wiring, app overview page, breadcrumb name resolution, and all tests.
