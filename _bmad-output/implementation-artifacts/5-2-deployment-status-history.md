# Story 5.2: Deployment Status & History

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to see the deployment status of each environment and a history of past deployments,
so that I can track what's deployed where and when.

## Acceptance Criteria

1. **GET deployments endpoint returns deployment history from Git**
   - **Given** deployments have been made via Git commits to `values-run-<env>.yaml` (Story 5.1)
   - **When** GET `/api/v1/teams/{teamId}/applications/{appId}/deployments?environmentId={envId}` is called
   - **Then** the portal queries Git commit history for `.helm/run/values-run-<env>.yaml` via `GitProvider.listCommits()`
   - **And** a list of deployments is returned, ordered by commit timestamp descending
   - **And** each deployment includes: `deploymentId` (commit SHA), `releaseVersion`, `status`, `startedAt`, `completedAt`, `deployedBy`, `environmentName`, `argocdDeepLink`
   - **And** the response is limited to the 25 most recent commits

2. **GET deployments returns all environments when no filter**
   - **Given** no `environmentId` query parameter is supplied
   - **When** GET `/api/v1/teams/{teamId}/applications/{appId}/deployments` is called
   - **Then** the portal queries commit history for all `values-run-*.yaml` files and merges the results
   - **And** results are ordered by timestamp descending, limited to 50

3. **Most recent deployment status enriched from live ArgoCD state**
   - **Given** the most recent Git commit changed `values-run-dev.yaml`
   - **When** the GET endpoint builds the history list
   - **Then** for the most recent deployment per environment, the status is enriched from live ArgoCD:
     - ArgoCD health "Healthy" + sync "Synced" → status "Deployed", `completedAt` from ArgoCD `operationState.finishedAt`
     - ArgoCD health "Progressing"/"Suspended" or sync "OutOfSync" → status "Deploying"
     - ArgoCD health "Degraded"/"Missing" → status "Failed"
   - **And** older deployments are assumed "Deployed" (they were superseded by a subsequent deployment)
   - **And** if ArgoCD is unreachable, the most recent deployment shows "Deploying" (graceful degradation)

4. **Deployment history fields extracted from Git commits**
   - **Given** deployment commits follow the convention `deploy: <version> to <env>\n\nDeployed-By: <user>`
   - **When** the portal parses the commit
   - **Then** `releaseVersion` is extracted from the commit message subject
   - **And** `deployedBy` is extracted from the `Deployed-By:` trailer (or commit author as fallback)
   - **And** `startedAt` is the commit timestamp
   - **And** `deploymentId` is the commit SHA

5. **Environment card expanded section shows deployment history**
   - **Given** a developer clicks on an environment card to expand it
   - **When** the deployment history loads
   - **Then** a compact table of recent deployments is shown with: version, status badge, timestamp (relative), deployed by
   - **And** the most recent deployment is visually highlighted
   - **And** loading state shows a PF6 Spinner while fetching
   - **And** error state shows a PF6 inline Alert if the fetch fails

6. **Deployment failure shows inline error and ArgoCD deep link**
   - **Given** the most recent deployment has status "Failed" (ArgoCD reports Unhealthy)
   - **When** the environment card displays the failure
   - **Then** the deployment row shows a red "Failed" status badge
   - **And** deep links to ArgoCD are available for investigation
   - **And** the environment card's existing status badge (from Stories 2.7/2.8) already shows "✕ Unhealthy" — the history table adds the "which version failed" context

7. **Resource ownership validation (mandatory scoping pattern)**
   - **Given** a request targets a resource outside the caller's team scope
   - **Then** 404 is returned (never 403)
   - **Given** a request targets an environment that does not belong to the application
   - **Then** 404 is returned
   - **Given** a request targets a non-existent application or environment
   - **Then** 404 is returned

## Tasks / Subtasks

