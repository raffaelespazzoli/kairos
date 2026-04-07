# Story 4.1: Tekton Adapter & Pipeline Triggering

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to trigger a CI build for my application from the portal,
So that I can start a pipeline without opening the Tekton UI or using kubectl.

## Acceptance Criteria

1. **TektonAdapter is an @ApplicationScoped CDI bean using Kubernetes API**
   - **Given** the TektonAdapter is an @ApplicationScoped CDI bean
   - **When** it needs to interact with Tekton
   - **Then** it uses the Kubernetes API (via the Fabric8 kubernetes-client + tekton-client) to create and query PipelineRun resources
   - **And** it authenticates to the target cluster using credentials from SecretManagerCredentialProvider (Vault-issued)
   - **And** the target cluster is determined from the application's build environment (the cluster assigned to the build namespace)

2. **POST build trigger creates a PipelineRun and returns BuildSummaryDto**
   - **Given** a developer triggers a build
   - **When** POST `/api/v1/teams/{teamId}/applications/{appId}/builds` is called
   - **Then** the TektonAdapter creates a PipelineRun in the application's build namespace
   - **And** the PipelineRun references the Tekton pipeline configured during onboarding
   - **And** a BuildSummaryDto is returned with: buildId, status ("Building"), startedAt, and the application context

3. **Casbin authorization permits member and lead roles**
   - **Given** the Casbin permission check runs
   - **When** a developer with "member" or "lead" role triggers a build
   - **Then** the request is permitted
   - **And** the TeamContext ensures the application belongs to the developer's team

4. **Integration errors are translated to developer language**
   - **Given** the target cluster is unreachable or Tekton returns an error
   - **When** the build trigger is attempted
   - **Then** a PortalIntegrationException is thrown with system="tekton"
   - **And** the error message is in developer language: "Build could not be started — the build cluster is unreachable"

5. **Tekton concepts are translated to portal domain**
   - **Given** the TektonAdapter translates Tekton concepts
   - **When** returning data to the service layer
   - **Then** PipelineRun is translated to "Build" in all portal domain types
   - **And** no Tekton-specific terminology (PipelineRun, TaskRun, Step) appears in API responses or UI

## Tasks / Subtasks

