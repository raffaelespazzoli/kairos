# Story 4.4: Release Creation from Successful Builds

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to create a versioned release from a successful build by tagging the Git commit and associating the container image,
So that I have a named, deployable artifact ready for promotion through environments.

## Acceptance Criteria

1. **Passed build row shows inline "Create Release" primary button**
   - **Given** a build has status "Passed" and has not yet been released
   - **When** the build row renders in the builds table
   - **Then** an inline "Create Release" primary button is displayed in the row's action column

2. **Release creation dialog shows build context and accepts a version tag**
   - **Given** a developer clicks "Create Release" on a passed build
   - **When** the release creation dialog appears
   - **Then** the developer can enter a version tag (e.g., "v1.4.2")
   - **And** the dialog shows the build number, commit SHA, and container image that will be associated
   - **And** a "Create" primary button and "Cancel" secondary button are available

3. **Backend creates Git tag and records registry association on confirm**
   - **Given** the developer confirms release creation
   - **When** POST `/api/v1/teams/{teamId}/applications/{appId}/releases` is called with the version and buildId
   - **Then** the backend calls `GitProvider.createTag()` to tag the Git commit with the version
   - **And** the `RegistryAdapter` records the association between the version tag and the container image reference
   - **And** a `201 Created` response is returned with the release details

4. **RegistryAdapter is an @ApplicationScoped CDI bean using OCI Distribution API**
   - **Given** the RegistryAdapter is an @ApplicationScoped CDI bean
   - **When** it queries or records image references
   - **Then** it uses the OCI Distribution API at the URL configured via `portal.registry.url`
   - **And** it translates registry concepts to portal domain types (releases, not tags/manifests)

5. **Inline release badge replaces button on success — no toast or modal**
   - **Given** a release is created successfully
   - **When** the build table updates
   - **Then** the "Create Release" button is replaced with a release badge: "Released v1.4.2"
   - **And** no toast or modal — the inline badge change IS the success confirmation

6. **Release creation failure shows inline error with retry**
   - **Given** release creation fails (Git tag already exists, registry unreachable)
   - **When** the error is returned
   - **Then** an inline error is displayed on the build row with the error message
   - **And** the "Create Release" button remains available for retry

7. **Casbin authorization permits member and lead roles**
   - **Given** the Casbin permission check runs
   - **When** a developer with "member" or "lead" role creates a release
   - **Then** the request is permitted

## Tasks / Subtasks

