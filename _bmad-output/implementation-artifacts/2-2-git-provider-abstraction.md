# Story 2.2: Git Provider Abstraction

Status: done

## Story

As a developer on the portal team,
I want a pluggable Git provider layer that supports multiple Git hosting platforms,
So that the portal can validate repositories and create onboarding PRs against any supported Git server.

## Acceptance Criteria

1. **GitProvider interface contract**
   - **Given** the GitProvider interface is defined
   - **When** reviewing its contract
   - **Then** it exposes the following operations:
     - `validateRepoAccess(String repoUrl)` — confirms the portal can read the repository
     - `readFile(String repoUrl, String branch, String filePath)` — reads a file's contents
     - `listDirectory(String repoUrl, String branch, String dirPath)` — lists files in a directory
     - `createBranch(String repoUrl, String branchName, String fromBranch)` — creates a new branch
     - `commitFiles(String repoUrl, String branch, Map<String, String> files, String message)` — commits multiple files
     - `createPullRequest(String repoUrl, String branch, String targetBranch, String title, String description)` — creates a PR and returns a PullRequest model with the PR URL

2. **Four implementations with factory selection**
   - **Given** four implementations exist: GitHubProvider, GitLabProvider, GiteaProvider, BitbucketProvider
   - **When** the active provider is selected via `portal.git.provider` configuration (default: "github")
   - **Then** a GitProviderFactory CDI bean produces the correct implementation based on the config value
   - **And** only the active provider is instantiated

3. **Token-based authentication**
   - **Given** the GitProvider needs to authenticate to the Git server
   - **When** any operation is called
   - **Then** it uses the token configured via `portal.git.token`
   - **And** communication uses HTTPS

4. **Error handling**
   - **Given** the Git server is unreachable or the token is invalid
   - **When** any GitProvider operation is called
   - **Then** a PortalIntegrationException is thrown with system="git" and a developer-friendly message describing the failure

5. **Infra repo configuration**
   - **Given** the infra repo URL is configured via `portal.git.infra-repo-url`
   - **When** onboarding operations need to write to the infra repo
   - **Then** the configured URL is used as the target repository for branch creation, commits, and PR creation

## Tasks / Subtasks