- [x] Task 1: Add `listCommits` method to GitProvider interface (AC: #1, #4)
  - [x] Add `List<GitCommit> listCommits(String repoUrl, String filePath, int maxResults)` to `GitProvider.java`
  - [x] Javadoc: returns commits that touched the given file path, ordered by timestamp descending
  - [x] Create `com.portal.integration.git.model.GitCommit` record with: `String sha`, `String author`, `Instant timestamp`, `String message`

- [x] Task 2: Implement `listCommits` in all GitProvider implementations (AC: #1)
  - [x] `GitHubProvider` — `GET /repos/{owner}/{repo}/commits?path={filePath}&per_page={limit}` (GitHub REST API v3)
  - [x] `GitLabProvider` — `GET /projects/{id}/repository/commits?path={filePath}&per_page={limit}` (GitLab API v4)
  - [x] `GiteaProvider` — `GET /repos/{owner}/{repo}/git/commits?path={filePath}&limit={limit}` (Gitea API)
  - [x] `BitbucketProvider` — `GET /repositories/{workspace}/{slug}/commits?path={filePath}&pagelen={limit}` (Bitbucket API v2)
  - [x] `DevGitProvider` — return canned deployment commits matching the `deploy: <version> to <env>` convention

- [x] Task 3: Create DeploymentHistoryDto record (AC: #1)
  - [x] Create `com.portal.deployment.DeploymentHistoryDto` record with: `String deploymentId`, `String releaseVersion`, `String status`, `Instant startedAt`, `Instant completedAt`, `String deployedBy`, `String environmentName`, `String argocdDeepLink`

- [x] Task 4: Add deployment history retrieval to DeploymentService (AC: #1, #2, #3, #4, #7)
  - [x] Add `listDeployments(Long teamId, Long appId, Long environmentId)` method
  - [x] `requireTeamApplication(teamId, appId)` — ownership check → 404
  - [x] If `environmentId` is not null: `requireApplicationEnvironment(appId, environmentId)` → 404, query `gitProvider.listCommits(app.gitRepoUrl, ".helm/run/values-run-<env>.yaml", 25)`
  - [x] If `environmentId` is null: query commits for each environment's values file, merge, sort by timestamp, limit to 50
  - [x] Parse each `GitCommit` into `DeploymentHistoryDto`:
    - [x] `deploymentId` = commit SHA
    - [x] `releaseVersion` = parse from commit message `deploy: <version> to <env>` — extract version between `deploy: ` and ` to `
    - [x] `deployedBy` = parse `Deployed-By:` trailer from commit message body, fall back to commit author
    - [x] `startedAt` = commit timestamp
    - [x] `status` / `completedAt` = "Deployed" for older commits, enriched for most recent (see Task 5)
    - [x] `environmentName` = resolve from environmentId, or parse from file path / commit message
    - [x] `argocdDeepLink` = build from `deepLinkService.generateArgoCdLink(app.name + "-run-" + env.name.toLowerCase())`
  - [x] Handle non-deployment commits gracefully — if a commit message doesn't match the `deploy:` pattern, skip it (the file may have been edited manually)

- [x] Task 5: Enrich most recent deployment with live ArgoCD status (AC: #3)
  - [x] Add private method `enrichLatestDeployment(DeploymentHistoryDto dto, Application app, Environment env)` to `DeploymentService`
  - [x] Call `argoCdAdapter.getEnvironmentStatuses(app.name, List.of(env))` to get live status
  - [x] Map `PortalEnvironmentStatus` → deployment status:
    - [x] `HEALTHY` → "Deployed", `completedAt` from `EnvironmentStatusDto.lastDeployedAt()`
    - [x] `UNHEALTHY` → "Failed", `completedAt` from `EnvironmentStatusDto.lastDeployedAt()`
    - [x] `DEPLOYING` → "Deploying", `completedAt` = null
    - [x] `NOT_DEPLOYED` → "Deploying", `completedAt` = null (ArgoCD hasn't detected the Git change yet)
  - [x] Catch `PortalIntegrationException` → default to "Deploying" (graceful degradation)
  - [x] Only enrich the most recent deployment per environment — older deployments default to "Deployed"

- [x] Task 6: Add GET endpoint to DeploymentResource (AC: #1, #2, #7)
  - [x] Add `@GET` method to existing `DeploymentResource` (created in Story 5.1)
  - [x] Accept `@PathParam teamId`, `@PathParam appId`, `@QueryParam("environmentId") Long environmentId` (optional)
  - [x] Returns `List<DeploymentHistoryDto>` with 200 OK
  - [x] PermissionFilter maps GET on `deployments` resource to `read` action — Casbin `p, member, deployments, read` already exists

- [x] Task 7: Add environmentId to EnvironmentChainEntry type and backend DTO (AC: #5)
  - [x] Add `Long environmentId` field to `EnvironmentChainEntryDto` Java record (add at end to avoid breaking existing constructor calls)
  - [x] Update `EnvironmentMapper.merge()` to include `environment.id` in the DTO
  - [x] Add `environmentId: number` to `EnvironmentChainEntry` TypeScript type
  - [x] Update `EnvironmentChain.tsx` to pass `teamId` and `appId` to `EnvironmentCard` via props

- [x] Task 8: Create frontend types for deployment (AC: #5)
  - [x] Create `src/main/webui/src/types/deployment.ts`
  - [x] Types: `DeploymentStatus = 'Deploying' | 'Deployed' | 'Failed'`, `DeploymentHistoryEntry` with: `deploymentId` (string), `releaseVersion`, `status` (DeploymentStatus), `startedAt`, `completedAt`, `deployedBy`, `environmentName`, `argocdDeepLink`

- [x] Task 9: Create frontend API function and hook (AC: #5)
  - [x] Create `src/main/webui/src/api/deployments.ts` — `fetchDeploymentHistory(teamId, appId, environmentId)` using `apiFetch<DeploymentHistoryEntry[]>`
  - [x] Create `src/main/webui/src/hooks/useDeployments.ts` — `useDeployments(teamId, appId, environmentId)` wrapping `useApiFetch` with conditional path (skip when environmentId is undefined)

- [x] Task 10: Update EnvironmentCard with deployment history section (AC: #5, #6)
  - [x] Add `teamId` and `appId` props to `EnvironmentCardProps` interface
  - [x] Replace the "Deployment history coming in Epic 5" placeholder in `EnvironmentCard.tsx`
  - [x] When card is expanded and `entry.environmentId` is available: call `useDeployments` (pass null path when collapsed to skip fetch)
  - [x] Loading: PF6 `Spinner` (small) with "Loading deployment history..."
  - [x] Success: PF6 compact `Table` with columns: Version, Status, When, By
  - [x] Status column uses PF6 `Label`: "Deployed" → `status="success"` + `CheckCircleIcon`, "Deploying" → `status="warning"` + `SyncAltIcon`, "Failed" → `status="danger"` + `ExclamationCircleIcon`
  - [x] "When" column shows relative time (reuse existing `relativeTime` helper)
  - [x] Most recent deployment (first row) highlighted with bold text
  - [x] "Failed" rows include ArgoCD deep link button
  - [x] Empty state: "No deployments yet"
  - [x] Error state: PF6 inline `Alert` variant="danger"

- [x] Task 11: Write backend tests (AC: #1, #2, #3, #4, #7)
  - [x] `DeploymentServiceHistoryTest.java` (`@QuarkusTest` + `@InjectMock`):
    - [x] List by environment: mock `gitProvider.listCommits` returning 3 deploy commits → verify 3 `DeploymentHistoryDto` entries with correct fields
    - [x] List all environments: mock for 2 envs → merged, sorted by timestamp
    - [x] Commit message parsing: `"deploy: v1.4.2 to dev\n\nDeployed-By: marco"` → version=v1.4.2, deployedBy=marco
    - [x] Commit message without trailer: falls back to commit author
    - [x] Non-deploy commit skipped: commit message "fix: typo in values" → not included in results
    - [x] Enrichment — HEALTHY: mock ArgoCD returning `HEALTHY` → most recent status = "Deployed", completedAt set
    - [x] Enrichment — UNHEALTHY: mock ArgoCD returning `UNHEALTHY` → most recent status = "Failed"
    - [x] Enrichment — DEPLOYING: mock ArgoCD returning `DEPLOYING` → status stays "Deploying"
    - [x] Enrichment — NOT_DEPLOYED: mock ArgoCD → status stays "Deploying"
    - [x] Enrichment — ArgoCD unreachable: mock throws → status stays "Deploying" (no exception propagated)
    - [x] Older deployments: default to "Deployed" status (no ArgoCD call)
    - [x] Team ownership: list for app in different team → 404
    - [x] Environment ownership: environmentId from different app → 404
  - [x] `DeploymentResourceIT.java` (extend from 5.1 — REST Assured):
    - [x] GET `/deployments?environmentId=X` → 200 with JSON array containing correct fields
    - [x] GET `/deployments` → 200 with all deployments
    - [x] Cross-team → 404
    - [x] Invalid environmentId → 404
    - [x] Casbin: member can read, unauthenticated → 401

- [x] Task 12: Write frontend tests (AC: #5, #6)
  - [x] `EnvironmentCard.test.tsx` (extend existing):
    - [x] Expanded card fetches deployment history
    - [x] Loading state shows Spinner
    - [x] Deployment history table renders with correct columns
    - [x] Status labels show correct variants (success/warning/danger)
    - [x] Failed deployment shows ArgoCD deep link
    - [x] Empty state shows "No deployments yet"
    - [x] Error state shows inline Alert
  - [x] `useDeployments.test.ts`:
    - [x] Returns deployment list
    - [x] Skips fetch when environmentId is undefined
    - [x] Error handling

### Review Findings

- [x] [Review][Patch] Preserve fetch failures instead of returning an empty deployment list [`developer-portal/src/main/java/com/portal/deployment/DeploymentService.java:154`] — fixed: single-env queries now propagate `PortalIntegrationException`; multi-env path still degrades gracefully; 2 tests added
- [x] [Review][Patch] Dev Git mock truncates hyphenated environment names in `values-run-<env>.yaml` [`developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java:40`] — fixed: regex changed from `(\w+)` to `([\w-]+)`
- [x] [Review][Defer] `EnvironmentMapper.merge()` still throws on duplicate environment names from status list [`developer-portal/src/main/java/com/portal/environment/EnvironmentMapper.java:17`] — deferred, pre-existing

## Dev Notes

### Prerequisite: Story 5.1 Must Be Implemented First

This story builds on Story 5.1's `DeploymentService`, `DeploymentResource`, `DeploymentStatusDto`, and `DeployRequest`. Those classes do not exist yet. **Do not start 5.2 until 5.1 is implemented and merged.**

### All File Paths Are Relative to `developer-portal/`

Every backend path (e.g., `src/main/java/com/portal/...`) and frontend path (e.g., `src/main/webui/src/...`) is relative to `developer-portal/`.

### Critical: No Database Table — Git IS the Deployment Ledger

**Design decision (party mode discussion 2026-04-09):** Deployment history comes from Git commit history on `values-run-<env>.yaml` files, NOT from a portal database table. This maintains the architecture principle that the portal doesn't persist deployment state.

Story 5.1 deploys by committing an imageTag change to `values-run-<env>.yaml`. Each commit is a deployment record:
- **Who:** commit author / `Deployed-By:` trailer
- **What version:** imageTag in the diff / parsed from structured commit message
- **When:** commit timestamp
- **To which env:** which `values-run-<env>.yaml` file was changed
- **Status:** enriched from live ArgoCD state for the most recent deployment

### GitProvider.listCommits — New Interface Method

The only new `GitProvider` method needed. Returns commits that touched a specific file path:

```java
/**
 * Lists commits that modified the given file, ordered by timestamp descending.
 *
 * @param repoUrl    the HTTPS URL of the repository
 * @param filePath   path to the file within the repository
 * @param maxResults maximum number of commits to return
 * @return commits ordered by timestamp descending
 */
List<GitCommit> listCommits(String repoUrl, String filePath, int maxResults);
```

**GitCommit model:**

```java
public record GitCommit(
    String sha,
    String author,
    Instant timestamp,
    String message
) {}
```

**Implementation per provider:**
- **GitHub:** `GET /repos/{owner}/{repo}/commits?path={filePath}&per_page={limit}`
- **GitLab:** `GET /projects/{id}/repository/commits?path={filePath}&per_page={limit}`
- **Gitea:** `GET /repos/{owner}/{repo}/git/commits?path={filePath}&limit={limit}`
- **Bitbucket:** `GET /repositories/{workspace}/{slug}/commits?path={filePath}&pagelen={limit}`
- **Dev mock:** return canned commits matching the deploy convention

This follows the exact same cross-cutting pattern as `listTags` (Story 4.5) and `createTag` (Story 4.4) — one new method across 5 implementations.

### Commit Message Parsing

Story 5.1 uses structured commit messages:

```
deploy: v1.4.2 to qa

Deployed-By: marco
```

Parsing logic:

```java
private Optional<DeploymentHistoryDto> parseDeployCommit(GitCommit commit, String envName, String argocdDeepLink) {
    String subject = commit.message().lines().findFirst().orElse("");
    
    // Match "deploy: <version> to <env>"
    if (!subject.startsWith("deploy: ")) {
        return Optional.empty();  // Not a deployment commit — skip
    }
    
    String[] parts = subject.substring("deploy: ".length()).split(" to ", 2);
    if (parts.length < 2) {
        return Optional.empty();
    }
    
    String version = parts[0].trim();
    String deployedBy = extractTrailer(commit.message(), "Deployed-By")
            .orElse(commit.author());
    
    return Optional.of(new DeploymentHistoryDto(
        commit.sha(),
        version,
        "Deployed",  // default — enriched for most recent only
        commit.timestamp(),
        null,         // completedAt — enriched for most recent only
        deployedBy,
        envName,
        argocdDeepLink
    ));
}
```

**Non-deployment commits are skipped.** If someone edits `values-run-dev.yaml` manually without the `deploy:` prefix, it's not included in the deployment history. This is intentional — only portal-initiated deployments appear.

### Status Enrichment — Most Recent Only

The portal enriches only the **most recent** deployment per environment with live ArgoCD status. All older deployments default to "Deployed" (they were superseded). This minimizes ArgoCD API calls — one call per environment, not per deployment.

| ArgoCD Status | Deployment Status | completedAt |
|---|---|---|
| HEALTHY | "Deployed" | `EnvironmentStatusDto.lastDeployedAt()` |
| UNHEALTHY | "Failed" | `EnvironmentStatusDto.lastDeployedAt()` |
| DEPLOYING | "Deploying" | null |
| NOT_DEPLOYED | "Deploying" | null (ArgoCD hasn't detected Git change yet) |

If ArgoCD is unreachable → default to "Deploying" (graceful degradation). The history query never fails because of ArgoCD issues.

### Frontend: EnvironmentChainEntry Needs environmentId

Same as originally designed — add `environmentId` to `EnvironmentChainEntryDto` and the TypeScript type so the frontend can call `GET /deployments?environmentId=X`.

Add at the **end** of the record to avoid breaking existing constructor calls:

```java
public record EnvironmentChainEntryDto(
    String environmentName,
    String clusterName,
    String namespace,
    int promotionOrder,
    String status,
    String deployedVersion,
    Instant lastDeployedAt,
    String argocdDeepLink,
    String vaultDeepLink,
    String grafanaDeepLink,
    Long environmentId            // NEW — at the end
) {}
```

### Frontend: EnvironmentCard Props Change

Add `teamId` and `appId` props so the card can build the deployment history API path:

```typescript
interface EnvironmentCardProps {
  entry: EnvironmentChainEntry;
  nextEnvName?: string;
  teamId: string;
  appId: string;
}
```

### Query Performance Consideration

Git API calls for commit history (100-500ms per provider) are slower than a database query (~5ms). This is acceptable because:
- Deployment history loads on card expand (lazy, user-initiated)
- Limited to 25 commits per environment
- Git providers have efficient file-path-scoped commit APIs
- No background polling — data fetched on demand

### Project Structure Notes

```
com.portal.deployment/
├── package-info.java            # EXISTS — placeholder
├── DeploymentResource.java      # FROM 5.1 — add @GET method
├── DeploymentService.java       # FROM 5.1 — add listDeployments + enrichment + parsing
├── DeploymentStatusDto.java     # FROM 5.1 — POST response (unchanged)
├── DeploymentHistoryDto.java    # NEW — GET response DTO
└── DeployRequest.java           # FROM 5.1 — request body (unchanged)

com.portal.integration.git/
├── GitProvider.java             # MODIFIED — add listCommits()
├── GitHubProvider.java          # MODIFIED — implement listCommits()
├── GitLabProvider.java          # MODIFIED — implement listCommits()
├── GiteaProvider.java           # MODIFIED — implement listCommits()
├── BitbucketProvider.java       # MODIFIED — implement listCommits()
├── DevGitProvider.java          # MODIFIED — implement listCommits() mock
└── model/GitCommit.java         # NEW — commit record

src/main/webui/src/
├── types/deployment.ts          # NEW
├── api/deployments.ts           # NEW
├── hooks/useDeployments.ts      # NEW
└── components/environment/
    ├── EnvironmentCard.tsx       # MODIFIED — deployment history section
    └── EnvironmentChain.tsx      # MODIFIED — pass teamId/appId to cards
```

### File Structure Requirements

**New backend files:**

```
src/main/java/com/portal/deployment/DeploymentHistoryDto.java
src/main/java/com/portal/integration/git/model/GitCommit.java
```

**Modified backend files:**

```
src/main/java/com/portal/integration/git/GitProvider.java              (add listCommits)
src/main/java/com/portal/integration/git/GitHubProvider.java           (implement listCommits)
src/main/java/com/portal/integration/git/GitLabProvider.java           (implement listCommits)
src/main/java/com/portal/integration/git/GiteaProvider.java            (implement listCommits)
src/main/java/com/portal/integration/git/BitbucketProvider.java        (implement listCommits)
src/main/java/com/portal/integration/git/DevGitProvider.java           (implement listCommits mock)
src/main/java/com/portal/deployment/DeploymentService.java             (add listDeployments + enrichment)
src/main/java/com/portal/deployment/DeploymentResource.java            (add @GET method)
src/main/java/com/portal/environment/EnvironmentChainEntryDto.java     (add environmentId)
src/main/java/com/portal/environment/EnvironmentMapper.java            (pass environment.id)
```

**New frontend files:**

```
src/main/webui/src/types/deployment.ts
src/main/webui/src/api/deployments.ts
src/main/webui/src/hooks/useDeployments.ts
```

**Modified frontend files:**

```
src/main/webui/src/types/environment.ts                               (add environmentId)
src/main/webui/src/components/environment/EnvironmentCard.tsx          (deployment history)
src/main/webui/src/components/environment/EnvironmentChain.tsx         (pass teamId/appId)
```

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `GitProvider.java` | `com.portal.integration.git` | EXISTS — add `listCommits()` |
| `GitHubProvider` + 3 others | `com.portal.integration.git` | EXISTS — implement `listCommits()` |
| `DevGitProvider` | `com.portal.integration.git` | EXISTS — mock `listCommits()` |
| `DeploymentResource.java` | `com.portal.deployment` | FROM 5.1 — add @GET |
| `DeploymentService.java` | `com.portal.deployment` | FROM 5.1 — add list + enrichment |
| `ArgoCdAdapter.java` | `com.portal.integration.argocd` | EXISTS — `getEnvironmentStatuses` for enrichment (NOT modified) |
| `PortalEnvironmentStatus` | `com.portal.environment` | EXISTS — HEALTHY, UNHEALTHY, DEPLOYING, NOT_DEPLOYED |
| `EnvironmentStatusDto` | `com.portal.environment` | EXISTS — live status from ArgoCD |
| `DeepLinkService` | `com.portal.deeplink` | EXISTS — `generateArgoCdLink()` |
| `EnvironmentCard.tsx` | `components/environment/` | EXISTS — replace placeholder |
| `EnvironmentChain.tsx` | `components/environment/` | EXISTS — pass through teamId/appId |
| `useApiFetch.ts` | `hooks/` | EXISTS — generic fetch hook |
| `apiFetch()` | `api/client.ts` | EXISTS — typed fetch wrapper |
| `casbin/policy.csv` | `resources/casbin/` | EXISTS — `deployments, read` |

### What NOT to Build

- **No Flyway migration** — no database table for deployments
- **No Deployment Panache entity** — Git is the ledger
- **No `@Transactional` annotations** — no database writes
- **No deploy/promote buttons** — Story 5.3
- **No production gating UI** — Story 5.4
- **No background polling** — history fetched on demand from Git
- **No pagination controls** — fixed limits (25/50) sufficient for MVP
- **No modification to EnvironmentService** — env chain endpoint stays as-is
- **No new ArgoCD methods** — reuse existing `getEnvironmentStatuses` for enrichment
- **No commit SHA return from `commitFiles`** — Story 5.2 gets SHAs from `listCommits`

### Anti-Patterns to Avoid

- **DO NOT** create a database table for deployment history — Git commit history IS the deployment ledger
- **DO NOT** fetch ArgoCD status for every historical deployment — only the most recent per env
- **DO NOT** fail the GET endpoint if ArgoCD is unreachable — graceful degradation
- **DO NOT** import entities from other packages — use `findById()` for cross-domain lookups
- **DO NOT** call `GitProvider` or `ArgoCdAdapter` from `DeploymentResource` — go through `DeploymentService`
- **DO NOT** return 403 for cross-team access — return 404
- **DO NOT** assume all commits to values files are deployments — filter by `deploy:` prefix

### Architecture Compliance

- Resource → Service → Adapter: `DeploymentResource` → `DeploymentService` → `GitProvider` + `ArgoCdAdapter`
- `require*` ownership validation methods
- Team-scoped access: 404 for cross-team or missing resources
- Portal doesn't persist deployment state — Git is the source of truth
- `getEnvironmentStatuses` used read-only for enrichment
- Cross-cutting `GitProvider` interface change follows proven Epic 4 pattern
- Developer domain language in API responses

### Previous Story Intelligence

**Story 5.1 (Deploy Release — Git-based):**
- Establishes `DeploymentService`, `DeploymentResource`, `DeployRequest`, `DeploymentStatusDto`
- Commit message convention: `deploy: <version> to <env>\n\nDeployed-By: <user>`
- `require*` ownership methods to reuse
- Values file path: `.helm/run/values-run-<env>.yaml`

**Story 4.4/4.5 (GitProvider.createTag/listTags):**
- Established pattern for cross-cutting GitProvider interface changes across 5 implementations
- Same approach for `listCommits` — one new method, 5 implementations

**Story 2.7/2.8 (ArgoCD + Environment Chain):**
- `ArgoCdAdapter.getEnvironmentStatuses` — reused for enrichment
- `PortalEnvironmentStatus` mapping — same status translation
- `EnvironmentCard.tsx` with expand/collapse — the component we modify

**Epic 4 Retrospective:**
- `require*` naming convention — reuse from 5.1
- Authorization AC template — AC #7
- Zero-failure test gate

### Data Flow

```
GET /api/v1/teams/{teamId}/applications/{appId}/deployments?environmentId=42

  → PermissionFilter: Casbin check (deployments, read)
  → DeploymentResource.listDeployments(teamId, appId, environmentId=42)
  → DeploymentService.listDeployments(teamId, appId, 42)
    → requireTeamApplication(teamId, appId) — 404 if cross-team
    → requireApplicationEnvironment(appId, 42) — 404 if wrong app
    → env = Environment.findById(42) → name="qa"
    → gitProvider.listCommits(app.gitRepoUrl, ".helm/run/values-run-qa.yaml", 25)
    → For each commit:
        Parse "deploy: v1.4.2 to qa\n\nDeployed-By: marco"
        → { sha, "v1.4.2", "Deployed", timestamp, null, "marco", "qa", argocdLink }
    → Enrich most recent:
        argoCdAdapter.getEnvironmentStatuses(app.name, [env])
        → HEALTHY → status="Deployed", completedAt=operationFinishedAt
    → Return List<DeploymentHistoryDto>
  → 200 OK: [
      { deploymentId: "abc123", releaseVersion: "v1.4.2", status: "Deployed",
        startedAt: "...", completedAt: "...", deployedBy: "marco",
        environmentName: "qa", argocdDeepLink: "https://argocd.../..." },
      { deploymentId: "def456", releaseVersion: "v1.4.1", status: "Deployed", ... },
      ...
    ]
```

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` § Story 5.2 — Deployment Status & History]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR25 — View deployment status]
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Data Architecture] — portal doesn't persist deployment state
- [Source: `_bmad-output/planning-artifacts/architecture.md` § REST API Structure] — `/deployments` endpoint
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Environment Chain Card Row] — expand to show deployment history
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Feedback Patterns] — deployment success/failure transitions
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` § Data Tables] — compact variant for history
- [Source: `_bmad-output/project-context.md` § Resource Ownership Validation] — require* pattern
- [Source: `_bmad-output/project-context.md` § Data Model] — 4 persisted entities (unchanged by this story)
- [Source: `_bmad-output/project-context.md` § GitOps Contract] — app repo .helm/run/ structure
- [Source: `_bmad-output/implementation-artifacts/5-1-deploy-release-to-environment.md`] — Git-based deployment mechanism
- [Source: `_bmad-output/implementation-artifacts/epic-4-retro-2026-04-09.md`] — require* convention
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitProvider.java`] — interface to extend
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitHubProvider.java`] — GitHub implementation pattern
- [Source: `developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java`] — Dev mock pattern
- [Source: `developer-portal/src/main/java/com/portal/integration/git/model/GitTag.java`] — Model record pattern
- [Source: `developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdRestAdapter.java`] — translateStatus
- [Source: `developer-portal/src/main/java/com/portal/environment/EnvironmentChainEntryDto.java`] — add environmentId
- [Source: `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx`] — replace placeholder
- [Source: `developer-portal/src/main/webui/src/hooks/useApiFetch.ts`] — hook pattern
- [Source: `developer-portal/src/main/webui/src/api/releases.ts`] — API function pattern

## Dev Agent Record

### Agent Model Used

claude-4.6-opus-high-thinking

### Debug Log References

- Backend unit tests: 463 passed (0 failures) — Surefire
- Frontend tests: 265 passed (0 failures) — Vitest
- TypeScript compilation: clean (`tsc --noEmit` passes)
- Pre-existing TS errors in `BuildStatusBadge.tsx` and `CreateReleaseModal.test.tsx` block `tsc -b` (Maven Quinoa build) — not related to this story

### Completion Notes List

- Implemented Git-based deployment history — no database table, Git commit history IS the deployment ledger
- Added `listCommits` to `GitProvider` interface following the same cross-cutting pattern as `listTags`/`createTag` (Epic 4)
- All 5 provider implementations (GitHub, GitLab, Gitea, Bitbucket, Dev mock) extended with provider-specific commit list APIs
- `DeploymentService.listDeployments()` queries Git commit history, parses structured commit messages, and enriches the most recent deployment per environment with live ArgoCD status
- Non-deployment commits (missing `deploy:` prefix) are gracefully skipped
- ArgoCD enrichment is fault-tolerant — unreachable ArgoCD defaults to "Deploying" status
- Frontend EnvironmentCard expanded section now shows deployment history table replacing the placeholder
- Deployment history is lazy-loaded (fetched only when card is expanded)
- `environmentId` added to `EnvironmentChainEntryDto` at the end of the record to avoid breaking existing constructor calls
- 13 new backend tests in `DeploymentServiceHistoryTest.java` covering all AC scenarios
- 5 new GET endpoint tests in `DeploymentResourceIT.java`
- 7 new frontend component tests in `EnvironmentCard.test.tsx`
- 6 new hook tests in `useDeployments.test.ts`
- Updated existing test files to include `environmentId` in test data (`EnvironmentChain.test.tsx`, `ApplicationOverviewPage.test.tsx`, `ApplicationSettingsPage.test.tsx`)

### Change Log

- 2026-04-10: Story 5.2 implemented — deployment status & history via Git commit history with ArgoCD enrichment

### File List

**New backend files:**
- `developer-portal/src/main/java/com/portal/integration/git/model/GitCommit.java`
- `developer-portal/src/main/java/com/portal/deployment/DeploymentHistoryDto.java`
- `developer-portal/src/test/java/com/portal/deployment/DeploymentServiceHistoryTest.java`

**Modified backend files:**
- `developer-portal/src/main/java/com/portal/integration/git/GitProvider.java` (added listCommits)
- `developer-portal/src/main/java/com/portal/integration/git/GitHubProvider.java` (implement listCommits)
- `developer-portal/src/main/java/com/portal/integration/git/GitLabProvider.java` (implement listCommits)
- `developer-portal/src/main/java/com/portal/integration/git/GiteaProvider.java` (implement listCommits)
- `developer-portal/src/main/java/com/portal/integration/git/BitbucketProvider.java` (implement listCommits)
- `developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java` (implement listCommits mock)
- `developer-portal/src/main/java/com/portal/deployment/DeploymentService.java` (added listDeployments + enrichment + parsing)
- `developer-portal/src/main/java/com/portal/deployment/DeploymentResource.java` (added @GET method)
- `developer-portal/src/main/java/com/portal/environment/EnvironmentChainEntryDto.java` (added environmentId)
- `developer-portal/src/main/java/com/portal/environment/EnvironmentMapper.java` (pass environment.id)
- `developer-portal/src/test/java/com/portal/deployment/DeploymentResourceIT.java` (added GET endpoint tests)

**New frontend files:**
- `developer-portal/src/main/webui/src/types/deployment.ts`
- `developer-portal/src/main/webui/src/api/deployments.ts`
- `developer-portal/src/main/webui/src/hooks/useDeployments.ts`
- `developer-portal/src/main/webui/src/hooks/useDeployments.test.ts`

**Modified frontend files:**
- `developer-portal/src/main/webui/src/types/environment.ts` (added environmentId)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx` (deployment history section)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.tsx` (pass teamId/appId)
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx` (pass teamId/appId to chain)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.test.tsx` (deployment history tests)
- `developer-portal/src/main/webui/src/components/environment/EnvironmentChain.test.tsx` (added environmentId)
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.test.tsx` (added environmentId)
- `developer-portal/src/main/webui/src/routes/ApplicationSettingsPage.test.tsx` (added environmentId)

**Planning files:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status updated)
- `_bmad-output/implementation-artifacts/5-2-deployment-status-history.md` (tasks checked, Dev Agent Record)
