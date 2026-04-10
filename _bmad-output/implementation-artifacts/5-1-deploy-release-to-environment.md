# Story 5.1: Deploy Release to Environment

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to deploy a specific release to any environment in my application's promotion chain,
so that I can get my code running in the target environment without using ArgoCD directly.

## Acceptance Criteria

1. **Git-based deployment: commit imageTag change to values file**
   - **Given** a developer deploys a release
   - **When** `DeploymentService.deployRelease()` is called
   - **Then** the portal reads the current `.helm/run/values-run-<env>.yaml` from the app repo via `GitProvider.readFile()`
   - **And** it updates the `image.tag` value to the specified release version
   - **And** it commits the change via `GitProvider.commitFiles()` with a structured commit message: `deploy: <version> to <env>`
   - **And** the commit author identity includes the portal user's name (from TeamContext)
   - **And** ArgoCD auto-sync detects the Git drift and reconciles the environment

2. **POST deployments endpoint triggers deployment and returns 201**
   - **Given** a developer deploys a release
   - **When** POST `/api/v1/teams/{teamId}/applications/{appId}/deployments` is called with `{ releaseVersion, environmentId }`
   - **Then** the DeploymentService resolves the application, environment, and performs the Git commit
   - **And** a 201 Created response is returned with: `deploymentId` (commit SHA), `releaseVersion`, `environmentName`, `status` ("Deploying"), `startedAt`

3. **Environment chain reflects deployment status via existing ArgoCD polling**
   - **Given** the Git commit is made and ArgoCD auto-sync begins reconciliation
   - **When** the environment chain re-fetches status (Stories 2.7/2.8)
   - **Then** the environment card updates to show "⟳ Deploying v1.4.2..." through the existing `getEnvironmentStatuses` polling
   - **And** transitions to "✓ Healthy" or "✕ Unhealthy" once ArgoCD completes reconciliation

4. **Integration failure returns PortalIntegrationException with developer-friendly error**
   - **Given** the Git server is unreachable or returns an error
   - **When** the deployment commit is attempted
   - **Then** a `PortalIntegrationException` is thrown with `system="git"`, `operation="deploy-release"`
   - **And** the error message is developer-friendly: "Deployment to [env] failed — could not commit to repository"

5. **Casbin authorization permits member, lead, and admin roles**
   - **Given** the Casbin permission check runs for a deployment request
   - **When** a developer with "member", "lead", or "admin" role deploys
   - **Then** the request is permitted (existing `p, member, deployments, deploy` policy)

6. **Argo Rollouts progressive delivery is handled transparently**
   - **Given** an Argo Rollouts progressive delivery strategy is configured
   - **When** ArgoCD auto-sync reconciles after the Git commit
   - **Then** Argo Rollouts manages the progressive delivery invisibly
   - **And** the portal reflects the rollout status through ArgoCD Application health

7. **Resource ownership validation (mandatory scoping pattern)**
   - **Given** a request targets a resource outside the caller's team scope
   - **Then** 404 is returned (never 403)
   - **Given** a request targets an environment that does not belong to the application
   - **Then** 404 is returned
   - **Given** a request targets a non-existent application or environment
   - **Then** 404 is returned

## Tasks / Subtasks

