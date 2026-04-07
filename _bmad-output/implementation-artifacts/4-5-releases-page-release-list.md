# Story 4.5: Releases Page & Release List

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to view all releases for my application with their version, creation date, associated build, and image reference,
So that I can see what's available for deployment and track my release history.

## Acceptance Criteria

1. **GET releases endpoint returns all releases for an application, most recent first**
   - **Given** a developer navigates to the Releases tab for an application
   - **When** GET `/api/v1/teams/{teamId}/applications/{appId}/releases` is called
   - **Then** all releases for the application are returned, ordered by creation date descending
   - **And** each release includes: version, createdAt, commitSha, imageReference
   - **And** buildId is included when recoverable from the tag metadata, otherwise null

2. **ApplicationReleasesPage renders a compact PatternFly Table of releases**
   - **Given** the ApplicationReleasesPage renders
   - **When** releases exist
   - **Then** a PatternFly Table (compact variant) displays all releases
   - **And** each row shows: version tag, creation date, commit SHA (truncated to 7 chars, monospace, full SHA on hover/tooltip), and container image reference (monospace)

3. **Release display uses developer-friendly formatting**
   - **Given** a release row is displayed
   - **When** the developer reviews it
   - **Then** the version tag is the primary identifier (e.g., "v1.4.2")
   - **And** the commit SHA uses monospace font with 7-char truncation and full SHA in a PatternFly Tooltip
   - **And** the image reference shows the full `registry/repository:tag` format in monospace

4. **Empty state follows portal conventions**
   - **Given** no releases exist for the application
   - **When** the releases page renders
   - **Then** a PatternFly EmptyState is shown with title: "No releases yet"
   - **And** description: "Create a release from a successful build to start deploying."

5. **Loading, error, and refresh states follow shared portal patterns**
   - **Given** release data is loading or a backend/integration error occurs
   - **Then** the page uses shared loading (Spinner) and inline alert (Alert) patterns already used elsewhere in the SPA
   - **And** a RefreshButton allows manual re-fetch

6. **Release list provides the data foundation for Epic 5 deployment actions**
   - **Given** the release list is populated
   - **When** Epic 5 (Deployment) is implemented
   - **Then** each release row provides a deployable artifact with a known version and image reference suitable for adding a "Deploy" action

7. **Casbin authorization permits member, lead, and admin roles to read releases**
   - **Given** the Casbin permission check runs
   - **When** a developer with any team role reads the releases list
   - **Then** the request is permitted (existing `releases, read` policy)

## Tasks / Subtasks