- [x] Task 1: Create GitProviderConfig, PullRequest model, and GitProvider interface (AC: #1, #3, #5)
  - [x] Create `GitProviderConfig.java` in `com.portal.integration.git` — `@ConfigMapping(prefix = "portal.git")` with properties: `provider()` (default "github"), `token()`, `infraRepoUrl()`, `apiUrl()` (optional, for self-hosted instances)
  - [x] Create `PullRequest.java` record in `com.portal.integration.git.model` with fields: `url` (String), `number` (int), `title` (String)
  - [x] Create `GitProvider.java` interface in `com.portal.integration.git` with 6 operations and Javadoc

- [x] Task 2: Create GitProviderFactory CDI bean and DevGitProvider (AC: #2)
  - [x] Create `GitProviderFactory.java` in `com.portal.integration.git` — `@ApplicationScoped` class with `@Produces @ApplicationScoped GitProvider gitProvider()` method
  - [x] Factory switch: "github" → GitHubProvider, "gitlab" → GitLabProvider, "gitea" → GiteaProvider, "bitbucket" → BitbucketProvider, "dev" → DevGitProvider
  - [x] Create `DevGitProvider.java` in `com.portal.integration.git` — returns canned responses for all operations (empty directory listings, dummy PullRequest, etc.)
  - [x] Throw `IllegalArgumentException` for unknown provider values

- [x] Task 3: Implement GitHubProvider (AC: #1, #3, #4)
  - [x] Create `GitHubProvider.java` in `com.portal.integration.git`
  - [x] Constructor accepts `GitProviderConfig` and `HttpClient`
  - [x] `validateRepoAccess`: `GET /repos/{owner}/{repo}` → 200 means accessible
  - [x] `readFile`: `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` → decode Base64 content
  - [x] `listDirectory`: same endpoint → returns array of item names
  - [x] `createBranch`: `GET /repos/{owner}/{repo}/git/ref/heads/{fromBranch}` to get SHA → `POST /repos/{owner}/{repo}/git/refs` with `refs/heads/{branchName}`
  - [x] `commitFiles`: Git Trees API — create blobs → create tree → create commit → update ref
  - [x] `createPullRequest`: `POST /repos/{owner}/{repo}/pulls` → return PullRequest model
  - [x] All requests: `Authorization: Bearer {token}`, `Accept: application/vnd.github+json`
  - [x] Wrap all HTTP/IO exceptions in PortalIntegrationException(system="git", operation=<method-name>)
  - [x] URL parsing helper: extract owner + repo from `https://github.com/{owner}/{repo}` style URLs

- [x] Task 4: Implement GitLabProvider (AC: #1, #3, #4)
  - [x] Create `GitLabProvider.java` in `com.portal.integration.git`
  - [x] Constructor accepts `GitProviderConfig` and `HttpClient`
  - [x] `validateRepoAccess`: `GET /projects/{urlEncodedPath}` → 200 means accessible
  - [x] `readFile`: `GET /projects/{id}/repository/files/{urlEncodedFilePath}/raw?ref={branch}` → raw content
  - [x] `listDirectory`: `GET /projects/{id}/repository/tree?path={dir}&ref={branch}` → list of names
  - [x] `createBranch`: `POST /projects/{id}/repository/branches?branch={name}&ref={fromBranch}`
  - [x] `commitFiles`: `POST /projects/{id}/repository/commits` with actions array (create action per file) — GitLab natively supports multi-file commits
  - [x] `createPullRequest`: `POST /projects/{id}/merge_requests` with source_branch, target_branch, title, description → return PullRequest model
  - [x] All requests: `PRIVATE-TOKEN: {token}` header
  - [x] Wrap all exceptions in PortalIntegrationException(system="git")
  - [x] URL parsing helper: extract project path from `https://gitlab.com/{group}/{subgroup}/{repo}` and URL-encode

- [x] Task 5: Implement GiteaProvider (AC: #1, #3, #4)
  - [x] Create `GiteaProvider.java` in `com.portal.integration.git`
  - [x] Constructor accepts `GitProviderConfig` and `HttpClient`
  - [x] `validateRepoAccess`: `GET /api/v1/repos/{owner}/{repo}` → 200
  - [x] `readFile`: `GET /api/v1/repos/{owner}/{repo}/contents/{path}?ref={branch}` → decode Base64
  - [x] `listDirectory`: same endpoint for directories → array of items
  - [x] `createBranch`: `POST /api/v1/repos/{owner}/{repo}/branches` with `new_branch_name` and `old_branch_name`
  - [x] `commitFiles`: Use Git Trees API (similar to GitHub) or Gitea content API for multi-file
  - [x] `createPullRequest`: `POST /api/v1/repos/{owner}/{repo}/pulls` → return PullRequest model
  - [x] All requests: `Authorization: token {token}` header
  - [x] Wrap all exceptions in PortalIntegrationException(system="git")

- [x] Task 6: Implement BitbucketProvider (AC: #1, #3, #4)
  - [x] Create `BitbucketProvider.java` in `com.portal.integration.git`
  - [x] Constructor accepts `GitProviderConfig` and `HttpClient`
  - [x] `validateRepoAccess`: `GET /2.0/repositories/{workspace}/{repo_slug}` → 200
  - [x] `readFile`: `GET /2.0/repositories/{workspace}/{repo_slug}/src/{branch}/{path}` → raw content
  - [x] `listDirectory`: `GET /2.0/repositories/{workspace}/{repo_slug}/src/{branch}/{path}/` → directory listing
  - [x] `createBranch`: get target commit hash, then `POST /2.0/repositories/{workspace}/{repo_slug}/refs/branches`
  - [x] `commitFiles`: `POST /2.0/repositories/{workspace}/{repo_slug}/src` with multipart form data
  - [x] `createPullRequest`: `POST /2.0/repositories/{workspace}/{repo_slug}/pullrequests` → return PullRequest model
  - [x] All requests: `Authorization: Bearer {token}` header
  - [x] Wrap all exceptions in PortalIntegrationException(system="git")

- [x] Task 7: Add configuration properties (AC: #3, #5)
  - [x] Add to `src/main/resources/application.properties`: `portal.git.provider`, `portal.git.token`, `portal.git.infra-repo-url`, `portal.git.api-url` with env var overrides
  - [x] Add `%dev.portal.git.provider=dev` for dev profile
  - [x] Add `portal.git.provider=dev` to `src/test/resources/application.properties`

- [x] Task 8: Write unit tests for GitHubProvider (AC: #1, #3, #4)
  - [x] Create `GitHubProviderTest.java` in `src/test/java/com/portal/integration/git/`
  - [x] Mock HttpClient responses for each operation
  - [x] Test `validateRepoAccess` — success (200) and failure (404, network error)
  - [x] Test `readFile` — decode Base64 content correctly
  - [x] Test `listDirectory` — parse array response into file names
  - [x] Test `createBranch` — verify two-step flow (get SHA → create ref)
  - [x] Test `commitFiles` — verify Git Trees API flow (blobs → tree → commit → update ref)
  - [x] Test `createPullRequest` — verify PullRequest model populated from response
  - [x] Test error handling — verify PortalIntegrationException thrown with system="git"
  - [x] Test URL parsing — various URL formats

- [x] Task 9: Write unit tests for GitLabProvider (AC: #1, #3, #4)
  - [x] Create `GitLabProviderTest.java` in `src/test/java/com/portal/integration/git/`
  - [x] Mock HttpClient responses for each operation
  - [x] Test `validateRepoAccess` — success and failure
  - [x] Test `readFile` — raw content returned
  - [x] Test `listDirectory` — parse tree response
  - [x] Test `createBranch` — verify single POST
  - [x] Test `commitFiles` — verify actions array in commit payload
  - [x] Test `createPullRequest` — verify merge request creation and PullRequest model
  - [x] Test error handling — PortalIntegrationException
  - [x] Test URL parsing — handle subgroups (e.g., `gitlab.com/group/sub/repo`)

- [x] Task 10: Write unit tests for GitProviderFactory (AC: #2)
  - [x] Create `GitProviderFactoryTest.java` in `src/test/java/com/portal/integration/git/`
  - [x] Test factory returns GitHubProvider when config = "github"
  - [x] Test factory returns GitLabProvider when config = "gitlab"
  - [x] Test factory returns GiteaProvider when config = "gitea"
  - [x] Test factory returns BitbucketProvider when config = "bitbucket"
  - [x] Test factory returns DevGitProvider when config = "dev"
  - [x] Test factory throws IllegalArgumentException for unknown provider

- [x] Task 11: Write integration test for CDI wiring (AC: #2)
  - [x] Create `GitProviderFactoryIT.java` in `src/test/java/com/portal/integration/git/`
  - [x] `@QuarkusTest` with `portal.git.provider=dev` in test config
  - [x] Verify `GitProvider` can be injected via `@Inject`
  - [x] Verify injected instance behaves as DevGitProvider (CDI proxy prevents direct type check)
  - [x] Call `validateRepoAccess` on injected instance to verify basic functionality

## Dev Notes

### Integration Adapter Pattern — Factory vs @IfBuildProperty

The SecretManagerAdapter uses `@IfBuildProperty` for build-time provider selection. This story uses a **different pattern**: a CDI **factory producer** (`GitProviderFactory`). The AC explicitly mandates "a GitProviderFactory CDI bean produces the correct implementation." This is a runtime selection pattern — the factory reads `portal.git.provider` config at startup and instantiates only the matching implementation.

**Why factory, not @IfBuildProperty:** The same portal artifact may connect to different git platforms depending on deployment environment. Runtime selection allows a single image to work with GitHub, GitLab, Gitea, or Bitbucket via environment variable override.

Reference factory pattern:

```java
@ApplicationScoped
public class GitProviderFactory {

    @Inject
    GitProviderConfig config;

    @Produces
    @ApplicationScoped
    public GitProvider gitProvider() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return switch (config.provider()) {
            case "github" -> new GitHubProvider(config, httpClient);
            case "gitlab" -> new GitLabProvider(config, httpClient);
            case "gitea" -> new GiteaProvider(config, httpClient);
            case "bitbucket" -> new BitbucketProvider(config, httpClient);
            case "dev" -> new DevGitProvider();
            default -> throw new IllegalArgumentException(
                "Unknown git provider: " + config.provider()
                + ". Supported: github, gitlab, gitea, bitbucket, dev");
        };
    }
}
```

Implementations are **NOT CDI beans** — the factory creates them directly. Only the produced `GitProvider` is a CDI bean.

### HTTP Client — java.net.http.HttpClient

Use `java.net.http.HttpClient` (JDK built-in), consistent with `VaultSecretManagerAdapter`. Do NOT use Quarkus REST Client for this — the `rest-client-jackson` extension in pom.xml is for future typed REST clients (ArgoCD, Grafana). The raw HttpClient gives full control over request construction per provider.

Timeout: 10 seconds connect, 30 seconds request.

### Config — GitProviderConfig Pattern

Follow `SecretManagerConfig` / `VaultConfig` pattern with SmallRye `@ConfigMapping`:

```java
@ConfigMapping(prefix = "portal.git")
public interface GitProviderConfig {

    @WithDefault("github")
    String provider();

    String token();

    String infraRepoUrl();

    Optional<String> apiUrl();
}
```

`apiUrl()` is Optional — when absent, each provider uses its cloud default:
- GitHub: `https://api.github.com`
- GitLab: `https://gitlab.com/api/v4`
- Gitea: derived from repoUrl (self-hosted always)
- Bitbucket: `https://api.bitbucket.org`

When present, it overrides the default (for GitHub Enterprise, self-hosted GitLab, etc.).

### PullRequest Model

Simple record in `com.portal.integration.git.model`:

```java
public record PullRequest(String url, int number, String title) {}
```

### URL Parsing — Provider-Specific

Each provider must parse `repoUrl` to extract platform-specific identifiers:

| Provider | Input URL | Extract |
|----------|-----------|---------|
| GitHub | `https://github.com/owner/repo` | owner=`owner`, repo=`repo` |
| GitLab | `https://gitlab.com/group/sub/repo` | projectPath=`group/sub/repo` → URL-encode to `group%2Fsub%2Frepo` |
| Gitea | `https://gitea.example.com/owner/repo` | owner=`owner`, repo=`repo`, apiBase from host |
| Bitbucket | `https://bitbucket.org/workspace/repo` | workspace=`workspace`, repoSlug=`repo` |

Strip trailing `.git` if present. Extract host for API base URL derivation.

### Error Handling Pattern

Follow `VaultSecretManagerAdapter` exactly:

```java
try {
    // HTTP call
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new PortalIntegrationException("git", operationName,
            "Git server returned HTTP " + response.statusCode() + " for " + operationDescription);
    }
    // parse response
} catch (PortalIntegrationException e) {
    throw e;
} catch (IOException | InterruptedException e) {
    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
    throw new PortalIntegrationException("git", operationName,
        "Failed to " + operationDescription + ": " + e.getMessage(), null, e);
} catch (RuntimeException e) {
    throw new PortalIntegrationException("git", operationName,
        "Failed to " + operationDescription + ": " + e.getMessage(), null, e);
}
```

### GitHub REST API — Key Endpoints

Base: `https://api.github.com` (or `{apiUrl}` for Enterprise)
Auth: `Authorization: Bearer {token}`, `Accept: application/vnd.github+json`

| Operation | Method & Path | Notes |
|-----------|--------------|-------|
| validateRepoAccess | `GET /repos/{owner}/{repo}` | 200 = accessible |
| readFile | `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` | Response has `content` (Base64) and `encoding` field |
| listDirectory | Same endpoint | Returns JSON array when path is a directory |
| createBranch | `GET /repos/{owner}/{repo}/git/ref/heads/{fromBranch}` → get `object.sha` → `POST /repos/{owner}/{repo}/git/refs` with `{"ref":"refs/heads/{branchName}","sha":"{sha}"}` | Two-step |
| commitFiles | Git Trees API: (1) get current commit SHA from ref, (2) create blobs per file via `POST /repos/{owner}/{repo}/git/blobs`, (3) create tree via `POST /repos/{owner}/{repo}/git/trees` referencing parent tree + new blobs, (4) create commit via `POST /repos/{owner}/{repo}/git/commits`, (5) update ref via `PATCH /repos/{owner}/{repo}/git/refs/heads/{branch}` | Multi-step |
| createPullRequest | `POST /repos/{owner}/{repo}/pulls` with `{"title":"...","body":"...","head":"{branch}","base":"{targetBranch}"}` | Response has `html_url`, `number`, `title` |

### GitLab REST API v4 — Key Endpoints

Base: `https://gitlab.com/api/v4` (or `{apiUrl}`)
Auth: `PRIVATE-TOKEN: {token}` header

| Operation | Method & Path | Notes |
|-----------|--------------|-------|
| validateRepoAccess | `GET /projects/{urlEncodedPath}` | 200 = accessible |
| readFile | `GET /projects/{id}/repository/files/{urlEncodedFilePath}/raw?ref={branch}` | Returns raw content |
| listDirectory | `GET /projects/{id}/repository/tree?path={dir}&ref={branch}` | Returns array with `name`, `type`, `path` |
| createBranch | `POST /projects/{id}/repository/branches?branch={name}&ref={fromBranch}` | Single call |
| commitFiles | `POST /projects/{id}/repository/commits` with `{"branch":"...","commit_message":"...","actions":[{"action":"create","file_path":"...","content":"..."},...]}}` | Natively multi-file |
| createPullRequest | `POST /projects/{id}/merge_requests` with `{"source_branch":"...","target_branch":"...","title":"...","description":"..."}` | Response has `web_url`, `iid`, `title` |

### Gitea REST API v1 — Key Endpoints

Base: `https://{host}/api/v1` (always derived from repoUrl host)
Auth: `Authorization: token {token}`

| Operation | Method & Path | Notes |
|-----------|--------------|-------|
| validateRepoAccess | `GET /repos/{owner}/{repo}` | 200 = accessible |
| readFile | `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` | Base64 content like GitHub |
| listDirectory | Same endpoint | Returns array for directories |
| createBranch | `POST /repos/{owner}/{repo}/branches` with `{"new_branch_name":"...","old_branch_name":"..."}` | Single call |
| commitFiles | Use Gitea content creation or Git Trees API (same as GitHub) | Follow GitHub approach |
| createPullRequest | `POST /repos/{owner}/{repo}/pulls` with `{"title":"...","body":"...","head":"...","base":"..."}` | Same structure as GitHub |

### Bitbucket Cloud REST API 2.0 — Key Endpoints

Base: `https://api.bitbucket.org` (or `{apiUrl}` for Data Center)
Auth: `Authorization: Bearer {token}`

| Operation | Method & Path | Notes |
|-----------|--------------|-------|
| validateRepoAccess | `GET /2.0/repositories/{workspace}/{repo_slug}` | 200 = accessible |
| readFile | `GET /2.0/repositories/{workspace}/{repo_slug}/src/{branch}/{path}` | Returns raw content |
| listDirectory | `GET /2.0/repositories/{workspace}/{repo_slug}/src/{branch}/{path}/` (trailing slash) | Returns `values` array with `path`, `type` |
| createBranch | Get commit hash from `GET /2.0/repositories/{workspace}/{repo_slug}/refs/branches/{fromBranch}` → `POST /2.0/repositories/{workspace}/{repo_slug}/refs/branches` with `{"name":"...","target":{"hash":"..."}}` | Two-step |
| commitFiles | `POST /2.0/repositories/{workspace}/{repo_slug}/src` — multipart form: one part per file path + `message` + `branch` fields | Multipart form |
| createPullRequest | `POST /2.0/repositories/{workspace}/{repo_slug}/pullrequests` with `{"title":"...","description":"...","source":{"branch":{"name":"..."}},"destination":{"branch":{"name":"..."}}}` | Response has `links.html.href`, `id`, `title` |

### DevGitProvider — Testing Support

Consistent with `DevSecretManagerAdapter` pattern. Returns canned responses:
- `validateRepoAccess`: no-op (always succeeds)
- `readFile`: returns empty string
- `listDirectory`: returns empty list
- `commitFiles`: no-op
- `createBranch`: no-op
- `createPullRequest`: returns `new PullRequest("https://dev-git/pr/1", 1, title)`

### Test Pattern — Unit Tests with Mocked HttpClient

Unit tests mock `java.net.http.HttpClient` to return pre-built `HttpResponse` objects. Pattern from `VaultSecretManagerAdapterTest`:

```java
class GitHubProviderTest {
    private GitHubProvider provider;
    private HttpClient mockHttpClient;
    private GitProviderConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockConfig = mock(GitProviderConfig.class);
        when(mockConfig.token()).thenReturn("test-token");
        when(mockConfig.apiUrl()).thenReturn(Optional.empty());
        provider = new GitHubProvider(mockConfig, mockHttpClient);
    }

    @Test
    void validateRepoAccessReturnsForAccessibleRepo() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        assertDoesNotThrow(() -> provider.validateRepoAccess("https://github.com/team/app"));
    }
}
```

### Integration Test — CDI Wiring

```java
@QuarkusTest
class GitProviderFactoryIT {
    @Inject
    GitProvider gitProvider;

    @Test
    void gitProviderIsInjectable() {
        assertNotNull(gitProvider);
    }

    @Test
    void devProviderIsSelectedInTestProfile() {
        assertTrue(gitProvider instanceof DevGitProvider);
    }
}
```

No `@TestSecurity` needed — this test doesn't go through REST endpoints.

### File Structure

New files (all under `developer-portal/`):

```
src/main/java/com/portal/integration/git/
├── GitProvider.java
├── GitHubProvider.java
├── GitLabProvider.java
├── GiteaProvider.java
├── BitbucketProvider.java
├── DevGitProvider.java
├── GitProviderConfig.java
├── GitProviderFactory.java
└── model/
    └── PullRequest.java

src/test/java/com/portal/integration/git/
├── GitHubProviderTest.java
├── GitLabProviderTest.java
├── GitProviderFactoryTest.java
└── GitProviderFactoryIT.java
```

Modified files:
- `src/main/resources/application.properties` — add `portal.git.*` properties
- `src/test/resources/application.properties` — add `portal.git.provider=dev`

### Previous Story Intelligence (Story 2.1)

Story 2.1 is in `ready-for-dev` (not yet implemented). It creates Application and Environment entities. Story 2.2 has **no data dependency** on 2.1 — the git provider abstraction is a standalone integration layer. The two stories can be developed in parallel.

Key learnings from Epic 1 (last completed stories):
- `PanacheEntityBase` with IDENTITY strategy is mandatory (not relevant for this story but awareness)
- `DevSecretManagerAdapter` pattern works well for dev/test — replicate with `DevGitProvider`
- All 87 unit + 33 integration + 90 frontend tests pass — do not introduce regressions
- `@IfBuildProperty` for secrets works at build time; this story deliberately uses a factory for runtime flexibility

### What NOT to Build in This Story

- **No REST endpoints** — GitProvider is consumed by OnboardingService (Story 2.3+), not exposed directly via REST
- **No onboarding logic** — Contract validation, manifest generation, and PR orchestration are Stories 2.3–2.5
- **No gitops/ package code** — ManifestGenerator and OnboardingPrBuilder come in later stories
- **No frontend** — This is purely backend integration layer
- **No Vault integration** — Git providers authenticate with a static token, not Vault-issued credentials
- **No background jobs** — All operations are synchronous request-scoped

### Project Structure Notes

Files go in the established integration package structure:
- `com.portal.integration.git/` — GitProvider interface, implementations, config, factory
- `com.portal.integration.git.model/` — PullRequest record
- Test mirrors: `src/test/java/com/portal/integration/git/`
- The `package-info.java` already exists in `com.portal.integration.git`

### References

- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.2] — Full acceptance criteria
- [Source: planning-artifacts/architecture.md § Git provider abstraction] — Interface sketch, four implementations, config keys
- [Source: planning-artifacts/architecture.md § Integration Adapters — Complete List] — GitProvider listed with config key `portal.git.provider=github`
- [Source: planning-artifacts/architecture.md § Configuration Management] — `portal.git.provider`, `portal.git.infra-repo-url`, `portal.git.token` env vars
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure] — `integration/git/` package with model/ subdirectory
- [Source: planning-artifacts/architecture.md § Architectural Boundaries] — "Pluggable integrations use an interface + implementation pattern"
- [Source: planning-artifacts/architecture.md § Implementation Order] — "Integration adapter framework (Vault credential provider first, then Git provider)"
- [Source: project-context.md § Critical Implementation Rules] — PortalIntegrationException contract, @ApplicationScoped for adapters
- [Source: project-context.md § Testing Rules] — Unit tests in matching package, integration tests with @QuarkusTest
- [Source: integration/secrets/SecretManagerAdapter.java] — Interface pattern reference
- [Source: integration/secrets/SecretManagerConfig.java] — ConfigMapping pattern reference
- [Source: integration/secrets/DevSecretManagerAdapter.java] — Dev adapter pattern reference
- [Source: integration/secrets/vault/VaultSecretManagerAdapter.java] — HttpClient usage, error handling pattern
- [Source: integration/secrets/vault/VaultConfig.java] — Nested config pattern reference
- [Source: integration/PortalIntegrationException.java] — Exception with system, operation, message, deepLink, cause

## Dev Agent Record

### Agent Model Used
claude-4.6-opus-high-thinking

### Debug Log References
- One test fix required: `readFileDecodesBase64WithNewlines` used a literal newline inside a JSON string value; fixed to use `\\n` escape sequence.
- IT test `devProviderIsSelectedInTestProfile` initially used `assertInstanceOf(DevGitProvider.class, ...)` which fails because CDI wraps `@ApplicationScoped` beans in a ClientProxy. Fixed to verify behavior instead of type.
- Pre-existing `GlobalExceptionMapperIT` failures (3 tests returning 404) are not introduced by this story.
- Post-review fixes: 4 findings addressed (HTTPS enforcement, URL encoding, factory hardening, GitLab doc).

### Completion Notes List
- Implemented complete GitProvider interface with 6 operations (validateRepoAccess, readFile, listDirectory, createBranch, commitFiles, createPullRequest)
- Four provider implementations: GitHubProvider (GitHub REST API v3), GitLabProvider (GitLab REST API v4), GiteaProvider (Gitea API v1), BitbucketProvider (Bitbucket Cloud API 2.0)
- Each provider uses provider-specific authentication headers and API patterns
- GitProviderFactory CDI producer enables runtime provider selection via `portal.git.provider` config
- DevGitProvider returns canned responses for dev/test environments
- GitProviderConfig with SmallRye @ConfigMapping for all portal.git.* properties
- PullRequest record model in git.model package
- HTTPS enforcement: all four real providers reject non-HTTPS URIs at request time via `requireHttps()` guard in `sendRequest`; Gitea also validates the derived API base
- URL encoding: GitHub, Gitea, Bitbucket now encode file paths (per segment) and query parameters (branch names) using `encodePath()`/`encodeQuery()` helpers; GitLab already encoded all values
- Factory hardened: provider string is now lowercased (`Locale.ROOT`) for case-insensitive matching; `HttpClient` is only built when a real provider is selected (skipped for dev)
- GitLab `commitFiles` documents that `action: "create"` matches the story spec (onboarding always writes new files to a fresh branch)
- 22 unit tests for GitHubProvider covering all operations, error handling, URL parsing, custom API base, HTTPS rejection, encoding
- 16 unit tests for GitLabProvider covering all operations, error handling, URL parsing with subgroups, HTTPS rejection
- 8 unit tests for GitProviderFactory covering all provider selections, case-insensitive matching, and unknown provider error
- 3 integration tests for CDI wiring verifying injection, DevGitProvider behavior, and basic functionality
- All 150 unit tests pass (0 failures, 0 errors), IT tests pass (only pre-existing GlobalExceptionMapperIT failures), no regressions
- Configuration added to both main and test application.properties with env var overrides

### File List
- developer-portal/src/main/java/com/portal/integration/git/GitProvider.java (new)
- developer-portal/src/main/java/com/portal/integration/git/GitProviderConfig.java (new)
- developer-portal/src/main/java/com/portal/integration/git/GitProviderFactory.java (new)
- developer-portal/src/main/java/com/portal/integration/git/GitHubProvider.java (new)
- developer-portal/src/main/java/com/portal/integration/git/GitLabProvider.java (new)
- developer-portal/src/main/java/com/portal/integration/git/GiteaProvider.java (new)
- developer-portal/src/main/java/com/portal/integration/git/BitbucketProvider.java (new)
- developer-portal/src/main/java/com/portal/integration/git/DevGitProvider.java (new)
- developer-portal/src/main/java/com/portal/integration/git/model/PullRequest.java (new)
- developer-portal/src/main/resources/application.properties (modified)
- developer-portal/src/test/resources/application.properties (modified)
- developer-portal/src/test/java/com/portal/integration/git/GitHubProviderTest.java (new)
- developer-portal/src/test/java/com/portal/integration/git/GitLabProviderTest.java (new)
- developer-portal/src/test/java/com/portal/integration/git/GitProviderFactoryTest.java (new)
- developer-portal/src/test/java/com/portal/integration/git/GitProviderFactoryIT.java (new)