- [ ] Task 1: Add Kubernetes and Tekton client dependencies to pom.xml (AC: #1)
  - [ ] Add `quarkus-kubernetes-client` extension
  - [ ] Add Fabric8 `tekton-client` dependency (version aligned with Quarkus BOM's Fabric8 version)

- [ ] Task 2: Add build config columns to Application entity + migration (AC: #1)
  - [ ] Create `V5__add_build_config_to_applications.sql` migration
  - [ ] Add `buildClusterId` and `buildNamespace` fields to `Application.java`

- [ ] Task 3: Update OnboardingService to persist build config (AC: #1)
  - [ ] Persist `buildClusterId` and `buildNamespace` on the Application entity during `confirmOnboarding()`

- [ ] Task 4: Create TektonAdapter interface and implementations (AC: #1, #4, #5)
  - [ ] Create `TektonAdapter.java` interface in `com.portal.integration.tekton`
  - [ ] Create `TektonKubeAdapter.java` production implementation
  - [ ] Create `DevTektonAdapter.java` dev-mode mock
  - [ ] Create `TektonConfig.java` configuration class

- [ ] Task 5: Create BuildService (AC: #1, #2, #3, #5)
  - [ ] Create `BuildService.java` in `com.portal.build`
  - [ ] Implement `triggerBuild(Long teamId, Long appId)` orchestration

- [ ] Task 6: Create BuildResource REST endpoint (AC: #2, #3)
  - [ ] Create `BuildResource.java` in `com.portal.build`
  - [ ] Implement POST `/api/v1/teams/{teamId}/applications/{appId}/builds`

- [ ] Task 7: Create BuildSummaryDto (AC: #2, #5)
  - [ ] Create `BuildSummaryDto.java` in `com.portal.build`

- [ ] Task 8: Add configuration properties (AC: #1)
  - [ ] Add `portal.tekton.provider` to application.properties
  - [ ] Add dev profile defaults
  - [ ] Add test profile configuration

- [ ] Task 9: Write backend tests (AC: #1-#5)
  - [ ] Create `TektonKubeAdapterTest.java` — unit tests with mocked KubernetesClient
  - [ ] Create `BuildServiceTest.java` — unit tests with mocked adapter and credential provider
  - [ ] Create `BuildResourceIT.java` — integration test for POST endpoint

## Dev Notes

### Critical Context: Build Cluster/Namespace Not Currently Persisted

**CRITICAL FINDING**: The current `OnboardingService.confirmOnboarding()` receives `buildClusterId` (via `OnboardingConfirmRequest.buildClusterId()`) and computes the build namespace, but **neither is persisted on the Application entity**. The onboarding only stores run environments (dev/qa/prod) in the `environments` table — the build environment is only used to generate GitOps manifests for the PR.

**Resolution**: Add `build_cluster_id` and `build_namespace` columns to the `applications` table so that `BuildService` can resolve the target cluster for Tekton API calls. This is a migration + entity update + onboarding change.

### Database Migration — V5

```sql
ALTER TABLE applications
    ADD COLUMN build_cluster_id BIGINT REFERENCES clusters(id),
    ADD COLUMN build_namespace  VARCHAR(255);
```

Both columns are nullable because existing applications were onboarded before this migration. `BuildService` must check for null and throw a meaningful error if build config is missing ("Application was onboarded before CI integration — re-onboard or contact an admin").

### Application Entity Update

Add to `Application.java`:

```java
@Column(name = "build_cluster_id")
public Long buildClusterId;

@Column(name = "build_namespace")
public String buildNamespace;
```

These fields reference the cluster where Tekton pipelines run and the Kubernetes namespace where PipelineRuns are created.

### OnboardingService Update

In `confirmOnboarding()`, after `app.persistAndFlush()` is called, the build config must be set. Actually, set it **before** `persistAndFlush()`:

```java
// Add BEFORE app.persistAndFlush()
app.buildClusterId = request.buildClusterId();
app.buildNamespace = OnboardingService.slugify(teamName) + "-"
        + OnboardingService.slugify(request.appName()) + "-build";
```

The build namespace follows the same convention as the existing `buildNamespaceName()` method (line 158): `{team}-{app}-build`. Reuse that method directly.

### Maven Dependencies — New in pom.xml

Add these two dependencies:

```xml
<!-- Kubernetes API client for Tekton and Argo Rollouts -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes-client</artifactId>
</dependency>
<!-- Fabric8 Tekton extension: TektonClient DSL and CRD model classes -->
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>tekton-client</artifactId>
</dependency>
```

**Version notes**: `quarkus-kubernetes-client` version is managed by the Quarkus BOM (3.34.2). Fabric8 `tekton-client` version is also managed by the BOM — the Quarkus BOM imports the Fabric8 Kubernetes Client BOM which covers all Fabric8 extensions including `tekton-client`. Do NOT hardcode a version — let the BOM manage it.

**Why not `quarkus-tekton-client` (Quarkiverse)?** The Quarkiverse extension injects a single CDI-managed `TektonClient` pointing at the local cluster. Our use case requires connecting to **different clusters dynamically** using Vault-issued credentials. We create `KubernetesClient` / `TektonClient` instances programmatically per-request. The Fabric8 library dependency gives us the model classes and DSL without the single-cluster CDI assumption.

### TektonAdapter Interface — `com.portal.integration.tekton`

```java
package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;

public interface TektonAdapter {

    /**
     * Creates a PipelineRun in the specified namespace on the target cluster.
     *
     * @param appName        portal application name (used to derive pipeline name)
     * @param namespace      Kubernetes namespace for the PipelineRun
     * @param clusterApiUrl  target cluster API server URL
     * @param clusterToken   Vault-issued bearer token for the cluster
     * @return build summary with PipelineRun metadata translated to portal domain
     */
    BuildSummaryDto triggerBuild(String appName, String namespace,
                                 String clusterApiUrl, String clusterToken);
}
```

**Pipeline name convention**: The PipelineRun's `.spec.pipelineRef.name` = the application name. During onboarding, the `.helm/build/` Helm chart creates a Tekton Pipeline named after the application. The TektonAdapter derives it from `appName`.

### TektonKubeAdapter — Production Implementation

```java
package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import com.portal.integration.PortalIntegrationException;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
@IfBuildProperty(name = "portal.tekton.provider", stringValue = "tekton", enableIfMissing = true)
public class TektonKubeAdapter implements TektonAdapter {

    @Inject
    DeepLinkService deepLinkService;

    @Override
    public BuildSummaryDto triggerBuild(String appName, String namespace,
                                         String clusterApiUrl, String clusterToken) {
        Config config = new ConfigBuilder()
                .withMasterUrl(clusterApiUrl)
                .withOauthToken(clusterToken)
                .withTrustCerts(false)
                .build();

        try (KubernetesClient kubeClient = new KubernetesClientBuilder()
                .withConfig(config).build()) {
            TektonClient tektonClient = kubeClient.adapt(TektonClient.class);

            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                        .withGenerateName(appName + "-")
                        .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                        .withNewPipelineRef()
                            .withName(appName)
                        .endPipelineRef()
                    .endSpec()
                    .build();

            PipelineRun created = tektonClient.v1().pipelineRuns()
                    .inNamespace(namespace)
                    .resource(pipelineRun)
                    .create();

            String runName = created.getMetadata().getName();
            return new BuildSummaryDto(
                    runName,
                    "Building",
                    Instant.now(),
                    appName,
                    deepLinkService.generateTektonLink(runName).orElse(null));

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            String deepLink = deepLinkService.generateTektonLink(appName).orElse(null);
            throw new PortalIntegrationException("tekton", "triggerBuild",
                    "Build could not be started \u2014 the build cluster is unreachable",
                    deepLink, e);
        }
    }
}
```

**Key patterns followed:**
- Programmatic `KubernetesClient` per call — no CDI injection of a shared client because each call may target a different cluster
- `try-with-resources` on `KubernetesClient` — the client holds HTTP connection pools that must be closed
- `withGenerateName(appName + "-")` — Kubernetes generates a unique suffix (e.g., `payment-svc-xk7f2`)
- `PipelineRef.name = appName` — convention: the Helm chart in `.helm/build/` creates a Pipeline named after the app
- `PortalIntegrationException` with system="tekton" and developer-friendly message — no Tekton jargon (no "PipelineRun", "TaskRun", "Step" in messages)
- `DeepLinkService` for the Tekton Dashboard deep link — already implemented and tested in Story 3.1
- `withTrustCerts(false)` for production — in dev mode, `DevTektonAdapter` bypasses this entirely

**WARNING — DO NOT use `kubeClient.adapt(TektonClient.class)` without first verifying the import path.** The correct Fabric8 Tekton v1 API classes are in the `io.fabric8.tekton.pipeline.v1` package (PipelineRun, PipelineRunBuilder). Do NOT use deprecated `v1beta1` classes. Tekton v1 API has been stable since Tekton Pipelines 0.44+.

### DevTektonAdapter — Dev-Mode Mock

```java
package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@IfBuildProperty(name = "portal.tekton.provider", stringValue = "dev")
public class DevTektonAdapter implements TektonAdapter {

    @Inject
    DeepLinkService deepLinkService;

    @Override
    public BuildSummaryDto triggerBuild(String appName, String namespace,
                                         String clusterApiUrl, String clusterToken) {
        String mockRunId = appName + "-" + UUID.randomUUID().toString().substring(0, 5);
        return new BuildSummaryDto(
                mockRunId,
                "Building",
                Instant.now(),
                appName,
                deepLinkService.generateTektonLink(mockRunId).orElse(null));
    }
}
```

### TektonConfig — Configuration

```java
package com.portal.integration.tekton;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "portal.tekton")
public interface TektonConfig {

    @WithDefault("tekton")
    String provider();

    @WithName("dashboard-url")
    Optional<String> dashboardUrl();
}
```

**Why `dashboardUrl()` is included**: `@ConfigMapping(prefix = "portal.tekton")` claims the `portal.tekton` namespace. SmallRye Config validates that ALL properties under a mapped prefix have corresponding interface methods. Since `portal.tekton.dashboard-url` already exists (added in Story 3.1), `TektonConfig` MUST map it — otherwise SmallRye throws "Unknown config property" at startup. The adapter itself does NOT use `dashboardUrl()` — deep links are generated by `DeepLinkService` via `DeepLinkConfig`. This is a dual-read: both `TektonConfig` and `DeepLinkConfig` read the same property. That's safe — `@ConfigMapping` and `@ConfigProperty` are independent read-only views.

This follows the same pattern as `ArgoCdConfig` which maps `portal.argocd.url` even though `DeepLinkConfig` also reads it.

### BuildSummaryDto — `com.portal.build`

```java
package com.portal.build;

import java.time.Instant;

public record BuildSummaryDto(
    String buildId,
    String status,
    Instant startedAt,
    String applicationName,
    String tektonDeepLink
) {}
```

**Design notes:**
- `buildId` is the PipelineRun name (Kubernetes-generated, e.g., `payment-svc-xk7f2`)
- `status` uses portal vocabulary: "Building" (not "Running" — Tekton terminology)
- `startedAt` is the Instant when the build was triggered
- `applicationName` provides application context in the response
- `tektonDeepLink` is a URL to the Tekton Dashboard PipelineRun view — populated via `DeepLinkService.generateTektonLink()`
- Story 4.2 will extend this DTO or create additional DTOs for build detail/history

### BuildService — `com.portal.build`

```java
package com.portal.build;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.cluster.Cluster;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerCredentialProvider;
import com.portal.integration.tekton.TektonAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class BuildService {

    @Inject
    TeamContext teamContext;

    @Inject
    TektonAdapter tektonAdapter;

    @Inject
    SecretManagerCredentialProvider credentialProvider;

    public BuildSummaryDto triggerBuild(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }

        if (app.buildClusterId == null || app.buildNamespace == null) {
            throw new IllegalStateException(
                    "Application does not have build configuration — "
                    + "it may have been onboarded before CI integration was available");
        }

        Cluster buildCluster = Cluster.findById(app.buildClusterId);
        if (buildCluster == null) {
            throw new IllegalStateException("Build cluster no longer exists");
        }

        ClusterCredential credential =
                credentialProvider.getCredentials(buildCluster.name, "portal");

        return tektonAdapter.triggerBuild(
                app.name,
                app.buildNamespace,
                buildCluster.apiServerUrl,
                credential.token());
    }
}
```

**Key patterns:**
- Team-scoped access: verifies `app.teamId.equals(teamId)` — returns 404 for cross-team or missing resources (security rule)
- Delegates credential fetching to `SecretManagerCredentialProvider` — TTL-aware cache, never stores credentials to disk
- `"portal"` is the Vault role used for cluster access — same as other adapters
- Throws `IllegalStateException` with developer-friendly message if build config is missing — this covers applications onboarded before V5 migration
- Resource → Service → Adapter call chain — resource never calls adapter directly

### BuildResource — `com.portal.build`

```java
package com.portal.build;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/teams/{teamId}/applications/{appId}/builds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BuildResource {

    @Inject
    BuildService buildService;

    @POST
    public Response triggerBuild(@PathParam("teamId") Long teamId,
                                 @PathParam("appId") Long appId) {
        BuildSummaryDto result = buildService.triggerBuild(teamId, appId);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
```

**Notes:**
- Returns **201 Created** per the API convention (POST that creates a resource)
- No request body needed — the build is triggered for the application as configured during onboarding
- Casbin check happens automatically via `PermissionFilter` → checks `builds, trigger` permission for the user's role
- Team scoping enforced by `BuildService.triggerBuild()` which validates `app.teamId`
- Story 4.2 will add GET endpoints to this resource for listing builds and retrieving build details

### Casbin Policy — Already Configured

The Casbin policy at `src/main/resources/casbin/policy.csv` already includes:

```
p, member, builds, read
p, member, builds, trigger
```

The `PermissionFilter` extracts the resource name from the URL path (`/api/v1/teams/{teamId}/applications/{appId}/builds` → `builds`) and the action from the HTTP method. For POST, the filter maps to `trigger`. **No Casbin policy changes are needed for this story.**

Verify the `PermissionFilter` maps POST to `trigger` for the `builds` resource. If it uses a different action mapping convention (e.g., POST → `create`), adjust the policy accordingly. The existing Casbin entries use `trigger` for builds, which is the correct portal-domain action.

### Configuration Properties

**Add to `application.properties`** (after the existing `portal.tekton.dashboard-url` line):

```properties
# Tekton adapter provider selection
portal.tekton.provider=${TEKTON_PROVIDER:tekton}
```

**Add to dev profile section** (in `application.properties`):

```properties
%dev.portal.tekton.provider=dev
```

**Add to `src/test/resources/application.properties`**:

```properties
# Tekton — use dev provider for tests (no real cluster)
portal.tekton.provider=dev
```

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `package-info.java` | `com.portal.build` | EXISTS — placeholder |
| `package-info.java` | `com.portal.integration.tekton` | EXISTS — placeholder |
| `DeepLinkService` | `com.portal.deeplink.DeepLinkService` | EXISTS — `generateTektonLink(pipelineRunId)` ready |
| `DeepLinkConfig` | `com.portal.deeplink.DeepLinkConfig` | EXISTS — reads `portal.tekton.dashboard-url` |
| `SecretManagerCredentialProvider` | `com.portal.integration.secrets` | EXISTS — TTL cache over Vault |
| `ClusterCredential` | `com.portal.integration.secrets` | EXISTS — record with `token()`, `isExpired()` |
| `PortalIntegrationException` | `com.portal.integration` | EXISTS — with system, operation, deepLink fields |
| `GlobalExceptionMapper` | `com.portal.common` | EXISTS — maps `PortalIntegrationException` → 502 |
| `PermissionFilter` | `com.portal.auth` | EXISTS — Casbin authorization check |
| `TeamContext` | `com.portal.auth` | EXISTS — request-scoped team+role bean |
| Casbin `builds, trigger` permission | `casbin/policy.csv` | EXISTS — member+lead can trigger |

### What NOT to Build

- **No build list/detail GET endpoints** — Story 4.2 adds build listing and status monitoring
- **No build log retrieval** — Story 4.2 adds log endpoint
- **No frontend components** — Story 4.3 creates the Builds page and Build table
- **No frontend API functions** — Story 4.3 creates `api/builds.ts` and `hooks/useBuilds.ts`
- **No frontend types** — Story 4.3 creates `types/build.ts`
- **No release creation** — Story 4.4 handles release creation
- **No container registry integration** — Story 4.4 introduces `RegistryAdapter`
- **No database table for builds** — Builds are NOT persisted in the portal database; they are fetched live from Tekton API. The portal is not the source of truth for build data.

### Anti-Patterns to Avoid

- **DO NOT** inject a CDI `KubernetesClient` or `TektonClient` — create them programmatically per call because each build may target a different cluster
- **DO NOT** cache `KubernetesClient` instances — they hold connection pools tied to specific cluster URLs and tokens; credentials expire
- **DO NOT** expose PipelineRun, TaskRun, or any Tekton CRD terminology in DTOs, API responses, or error messages — translate to "Build" in all portal-facing types
- **DO NOT** use `@ConfigMapping(prefix = "portal")` — too broad; use `@ConfigMapping(prefix = "portal.tekton")` for Tekton-specific config only
- **DO NOT** add `@ConfigProperty(name = "portal.tekton.dashboard-url")` to `TektonConfig` — it's already handled by `DeepLinkConfig`; the `TektonAdapter` gets deep links from `DeepLinkService`
- **DO NOT** use Spring annotations — use Quarkus/CDI: `@ApplicationScoped`, `@Inject`, `@Path`, etc.
- **DO NOT** call `Cluster.findById()` or `Application.findById()` from the adapter — entity lookups happen in `BuildService`; the adapter receives resolved values
- **DO NOT** store credentials in any variable with a lifecycle longer than the request — the `clusterToken` parameter is used once and discarded
- **DO NOT** use deprecated Tekton `v1beta1` API classes — use `io.fabric8.tekton.pipeline.v1` (PipelineRun, PipelineRunBuilder)
- **DO NOT** hardcode cluster API URLs or tokens — always resolve from database + SecretManagerCredentialProvider

### Testing Strategy

**TektonKubeAdapterTest.java** — Unit test with mocked Fabric8 clients:

```java
class TektonKubeAdapterTest {
    // Cannot use @QuarkusTest because TektonKubeAdapter is disabled
    // in tests (portal.tekton.provider=dev). Use plain Mockito:
    //
    // 1. Mock KubernetesClientBuilder to return a mock KubernetesClient
    // 2. Mock TektonClient from kubeClient.adapt(TektonClient.class)
    // 3. Mock pipelineRuns().inNamespace().resource().create() chain
    // 4. Verify PipelineRun metadata (generateName, namespace, pipelineRef)
    // 5. Verify error handling → PortalIntegrationException
    //
    // Alternative: Test with @QuarkusTest by setting a test profile
    // that activates portal.tekton.provider=tekton, then @InjectMock
    // the KubernetesClient. But since TektonKubeAdapter creates clients
    // programmatically (not CDI-injected), mocking requires injecting
    // a factory or using constructor injection for testability.
}
```

**Testability approach**: Extract client creation into a package-private method that tests can override:

```java
// In TektonKubeAdapter:
KubernetesClient createClient(String clusterApiUrl, String clusterToken) {
    Config config = new ConfigBuilder()
            .withMasterUrl(clusterApiUrl)
            .withOauthToken(clusterToken)
            .withTrustCerts(false)
            .build();
    return new KubernetesClientBuilder().withConfig(config).build();
}
```

Tests can then subclass `TektonKubeAdapter` and override `createClient()` to return a mock, or use a CDI alternative.

**BuildServiceTest.java** — `@QuarkusTest` with `@InjectMock`:

```java
@QuarkusTest
class BuildServiceTest {

    @Inject
    BuildService buildService;

    @InjectMock
    TektonAdapter tektonAdapter; // DevTektonAdapter is active; @InjectMock replaces it

    @InjectMock
    SecretManagerCredentialProvider credentialProvider;

    @Test
    void triggerBuildCallsAdapterWithCorrectParams() {
        // Setup: insert test Application with buildClusterId + buildNamespace
        // Setup: insert test Cluster
        // Mock: credentialProvider.getCredentials() returns test credential
        // Mock: tektonAdapter.triggerBuild() returns expected DTO
        //
        // Act: buildService.triggerBuild(teamId, appId)
        //
        // Assert: adapter called with correct namespace, cluster URL, token
        // Assert: returned DTO matches expected values
    }

    @Test
    void triggerBuildReturns404ForCrossTeamApp() {
        // Verify NotFoundException for app.teamId != requested teamId
    }

    @Test
    void triggerBuildThrowsWhenBuildConfigMissing() {
        // Verify IllegalStateException when buildClusterId is null
    }
}
```

**BuildResourceIT.java** — Integration test for the REST endpoint:

```java
@QuarkusTest
class BuildResourceIT {

    @InjectMock
    TektonAdapter tektonAdapter;

    @InjectMock
    SecretManagerCredentialProvider credentialProvider;

    @Test
    @TestSecurity(user = "test-user")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "1"),
        @Claim(key = "role", value = "member")
    })
    void triggerBuildReturns201() {
        // Setup: seed Application + Cluster in DB
        // Mock: adapter + credential provider
        //
        // REST Assured:
        // given()
        //   .when().post("/api/v1/teams/1/applications/{appId}/builds")
        //   .then()
        //     .statusCode(201)
        //     .body("status", equalTo("Building"))
        //     .body("buildId", notNullValue());
    }

    @Test
    @TestSecurity(user = "test-user")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "1"),
        @Claim(key = "role", value = "member")
    })
    void triggerBuildReturns404ForOtherTeamApp() {
        // App belongs to team 2, request comes from team 1 → 404
    }
}
```

### Previous Story Intelligence

**Epic 3 Retrospective (2026-04-07) — Action Items for Epic 4:**
- Every story must leave the test suite at **0 failures** — no "pre-existing, unrelated" carve-outs
- When a component's API changes, update **ALL consumers** including integration tests in the same story
- Add gotchas to `project-context.md` immediately
- Pre-flight test gate enforced — story development HALTs if any tests are failing before work begins

**Story 3.1 (Deep Link Service):**
- `DeepLinkService.generateTektonLink(pipelineRunId)` is fully implemented and tested
- Returns `Optional<String>` — empty when `portal.tekton.dashboard-url` is not configured
- URL pattern: `{tektonDashboardUrl}/#/pipelineruns/{pipelineRunId}`
- `DeepLinkConfig` reads `portal.tekton.dashboard-url` via `@ConfigProperty` (not `@ConfigMapping`)

**Epic 4 Readiness Assessment (from Epic 3 Retro):**
- 314+ backend tests passing
- 186/186 frontend tests passing
- No blockers for Epic 4
- First direct K8s API usage for TektonAdapter
- First non-JSON API response (build logs — Story 4.2, not this story)

### Git Intelligence

Recent commit patterns:
- `861aa61` — Epic 3 retro: test fixes, pre-flight gate added
- `661c40b` — Story 3.3: Deep links on env chain
- `0b23efb` — Stories 3.1/3.2: DeepLinkService, DevSpaces, Vault
- Each story is one atomic commit with all files

### Data Flow for Build Triggering

```
POST /api/v1/teams/{teamId}/applications/{appId}/builds
  → PermissionFilter: Casbin check (builds, trigger) for user role
  → TeamContextFilter: extract team from JWT, populate TeamContext
  → BuildResource.triggerBuild(teamId, appId)
  → BuildService.triggerBuild(teamId, appId)
    → Application.findById(appId) — verify team ownership
    → Cluster.findById(app.buildClusterId) — resolve build cluster
    → SecretManagerCredentialProvider.getCredentials(cluster.name, "portal")
    → TektonAdapter.triggerBuild(appName, buildNamespace, clusterApiUrl, token)
      → (production) Create KubernetesClient → adapt(TektonClient) → create PipelineRun
      → (dev) Return mock BuildSummaryDto
    → DeepLinkService.generateTektonLink(pipelineRunName)
  → 201 Created: { buildId, status: "Building", startedAt, applicationName, tektonDeepLink }
```

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/build/
├── BuildResource.java
├── BuildService.java
└── BuildSummaryDto.java

src/main/java/com/portal/integration/tekton/
├── TektonAdapter.java        (interface)
├── TektonKubeAdapter.java    (production: Fabric8 Kubernetes/Tekton client)
├── TektonConfig.java         (config mapping)
└── DevTektonAdapter.java     (dev mock)
```

**Modified backend files:**
```
pom.xml                                              (add dependencies)
src/main/java/com/portal/application/Application.java (add buildClusterId, buildNamespace)
src/main/java/com/portal/onboarding/OnboardingService.java (persist build config)
src/main/resources/application.properties            (add portal.tekton.provider)
src/test/resources/application.properties            (add tekton test config)
```

**New migration:**
```
src/main/resources/db/migration/V5__add_build_config_to_applications.sql
```

**New test files:**
```
src/test/java/com/portal/build/
├── BuildServiceTest.java
└── BuildResourceIT.java

src/test/java/com/portal/integration/tekton/
└── TektonKubeAdapterTest.java
```

### References

- [Source: planning-artifacts/epics.md § Story 4.1 (line 1088)] — Full acceptance criteria
- [Source: planning-artifacts/epics.md § Story 4.2-4.5 (line 1123-1292)] — Future stories for cross-story context
- [Source: planning-artifacts/architecture.md § Integration Adapters table (line 1064)] — TektonAdapter: Kubernetes API, Phase 3
- [Source: planning-artifacts/architecture.md § Additional extensions (line 263)] — `kubernetes-client` for Tekton/Argo Rollouts
- [Source: planning-artifacts/architecture.md § Project Structure (line 809-813)] — `build/` package: BuildResource, BuildService, BuildSummaryDto
- [Source: planning-artifacts/architecture.md § Project Structure (line 872-877)] — `integration/tekton/` package: TektonAdapter, TektonConfig, model/
- [Source: planning-artifacts/architecture.md § Data Flow (line 1095-1113)] — Service → CredentialProvider → TektonAdapter flow
- [Source: planning-artifacts/architecture.md § Configuration Properties (line 1136-1137)] — `portal.tekton.dashboard-url`
- [Source: planning-artifacts/architecture.md § Naming Patterns (line 609)] — TektonAdapter naming convention
- [Source: planning-artifacts/architecture.md § Cross-Cutting Concerns §5 (line 199)] — Developer Abstraction Layer
- [Source: project-context.md § Domain Language Translation] — PipelineRun → Build
- [Source: project-context.md § Anti-Patterns] — Adapter throws PortalIntegrationException, not raw exceptions
- [Source: project-context.md § Testing Rules] — @InjectMock for adapter mocking in integration tests
- [Source: project-context.md § Framework-Specific Rules] — Resource → Service → Adapter call chain
- [Source: implementation-artifacts/epic-3-retro-2026-04-07.md § Action Items] — Zero-failure test gate, component API cascading
- [Source: implementation-artifacts/3-1-deep-link-service-shared-component.md] — DeepLinkService patterns
- [Source: developer-portal/src/main/resources/casbin/policy.csv] — builds/trigger permission already configured
- [Source: developer-portal/src/main/java/com/portal/onboarding/OnboardingService.java] — Build namespace convention and buildClusterId handling
- [Source: developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdRestAdapter.java] — Adapter pattern reference

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