- [ ] Task 1: Add `listTags` to GitProvider interface and all implementations (AC: #1)
  - [ ] Add `GitTag` record to `com.portal.integration.git.model` with fields: `name` (String), `commitSha` (String), `createdAt` (Instant)
  - [ ] Add `List<GitTag> listTags(String repoUrl)` to `GitProvider.java` interface
  - [ ] Implement in `GitHubProvider.java` via `GET /repos/{owner}/{repo}/tags` — extract `name`, `commit.sha`; date from tag annotation or commit date
  - [ ] Implement in `GitLabProvider.java` via `GET /projects/:id/repository/tags` — extract `name`, `commit.id`, `commit.committed_date`
  - [ ] Implement in `GiteaProvider.java` via `GET /repos/{owner}/{repo}/tags` — extract `name`, `commit.sha`, date
  - [ ] Implement in `BitbucketProvider.java` via `GET /repositories/{workspace}/{repo_slug}/refs/tags` — extract `name`, `target.hash`, `target.date`
  - [ ] Implement in `DevGitProvider.java` — return mock list of 3-5 sample tags with realistic data

- [ ] Task 2: Add GET endpoint to ReleaseResource (AC: #1, #7)
  - [ ] Add `GET` method to `ReleaseResource.java` at `/api/v1/teams/{teamId}/applications/{appId}/releases`
  - [ ] Returns `List<ReleaseSummaryDto>` with 200 OK
  - [ ] Verify Casbin `releases, read` permission is already in `policy.csv` (it is — `p, member, releases, read`)

- [ ] Task 3: Add `listReleases` to ReleaseService (AC: #1, #6)
  - [ ] Add `listReleases(Long teamId, Long appId)` to `ReleaseService.java`
  - [ ] Resolve application (team-scoped, 404 for cross-team access)
  - [ ] Call `GitProvider.listTags(app.gitRepoUrl)` to get all release tags
  - [ ] For each tag: construct `ReleaseSummaryDto` with version, createdAt, commitSha, imageReference
  - [ ] Construct image reference using `RegistryConfig.url()` + app naming convention: `{registryUrl}/{teamName}/{appName}:{version}`
  - [ ] If `registryConfig.url()` is empty, set imageReference to null
  - [ ] Sort by createdAt descending
  - [ ] Return the list (empty list if no tags found — no error)

- [ ] Task 4: Extend ReleaseSummaryDto if needed (AC: #1)
  - [ ] Verify `ReleaseSummaryDto` from Story 4.4 has: `version`, `createdAt`, `buildId`, `commitSha`, `imageReference`
  - [ ] `buildId` must be nullable — it is available when created via the portal's POST endpoint but not when listing from Git tags
  - [ ] If Story 4.4 used a Java `record`, ensure nullable fields use wrapper types or Optional

- [ ] Task 5: Add frontend release types and API helper (AC: #2, #3, #5)
  - [ ] Verify `src/main/webui/src/types/release.ts` exists from Story 4.4 with `ReleaseSummary` type
  - [ ] If not, create it with: `version: string`, `createdAt: string`, `buildId: string | null`, `commitSha: string`, `imageReference: string | null`
  - [ ] Add `fetchReleases(teamId: number, appId: number): Promise<ReleaseSummary[]>` to `src/main/webui/src/api/releases.ts` using `apiFetch()`
  - [ ] Create `src/main/webui/src/hooks/useReleases.ts` using the `useApiFetch<ReleaseSummary[]>` pattern with path `/api/v1/teams/${teamId}/applications/${appId}/releases`

- [ ] Task 6: Create ReleaseTable component (AC: #2, #3)
  - [ ] Create `src/main/webui/src/components/release/ReleaseTable.tsx`
  - [ ] PatternFly Table (compact variant) with columns: Version, Created, Commit, Image
  - [ ] Version column: plain text, primary identifier
  - [ ] Created column: formatted date/time (use `toLocaleDateString()` + `toLocaleTimeString()` or similar)
  - [ ] Commit column: 7-char truncated SHA in monospace (`fontFamily: 'var(--pf-v6-global--FontFamily--monospace)'` or PF6 utility class), full SHA in PatternFly `Tooltip`
  - [ ] Image column: full `registry/repo:tag` in monospace, show "—" if null

- [ ] Task 7: Replace ApplicationReleasesPage stub with real implementation (AC: #2, #4, #5)
  - [ ] Replace stub content in `src/main/webui/src/routes/ApplicationReleasesPage.tsx`
  - [ ] Use `useParams()` to get `teamId` and `appId`
  - [ ] Use `useApiFetch` hook (or the new `useReleases` hook) to fetch releases
  - [ ] Page header: Title "Releases" + `RefreshButton`
  - [ ] Loading state: `LoadingSpinner` component (with systemName="Git" or "Portal")
  - [ ] Error state: `ErrorAlert` component inline
  - [ ] Data state: render `ReleaseTable` with the fetched data
  - [ ] Empty state: PatternFly `EmptyState` with title "No releases yet", description "Create a release from a successful build to start deploying."
  - [ ] No primary action button on this page — releases are created from the Builds page (Story 4.4)

- [ ] Task 8: Write backend tests (AC: #1, #2, #7)
  - [ ] Add `listTags` tests to existing Git provider test files (at minimum `GitHubProviderTest.java`)
  - [ ] Verify mock HTTP response parsing, empty tags list, and error handling (404 repo, network failure)
  - [ ] Extend `ReleaseServiceTest.java` (`@QuarkusTest` + `@InjectMock`): test `listReleases()` with mocked GitProvider returning tags
  - [ ] Test team-scoped 404 for cross-team application access
  - [ ] Test empty list when no tags exist
  - [ ] Test `PortalIntegrationException` when GitProvider fails (→ 502)
  - [ ] Extend `ReleaseResourceIT.java`: REST Assured test for GET endpoint, verify 200 response, array format, 404 for cross-team

- [ ] Task 9: Write frontend tests (AC: #2, #3, #4, #5)
  - [ ] Create `src/main/webui/src/components/release/ReleaseTable.test.tsx`: render with data, verify columns, monospace rendering, truncated SHA, tooltip
  - [ ] Create `src/main/webui/src/routes/ApplicationReleasesPage.test.tsx`: test loading, success, error, and empty states
  - [ ] Mock API responses at the `apiFetch()` level
  - [ ] Account for React 18 StrictMode in effect-driven fetch logic

## Dev Notes

### Critical Dependency: Stories 4.1–4.4 Must Be Implemented First

This story extends the release backend and frontend infrastructure from Stories 4.1–4.4. The following must exist before starting:

- `ReleaseResource.java` with POST endpoint (Story 4.4)
- `ReleaseService.java` with `createRelease()` method (Story 4.4)
- `ReleaseSummaryDto.java` and `CreateReleaseRequest.java` (Story 4.4)
- `RegistryAdapter.java` interface + `RegistryOciAdapter` + `DevRegistryAdapter` (Story 4.4)
- `RegistryConfig.java` with `portal.registry.url` (Story 4.4)
- `GitProvider.createTag()` across all implementations (Story 4.4)
- `BuildResource`, `BuildService`, `TektonAdapter` infrastructure (Stories 4.1–4.2)
- `ApplicationBuildsPage.tsx` with builds table (Story 4.3)
- Frontend `types/release.ts`, `api/releases.ts` with `createRelease()` (Story 4.4)

**Pre-flight check:** verify all Story 4.1–4.4 artifacts are implemented and all tests pass before starting.

### CRITICAL: Releases Are NOT Database-Persisted

The portal is NOT the source of truth for releases. A release is a Git tag + container image tag — both exist in external systems. The release list is populated live on every request by querying Git tags via `GitProvider.listTags()`.

This means:
- **No Flyway migration** — no `releases` table in the database
- **No Panache entity** — no `Release.java` extending `PanacheEntityBase`
- **List comes from Git tags** — `GitProvider.listTags(repoUrl)` is the data source
- **Image reference is derived** — constructed from `RegistryConfig.url()` + team/app naming convention, not fetched from the registry
- **buildId is nullable** — it is known at creation time (Story 4.4) but NOT recoverable from Git tags. Show "—" in the UI when null.

### GitProvider.listTags() — New Interface Method

Add `listTags` to the `GitProvider` interface. This is a cross-cutting change affecting all five implementations.

```java
List<GitTag> listTags(String repoUrl);
```

**GitTag model:**

```java
package com.portal.integration.git.model;

public record GitTag(
    String name,
    String commitSha,
    Instant createdAt
) {}
```

**GitHub:** `GET /repos/{owner}/{repo}/tags` → returns `[{"name": "v1.4.2", "commit": {"sha": "abc123..."}}]`. Note: the tags endpoint does not return dates directly. To get the tag creation date, use `GET /repos/{owner}/{repo}/git/refs/tags/{tagName}` to get the ref, then `GET /repos/{owner}/{repo}/git/tags/{sha}` for annotated tags, or use the commit date from `commit.url`. **Simplest for MVP:** use the commit date from `GET /repos/{owner}/{repo}/git/ref/tags/{tagName}` or fetch each tag's commit to get its date. Alternatively, use `GET /repos/{owner}/{repo}/tags` which returns commit objects — make a single follow-up `GET /repos/{owner}/{repo}/commits/{sha}` to get dates if needed.

**Practical simplification:** GitHub's `GET /repos/{owner}/{repo}/tags` returns the tag name and commit SHA but NOT the creation date. For MVP, use the GitHub GraphQL API (`refs(refPrefix: "refs/tags/")`) which returns `target { oid committedDate }`, OR fall back to using `GET /repos/{owner}/{repo}/git/refs/tags` + commit lookup. The REST approach is: (1) call `/repos/{owner}/{repo}/tags` for names+SHAs, (2) batch commit lookups for dates. For DevGitProvider, generate deterministic mock dates.

**GitLab:** `GET /projects/:id/repository/tags` → returns `[{"name": "v1.4.2", "commit": {"id": "abc123...", "committed_date": "2026-04-01T12:00:00Z"}}]` — dates included directly.

**Gitea:** `GET /repos/{owner}/{repo}/tags` → returns `[{"name": "v1.4.2", "id": "abc123...", "commit": {"sha": "def456..."}}]`. Dates available via the commit object.

**Bitbucket:** `GET /repositories/{workspace}/{repo_slug}/refs/tags` → returns `{"values": [{"name": "v1.4.2", "target": {"hash": "abc123...", "date": "2026-04-01T12:00:00+00:00"}}]}`.

**DevGitProvider:** Return a mock list of 3-5 tags with realistic data:

```java
@Override
public List<GitTag> listTags(String repoUrl) {
    return List.of(
        new GitTag("v1.2.0", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", Instant.parse("2026-04-07T10:00:00Z")),
        new GitTag("v1.1.0", "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3", Instant.parse("2026-04-05T14:30:00Z")),
        new GitTag("v1.0.0", "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", Instant.parse("2026-04-01T09:00:00Z"))
    );
}
```

**Error handling:** If the repo is unreachable or the API returns an error, throw `PortalIntegrationException` with `system="git"`, `operation="list-tags"`. The GlobalExceptionMapper converts this to 502 with standardized error JSON.

### ReleaseService.listReleases() — Orchestration

```java
public List<ReleaseSummaryDto> listReleases(Long teamId, Long appId) {
    Application app = resolveTeamApplication(teamId, appId);

    List<GitTag> tags = gitProvider.listTags(app.gitRepoUrl);

    return tags.stream()
        .sorted(Comparator.comparing(GitTag::createdAt).reversed())
        .map(tag -> new ReleaseSummaryDto(
            tag.name(),
            tag.createdAt(),
            null,    // buildId — not recoverable from Git tags
            tag.commitSha(),
            buildImageReference(tag.name())
        ))
        .toList();
}

private String buildImageReference(String version) {
    return registryConfig.url()
        .map(url -> url + ":" + version)
        .orElse(null);
}
```

**Team-scoped access:** follows the same `Application.findById()` + `teamId` check pattern from `BuildService` and the existing `createRelease` method.

**Image reference construction:** Use `RegistryConfig.url()` which contains the base image path. The convention is `{registryUrl}:{version}`. If `portal.registry.url` includes the full image name (e.g., `registry.example.com/team/app`), then the image reference becomes `registry.example.com/team/app:v1.4.2`. If the registry URL is not configured (empty Optional), imageReference is null.

**Note:** If the Application entity gains an `imageBasePath` field in a future story, use that instead of RegistryConfig.url() for per-app image reference construction.

### ReleaseResource — GET Endpoint

```java
@GET
public List<ReleaseSummaryDto> listReleases(@PathParam("teamId") Long teamId,
                                             @PathParam("appId") Long appId) {
    return releaseService.listReleases(teamId, appId);
}
```

Returns **200 OK** with a JSON array. Empty array for no releases (not 404). The `@Path` and `@Produces` are already on the class from Story 4.4.

### ReleaseSummaryDto Compatibility

Story 4.4 defines `ReleaseSummaryDto` as:

```java
public record ReleaseSummaryDto(
    String version,
    Instant createdAt,
    String buildId,
    String commitSha,
    String imageReference
) {}
```

`buildId` must accept null — Java records allow null values for reference types. In JSON, null fields serialize as `null`. The frontend type should have `buildId: string | null`.

### Frontend: Release Types

Verify or create `src/main/webui/src/types/release.ts`:

```typescript
export interface ReleaseSummary {
  version: string;
  createdAt: string;
  buildId: string | null;
  commitSha: string;
  imageReference: string | null;
}
```

### Frontend: API Helper

Add to `src/main/webui/src/api/releases.ts` (which may already have `createRelease` from Story 4.4):

```typescript
export function fetchReleases(teamId: number, appId: number): Promise<ReleaseSummary[]> {
  return apiFetch<ReleaseSummary[]>(`/api/v1/teams/${teamId}/applications/${appId}/releases`);
}
```

### Frontend: useReleases Hook

Create `src/main/webui/src/hooks/useReleases.ts` using the existing `useApiFetch` pattern:

```typescript
import { useApiFetch } from './useApiFetch';
import type { ReleaseSummary } from '../types/release';

export function useReleases(teamId: string | undefined, appId: string | undefined) {
  const path = teamId && appId
    ? `/api/v1/teams/${teamId}/applications/${appId}/releases`
    : null;
  return useApiFetch<ReleaseSummary[]>(path);
}
```

Pass `null` when params are undefined — `useApiFetch` skips the fetch in that case (see existing implementation).

### Frontend: ReleaseTable Component

Create `src/main/webui/src/components/release/ReleaseTable.tsx`:

**PatternFly 6 Table (compact variant) with columns:**

| Column | Content | Style |
|--------|---------|-------|
| Version | Tag name (e.g., "v1.4.2") | Default font, bold or strong emphasis |
| Created | Formatted date and time | Default font |
| Commit | 7-char truncated SHA | Monospace (`Red Hat Mono`), full SHA in `Tooltip` |
| Image | Full registry/repo:tag | Monospace, "—" if null |

Use PatternFly `Table` with `TableVariant.compact`. Use PatternFly `Tooltip` component for the full commit SHA on hover. Apply monospace styling via the PatternFly global monospace font variable.

```
┌───────────┬─────────────────────┬──────────┬────────────────────────────────┐
│ Version   │ Created             │ Commit   │ Image                          │
├───────────┼─────────────────────┼──────────┼────────────────────────────────┤
│ v1.2.0    │ Apr 7, 2026 10:00   │ a1b2c3d  │ registry.ex.../app:v1.2.0     │
│ v1.1.0    │ Apr 5, 2026 14:30   │ b2c3d4e  │ registry.ex.../app:v1.1.0     │
│ v1.0.0    │ Apr 1, 2026 09:00   │ c3d4e5f  │ registry.ex.../app:v1.0.0     │
└───────────┴─────────────────────┴──────────┴────────────────────────────────┘
```

### Frontend: ApplicationReleasesPage

Replace the current stub at `src/main/webui/src/routes/ApplicationReleasesPage.tsx`.

Follow the same page pattern as `ApplicationOverviewPage.tsx`:
1. `useParams()` for `teamId`, `appId`
2. Fetch data via hook
3. Loading → Spinner
4. Error → inline Alert
5. Data → ReleaseTable
6. Empty → PatternFly EmptyState

Page header with title "Releases" + `RefreshButton` in a `Flex` layout (same pattern as overview page).

**No primary action button** on this page — release creation happens on the Builds page (Story 4.4). The UX spec action hierarchy does not define a primary action for the Releases tab.

### Casbin Policy — Already Configured

`policy.csv` includes:

```
p, member, releases, read
```

The `PermissionFilter` maps GET → `read`. Member inherits to lead → admin. **No Casbin policy changes needed.**

### File Structure Requirements

**New backend files:**

```
src/main/java/com/portal/integration/git/model/GitTag.java   (new record)
```

**Modified backend files:**

```
src/main/java/com/portal/integration/git/GitProvider.java           (add listTags method)
src/main/java/com/portal/integration/git/GitHubProvider.java        (implement listTags)
src/main/java/com/portal/integration/git/GitLabProvider.java        (implement listTags)
src/main/java/com/portal/integration/git/GiteaProvider.java         (implement listTags)
src/main/java/com/portal/integration/git/BitbucketProvider.java     (implement listTags)
src/main/java/com/portal/integration/git/DevGitProvider.java        (implement listTags mock)
src/main/java/com/portal/release/ReleaseResource.java              (add GET method)
src/main/java/com/portal/release/ReleaseService.java               (add listReleases method)
```

**New frontend files:**

```
src/main/webui/src/hooks/useReleases.ts
src/main/webui/src/components/release/ReleaseTable.tsx
src/main/webui/src/components/release/ReleaseTable.test.tsx
src/main/webui/src/routes/ApplicationReleasesPage.test.tsx
```

**Modified frontend files:**

```
src/main/webui/src/routes/ApplicationReleasesPage.tsx         (replace stub)
src/main/webui/src/api/releases.ts                            (add fetchReleases if not already there from 4.4)
src/main/webui/src/types/release.ts                           (verify ReleaseSummary type; add if missing)
```

**New test files:**

```
src/test/java/com/portal/integration/git/GitHubProviderTest.java    (extend with listTags tests)
src/test/java/com/portal/release/ReleaseServiceTest.java            (extend with listReleases tests)
src/test/java/com/portal/release/ReleaseResourceIT.java             (extend with GET tests)
```

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `ApplicationReleasesPage.tsx` | `src/main/webui/src/routes/` | EXISTS — stub, replace contents |
| `App.tsx` route wiring | `/teams/:teamId/apps/:appId/releases` | EXISTS — already wired |
| `ApplicationTabs.tsx` | `src/main/webui/src/components/layout/` | EXISTS — "Releases" tab already present |
| `ReleaseResource.java` | `com.portal.release` | EXISTS from Story 4.4 — add GET method |
| `ReleaseService.java` | `com.portal.release` | EXISTS from Story 4.4 — add listReleases method |
| `ReleaseSummaryDto.java` | `com.portal.release` | EXISTS from Story 4.4 — verify nullable buildId |
| `RegistryConfig.java` | `com.portal.integration.registry` | EXISTS from Story 4.4 — use `url()` for image reference |
| `RegistryAdapter.java` | `com.portal.integration.registry` | EXISTS from Story 4.4 — no changes needed |
| `GitProvider.java` | `com.portal.integration.git` | EXISTS — add listTags |
| `GitHubProvider.java` | `com.portal.integration.git` | EXISTS — add listTags |
| All other Git providers | `com.portal.integration.git` | EXISTS — add listTags to each |
| `package-info.java` | `com.portal.release`, `com.portal.integration.registry` | EXISTS — placeholders |
| `apiFetch()` | `src/main/webui/src/api/client.ts` | EXISTS — typed fetch wrapper |
| `useApiFetch` | `src/main/webui/src/hooks/useApiFetch.ts` | EXISTS — generic data-fetching hook |
| `LoadingSpinner` | `src/main/webui/src/components/shared/` | EXISTS — reuse |
| `ErrorAlert` | `src/main/webui/src/components/shared/` | EXISTS — reuse |
| `RefreshButton` | `src/main/webui/src/components/shared/` | EXISTS — reuse |
| `types/release.ts` | `src/main/webui/src/types/` | LIKELY EXISTS from Story 4.4 — verify |
| `api/releases.ts` | `src/main/webui/src/api/` | LIKELY EXISTS from Story 4.4 — verify, extend |
| Casbin `releases, read` | `casbin/policy.csv` | EXISTS — member can read releases |
| `PortalIntegrationException` | `com.portal.integration` | EXISTS |
| `GlobalExceptionMapper` | `com.portal.common` | EXISTS |
| `PermissionFilter` | `com.portal.auth` | EXISTS — maps GET → `read` |
| `TeamContext` | `com.portal.auth` | EXISTS — request-scoped team+role bean |
| `git/model/` package | `com.portal.integration.git.model` | EXISTS — has `PullRequest.java` |

### What NOT to Build

- **No database table** for releases — releases are Git tags + registry tags, fetched live
- **No Panache entity** for Release — no persistence
- **No Flyway migration** — no schema changes for this story
- **No polling, SSE, or WebSockets** — manual refresh model
- **No deploy button** on release rows — Story 5.1 handles deployment
- **No release deletion** — not in MVP scope
- **No release creation from this page** — creation happens on the Builds page (Story 4.4)
- **No filtering or search** on the releases table — future enhancement
- **No pagination** — all releases returned in a single list for MVP
- **No changelog or diff view** — not in MVP scope
- **No RegistryAdapter changes** — image references are derived from `RegistryConfig.url()` + version, not queried from the registry
- **No new Casbin policy entries** — existing `releases, read` permission covers this

### Anti-Patterns to Avoid

- **DO NOT** create a `releases` database table — releases are Git tags, fetched live
- **DO NOT** store release state in the portal database
- **DO NOT** expose Git infrastructure concepts in the UI — use "release", "version", not "tag", "ref"
- **DO NOT** call Git APIs from the frontend — all through backend REST API
- **DO NOT** bypass `apiFetch()` — use the shared client for all fetches
- **DO NOT** add polling or auto-refresh — user-initiated refresh only
- **DO NOT** cache release data between navigations — fresh data on every mount
- **DO NOT** use Spring annotations — use Quarkus/CDI
- **DO NOT** use `any` types in TypeScript — strict mode enforced
- **DO NOT** create custom CSS for PatternFly components — use PF6 design tokens
- **DO NOT** create a barrel file (`index.ts`) — import directly from source files
- **DO NOT** add a primary action button to the Releases page — releases are created from Builds

### Architecture Compliance

- Resource → Service → Adapter call chain (ReleaseResource → ReleaseService → GitProvider)
- Team-scoped access: 404 for cross-team or missing resources
- `PortalIntegrationException` for all integration failures → 502 via GlobalExceptionMapper
- Collections returned as JSON arrays (not wrapper objects)
- Empty collection → 200 with `[]` (not 404)
- Dates: ISO 8601 UTC strings in API responses — frontend formats to local time
- PatternFly 6 Table (compact variant) for the release list
- Frontend uses relative `/api/v1/...` URLs via `apiFetch()`
- Developer domain language only: "release", "version", not "tag", "manifest"
- `@ApplicationScoped` CDI beans for adapters
- No global loading state — each view section loads independently

### Testing Requirements

**Backend:**

- `GitHubProviderTest.java`: Verify `listTags` calls `GET /repos/{owner}/{repo}/tags`, parses response correctly. Test empty tags list. Test error response → `PortalIntegrationException`.
- `ReleaseServiceTest.java` (`@QuarkusTest` + `@InjectMock`): Mock `GitProvider.listTags()` returning test tags. Verify list sorting (descending by date). Verify image reference construction. Verify team-scoped 404 for cross-team app. Verify empty list for no tags. Verify `PortalIntegrationException` propagation when Git fails.
- `ReleaseResourceIT.java`: REST Assured GET test. Verify 200 response with array. Verify 404 for cross-team access. Verify 502 when GitProvider throws.

**Frontend:**

- `ReleaseTable.test.tsx`: Render with sample data. Verify all columns render. Verify commit SHA truncation. Verify monospace styling. Verify "—" for null image reference.
- `ApplicationReleasesPage.test.tsx`: Test loading state (Spinner visible). Test success state (table rendered with data). Test error state (Alert visible). Test empty state (EmptyState component with correct text). Mock API at `apiFetch()` level. Account for React 18 StrictMode double-invoke of useEffect.

### Previous Story Intelligence

**Story 4.4 (Release Creation):**
- Established `ReleaseResource`, `ReleaseService`, `ReleaseSummaryDto` in `com.portal.release`
- Established `RegistryAdapter` + `RegistryConfig` with `portal.registry.url`
- Added `GitProvider.createTag()` across all implementations — `listTags()` follows the same cross-cutting pattern
- The team-scoped application resolution pattern in `ReleaseService.createRelease()` should be extracted to a shared `resolveTeamApplication()` method for reuse by `listReleases()`
- `DevGitProvider` has a no-op `createTag` — needs a real mock for `listTags`

**Story 4.3 (Builds Page):**
- Established the frontend page pattern: hook → loading → error → data → empty state
- `ApplicationBuildsPage.tsx` demonstrates the compact table, inline actions, and page structure
- `components/build/` directory established — follow same pattern for `components/release/`

**Story 4.2 (Build Monitoring):**
- Established the `BuildSummaryDto` list endpoint pattern — `listReleases` follows same convention
- Builds list returns 200 with array, empty array for no builds

**Story 4.1 (Tekton Adapter):**
- Established `@IfBuildProperty`-based adapter switching pattern
- `DevTektonAdapter` serves as dev-mode mock reference — `DevGitProvider.listTags()` follows similar pattern

**Epic 3 Retrospective Action Items:**
- Every story must leave the test suite at **0 failures**
- When a component's API changes (e.g., `GitProvider`), update **ALL consumers** including tests
- Pre-flight test gate enforced — all tests must pass before starting work

### Data Flow for Release Listing

```
GET /api/v1/teams/{teamId}/applications/{appId}/releases
  → PermissionFilter: Casbin check (releases, read) for user role
  → TeamContextFilter: extract team from JWT, populate TeamContext
  → ReleaseResource.listReleases(teamId, appId)
  → ReleaseService.listReleases(teamId, appId)
    → Application.findById(appId) — verify team ownership, 404 if cross-team
    → GitProvider.listTags(app.gitRepoUrl)
      → (production) GitHub/GitLab/etc. API call to list tags with commit SHAs and dates
      → (dev) DevGitProvider returns mock tags
    → For each GitTag:
      → Construct imageReference from RegistryConfig.url() + tag.name
      → Map to ReleaseSummaryDto(version, createdAt, null, commitSha, imageReference)
    → Sort by createdAt descending
    → Return List<ReleaseSummaryDto>
  → 200 OK: [{ version, createdAt, buildId, commitSha, imageReference }, ...]
```

### Project Structure Notes

- `com.portal.integration.git.model/` already contains `PullRequest.java` — add `GitTag.java` here
- `ReleaseResource.java` from Story 4.4 already has `@Path`, `@Produces`, `@Consumes`, and `@Inject ReleaseService` — just add the GET method
- The `components/release/` directory may or may not exist from Story 4.4 (4.4 creates UI components under `components/build/`). Create it if needed.
- Follow the pattern from `ApplicationOverviewPage.tsx` for the page layout: `PageSection` → `Flex` header → data content
- Existing `useApiFetch` hook handles path=null (skip fetch), loading/error state, and refresh — the `useReleases` hook is a thin wrapper

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` § Story 4.5 (line 1260-1292)]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR22 — Release list requirement]
- [Source: `_bmad-output/planning-artifacts/architecture.md` § REST API Structure (line 474)] — `/releases` endpoint
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Project Structure (line 814-817)] — `release/` package
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Frontend Structure (line 921-999)] — `api/releases.ts`, `hooks/useReleases.ts`, `types/release.ts`, `components/release/ReleaseTable.tsx`, `routes/ApplicationReleasesPage.tsx`
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Integration Adapters (line 1049-1066)] — GitProvider, RegistryAdapter
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Portal Persistence Scope (line 404-410)] — Release artifacts NOT persisted, fetched live from Git + Registry
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Format Patterns (line 691-697)] — Collections as arrays, ISO 8601 dates
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Git Provider (line 519-529)] — Interface definition
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Casbin (line 458-461)] — member can read releases
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Application Tabs (line 229)] — Releases tab in application context
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Data Tables (line 496-502)] — Compact table variant for releases
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Empty States (line 1246)] — "No releases yet" copy
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Typography (line 459-462)] — Red Hat Mono for SHAs, versions, image references
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § PatternFly Components (line 897-909)] — Table compact for release list
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Action Hierarchy (line 1150-1168)] — No primary action on Releases tab
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Journey 2 (line 694-778)] — Build → Release flow
- [Source: `_bmad-output/project-context.md` § Domain Language Translation] — Git tag + container image = Release
- [Source: `_bmad-output/project-context.md` § Data Model] — Portal is NOT source of truth; releases fetched live
- [Source: `_bmad-output/project-context.md` § Anti-Patterns] — Adapter throws PortalIntegrationException
- [Source: `_bmad-output/project-context.md` § Testing Rules] — @InjectMock for adapter mocking
- [Source: `_bmad-output/project-context.md` § Framework-Specific Rules] — Resource → Service → Adapter
- [Source: `_bmad-output/project-context.md` § TypeScript Rules] — apiFetch(), useApiFetch, types in src/types/
- [Source: `_bmad-output/implementation-artifacts/4-4-release-creation-from-successful-builds.md`] — Predecessor story
- [Source: `_bmad-output/implementation-artifacts/4-3-builds-page-build-table.md`] — Frontend page pattern reference
- [Source: `_bmad-output/implementation-artifacts/4-2-build-monitoring-log-retrieval.md`] — List endpoint pattern reference
- [Source: `_bmad-output/implementation-artifacts/4-1-tekton-adapter-pipeline-triggering.md`] — Adapter pattern reference
- [Source: `developer-portal/src/main/webui/src/routes/ApplicationReleasesPage.tsx`] — Current stub to replace
- [Source: `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx`] — Page pattern reference
- [Source: `developer-portal/src/main/webui/src/hooks/useApiFetch.ts`] — Data-fetching hook pattern
- [Source: `developer-portal/src/main/webui/src/components/layout/ApplicationTabs.tsx`] — Releases tab already wired
- [Source: `developer-portal/src/main/webui/src/App.tsx`] — Route already wired for /releases
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitProvider.java`] — Interface to extend
- [Source: `developer-portal/src/main/java/com/portal/integration/git/model/PullRequest.java`] — Model package reference

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