- [ ] Task 1: Create DeployRequest record (AC: #2)
  - [ ] Create `com.portal.deployment.DeployRequest` record with fields: `String releaseVersion`, `Long environmentId`
  - [ ] Both fields are required (non-null)

- [ ] Task 2: Create DeploymentStatusDto record (AC: #2)
  - [ ] Create `com.portal.deployment.DeploymentStatusDto` record with fields: `String deploymentId`, `String releaseVersion`, `String environmentName`, `String status`, `Instant startedAt`
  - [ ] `deploymentId` is the Git commit SHA returned by the commit operation
  - [ ] `status` is always "Deploying" in the POST response (live status comes from existing environment chain polling)

- [ ] Task 3: Create DeploymentService (AC: #1, #2, #4, #7)
  - [ ] Create `com.portal.deployment.DeploymentService` as `@ApplicationScoped`
  - [ ] Inject `GitProvider`, `TeamContext`
  - [ ] `deployRelease(Long teamId, Long appId, DeployRequest request)` method:
    - [ ] `requireTeamApplication(teamId, appId)` — Application lookup with team ownership check → 404
    - [ ] `requireApplicationEnvironment(appId, request.environmentId())` — Environment lookup with application ownership check → 404
    - [ ] Build values file path: `.helm/run/values-run-` + env.name.toLowerCase() + `.yaml`
    - [ ] Read current file: `gitProvider.readFile(app.gitRepoUrl, defaultBranch, valuesFilePath)`
    - [ ] Parse YAML and update `image.tag` to `request.releaseVersion()`
    - [ ] Build commit message: `"deploy: " + request.releaseVersion() + " to " + env.name.toLowerCase()`
    - [ ] Commit: `gitProvider.commitFiles(app.gitRepoUrl, defaultBranch, Map.of(valuesFilePath, updatedYaml), commitMessage)`
    - [ ] Return `DeploymentStatusDto` with commit SHA (or a correlation ID), releaseVersion, envName, "Deploying", `Instant.now()`
  - [ ] Use `require*` naming convention for ownership validation methods per Epic 4 retro action
  - [ ] Add `portal.git.default-branch` config property (default: `"main"`) via `@ConfigProperty`

- [ ] Task 4: Implement YAML imageTag update logic (AC: #1)
  - [ ] Add private method `updateImageTag(String yamlContent, String newTag)` to `DeploymentService`
  - [ ] Parse YAML using SnakeYAML (`org.yaml.snakeyaml.Yaml`) — already available transitively in Quarkus
  - [ ] Navigate to `image.tag` key and update its value
  - [ ] Serialize back to YAML string preserving structure
  - [ ] Handle missing `image.tag` key gracefully → throw `IllegalArgumentException` with clear message
  - [ ] Alternative simpler approach: regex-based replacement of `tag: <old>` → `tag: <new>` if YAML structure is predictable — choose based on implementation complexity

- [ ] Task 5: Create DeploymentResource (AC: #2, #5)
  - [ ] Create `com.portal.deployment.DeploymentResource` at `@Path("/api/v1/teams/{teamId}/applications/{appId}/deployments")`
  - [ ] `@POST` method accepting `@PathParam teamId`, `@PathParam appId`, `DeployRequest` body
  - [ ] Returns `Response` with 201 Created status and `DeploymentStatusDto` entity
  - [ ] `@Produces(MediaType.APPLICATION_JSON)` and `@Consumes(MediaType.APPLICATION_JSON)`
  - [ ] PermissionFilter already maps POST on `deployments` resource to `deploy` action — no Casbin policy changes needed

- [ ] Task 6: Write backend tests (AC: #1, #2, #4, #5, #7)
  - [ ] `DeploymentServiceTest.java` (`@QuarkusTest` + `@InjectMock`):
    - [ ] Happy path: mock `GitProvider.readFile` returning sample YAML, mock `commitFiles`, verify commit is called with correct updated YAML and structured commit message
    - [ ] Verify imageTag is correctly updated in YAML content
    - [ ] Team-scoped 404: attempt to deploy for app in different team → 404
    - [ ] Environment ownership 404: environmentId from different app → 404
    - [ ] Non-existent application → 404
    - [ ] Non-existent environment → 404
    - [ ] GitProvider throws `PortalIntegrationException` → propagated to caller (→ 502 via GlobalExceptionMapper)
    - [ ] Missing `image.tag` in YAML → clear error
  - [ ] `DeploymentResourceIT.java` (REST Assured):
    - [ ] POST → 201 with JSON body containing `deploymentId`, `releaseVersion`, `environmentName`, `status`, `startedAt`
    - [ ] Cross-team access → 404
    - [ ] Invalid environment → 404
    - [ ] Git provider failure → 502 with standardized error JSON
    - [ ] Verify Casbin: member can deploy, unauthenticated → 401
  - [ ] `DeploymentServiceYamlTest.java` (unit test, no Quarkus):
    - [ ] `updateImageTag` with standard values YAML → correct output
    - [ ] `updateImageTag` with missing image.tag → error
    - [ ] `updateImageTag` preserves other YAML keys and comments

- [ ] Task 7: Verify PermissionFilter integration (AC: #5)
  - [ ] Confirm `PermissionFilter.mapAction` returns `"deploy"` for POST on `deployments` resource (already implemented)
  - [ ] Confirm `policy.csv` has `p, member, deployments, deploy` (already exists)

## Dev Notes

### All File Paths Are Relative to `developer-portal/`

Every backend path (e.g., `src/main/java/com/portal/...`) is relative to the `developer-portal/` directory at the repository root.

### Critical: This Story Uses Git-Based Deployment (Not ArgoCD API)

**Design decision (party mode discussion 2026-04-09):** Deployment is a Git commit to the app repo's `values-run-<env>.yaml`, NOT an ArgoCD API parameter override + sync. This keeps the portal consistent as a "Git orchestration layer" — all write operations go through Git. ArgoCD auto-sync detects the drift and reconciles. The portal never calls ArgoCD write APIs for deployment.

**Why Git-based instead of ArgoCD API override:**
- All portal write operations flow through Git (onboarding PRs, release tags, now deployments) — consistent pattern
- Deployment history is Git commit history — no portal database table needed (Story 5.2)
- "Who deployed" is the commit author — ArgoCD API overrides lose user identity
- The desired state is always in Git, not in ArgoCD memory — true GitOps
- ArgoCD auto-sync handles reconciliation without explicit sync API calls

### Deployment Mechanism

```
Developer clicks "Deploy v1.4.2 to QA"
  → POST /api/v1/teams/{teamId}/applications/{appId}/deployments
    { releaseVersion: "v1.4.2", environmentId: 42 }
  → DeploymentService.deployRelease():
    1. requireTeamApplication(teamId, appId)    → 404 if cross-team
    2. requireApplicationEnvironment(appId, 42) → 404 if wrong app
    3. GitProvider.readFile(app.gitRepoUrl, "main", ".helm/run/values-run-qa.yaml")
    4. Parse YAML, update image.tag → "v1.4.2"
    5. GitProvider.commitFiles(app.gitRepoUrl, "main",
         {".helm/run/values-run-qa.yaml": updatedYaml},
         "deploy: v1.4.2 to qa")
    6. Return DeploymentStatusDto(commitSha, "v1.4.2", "qa", "Deploying", now)
  → 201 Created

  (asynchronously)
  ArgoCD auto-sync detects Git drift → reconciles → env goes Healthy/Unhealthy
  Environment chain polling (Stories 2.7/2.8) reflects the transition
```

### YAML Update — values-run-<env>.yaml

The values file follows the Helm convention. A typical `values-run-dev.yaml`:

```yaml
image:
  repository: registry.example.com/team/orders-api
  tag: v1.4.1
  pullPolicy: IfNotPresent

replicaCount: 2
```

The portal reads this file, updates `image.tag` to the new version, and commits it back. All other values are preserved unchanged.

**SnakeYAML parsing approach:**

```java
private String updateImageTag(String yamlContent, String newTag) {
    Yaml yaml = new Yaml();
    Map<String, Object> values = yaml.load(yamlContent);
    
    Object imageSection = values.get("image");
    if (!(imageSection instanceof Map)) {
        throw new IllegalArgumentException(
            "values file missing 'image' section — cannot deploy");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> image = (Map<String, Object>) imageSection;
    image.put("tag", newTag);
    
    return yaml.dump(values);
}
```

**Note on SnakeYAML availability:** SnakeYAML is available transitively in Quarkus via `smallrye-config-source-yaml`. No additional dependency needed. Use `org.yaml.snakeyaml.Yaml`.

### Commit Message Convention

Structured commit messages enable Story 5.2 to parse deployment history from Git:

```
deploy: v1.4.2 to qa
```

Format: `deploy: <releaseVersion> to <envName>`

The commit author is set by the Git provider's authentication token — in practice, the portal's service account. To identify **which portal user** triggered the deployment, include the username in the commit message body or use Git's author field. The simplest approach: append the user to the subject line or use a trailer:

```
deploy: v1.4.2 to qa

Deployed-By: marco
```

Use `teamContext.getTeamIdentifier()` for the user identity.

### Default Branch Configuration

Add `portal.git.default-branch` config property:

```properties
# application.properties
portal.git.default-branch=main
```

```java
@ConfigProperty(name = "portal.git.default-branch", defaultValue = "main")
String defaultBranch;
```

### DeploymentService — Ownership Validation with `require*` Pattern

```java
@ApplicationScoped
public class DeploymentService {

    @Inject GitProvider gitProvider;
    @Inject TeamContext teamContext;

    @ConfigProperty(name = "portal.git.default-branch", defaultValue = "main")
    String defaultBranch;

    public DeploymentStatusDto deployRelease(Long teamId, Long appId, DeployRequest request) {
        Application app = requireTeamApplication(teamId, appId);
        Environment env = requireApplicationEnvironment(appId, request.environmentId());

        String valuesPath = ".helm/run/values-run-" + env.name.toLowerCase() + ".yaml";
        String currentYaml = gitProvider.readFile(app.gitRepoUrl, defaultBranch, valuesPath);
        String updatedYaml = updateImageTag(currentYaml, request.releaseVersion());

        String commitMessage = "deploy: " + request.releaseVersion() + " to " + env.name.toLowerCase()
                + "\n\nDeployed-By: " + teamContext.getTeamIdentifier();

        gitProvider.commitFiles(app.gitRepoUrl, defaultBranch,
                Map.of(valuesPath, updatedYaml), commitMessage);

        return new DeploymentStatusDto(
            "commit",  // or extract SHA if GitProvider returns it
            request.releaseVersion(),
            env.name,
            "Deploying",
            Instant.now()
        );
    }

    private Application requireTeamApplication(Long teamId, Long appId) { ... }
    private Environment requireApplicationEnvironment(Long appId, Long envId) { ... }
}
```

**Note on commit SHA:** The current `GitProvider.commitFiles()` returns `void`. If the frontend or Story 5.2 needs the commit SHA as the deploymentId, we have two options: (a) change `commitFiles` to return a `String` SHA — this is a cross-cutting change across 5 providers, or (b) use a generated correlation ID. For MVP, option (b) is simpler — use `UUID.randomUUID().toString()` and let Story 5.2 retrieve actual commits from Git history. Alternatively, extend `commitFiles` to return the SHA if the extra information is valuable.

### Project Structure Notes

```
com.portal.deployment/
├── package-info.java            # EXISTS — placeholder from project scaffolding
├── DeploymentResource.java      # NEW — REST endpoint
├── DeploymentService.java       # NEW — business logic + ownership validation + YAML update
├── DeploymentStatusDto.java     # NEW — response DTO
└── DeployRequest.java           # NEW — request body record
```

### File Structure Requirements

**New backend files:**

```
src/main/java/com/portal/deployment/DeploymentResource.java
src/main/java/com/portal/deployment/DeploymentService.java
src/main/java/com/portal/deployment/DeploymentStatusDto.java
src/main/java/com/portal/deployment/DeployRequest.java
```

**New test files:**

```
src/test/java/com/portal/deployment/DeploymentServiceTest.java
src/test/java/com/portal/deployment/DeploymentResourceIT.java
src/test/java/com/portal/deployment/DeploymentServiceYamlTest.java
```

**No modified files** — unlike the previous ArgoCD-based design, we do NOT extend ArgoCdAdapter, ArgoCdRestClient, ArgoCdRestAdapter, or DevArgoCdAdapter. The deployment mechanism is entirely Git-based using the existing `GitProvider` interface.

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `GitProvider.java` | `com.portal.integration.git` | EXISTS — has `readFile()` and `commitFiles()` |
| `GitHubProvider.java` | `com.portal.integration.git` | EXISTS — implements readFile/commitFiles |
| `GitLabProvider.java` | `com.portal.integration.git` | EXISTS — implements readFile/commitFiles |
| `GiteaProvider.java` | `com.portal.integration.git` | EXISTS — implements readFile/commitFiles |
| `BitbucketProvider.java` | `com.portal.integration.git` | EXISTS — implements readFile/commitFiles |
| `DevGitProvider.java` | `com.portal.integration.git` | EXISTS — mock readFile/commitFiles |
| `GitProviderConfig.java` | `com.portal.integration.git` | EXISTS — provider, token, infraRepoUrl, apiUrl |
| `PortalIntegrationException` | `com.portal.integration` | EXISTS — use for all Git errors |
| `GlobalExceptionMapper` | `com.portal.common` | EXISTS — converts exceptions to 502 |
| `PermissionFilter.java` | `com.portal.auth` | EXISTS — handles `deployments, deploy` and `deploy-prod` |
| `TeamContext` | `com.portal.auth` | EXISTS — request-scoped, has `getTeamIdentifier()` |
| `Environment.java` | `com.portal.environment` | EXISTS — entity with applicationId, name |
| `Application.java` | `com.portal.application` | EXISTS — entity with gitRepoUrl, teamId |
| `ArgoCdAdapter` | `com.portal.integration.argocd` | EXISTS — `getEnvironmentStatuses` for status polling (NOT modified) |
| `EnvironmentCard.tsx` | `components/environment/` | EXISTS — already shows DEPLOYING status |
| `EnvironmentChain.tsx` | `components/environment/` | EXISTS — horizontal promotion flow |
| `casbin/policy.csv` | `resources/casbin/` | EXISTS — `deployments` permissions configured |
| `deployment/package-info.java` | `com.portal.deployment` | EXISTS — placeholder |

### What NOT to Build

- **No ArgoCdAdapter extension** — deployment is Git-based, not ArgoCD API
- **No ArgoCdRestClient PATCH or sync endpoints** — ArgoCD auto-sync handles reconciliation
- **No frontend changes** — UI deploy/promote buttons are Story 5.3
- **No deployment history / GET endpoint** — that's Story 5.2
- **No production gating** — that's Story 5.4 (Casbin `deploy-prod` gate)
- **No database table** for deployments — deployments are Git commits, not persisted entities
- **No Flyway migration** — no schema changes
- **No new Casbin policy entries** — existing `deployments, deploy` covers this
- **No new GitProvider interface methods** — `readFile` and `commitFiles` already exist
- **No polling, SSE, or WebSockets** — existing environment chain polling handles status updates
- **No release validation** against Git tags — if the version doesn't exist as an image, ArgoCD will report the error

### Anti-Patterns to Avoid

- **DO NOT** call ArgoCD write APIs (PATCH, sync) — use Git commit, let auto-sync handle it
- **DO NOT** persist deployment records in the database — deployments are Git commits
- **DO NOT** create a `Deployment` Panache entity or Flyway migration
- **DO NOT** add frontend code — backend only in this story
- **DO NOT** implement production gating (lead-only enforcement) — Story 5.4
- **DO NOT** implement GET /deployments or deployment history — Story 5.2
- **DO NOT** import entities from other packages — use IDs and `findById()` for cross-domain lookups
- **DO NOT** call `GitProvider` directly from `DeploymentResource` — go through `DeploymentService`
- **DO NOT** use Spring annotations — use Quarkus/CDI (`@ApplicationScoped`, `@Inject`, `@Path`)
- **DO NOT** return 403 for cross-team or cross-app resource access — return 404

### Architecture Compliance

- Resource → Service → Adapter call chain: `DeploymentResource` → `DeploymentService` → `GitProvider`
- `require*` ownership validation methods per Epic 4 retro action item
- Team-scoped access: 404 for cross-team or missing resources
- `PortalIntegrationException` for all Git failures → 502 via `GlobalExceptionMapper`
- 201 Created for successful POST
- `@ApplicationScoped` CDI bean for `DeploymentService`
- Developer domain language: "deployment", "environment", "release" — not "sync", "application", "tag"
- All portal write operations through Git — consistent with onboarding (PR), release (tag), deployment (commit)
- No ArgoCD write calls — portal reads from ArgoCD, writes through Git

### Testing Requirements

**Backend unit/integration tests:**

- `DeploymentServiceTest.java` (`@QuarkusTest` + `@InjectMock`):
  - Happy path: mock `GitProvider.readFile` returning sample YAML, mock `commitFiles`, verify 201 response with correct fields
  - Verify `commitFiles` called with updated YAML containing new imageTag
  - Verify commit message follows `deploy: <version> to <env>` convention
  - Verify commit message includes `Deployed-By:` trailer
  - Team ownership: app belongs to different team → `NotFoundException` (404)
  - Environment ownership: environment belongs to different app → `NotFoundException` (404)
  - Non-existent app → `NotFoundException` (404)
  - Non-existent environment → `NotFoundException` (404)
  - Git provider failure: mock throws `PortalIntegrationException` → propagated (502)

- `DeploymentServiceYamlTest.java` (plain JUnit, no Quarkus — fast unit test):
  - `updateImageTag` with standard values YAML → `image.tag` updated, other keys preserved
  - `updateImageTag` with nested values → structure preserved
  - `updateImageTag` with missing `image` section → clear error message
  - `updateImageTag` with missing `tag` key → clear error message

- `DeploymentResourceIT.java` (REST Assured):
  - POST → 201 with correct JSON structure
  - Cross-team application → 404
  - Invalid environmentId → 404
  - Git failure → 502 with standardized error JSON
  - Casbin: member can deploy, unauthenticated → 401

**No frontend tests** in this story.

### Previous Story Intelligence

**Story 2.5 (Onboarding PR Creation):**
- Established `GitProvider.commitFiles()` pattern — commits multiple files to a branch
- Established `GitProvider.readFile()` for reading repo content
- Established onboarding flow using Git as the write mechanism

**Story 4.4 (Release Creation):**
- Established `GitProvider.createTag()` — another Git write operation
- Cross-cutting change across 5 GitProvider implementations — same pattern if we need to extend the interface

**Story 4.5 (Releases Page):**
- `ReleaseService` establishes the `requireTeamApplication()` pattern — reuse in `DeploymentService`

**Story 2.7/2.8 (ArgoCD + Environment Chain):**
- Established `ArgoCdAdapter.getEnvironmentStatuses` — read-only status polling (still used for deployment status display, NOT modified)
- `DEPLOYING` status shows when ArgoCD detects OutOfSync/Progressing after the Git commit triggers auto-sync

**Epic 4 Retrospective Action Items:**
- **`require*` naming convention** for ownership validation — apply in `DeploymentService`
- **Authorization AC template** — included as AC #7
- **Zero-failure test gate** — all tests must pass

### Data Flow

```
POST /api/v1/teams/{teamId}/applications/{appId}/deployments
  { releaseVersion: "v1.4.2", environmentId: 42 }

  → PermissionFilter: Casbin check (deployments, deploy) for user role
  → TeamContextFilter: extract team from JWT, populate TeamContext
  → DeploymentResource.deploy(teamId, appId, request)
  → DeploymentService.deployRelease(teamId, appId, request)
    → requireTeamApplication(teamId, appId) — 404 if cross-team
    → requireApplicationEnvironment(appId, 42) — 404 if wrong app
    → GitProvider.readFile(gitRepoUrl, "main", ".helm/run/values-run-qa.yaml")
    → updateImageTag(currentYaml, "v1.4.2") — parse YAML, update image.tag
    → GitProvider.commitFiles(gitRepoUrl, "main",
        {".helm/run/values-run-qa.yaml": updatedYaml},
        "deploy: v1.4.2 to qa\n\nDeployed-By: marco")
    → Return DeploymentStatusDto("corr-id", "v1.4.2", "qa", "Deploying", now)
  → 201 Created

Status updates flow separately through existing polling:
  GET /api/v1/teams/{teamId}/applications/{appId}/environments
    → ArgoCdAdapter.getEnvironmentStatuses → translates ArgoCD sync/health
    → Environment card: "⟳ Deploying v1.4.2..." → "✓ Healthy — v1.4.2"
```

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` § Story 5.1]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR23 — Deploy release to environment]
- [Source: `_bmad-output/planning-artifacts/prd.md` § FR26 — ArgoCD/Argo Rollouts deployment execution]
- [Source: `_bmad-output/planning-artifacts/architecture.md` § Data Architecture] — portal doesn't persist deployment state
- [Source: `_bmad-output/planning-artifacts/architecture.md` § REST API Structure] — `/deployments` endpoint
- [Source: `_bmad-output/project-context.md` § Resource Ownership Validation] — require* pattern
- [Source: `_bmad-output/project-context.md` § Domain Language Translation] — ArgoCD sync = Deployment
- [Source: `_bmad-output/project-context.md` § GitOps Contract] — app repo .helm/run/ structure
- [Source: `_bmad-output/implementation-artifacts/epic-4-retro-2026-04-09.md`] — Authorization pattern, require* convention
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitProvider.java`] — readFile(), commitFiles()
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitHubProvider.java`] — GitHub implementation
- [Source: `developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java`] — Dev mock
- [Source: `developer-portal/src/main/java/com/portal/integration/git/GitProviderConfig.java`] — provider config
- [Source: `developer-portal/src/main/java/com/portal/auth/TeamContext.java`] — getTeamIdentifier()
- [Source: `developer-portal/src/main/java/com/portal/auth/PermissionFilter.java`] — deploy/deploy-prod mapping
- [Source: `developer-portal/src/main/resources/casbin/policy.csv`] — deployments permissions

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### Change Log

### File List