- [x] Task 1: Add `createTag` to GitProvider interface and all implementations (AC: #3)
  - [x] Add `createTag(String repoUrl, String commitSha, String tagName)` to `GitProvider.java`
  - [x] Implement in `GitHubProvider.java` via `POST /repos/{owner}/{repo}/git/refs` with `refs/tags/{tagName}`
  - [x] Implement in `GitLabProvider.java` via `POST /projects/:id/repository/tags`
  - [x] Implement in `GiteaProvider.java` via `POST /repos/{owner}/{repo}/tags`
  - [x] Implement in `BitbucketProvider.java` via `POST /repositories/{workspace}/{repo_slug}/refs/tags`
  - [x] Implement no-op in `DevGitProvider.java`
  - [x] Update all existing GitProvider tests to cover `createTag()`

- [x] Task 2: Add `commitSha` to BuildDetailDto and extend TektonAdapter (AC: #2, #3)
  - [x] Add `commitSha` field to `BuildDetailDto.java` in `com.portal.build`
  - [x] Extract commit SHA from PipelineRun's `spec.params` or `status.results` (key: `COMMIT_SHA` or `git-commit`) in `TektonKubeAdapter`
  - [x] Add mock `commitSha` to `DevTektonAdapter` build detail responses
  - [x] Update `BuildSummaryDto` with optional `imageReference` field so the builds list can show which rows have releasable artifacts

- [x] Task 3: Create RegistryAdapter interface and implementations (AC: #4)
  - [x] Create `RegistryAdapter.java` interface in `com.portal.integration.registry`
  - [x] Create `RegistryOciAdapter.java` production implementation using OCI Distribution API
  - [x] Create `DevRegistryAdapter.java` dev-mode mock
  - [x] Create `RegistryConfig.java` configuration mapping

- [x] Task 4: Create ReleaseService and ReleaseResource (AC: #3, #7)
  - [x] Create `ReleaseService.java` in `com.portal.release`
  - [x] Create `ReleaseResource.java` in `com.portal.release` with POST endpoint
  - [x] Create `ReleaseSummaryDto.java` and `CreateReleaseRequest.java` DTOs
  - [x] Orchestrate: resolve build → get commit SHA → call `GitProvider.createTag()` → call `RegistryAdapter.tagImage()` → return release DTO

- [x] Task 5: Add configuration properties for registry (AC: #4)
  - [x] Add `portal.registry.url` to `application.properties`
  - [x] Add `portal.registry.provider` for dev/prod switching
  - [x] Add dev profile defaults
  - [x] Add test profile configuration

- [x] Task 6: Add frontend release types and API helper (AC: #1, #2, #5, #6)
  - [x] Add release types to `src/main/webui/src/types/release.ts`: `ReleaseSummary`, `CreateReleaseRequest`
  - [x] Create `src/main/webui/src/api/releases.ts` with `createRelease()` function
  - [x] Extend `src/main/webui/src/types/build.ts` with `commitSha` and `imageReference` on build summary if needed

- [x] Task 7: Wire "Create Release" button and implement release dialog (AC: #1, #2, #5, #6)
  - [x] Wire the existing "Create Release" button from Story 4.3 (change from disabled to functional)
  - [x] Create `CreateReleaseModal.tsx` component in `src/main/webui/src/components/build/`
  - [x] Modal shows build number, commit SHA (truncated, monospace), image reference (monospace)
  - [x] Version tag text input with validation (non-empty, semver-like pattern)
  - [x] On success: replace "Create Release" button with "Released {version}" success Label
  - [x] On failure: show inline Alert on the build row with error message, keep button available for retry

- [x] Task 8: Write backend tests (AC: #1-#7)
  - [x] Add `createTag` tests to existing GitProvider test files
  - [x] Create `RegistryOciAdapterTest.java` with mocked HTTP responses
  - [x] Create `ReleaseServiceTest.java` with `@QuarkusTest` + `@InjectMock`
  - [x] Create `ReleaseResourceIT.java` integration test for POST endpoint
  - [x] Test error paths: tag already exists, registry unreachable, cross-team access returns 404

- [x] Task 9: Write frontend tests (AC: #1, #2, #5, #6)
  - [x] Create `CreateReleaseModal.test.tsx` covering dialog render, version input, create/cancel actions
  - [x] Update `ApplicationBuildsPage.test.tsx` to test release creation flow: button click → dialog → success badge / error alert
  - [x] Test disabled state for already-released builds

## Dev Notes

### Critical Dependency: Stories 4.1, 4.2, and 4.3 Must Be Implemented First

This story extends the builds page and backend build infrastructure from Stories 4.1–4.3. The following must exist:

- `BuildResource`, `BuildService`, `TektonAdapter` (Stories 4.1 + 4.2)
- `BuildDetailDto` with status, imageReference, and build metadata (Story 4.2)
- `ApplicationBuildsPage.tsx` with the builds table and disabled "Create Release" button on passed rows (Story 4.3)
- Build types, API helpers, and hooks in the frontend (Story 4.3)

**Pre-flight check:** verify all Story 4.1–4.3 artifacts are implemented and all tests pass.

### CRITICAL FINDING: `commitSha` Is Not in BuildDetailDto

The current `BuildDetailDto` (from Story 4.2) does NOT include a `commitSha` field. The release dialog requires the commit SHA to display and the backend needs it to tag. The PipelineRun typically carries the source commit in one of:

- `.spec.params[]` where name is `git-revision` or `COMMIT_SHA`
- `.status.results[]` where name is `COMMIT_SHA` or `commit-sha`
- The annotation `tekton.dev/git-commit` on the PipelineRun

**Resolution:** Extend `BuildDetailDto` to include `commitSha` (nullable String). Extend `TektonKubeAdapter.getBuildDetail()` to extract it. Prioritize `status.results` (set by the pipeline at runtime) over `spec.params` (set at creation). If unavailable, set to `null` and have the release dialog show "Commit SHA unavailable" — the backend can still attempt the tag via a branch HEAD if needed, but this is a degraded path.

### CRITICAL FINDING: `imageReference` Missing from BuildSummaryDto List View

Story 4.2 extended `BuildSummaryDto` with `completedAt` and `duration`, but `imageReference` is only on `BuildDetailDto`. The builds list endpoint returns `List<BuildSummaryDto>`. For Story 4.4, the build table needs to know which builds have an image reference (to show "Create Release" vs. nothing) and it needs `imageReference` to display in the release dialog.

**Resolution options (pick the simplest):**
1. **Recommended:** Add `imageReference` (nullable) to `BuildSummaryDto` so the list view has it. Update `TektonKubeAdapter.listBuilds()` to populate it from PipelineRun results. This avoids a detail-fetch just to determine releasability.
2. Fetch detail on-demand when "Create Release" is clicked — works but adds latency.

### GitProvider.createTag() — New Interface Method

`createTag` must be added to the `GitProvider` interface. It does NOT currently exist. This is a cross-cutting change affecting all five implementations.

**GitHub implementation** (follows the same Git refs API pattern as `createBranch`):

```java
void createTag(String repoUrl, String commitSha, String tagName);
```

GitHub REST API: `POST /repos/{owner}/{repo}/git/refs`

```json
{
  "ref": "refs/tags/v1.4.2",
  "sha": "abc123def456..."
}
```

This creates a lightweight tag. The pattern mirrors the existing `createBranch()` method which already uses the same API endpoint with `refs/heads/` prefix — change to `refs/tags/` prefix.

**GitLab:** `POST /projects/:id/repository/tags` with body `{ "tag_name": "v1.4.2", "ref": "abc123..." }`

**Gitea:** `POST /repos/{owner}/{repo}/tags` with body `{ "tag_name": "v1.4.2", "target": "abc123..." }`

**Bitbucket:** `POST /repositories/{workspace}/{repo}/refs/tags` with body `{ "name": "v1.4.2", "target": { "hash": "abc123..." } }`

**DevGitProvider:** No-op, return immediately.

**Error handling:** If the tag already exists, the Git API returns 422 (GitHub) or 400 (GitLab). Catch this and throw `PortalIntegrationException` with system="git", message="Release tag already exists — choose a different version".

### RegistryAdapter — OCI Distribution API for Image Re-Tagging

The RegistryAdapter's primary operation for releases is **tagging an existing image with a version tag**. The build pipeline produces an image tagged with the commit SHA (e.g., `registry.example.com/team/app:abc1234`). The release process adds a version tag (e.g., `:v1.4.2`) pointing to the same manifest.

**OCI Distribution API pattern:**

1. `GET /v2/{name}/manifests/{existing-tag}` — fetch the manifest for the build image (by commit-SHA tag)
2. `PUT /v2/{name}/manifests/{new-tag}` — push the same manifest with the version tag

Both calls require the `Accept`/`Content-Type` header: `application/vnd.oci.image.manifest.v1+json` (or `application/vnd.docker.distribution.manifest.v2+json` for Docker-compatible registries).

**Authentication:** Registry authentication typically uses Bearer tokens. The flow:
1. Attempt the request — registry returns `401` with `Www-Authenticate: Bearer realm="...",service="...",scope="..."`
2. Request a token from the auth endpoint
3. Retry with `Authorization: Bearer {token}`

For MVP, use the portal's Git token or a dedicated registry token configured via `portal.registry.token`. The credential source depends on the registry deployment.

**RegistryAdapter interface:**

```java
package com.portal.integration.registry;

public interface RegistryAdapter {
    void tagImage(String imageReference, String newTag);
}
```

- `imageReference`: the full image reference from the build (e.g., `registry.example.com/team/app:abc1234`)
- `newTag`: the version tag to apply (e.g., `v1.4.2`)

**RegistryOciAdapter** parses the image reference to extract registry URL, repository name, and existing tag, then performs the GET+PUT flow.

**DevRegistryAdapter:** No-op, return immediately.

### ReleaseService — Orchestration Flow

```java
package com.portal.release;

@ApplicationScoped
public class ReleaseService {

    @Inject TeamContext teamContext;
    @Inject BuildService buildService;
    @Inject GitProvider gitProvider;
    @Inject RegistryAdapter registryAdapter;

    public ReleaseSummaryDto createRelease(Long teamId, Long appId,
                                            CreateReleaseRequest request) {
        // 1. Resolve application (team-scoped, 404 for cross-team)
        Application app = resolveTeamApplication(teamId, appId);

        // 2. Get build detail to extract commitSha and imageReference
        BuildDetailDto build = buildService.getBuildDetail(teamId, appId, request.buildId());

        // 3. Validate build is "Passed"
        if (!"Passed".equals(build.status())) {
            throw new IllegalArgumentException(
                    "Cannot create release from build with status: " + build.status());
        }

        // 4. Tag the Git commit
        gitProvider.createTag(app.gitRepoUrl, build.commitSha(), request.version());

        // 5. Tag the container image in the registry
        if (build.imageReference() != null) {
            registryAdapter.tagImage(build.imageReference(), request.version());
        }

        // 6. Return release summary
        return new ReleaseSummaryDto(
                request.version(),
                Instant.now(),
                request.buildId(),
                build.commitSha(),
                build.imageReference());
    }
}
```

**Team-scoped access:** follows the same `Application.findById()` + `teamId` check pattern from `BuildService`.

**No database persistence:** releases are NOT stored in the portal database. A release is a Git tag + registry tag — the portal is not the source of truth. Story 4.5 (Releases Page) will list releases by querying Git tags + registry metadata live.

### ReleaseResource — REST Endpoint

```java
@Path("/api/v1/teams/{teamId}/applications/{appId}/releases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReleaseResource {

    @Inject ReleaseService releaseService;

    @POST
    public Response createRelease(@PathParam("teamId") Long teamId,
                                   @PathParam("appId") Long appId,
                                   CreateReleaseRequest request) {
        ReleaseSummaryDto result = releaseService.createRelease(teamId, appId, request);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
```

Returns **201 Created** per API convention (POST that creates a resource).

### DTOs — `com.portal.release`

```java
public record CreateReleaseRequest(
    String buildId,
    String version
) {}

public record ReleaseSummaryDto(
    String version,
    Instant createdAt,
    String buildId,
    String commitSha,
    String imageReference
) {}
```

### Casbin Policy — Already Configured

`policy.csv` already includes:

```
p, member, releases, read
p, member, releases, create
```

The `PermissionFilter` maps POST → `create` for the `releases` resource. **No Casbin policy changes needed.**

### Configuration Properties

**Add to `application.properties`:**

```properties
# Container Registry
portal.registry.url=${REGISTRY_URL:}
portal.registry.token=${REGISTRY_TOKEN:}
portal.registry.provider=${REGISTRY_PROVIDER:oci}
```

**Add to dev profile section:**

```properties
%dev.portal.registry.provider=dev
```

**Add to `src/test/resources/application.properties`:**

```properties
portal.registry.provider=dev
portal.registry.url=https://registry.test.example.com
portal.registry.token=test-registry-token
```

### RegistryConfig — `com.portal.integration.registry`

```java
@ConfigMapping(prefix = "portal.registry")
public interface RegistryConfig {

    @WithDefault("oci")
    String provider();

    Optional<String> url();

    Optional<String> token();
}
```

### Frontend: Release Dialog Component

Use PatternFly 6 `Modal` with `ModalHeader`, `ModalBody`, `ModalFooter`. The dialog is lightweight — not a wizard.

```
┌──────────────────────────────────────────┐
│ Create Release                           │
├──────────────────────────────────────────┤
│                                          │
│ Build:        #payment-svc-xk7f2        │
│ Commit:       abc1234                    │
│ Image:        registry.ex.../app:abc1234│
│                                          │
│ Version tag:  [ v1.4.2          ]        │
│                                          │
│              [Cancel]  [Create]          │
└──────────────────────────────────────────┘
```

**Composed from:** PatternFly `Modal` + `DescriptionList` for build info + `TextInput` for version tag + `ModalFooter` with `Button` (primary "Create", secondary "Cancel").

**Version input validation:**
- Non-empty
- Basic semver-like pattern: starts with `v` followed by digits/dots (e.g., `v1.0.0`, `v2.1.3-rc1`)
- Real validation happens server-side (Git API rejects invalid tag names)
- Show PatternFly `FormHelperText` with validation state

**Success behavior:** Close dialog, replace "Create Release" with a PatternFly `Label` (status="success"): "Released v1.4.2". No toast, no modal — inline state change IS the confirmation.

**Error behavior:** Close dialog, show inline `Alert` (variant="danger") on the build row. Keep "Create Release" button available for retry.

### Frontend: Extending the Builds Page (Story 4.3 Handoff)

Story 4.3 creates the "Create Release" button but leaves it disabled or with a tooltip. Story 4.4 must:

1. Wire the button's `onClick` to open the `CreateReleaseModal`
2. Pass the build's `buildId`, `commitSha`, `imageReference`, and application context to the modal
3. Track per-row release state: `idle | creating | released | error`
4. On success: update the row state to show the release badge
5. On error: update the row state to show the inline error

**State management:** Use React `useState` at the `ApplicationBuildsPage` level to track `releaseState: Map<string, { status, version?, error? }>` keyed by buildId.

### File Structure Requirements

**New backend files:**

```
src/main/java/com/portal/release/
├── ReleaseResource.java
├── ReleaseService.java
├── ReleaseSummaryDto.java
└── CreateReleaseRequest.java

src/main/java/com/portal/integration/registry/
├── RegistryAdapter.java        (interface)
├── RegistryOciAdapter.java     (production: OCI Distribution API)
├── RegistryConfig.java         (config mapping)
└── DevRegistryAdapter.java     (dev mock)
```

**Modified backend files:**

```
src/main/java/com/portal/integration/git/GitProvider.java           (add createTag method)
src/main/java/com/portal/integration/git/GitHubProvider.java        (implement createTag)
src/main/java/com/portal/integration/git/GitLabProvider.java        (implement createTag)
src/main/java/com/portal/integration/git/GiteaProvider.java         (implement createTag)
src/main/java/com/portal/integration/git/BitbucketProvider.java     (implement createTag)
src/main/java/com/portal/integration/git/DevGitProvider.java        (implement createTag no-op)
src/main/java/com/portal/build/BuildDetailDto.java                  (add commitSha field)
src/main/java/com/portal/build/BuildSummaryDto.java                 (add imageReference field)
src/main/java/com/portal/integration/tekton/TektonKubeAdapter.java  (extract commitSha, add imageRef to list)
src/main/java/com/portal/integration/tekton/DevTektonAdapter.java   (add mock commitSha/imageRef)
src/main/resources/application.properties                           (add registry config)
src/test/resources/application.properties                           (add registry test config)
```

**New frontend files:**

```
src/main/webui/src/types/release.ts
src/main/webui/src/api/releases.ts
src/main/webui/src/components/build/CreateReleaseModal.tsx
src/main/webui/src/components/build/CreateReleaseModal.test.tsx
```

**Modified frontend files:**

```
src/main/webui/src/routes/ApplicationBuildsPage.tsx     (wire Create Release button + state)
src/main/webui/src/routes/ApplicationBuildsPage.test.tsx (add release flow tests)
src/main/webui/src/types/build.ts                       (add commitSha, imageReference if missing)
```

**New test files:**

```
src/test/java/com/portal/release/
├── ReleaseServiceTest.java
└── ReleaseResourceIT.java

src/test/java/com/portal/integration/registry/
└── RegistryOciAdapterTest.java
```

**Modified test files:**

```
src/test/java/com/portal/integration/git/GitHubProviderTest.java   (add createTag tests)
src/test/java/com/portal/integration/git/GitLabProviderTest.java   (add createTag tests)
src/test/java/com/portal/integration/tekton/TektonKubeAdapterTest.java (commitSha extraction)
src/test/java/com/portal/build/BuildServiceTest.java               (if DTO changes cascade)
src/test/java/com/portal/build/BuildResourceIT.java                (if DTO changes cascade)
```

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `package-info.java` | `com.portal.release` | EXISTS — placeholder |
| `package-info.java` | `com.portal.integration.registry` | EXISTS — placeholder |
| `GitProvider.java` | `com.portal.integration.git` | EXISTS — interface without `createTag` |
| `GitHubProvider.java` | `com.portal.integration.git` | EXISTS — needs `createTag` addition |
| `GitLabProvider.java` | `com.portal.integration.git` | EXISTS — needs `createTag` addition |
| `GiteaProvider.java` | `com.portal.integration.git` | EXISTS — needs `createTag` addition |
| `BitbucketProvider.java` | `com.portal.integration.git` | EXISTS — needs `createTag` addition |
| `DevGitProvider.java` | `com.portal.integration.git` | EXISTS — needs no-op `createTag` |
| `GitProviderFactory.java` | `com.portal.integration.git` | EXISTS — no change needed |
| `GitProviderConfig.java` | `com.portal.integration.git` | EXISTS — has `token()`, `apiUrl()` |
| `BuildService.java` | `com.portal.build` | EXISTS — has team-scoped methods |
| `BuildDetailDto.java` | `com.portal.build` | EXISTS — needs `commitSha` field |
| `BuildSummaryDto.java` | `com.portal.build` | EXISTS — needs `imageReference` field |
| `TektonAdapter.java` | `com.portal.integration.tekton` | EXISTS — interface |
| `TektonKubeAdapter.java` | `com.portal.integration.tekton` | EXISTS — needs commitSha extraction |
| `DevTektonAdapter.java` | `com.portal.integration.tekton` | EXISTS — needs mock commitSha |
| `PortalIntegrationException` | `com.portal.integration` | EXISTS — with system, operation, deepLink |
| `GlobalExceptionMapper` | `com.portal.common` | EXISTS — maps exceptions to error JSON |
| `PermissionFilter` | `com.portal.auth` | EXISTS — maps POST → `create` |
| `TeamContext` | `com.portal.auth` | EXISTS — request-scoped team+role bean |
| Casbin `releases, create` | `casbin/policy.csv` | EXISTS — member+lead can create |
| `apiFetch()` | `src/main/webui/src/api/client.ts` | EXISTS — typed fetch wrapper |
| Builds page stub or implementation | `src/main/webui/src/routes/ApplicationBuildsPage.tsx` | EXISTS — Story 4.3 builds this |
| Application route wiring | `src/main/webui/src/App.tsx` | EXISTS — `/releases` route already wired |
| Releases page stub | `src/main/webui/src/routes/ApplicationReleasesPage.tsx` | EXISTS — placeholder |

### What NOT to Build

- **No Releases list page** — Story 4.5 creates `ApplicationReleasesPage`
- **No releases list GET endpoint** — Story 4.5 adds `GET /releases`
- **No database table for releases** — Releases are Git tags + registry tags; the portal is NOT the source of truth
- **No polling or WebSockets** — Manual refresh model
- **No deploy button on releases** — Story 5.1 handles deployment
- **No release deletion** — Not in MVP scope
- **No automatic release creation** — Releases are developer-initiated only
- **No changelog generation** — Not in MVP scope
- **No registry browsing or image list** — Only the tag association operation

### Anti-Patterns to Avoid

- **DO NOT** create a `releases` database table — releases are Git tags + registry tags, fetched live when needed
- **DO NOT** store release state in the portal — the Git server and registry are the sources of truth
- **DO NOT** expose Git or registry infrastructure concepts in the UI — use "release", "version", not "tag", "manifest", "ref"
- **DO NOT** show a toast or success modal after release creation — the inline badge IS the confirmation
- **DO NOT** bypass `apiFetch()` — use the shared client for the POST call
- **DO NOT** add the release dialog to the Releases page — it lives on the Builds page inline with passed build rows
- **DO NOT** cache release state between navigations — fresh data on every mount
- **DO NOT** call Git or registry APIs from the frontend — all through backend REST API
- **DO NOT** use Spring annotations — use Quarkus/CDI
- **DO NOT** call `Application.findById()` from adapters — entity lookups in `ReleaseService` only
- **DO NOT** use `v1beta1` Tekton classes for commit SHA extraction — use `v1`
- **DO NOT** throw raw HTTP/connection exceptions from RegistryAdapter — wrap in `PortalIntegrationException`

### Architecture Compliance

- Resource → Service → Adapter call chain (ReleaseResource → ReleaseService → GitProvider + RegistryAdapter)
- Team-scoped access: 404 for cross-team or missing resources
- `PortalIntegrationException` for all integration failures → 502 via GlobalExceptionMapper
- PatternFly 6 Modal for the dialog — no custom HTML/CSS
- Frontend uses relative `/api/v1/...` URLs via `apiFetch()`
- Inline success/error feedback on the builds page — no separate navigation
- Developer domain language only: "release", "version", not "tag", "ref", "manifest"
- `@ApplicationScoped` CDI beans for all adapters
- `@IfBuildProperty` or factory pattern for registry adapter switching

### Testing Requirements

**Backend:**

- `GitHubProviderTest.java`: Verify `createTag` calls `POST /repos/{owner}/{repo}/git/refs` with `refs/tags/{tagName}` payload. Verify 422 response → `PortalIntegrationException` with "tag already exists".
- `RegistryOciAdapterTest.java`: Mock HTTP client. Verify GET manifest → PUT manifest with new tag flow. Verify error handling for unreachable registry.
- `ReleaseServiceTest.java` (`@QuarkusTest` + `@InjectMock`): Verify orchestration: build lookup → validate "Passed" → createTag → tagImage → return DTO. Verify 404 for cross-team app. Verify error for non-Passed build.
- `ReleaseResourceIT.java`: REST Assured integration test for POST endpoint. Verify 201 response, 404 for cross-team, 400 for invalid input.

**Frontend:**

- `CreateReleaseModal.test.tsx`: Render with build data, verify fields shown, version input, create/cancel actions.
- Update `ApplicationBuildsPage.test.tsx`: Test full flow — click "Create Release" → modal opens → enter version → submit → badge appears. Test error path → error alert shown, button remains.

### Previous Story Intelligence

**Story 4.1 (Tekton Adapter):**
- Established `@IfBuildProperty`-based adapter switching pattern
- `DevTektonAdapter` serves as dev-mode mock — follow same pattern for `DevRegistryAdapter`
- `BuildService.triggerBuild()` demonstrates team-scoped application resolution pattern

**Story 4.2 (Build Monitoring):**
- `BuildDetailDto` has `imageReference` from PipelineRun results — key input for RegistryAdapter
- `BuildSummaryDto` was extended with `completedAt` + `duration` — adding `imageReference` follows same pattern
- Status translation helper in `TektonKubeAdapter` — commit SHA extraction follows same `PipelineRun.getStatus().getResults()` pattern

**Story 4.3 (Builds Page):**
- Creates the disabled "Create Release" button on passed build rows — Story 4.4 enables it
- Establishes the `components/build/` directory for build-related UI components
- Establishes `types/build.ts` and `api/builds.ts` — release types/APIs are siblings

**Epic 3 Retrospective Action Items:**
- Every story must leave the test suite at 0 failures
- When a component's API changes (e.g., `BuildDetailDto`, `GitProvider`), update ALL consumers including tests
- Pre-flight test gate enforced

### Data Flow for Release Creation

```
POST /api/v1/teams/{teamId}/applications/{appId}/releases
  { "buildId": "payment-svc-xk7f2", "version": "v1.4.2" }

  → PermissionFilter: Casbin check (releases, create)
  → TeamContextFilter: extract team from JWT
  → ReleaseResource.createRelease(teamId, appId, request)
  → ReleaseService.createRelease(teamId, appId, request)
    → Application.findById(appId) — verify team ownership
    → BuildService.getBuildDetail(teamId, appId, buildId)
      → TektonAdapter.getBuildDetail(buildId, namespace, clusterUrl, token)
      → Returns BuildDetailDto { commitSha, imageReference, status: "Passed", ... }
    → Validate build.status == "Passed"
    → GitProvider.createTag(app.gitRepoUrl, build.commitSha, "v1.4.2")
      → GitHub: POST /repos/{owner}/{repo}/git/refs { ref: "refs/tags/v1.4.2", sha: "..." }
    → RegistryAdapter.tagImage(build.imageReference, "v1.4.2")
      → GET /v2/{name}/manifests/{build-tag} → manifest
      → PUT /v2/{name}/manifests/v1.4.2 with same manifest
    → Return ReleaseSummaryDto
  → 201 Created: { version, createdAt, buildId, commitSha, imageReference }
```

### Project Structure Notes

- `com.portal.release` package already has a `package-info.java` placeholder — add the new classes here
- `com.portal.integration.registry` package already has a `package-info.java` placeholder — add adapter classes here
- The `GitProvider` interface change is the biggest cross-cutting concern — affects all 5 implementations
- Follow the existing `ArgoCdRestAdapter` HTTP client pattern for `RegistryOciAdapter` (uses `quarkus-rest-client-jackson` or `java.net.http.HttpClient`)
- The `GitHubProvider` uses `java.net.http.HttpClient` directly — follow the same pattern for consistency in the `RegistryOciAdapter`
- `ApplicationBuildsPage.tsx` from Story 4.3 already has the builds table structure — extend rather than rewrite

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` § Story 4.4 (line 1217-1258)]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR21-FR22, Release Management]
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Project Structure (line 320-365)] — `release/` + `integration/registry/` packages
- [Source: `_bmad-output/planning-artifacts/architecture.md` § REST API Structure (line 474)] — `/releases` endpoint
- [Source: `_bmad-output/planning-artifacts/architecture.md` § GitProvider Interface (line 520-526)] — current interface (no `createTag`)
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Platform Dependencies (line 86-87)] — "create tags" listed as Git operation
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Integration Adapters (line 1062-1066)] — RegistryAdapter: OCI Distribution API, Phase 3
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Configuration Properties (line 1143)] — `portal.registry.url`
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Casbin (line 456-462)] — member can create releases
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Data Flow (line 1095-1113)] — Service → Adapter flow
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Error Response (line 485-496)] — Standardized error JSON
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Build & Release (H) (line 574-577)] — Inline Create Release on successful builds
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Action Hierarchy (line 1150-1184)] — Create Release is primary action on Build row (passed)
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Feedback Patterns (line 1186-1197)] — Release created → inline badge, no toast/modal
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Journey 2 (line 694-731)] — Build → Release flow
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Error Patterns (line 859-870)] — Inline diagnosis with escape hatch
- [Source: `_bmad-output/project-context.md` § Domain Language Translation] — Git tag + container image = Release
- [Source: `_bmad-output/project-context.md` § Anti-Patterns] — Adapter throws PortalIntegrationException
- [Source: `_bmad-output/project-context.md` § Testing Rules] — @InjectMock for adapter mocking
- [Source: `_bmad-output/project-context.md` § Framework-Specific Rules] — Resource → Service → Adapter
- [Source: `_bmad-output/project-context.md` § Data Model] — Portal is NOT source of truth; releases fetched live
- [Source: `_bmad-output/implementation-artifacts/4-1-tekton-adapter-pipeline-triggering.md`]
- [Source: `_bmad-output/implementation-artifacts/4-2-build-monitoring-log-retrieval.md`]
- [Source: `_bmad-output/implementation-artifacts/4-3-builds-page-build-table.md`]
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitProvider.java`]
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitHubProvider.java`]
- [Source: `developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java`]
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitProviderConfig.java`]
- [Source: `developer-portal/src/main/resources/casbin/policy.csv`]
- [Source: `developer-portal/src/main/resources/application.properties`]
- [Source: `developer-portal/src/main/webui/src/api/client.ts`]
- [Source: `developer-portal/src/main/webui/src/hooks/useApiFetch.ts`]
- [Source: OCI Distribution Specification v1.1] — Manifest tag association via GET+PUT

## Dev Agent Record

### Agent Model Used

claude-4.6-opus-high-thinking

### Debug Log References

- Backend tests: 394 passed, 0 failures
- Frontend tests: 235 passed, 0 failures (26 test files)

### Completion Notes List

- Task 1: Added `createTag(repoUrl, commitSha, tagName)` to GitProvider interface and implemented in all 5 providers (GitHub, GitLab, Gitea, Bitbucket, Dev). Each implementation catches provider-specific HTTP error codes for "tag already exists" and wraps in PortalIntegrationException.
- Task 2: Added `commitSha` field to BuildDetailDto record. TektonKubeAdapter extracts commit SHA from PipelineRun status.results (priority) then spec.params. DevTektonAdapter provides mock commitSha values. BuildSummaryDto already had imageReference from Story 4.2.
- Task 3: Created RegistryAdapter interface with `tagImage(imageReference, newTag)`. RegistryOciAdapter uses OCI Distribution API (GET manifest + PUT with new tag). DevRegistryAdapter is a no-op. RegistryConfig maps `portal.registry.*` properties.
- Task 4: Created ReleaseService orchestrating: resolve app → get build detail → validate Passed → createTag → tagImage → return DTO. ReleaseResource exposes POST endpoint at `/api/v1/teams/{teamId}/applications/{appId}/releases` returning 201. No database persistence — releases are Git tags + registry tags.
- Task 5: Added `portal.registry.url`, `portal.registry.token`, `portal.registry.provider` to application.properties. Dev profile uses `dev` provider. Test profile uses `dev` provider with test URLs.
- Task 6: Created `release.ts` types (ReleaseSummary, CreateReleaseRequest) and `releases.ts` API helper using apiFetch. Added `commitSha` to BuildDetail frontend type.
- Task 7: Created CreateReleaseModal with PF6 Modal, DescriptionList for build context, TextInput with semver validation. BuildTable now tracks per-row release state (idle/creating/released/error). On success: inline "Released vX.Y.Z" Label replaces button. On failure: inline danger Alert on build row with retry.
- Task 8: GitHubProviderCreateTagTest (4 tests), RegistryOciAdapterTest (5 tests), ReleaseServiceTest (5 tests), ReleaseResourceIT (6 tests). Updated existing BuildServiceTest and BuildResourceIT for new commitSha field.
- Task 9: CreateReleaseModal.test.tsx (11 tests) covering render, validation, submit, cancel, loading states. Updated ApplicationBuildsPage.test.tsx with release flow tests (open dialog, success badge, error alert).

### File List

**New backend files:**
- developer-portal/src/main/java/com/portal/integration/registry/RegistryAdapter.java
- developer-portal/src/main/java/com/portal/integration/registry/RegistryOciAdapter.java
- developer-portal/src/main/java/com/portal/integration/registry/DevRegistryAdapter.java
- developer-portal/src/main/java/com/portal/integration/registry/RegistryConfig.java
- developer-portal/src/main/java/com/portal/release/ReleaseResource.java
- developer-portal/src/main/java/com/portal/release/ReleaseService.java
- developer-portal/src/main/java/com/portal/release/ReleaseSummaryDto.java
- developer-portal/src/main/java/com/portal/release/CreateReleaseRequest.java

**Modified backend files:**
- developer-portal/src/main/java/com/portal/integration/git/GitProvider.java (added createTag method)
- developer-portal/src/main/java/com/portal/integration/git/GitHubProvider.java (implemented createTag)
- developer-portal/src/main/java/com/portal/integration/git/GitLabProvider.java (implemented createTag)
- developer-portal/src/main/java/com/portal/integration/git/GiteaProvider.java (implemented createTag)
- developer-portal/src/main/java/com/portal/integration/git/BitbucketProvider.java (implemented createTag)
- developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java (implemented createTag no-op)
- developer-portal/src/main/java/com/portal/build/BuildDetailDto.java (added commitSha field)
- developer-portal/src/main/java/com/portal/integration/tekton/TektonKubeAdapter.java (commitSha extraction)
- developer-portal/src/main/java/com/portal/integration/tekton/DevTektonAdapter.java (mock commitSha)
- developer-portal/src/main/resources/application.properties (registry config)
- developer-portal/src/test/resources/application.properties (registry test config)

**New frontend files:**
- developer-portal/src/main/webui/src/types/release.ts
- developer-portal/src/main/webui/src/api/releases.ts
- developer-portal/src/main/webui/src/components/build/CreateReleaseModal.tsx
- developer-portal/src/main/webui/src/components/build/CreateReleaseModal.test.tsx

**Modified frontend files:**
- developer-portal/src/main/webui/src/types/build.ts (added commitSha to BuildDetail)
- developer-portal/src/main/webui/src/components/build/BuildTable.tsx (wired Create Release + release state)
- developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.test.tsx (release flow tests)

**New test files:**
- developer-portal/src/test/java/com/portal/integration/git/GitHubProviderCreateTagTest.java
- developer-portal/src/test/java/com/portal/integration/registry/RegistryOciAdapterTest.java
- developer-portal/src/test/java/com/portal/release/ReleaseServiceTest.java
- developer-portal/src/test/java/com/portal/release/ReleaseResourceIT.java

**Modified test files:**
- developer-portal/src/test/java/com/portal/build/BuildServiceTest.java (updated for commitSha field)
- developer-portal/src/test/java/com/portal/build/BuildResourceIT.java (updated for commitSha field)
