# Story 2.7: ArgoCD Adapter & Environment Status

Status: done

## Story

As a developer on the portal team,
I want an ArgoCD adapter that fetches application sync and health status from the ArgoCD API,
So that the portal can display live environment state without exposing ArgoCD concepts to developers.

## Acceptance Criteria

1. **ArgoCdAdapter as CDI bean with configurable ArgoCD URL**
   - **Given** the ArgoCdAdapter is an @ApplicationScoped CDI bean
   - **When** it needs to query an ArgoCD instance
   - **Then** it uses the ArgoCD REST API at the URL configured via `portal.argocd.url`
   - **And** it authenticates using a Bearer token configured via `portal.argocd.token`

2. **Environment status retrieval**
   - **Given** the adapter is asked for the status of an application's environments
   - **When** `getEnvironmentStatuses(String appName, List<Environment> environments)` is called
   - **Then** it queries ArgoCD for each ArgoCD Application matching the naming convention (`<app>-run-<env>`)
   - **And** it returns a list of EnvironmentStatusDto objects in portal domain language

3. **ArgoCD status to portal domain translation**
   - **Given** the ArgoCD API returns sync and health status for an application
   - **When** the adapter translates the response
   - **Then** ArgoCD "Synced" + "Healthy" maps to portal status "Healthy"
   - **And** ArgoCD "Synced" + "Degraded" or "Missing" maps to portal status "Unhealthy"
   - **And** ArgoCD "OutOfSync" or "Progressing" maps to portal status "Deploying"
   - **And** ArgoCD application not found maps to portal status "Not Deployed"
   - **And** the deployed image tag / version is extracted from the ArgoCD application status

4. **Parallel ArgoCD queries**
   - **Given** the adapter queries ArgoCD for multiple environments
   - **When** parallel calls are needed for performance
   - **Then** calls to ArgoCD are executed in parallel using CompletableFuture
   - **And** results are aggregated before returning

5. **Error handling for unreachable ArgoCD**
   - **Given** ArgoCD is unreachable
   - **When** the adapter attempts to query it
   - **Then** a PortalIntegrationException is thrown with system="argocd" and a message: "Deployment status unavailable — ArgoCD is unreachable"
   - **And** the deep link to ArgoCD UI is included if the URL is configured

6. **EnvironmentStatusDto shape**
   - **Given** the adapter returns environment status
   - **When** reviewing the EnvironmentStatusDto
   - **Then** it contains: environmentName, status (Healthy/Unhealthy/Deploying/NotDeployed), deployedVersion, lastDeployedAt, argocdAppName, and a deep link URL to the ArgoCD Application UI

## Tasks / Subtasks

- [x] Task 1: Add ArgoCD configuration properties (AC: #1)
  - [x] Add `portal.argocd.url`, `portal.argocd.token`, `portal.argocd.provider` to `application.properties`
  - [x] Add `%dev.portal.argocd.provider=dev` for dev mode activation
  - [x] Create `ArgoCdConfig.java` using `@ConfigMapping(prefix = "portal.argocd")`
  - [x] Configure Quarkus REST Client: `quarkus.rest-client.argocd-api.url` referencing `ARGOCD_URL`

- [x] Task 2: Create ArgoCD REST Client interface (AC: #1)
  - [x] Create `ArgoCdRestClient.java` in `com.portal.integration.argocd` using `@RegisterRestClient(configKey = "argocd-api")`
  - [x] Define `@GET @Path("/applications/{name}") JsonNode getApplication(...)` accepting `@HeaderParam("Authorization")` and `@PathParam("name")`
  - [x] Jackson-compatible response binding to `JsonNode` for flexibility with ArgoCD API response shape

- [x] Task 3: Create internal ArgoCD response models (AC: #2, #3)
  - [x] Create `ArgoCdApplication.java` record in `com.portal.integration.argocd.model` — parsed from ArgoCD API JSON
  - [x] Create `ArgoCdSyncStatus.java` record in `com.portal.integration.argocd.model` — holds syncStatus, healthStatus, deployedVersion, operationFinishedAt

- [x] Task 4: Create EnvironmentStatusDto (AC: #6)
  - [x] Create `EnvironmentStatusDto.java` record in `com.portal.environment`
  - [x] Fields: `String environmentName`, `PortalEnvironmentStatus status`, `String deployedVersion`, `Instant lastDeployedAt`, `String argocdAppName`, `String argocdDeepLink`
  - [x] Create `PortalEnvironmentStatus` enum in `com.portal.environment`: `HEALTHY`, `UNHEALTHY`, `DEPLOYING`, `NOT_DEPLOYED`

- [x] Task 5: Create ArgoCdAdapter interface (AC: #2)
  - [x] Create `ArgoCdAdapter.java` interface in `com.portal.integration.argocd`
  - [x] Method: `List<EnvironmentStatusDto> getEnvironmentStatuses(String appName, List<Environment> environments)`

- [x] Task 6: Implement ArgoCdRestAdapter (AC: #1, #2, #3, #4, #5, #6)
  - [x] Create `ArgoCdRestAdapter.java` in `com.portal.integration.argocd`, `@ApplicationScoped`
  - [x] Activate via `@IfBuildProperty(name = "portal.argocd.provider", stringValue = "argocd", enableIfMissing = true)`
  - [x] Inject `@RestClient ArgoCdRestClient` and `ArgoCdConfig`
  - [x] Implement `getEnvironmentStatuses`: build ArgoCD app name per environment (`<appName>-run-<envName>`), query each in parallel via `CompletableFuture`, aggregate results
  - [x] Implement status translation: parse ArgoCD sync + health into `PortalEnvironmentStatus`
  - [x] Extract deployed version from ArgoCD response summary images
  - [x] Build deep link URL: `{argocdUrl}/applications/{argocdAppName}`
  - [x] Catch `WebApplicationException` / connection failures → throw `PortalIntegrationException(system="argocd", ...)`

- [x] Task 7: Implement DevArgoCdAdapter (AC: #1)
  - [x] Create `DevArgoCdAdapter.java` in `com.portal.integration.argocd`, `@ApplicationScoped`
  - [x] Activate via `@IfBuildProperty(name = "portal.argocd.provider", stringValue = "dev")`
  - [x] Return mock EnvironmentStatusDto data: first env as HEALTHY with "v1.2.3", second as DEPLOYING, others as NOT_DEPLOYED
  - [x] Follow the `DevSecretManagerAdapter` pattern exactly

- [x] Task 8: Write ArgoCdRestAdapter unit tests (AC: #2, #3, #4, #5)
  - [x] Create `ArgoCdRestAdapterTest.java` in `src/test/java/com/portal/integration/argocd/`
  - [x] Mock `ArgoCdRestClient` and `ArgoCdConfig` via reflection (same pattern as VaultSecretManagerAdapterTest)
  - [x] Test `getEnvironmentStatuses` with single environment: verify correct ArgoCD app name, correct status mapping
  - [x] Test status mapping: Synced+Healthy → HEALTHY, Synced+Degraded → UNHEALTHY, OutOfSync → DEPLOYING, 404 → NOT_DEPLOYED
  - [x] Test deployed version extraction from ArgoCD response
  - [x] Test deep link URL construction
  - [x] Test ArgoCD unreachable → PortalIntegrationException with system="argocd"
  - [x] Test parallel execution: verify multiple environments queried and results aggregated

- [x] Task 9: Write ArgoCdAdapter integration test (AC: #1, #2, #5)
  - [x] Create `ArgoCdAdapterIT.java` in `src/test/java/com/portal/integration/argocd/`
  - [x] `@QuarkusTest` with `@InjectMock ArgoCdRestClient` to mock the REST client
  - [x] Test full adapter lifecycle: config injected, REST client called, response translated, DTO returned
  - [x] Test error propagation: REST client throws → PortalIntegrationException

- [x] Task 10: Write EnvironmentStatusDto serialization test (AC: #6)
  - [x] Create `EnvironmentStatusDtoTest.java` in `src/test/java/com/portal/environment/`
  - [x] Verify JSON serialization: all fields present, enum serialized as string, Instant as ISO 8601

## Dev Notes

### Hard Dependencies

This story **requires** Story 2.1 to be implemented first:
- **Story 2.1** — `Environment` entity with `findByApplicationOrderByPromotionOrder(Long applicationId)` query method, which returns the list of environments this adapter needs to iterate over

This story has **no frontend component** — it is a pure backend adapter. The frontend visualization is Story 2.8 (Environment Chain Visualization), which consumes the EnvironmentStatusDto via a REST endpoint created in that story.

### Package: `com.portal.integration.argocd` — New Code

The `argocd/` package currently has only `package-info.java`. This story creates the full adapter:

```
com.portal.integration.argocd/
├── ArgoCdAdapter.java          # Interface — getEnvironmentStatuses()
├── ArgoCdRestAdapter.java      # Real impl — calls ArgoCD REST API
├── ArgoCdConfig.java           # @ConfigMapping for portal.argocd.*
├── ArgoCdRestClient.java       # @RegisterRestClient for ArgoCD API
├── DevArgoCdAdapter.java       # Dev mode impl — returns mock data
├── model/
│   ├── ArgoCdApplication.java  # Internal API response model
│   └── ArgoCdSyncStatus.java   # Internal parsed status
└── package-info.java           # EXISTS
```

### Package: `com.portal.environment` — New Code

The `environment/` package currently has only `package-info.java`. This story adds domain-level DTOs:

```
com.portal.environment/
├── EnvironmentStatusDto.java       # NEW — portal domain status DTO
├── PortalEnvironmentStatus.java    # NEW — enum: HEALTHY, UNHEALTHY, DEPLOYING, NOT_DEPLOYED
└── package-info.java               # EXISTS
```

The `EnvironmentResource.java` and `EnvironmentService.java` from the architecture are NOT created in this story — they belong to Story 2.8 which creates the REST endpoint that calls this adapter.

### ArgoCdAdapter Interface

```java
package com.portal.integration.argocd;

import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.Environment;
import java.util.List;

/**
 * Adapter for querying ArgoCD Application sync and health status.
 * Translates ArgoCD concepts to portal domain language.
 */
public interface ArgoCdAdapter {

    /**
     * Queries ArgoCD for the live status of each environment's ArgoCD Application.
     *
     * @param appName     the portal application name (used to derive ArgoCD app names)
     * @param environments the environments to check, ordered by promotion_order
     * @return status for each environment in the same order as input
     */
    List<EnvironmentStatusDto> getEnvironmentStatuses(String appName, List<Environment> environments);
}
```

This is an interface (not a concrete class) to support `DevArgoCdAdapter` in dev mode via `@IfBuildProperty`, following the established `SecretManagerAdapter` / `DevSecretManagerAdapter` pattern.

### ArgoCdConfig — Configuration Mapping

```java
@ConfigMapping(prefix = "portal.argocd")
public interface ArgoCdConfig {

    @WithDefault("argocd")
    String provider();

    String url();

    Optional<String> token();
}
```

Follows the `SecretManagerConfig` pattern exactly. The `provider` key selects between `ArgoCdRestAdapter` (value `"argocd"`) and `DevArgoCdAdapter` (value `"dev"`).

### Configuration Properties to Add

Add to `application.properties`:

```properties
# ArgoCD
portal.argocd.provider=${ARGOCD_PROVIDER:argocd}
portal.argocd.url=${ARGOCD_URL:http://localhost:8080}
portal.argocd.token=${ARGOCD_TOKEN:}

# Quarkus REST Client for ArgoCD API
quarkus.rest-client.argocd-api.url=${ARGOCD_URL:http://localhost:8080}

# Dev profile
%dev.portal.argocd.provider=dev
```

The `quarkus.rest-client.argocd-api.url` line configures the Quarkus REST Client URL. The `argocd-api` config key matches `@RegisterRestClient(configKey = "argocd-api")` on the client interface.

### ArgoCdRestClient — Quarkus REST Client Interface

```java
@RegisterRestClient(configKey = "argocd-api")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface ArgoCdRestClient {

    @GET
    @Path("/applications/{name}")
    JsonNode getApplication(
            @PathParam("name") String name,
            @HeaderParam("Authorization") String authHeader);
}
```

Uses `JsonNode` return type (not a typed model) because the ArgoCD API response is deeply nested and we only need a few fields. This avoids creating a brittle model for the entire ArgoCD response schema. The adapter extracts the needed fields from the `JsonNode`.

**Why Quarkus REST Client (not raw HttpClient):** The `quarkus-rest-client-jackson` extension is already in `pom.xml` specifically for this purpose (per architecture: "Typed REST clients for ArgoCD API, Grafana API, Git server API"). The Vault adapter uses raw `java.net.http.HttpClient` because it was built before the REST client pattern was established — ArgoCD is the first adapter to use the proper Quarkus REST Client approach. Future adapters (Tekton, Grafana) should follow this same pattern.

### ArgoCD API Response Structure

The ArgoCD REST API `GET /api/v1/applications/{name}` returns a deeply nested JSON object. The relevant fields for status extraction:

```json
{
  "metadata": {
    "name": "payment-svc-run-dev"
  },
  "status": {
    "sync": {
      "status": "Synced"
    },
    "health": {
      "status": "Healthy"
    },
    "operationState": {
      "finishedAt": "2026-04-05T10:30:00Z"
    },
    "summary": {
      "images": [
        "registry.example.com/team/payment-svc:v1.2.3"
      ]
    }
  }
}
```

**ArgoCD sync status values:** `Synced`, `OutOfSync`, `Unknown`
**ArgoCD health status values:** `Healthy`, `Progressing`, `Degraded`, `Suspended`, `Missing`, `Unknown`

### Internal Model: ArgoCdSyncStatus

```java
package com.portal.integration.argocd.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Parsed subset of ArgoCD Application status.
 * Internal to the adapter — never exposed outside the argocd package.
 */
public record ArgoCdSyncStatus(
    String syncStatus,
    String healthStatus,
    Optional<String> deployedVersion,
    Optional<Instant> operationFinishedAt
) {}
```

### Internal Model: ArgoCdApplication

```java
package com.portal.integration.argocd.model;

/**
 * Minimal representation of an ArgoCD Application for internal adapter use.
 */
public record ArgoCdApplication(
    String name,
    ArgoCdSyncStatus status
) {}
```

### ArgoCdRestAdapter — Full Implementation Strategy

```java
@ApplicationScoped
@IfBuildProperty(name = "portal.argocd.provider", stringValue = "argocd", enableIfMissing = true)
public class ArgoCdRestAdapter implements ArgoCdAdapter {

    @Inject
    @RestClient
    ArgoCdRestClient restClient;

    @Inject
    ArgoCdConfig config;

    @Override
    public List<EnvironmentStatusDto> getEnvironmentStatuses(String appName,
            List<Environment> environments) {
        String authHeader = config.token()
                .map(t -> "Bearer " + t)
                .orElse("");

        List<CompletableFuture<EnvironmentStatusDto>> futures = environments.stream()
                .map(env -> CompletableFuture.supplyAsync(() ->
                        fetchSingleEnvironmentStatus(appName, env, authHeader)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private EnvironmentStatusDto fetchSingleEnvironmentStatus(String appName,
            Environment env, String authHeader) {
        String argoAppName = appName + "-run-" + env.name.toLowerCase();
        String deepLink = config.url() + "/applications/" + argoAppName;

        try {
            JsonNode response = restClient.getApplication(argoAppName, authHeader);
            ArgoCdSyncStatus syncStatus = parseStatus(response);
            PortalEnvironmentStatus portalStatus = translateStatus(
                    syncStatus.syncStatus(), syncStatus.healthStatus());

            return new EnvironmentStatusDto(
                    env.name,
                    portalStatus,
                    syncStatus.deployedVersion().orElse(null),
                    syncStatus.operationFinishedAt().orElse(null),
                    argoAppName,
                    deepLink);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return new EnvironmentStatusDto(
                        env.name, PortalEnvironmentStatus.NOT_DEPLOYED,
                        null, null, argoAppName, deepLink);
            }
            throw new PortalIntegrationException("argocd", "getEnvironmentStatus",
                    "Deployment status unavailable — ArgoCD returned an error",
                    deepLink, e);
        } catch (Exception e) {
            throw new PortalIntegrationException("argocd", "getEnvironmentStatus",
                    "Deployment status unavailable — ArgoCD is unreachable",
                    deepLink, e);
        }
    }

    private ArgoCdSyncStatus parseStatus(JsonNode root) {
        JsonNode status = root.path("status");
        String syncStatus = status.path("sync").path("status").asText("Unknown");
        String healthStatus = status.path("health").path("status").asText("Unknown");

        Optional<String> version = Optional.empty();
        JsonNode images = status.path("summary").path("images");
        if (images.isArray() && !images.isEmpty()) {
            String image = images.get(0).asText();
            int colonIdx = image.lastIndexOf(':');
            if (colonIdx > 0) {
                version = Optional.of(image.substring(colonIdx + 1));
            }
        }

        Optional<Instant> finishedAt = Optional.empty();
        String finishedAtStr = status.path("operationState").path("finishedAt").asText(null);
        if (finishedAtStr != null) {
            finishedAt = Optional.of(Instant.parse(finishedAtStr));
        }

        return new ArgoCdSyncStatus(syncStatus, healthStatus, version, finishedAt);
    }

    static PortalEnvironmentStatus translateStatus(String syncStatus, String healthStatus) {
        if ("OutOfSync".equals(syncStatus) || "Progressing".equals(healthStatus)) {
            return PortalEnvironmentStatus.DEPLOYING;
        }
        if ("Synced".equals(syncStatus) && "Healthy".equals(healthStatus)) {
            return PortalEnvironmentStatus.HEALTHY;
        }
        if ("Synced".equals(syncStatus)
                && ("Degraded".equals(healthStatus) || "Missing".equals(healthStatus))) {
            return PortalEnvironmentStatus.UNHEALTHY;
        }
        if ("Unknown".equals(syncStatus) && "Unknown".equals(healthStatus)) {
            return PortalEnvironmentStatus.NOT_DEPLOYED;
        }
        return PortalEnvironmentStatus.UNHEALTHY;
    }
}
```

**Key implementation notes:**
- `CompletableFuture.supplyAsync()` for parallel ArgoCD calls — uses the common ForkJoinPool, acceptable for MVP; a managed executor can be added later
- 404 from ArgoCD means the ArgoCD Application doesn't exist → portal status "Not Deployed" (not an error)
- Any other HTTP error or connection failure → `PortalIntegrationException` with deep link
- `translateStatus` is package-private static method for easy unit testing
- Version extracted from the first image in the ArgoCD summary — the tag after the last colon

### ArgoCD App Naming Convention

ArgoCD Applications are created during onboarding (Story 2.5) with the naming convention:
- Build: `<appName>-build`
- Run per environment: `<appName>-run-<env>` (e.g., `payment-svc-run-dev`, `payment-svc-run-qa`, `payment-svc-run-prod`)

This adapter queries the **run** applications only — build pipeline status is a Tekton concern (Epic 4), not ArgoCD.

### EnvironmentStatusDto — Portal Domain DTO

```java
package com.portal.environment;

import java.time.Instant;

/**
 * Live status of a single environment, translated from ArgoCD domain to portal domain language.
 * Used by EnvironmentService/EnvironmentResource in Story 2.8 and beyond.
 */
public record EnvironmentStatusDto(
    String environmentName,
    PortalEnvironmentStatus status,
    String deployedVersion,
    Instant lastDeployedAt,
    String argocdAppName,
    String argocdDeepLink
) {}
```

### PortalEnvironmentStatus Enum

```java
package com.portal.environment;

/**
 * Portal domain status for an environment — developer-facing, not ArgoCD terminology.
 */
public enum PortalEnvironmentStatus {
    HEALTHY,
    UNHEALTHY,
    DEPLOYING,
    NOT_DEPLOYED
}
```

### DevArgoCdAdapter — Dev Mode Implementation

```java
@ApplicationScoped
@IfBuildProperty(name = "portal.argocd.provider", stringValue = "dev")
public class DevArgoCdAdapter implements ArgoCdAdapter {

    @Override
    public List<EnvironmentStatusDto> getEnvironmentStatuses(String appName,
            List<Environment> environments) {
        return environments.stream()
                .map(env -> {
                    int order = env.promotionOrder;
                    PortalEnvironmentStatus status = switch (order) {
                        case 0 -> PortalEnvironmentStatus.HEALTHY;
                        case 1 -> PortalEnvironmentStatus.DEPLOYING;
                        default -> PortalEnvironmentStatus.NOT_DEPLOYED;
                    };
                    String version = status == PortalEnvironmentStatus.HEALTHY ? "v1.2.3" : null;
                    Instant deployedAt = status == PortalEnvironmentStatus.HEALTHY
                            ? Instant.now().minusSeconds(7200) : null;

                    return new EnvironmentStatusDto(
                            env.name, status, version, deployedAt,
                            appName + "-run-" + env.name.toLowerCase(),
                            "https://dev-argocd/applications/" + appName + "-run-" + env.name.toLowerCase());
                })
                .toList();
    }
}
```

First environment (promotion_order 0) returns HEALTHY with a version, second returns DEPLOYING, rest return NOT_DEPLOYED. This provides realistic mock data for the dev UI (Story 2.8).

### Status Translation Rules — Complete Matrix

| ArgoCD Sync Status | ArgoCD Health Status | Portal Status | Rationale |
|---|---|---|---|
| Synced | Healthy | HEALTHY | App is running and in sync |
| Synced | Degraded | UNHEALTHY | App is deployed but failing |
| Synced | Missing | UNHEALTHY | Resources exist in Git but not in cluster |
| Synced | Suspended | DEPLOYING | Argo Rollout paused mid-deploy |
| Synced | Progressing | DEPLOYING | Resources updating (e.g., pods rolling) |
| OutOfSync | Healthy | DEPLOYING | New version committed, sync pending |
| OutOfSync | Progressing | DEPLOYING | Actively syncing new version |
| OutOfSync | Degraded | UNHEALTHY | Out of sync AND failing |
| Unknown | Unknown | NOT_DEPLOYED | App exists in ArgoCD but no status available |
| (404 Not Found) | — | NOT_DEPLOYED | ArgoCD Application does not exist |

The `translateStatus` method implements these rules. The primary signals:
1. If sync is "OutOfSync" OR health is "Progressing" or "Suspended" → DEPLOYING (something is in motion)
2. If sync is "Synced" AND health is "Healthy" → HEALTHY (everything is good)
3. If sync is "Synced" AND health is "Degraded" or "Missing" → UNHEALTHY (deployed but broken)
4. Unknown/Unknown → NOT_DEPLOYED (no meaningful state)
5. All other combinations → UNHEALTHY (conservative fallback)

### Parallel Execution Pattern

```java
List<CompletableFuture<EnvironmentStatusDto>> futures = environments.stream()
        .map(env -> CompletableFuture.supplyAsync(() ->
                fetchSingleEnvironmentStatus(appName, env, authHeader)))
        .toList();

return futures.stream()
        .map(CompletableFuture::join)
        .toList();
```

This fires all ArgoCD queries concurrently and waits for all to complete. For an app with 3 environments, this means 3 parallel HTTP calls instead of 3 sequential ones — meeting the <3s API response target from NFR.

**Thread pool:** `CompletableFuture.supplyAsync()` uses the common ForkJoinPool. For MVP this is acceptable. If ArgoCD latency becomes a concern, inject a `ManagedExecutor` from Quarkus for explicit thread pool control.

**Error isolation:** Each environment is queried independently. If one ArgoCD Application returns a 404 (Not Deployed), the adapter still returns results for other environments. Only connection-level failures (ArgoCD unreachable) propagate as exceptions.

Wait — re-reading AC #5: "a PortalIntegrationException is thrown with system='argocd'" for unreachable ArgoCD. This means the ENTIRE call fails if ArgoCD is down. Individual 404s are handled per-environment, but connectivity failures are propagated. The implementation above handles this correctly: 404 → NOT_DEPLOYED per environment, but connection exceptions → PortalIntegrationException bubbles up.

**Caveat:** If ArgoCD is reachable but one specific app returns a non-404 error (e.g., 500), the current implementation will throw `PortalIntegrationException` and abort all results. Consider whether to treat per-app errors as degraded (return UNHEALTHY) or propagate. For MVP, propagating is acceptable — Story 2.8 will show an inline Alert for the entire environments section.

### Error Handling

All errors from ArgoCD calls must be wrapped in `PortalIntegrationException`:

```java
throw new PortalIntegrationException(
    "argocd",
    "getEnvironmentStatus",
    "Deployment status unavailable — ArgoCD is unreachable",
    deepLink,
    cause);
```

The `GlobalExceptionMapper` already handles `PortalIntegrationException` → 502 response with the standardized error JSON including system and deepLink fields.

Error messages must use developer language — never expose ArgoCD API details, HTTP status codes, or infrastructure terms. Examples:
- Connection failure: "Deployment status unavailable — ArgoCD is unreachable"
- 500 from ArgoCD: "Deployment status unavailable — ArgoCD returned an error"
- Timeout: "Deployment status unavailable — ArgoCD did not respond in time"

### Test Configuration

Add to `src/test/resources/application.properties`:

```properties
portal.argocd.provider=dev
```

This ensures integration tests use `DevArgoCdAdapter` by default. Tests that need to exercise the real adapter logic should use `@InjectMock ArgoCdRestClient` to mock the REST client while keeping the adapter's translation logic live.

Alternatively, `ArgoCdRestAdapterTest` (unit test) mocks the REST client via reflection, just like `VaultSecretManagerAdapterTest` mocks the Vault HTTP client.

### Unit Test — ArgoCdRestAdapterTest

```java
class ArgoCdRestAdapterTest {
    private ArgoCdRestAdapter adapter;
    private ArgoCdRestClient mockClient;
    private ArgoCdConfig mockConfig;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(ArgoCdRestClient.class);
        mockConfig = mock(ArgoCdConfig.class);
        when(mockConfig.url()).thenReturn("https://argocd.example.com");
        when(mockConfig.token()).thenReturn(Optional.of("test-token"));

        adapter = new ArgoCdRestAdapter();
        Field clientField = ArgoCdRestAdapter.class.getDeclaredField("restClient");
        clientField.setAccessible(true);
        clientField.set(adapter, mockClient);
        Field configField = ArgoCdRestAdapter.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(adapter, mockConfig);
    }

    @Test
    void healthySyncedReturnsHealthyStatus() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy", "registry/app:v1.0.0");
        when(mockClient.getApplication(eq("my-app-run-dev"), anyString()))
                .thenReturn(response);

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(PortalEnvironmentStatus.HEALTHY);
        assertThat(results.get(0).deployedVersion()).isEqualTo("v1.0.0");
        assertThat(results.get(0).argocdDeepLink())
                .isEqualTo("https://argocd.example.com/applications/my-app-run-dev");
    }

    @Test
    void notFoundReturnsNotDeployed() {
        when(mockClient.getApplication(anyString(), anyString()))
                .thenThrow(new WebApplicationException(404));

        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("my-app", List.of(env));

        assertThat(results.get(0).status()).isEqualTo(PortalEnvironmentStatus.NOT_DEPLOYED);
    }

    @Test
    void connectionFailureThrowsPortalException() {
        when(mockClient.getApplication(anyString(), anyString()))
                .thenThrow(new ProcessingException("Connection refused"));

        Environment env = buildEnv("dev", 0);
        assertThatThrownBy(() -> adapter.getEnvironmentStatuses("my-app", List.of(env)))
                .isInstanceOf(PortalIntegrationException.class)
                .satisfies(e -> {
                    PortalIntegrationException pie = (PortalIntegrationException) e;
                    assertThat(pie.getSystem()).isEqualTo("argocd");
                    assertThat(pie.getDeepLink()).contains("argocd.example.com");
                });
    }

    @Test
    void translateStatusCoversAllMappings() {
        assertThat(ArgoCdRestAdapter.translateStatus("Synced", "Healthy"))
                .isEqualTo(PortalEnvironmentStatus.HEALTHY);
        assertThat(ArgoCdRestAdapter.translateStatus("Synced", "Degraded"))
                .isEqualTo(PortalEnvironmentStatus.UNHEALTHY);
        assertThat(ArgoCdRestAdapter.translateStatus("Synced", "Missing"))
                .isEqualTo(PortalEnvironmentStatus.UNHEALTHY);
        assertThat(ArgoCdRestAdapter.translateStatus("OutOfSync", "Healthy"))
                .isEqualTo(PortalEnvironmentStatus.DEPLOYING);
        assertThat(ArgoCdRestAdapter.translateStatus("Synced", "Progressing"))
                .isEqualTo(PortalEnvironmentStatus.DEPLOYING);
        assertThat(ArgoCdRestAdapter.translateStatus("Unknown", "Unknown"))
                .isEqualTo(PortalEnvironmentStatus.NOT_DEPLOYED);
    }

    // Helper: build mock ArgoCD JSON response
    private JsonNode buildArgoCdResponse(String syncStatus, String healthStatus, String image) {
        // Use objectMapper to build nested JSON matching ArgoCD response structure
    }

    // Helper: build Environment entity for testing
    private Environment buildEnv(String name, int order) {
        Environment env = new Environment();
        env.name = name;
        env.promotionOrder = order;
        return env;
    }
}
```

**Note on Environment entity in tests:** `Environment` extends `PanacheEntityBase` which has static methods that require Quarkus runtime. For unit tests, you can instantiate `Environment` directly (setting public fields) without calling any Panache static methods. If the entity constructor or static methods cause issues, use the `@QuarkusTest` IT test instead.

### Integration Test — ArgoCdAdapterIT

```java
@QuarkusTest
class ArgoCdAdapterIT {

    @InjectMock
    @RestClient
    ArgoCdRestClient restClient;

    @Inject
    ArgoCdAdapter adapter;

    @Test
    void adapterTranslatesHealthyResponse() {
        JsonNode response = buildArgoCdResponse("Synced", "Healthy", "registry/app:v2.0.0");
        when(restClient.getApplication(eq("test-app-run-dev"), anyString()))
                .thenReturn(response);

        // Build Environment entity (requires test data in DB or mock)
        Environment env = buildEnv("dev", 0);
        List<EnvironmentStatusDto> results =
                adapter.getEnvironmentStatuses("test-app", List.of(env));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(PortalEnvironmentStatus.HEALTHY);
        assertThat(results.get(0).deployedVersion()).isEqualTo("v2.0.0");
    }
}
```

**Important:** The IT test `@InjectMock @RestClient ArgoCdRestClient` mocks the REST client at the CDI level, so the `ArgoCdRestAdapter` is the live bean injected via `ArgoCdAdapter adapter` — this tests the real translation logic with a mocked HTTP layer. Make sure the test `application.properties` has `portal.argocd.provider=argocd` (not `dev`) OR the IT test uses `@TestProfile` to override.

**Alternative:** If `portal.argocd.provider=dev` in test config activates `DevArgoCdAdapter` instead of `ArgoCdRestAdapter`, the IT test won't exercise the real adapter. To resolve: either use `@TestProfile` with provider=argocd, or set `portal.argocd.provider=argocd` in test config and use `@InjectMock @RestClient` for all IT tests.

Recommended approach: In `src/test/resources/application.properties`, set `portal.argocd.provider=argocd` so the real adapter is active in tests. Use `@InjectMock @RestClient ArgoCdRestClient` in every IT test to prevent real HTTP calls.

### Existing Code to Reuse

| Component | Location | Usage |
|-----------|----------|-------|
| `Environment` entity | `environment/Environment.java` | **From Story 2.1** — passed to `getEnvironmentStatuses()` with `name` and `promotionOrder` fields |
| `PortalIntegrationException` | `integration/PortalIntegrationException.java` | Throw for ArgoCD failures with system="argocd", deep link |
| `GlobalExceptionMapper` | `common/GlobalExceptionMapper.java` | Already handles PortalIntegrationException → 502 |
| `ErrorResponse` | `common/ErrorResponse.java` | Standardized error JSON shape — no changes needed |
| `SecretManagerAdapter` pattern | `integration/secrets/SecretManagerAdapter.java` | Interface pattern to follow |
| `DevSecretManagerAdapter` pattern | `integration/secrets/DevSecretManagerAdapter.java` | `@IfBuildProperty` activation pattern to follow |
| `VaultSecretManagerAdapter` | `integration/secrets/vault/VaultSecretManagerAdapter.java` | Constructor + error handling pattern to reference |
| `SecretManagerConfig` | `integration/secrets/SecretManagerConfig.java` | `@ConfigMapping` pattern to follow for ArgoCdConfig |
| `ClusterCredential` | `integration/secrets/ClusterCredential.java` | Record pattern for adapter return types |
| `quarkus-rest-client-jackson` | `pom.xml` | Already a dependency — enables `@RegisterRestClient` |

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/integration/argocd/
├── ArgoCdAdapter.java
├── ArgoCdRestAdapter.java
├── ArgoCdConfig.java
├── ArgoCdRestClient.java
├── DevArgoCdAdapter.java
└── model/
    ├── ArgoCdApplication.java
    └── ArgoCdSyncStatus.java

src/main/java/com/portal/environment/
├── EnvironmentStatusDto.java
└── PortalEnvironmentStatus.java

src/test/java/com/portal/integration/argocd/
├── ArgoCdRestAdapterTest.java
└── ArgoCdAdapterIT.java

src/test/java/com/portal/environment/
└── EnvironmentStatusDtoTest.java
```

**Modified files:**
```
src/main/resources/application.properties            (add ArgoCD config + REST Client config)
src/test/resources/application.properties             (add portal.argocd.provider=argocd)
```

**No frontend files** — this story is backend-only. Story 2.8 creates the frontend visualization.

### Previous Story Intelligence

**Story 2.1 (Application & Environment Data Model):**
- `Environment` entity at `com.portal.environment.Environment` extends `PanacheEntityBase`
- Fields: `id` (Long), `name`, `applicationId` (Long FK), `clusterId` (Long FK), `namespace`, `promotionOrder` (int)
- `findByApplicationOrderByPromotionOrder(Long applicationId)` returns environments in promotion chain order
- The adapter receives the list of `Environment` objects — it needs `name` and `promotionOrder`

**Story 2.5 (Onboarding PR Creation & Completion):**
- Environment entities are persisted during onboarding with `promotionOrder` 0, 1, 2, ...
- The ArgoCD Application names created during onboarding follow: `<appName>-run-<envName>` (lowercase)
- This naming convention is the contract the adapter relies on to find the right ArgoCD Application

**Epic 1 Vault Adapter Pattern (Story 1.6):**
- `SecretManagerAdapter` interface → `VaultSecretManagerAdapter` + `DevSecretManagerAdapter`
- `@IfBuildProperty(name = "portal.secrets.provider", stringValue = "vault", enableIfMissing = true)` on real impl
- `@IfBuildProperty(name = "portal.secrets.provider", stringValue = "dev")` on dev impl
- `SecretManagerConfig` uses `@ConfigMapping(prefix = "portal.secrets")` with `@WithDefault` on provider
- Unit tests mock via reflection (`Field.setAccessible(true)`)
- IT tests use `@InjectMock` for CDI-level mocking

### What NOT to Build in This Story

- **No EnvironmentResource REST endpoint** — that's Story 2.8; this story creates only the adapter
- **No EnvironmentService** — that's Story 2.8; the service orchestrates between DB and adapter
- **No frontend components** — the environment chain visualization is Story 2.8
- **No ArgoCD sync/deploy triggers** — the adapter only READS status; deployment operations are Epic 5
- **No Vault credential lookup for ArgoCD** — ArgoCD authentication uses a static token for MVP, not Vault-issued credentials. The architecture mentions Vault credentials for Kubernetes API access (Tekton, Argo Rollouts), but ArgoCD has its own REST API with token auth
- **No background polling or caching** — status is fetched live per request per the architecture ("no stale data", "every API response reflects live state")
- **No build status from ArgoCD** — build pipeline status comes from Tekton (Epic 4). This adapter only queries `<app>-run-<env>` applications, not `<app>-build`

### References

- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.7 (line 838)] — Full acceptance criteria
- [Source: planning-artifacts/architecture.md § Integration Adapters Complete List (line 1049)] — ArgoCdAdapter in `integration/argocd/`, ArgoCD REST API, Phase 1
- [Source: planning-artifacts/architecture.md § Integration Architecture (line 502)] — Adapter pattern: @ApplicationScoped CDI bean, translates platform concepts to portal domain
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure (line 865)] — `argocd/` package: ArgoCdAdapter, ArgoCdConfig, model/ (ArgoCdApplication, ArgoCdSyncStatus)
- [Source: planning-artifacts/architecture.md § environment/ package (line 795)] — EnvironmentStatusDto.java in environment package
- [Source: planning-artifacts/architecture.md § Naming Conventions (line 609)] — ArgoCdAdapter naming, EnvironmentStatusDto naming
- [Source: planning-artifacts/architecture.md § Configuration Properties (line 554)] — `portal.argocd.url=${ARGOCD_URL}`
- [Source: planning-artifacts/architecture.md § Quarkus Extensions (line 261)] — `rest-client-jackson` for ArgoCD API
- [Source: planning-artifacts/architecture.md § Parallel integration calls (line 500)] — CompletableFuture for parallel adapter calls
- [Source: planning-artifacts/architecture.md § Backend error handling (line 717)] — PortalIntegrationException → ExceptionMapper → 502
- [Source: planning-artifacts/architecture.md § Anti-Patterns (line 745)] — REST resource calling ArgoCD directly is forbidden; must go through adapter
- [Source: planning-artifacts/architecture.md § Deep-link-only systems (line 1074)] — ArgoCD UI URL: `{argocdUrl}/applications/{appName}`
- [Source: planning-artifacts/architecture.md § Data Flow (line 1095)] — Service → ArgoCdAdapter (with cluster cred) flow
- [Source: planning-artifacts/architecture.md § ArgoCD Applications per onboarded app (line 176)] — `<app>-run-<env>` naming convention
- [Source: planning-artifacts/ux-design-specification.md § Environment Chain Card Row] — Four states: Healthy (green), Unhealthy (red), Deploying (yellow), Not deployed (grey)
- [Source: planning-artifacts/ux-design-specification.md § Loading and data freshness (UX-DR13)] — "Fetching status from ArgoCD..." spinner, manual refresh, partial data rendering when systems unreachable
- [Source: planning-artifacts/ux-design-specification.md § Deep Link Patterns (UX-DR15)] — "Open in ArgoCD ↗", new tab, scoped to exact resource
- [Source: project-context.md § Technology Stack] — Quarkus 3.34.x, `quarkus-rest-client-jackson`
- [Source: project-context.md § Framework-Specific Rules] — All integration adapters throw PortalIntegrationException, REST → Service → Adapter chain
- [Source: project-context.md § Testing Rules] — Mock platform integrations, @InjectMock for adapter mocking, Unit: ClassTest.java, IT: ClassIT.java
- [Source: project-context.md § Anti-Patterns] — No caching platform state beyond current request, developer-language error messages
- [Source: project-context.md § Domain Language Translation] — ArgoCD sync → "Deployment" in developer language
- [Source: integration/PortalIntegrationException.java] — Constructor: (system, operation, message, deepLink, cause)
- [Source: common/GlobalExceptionMapper.java] — PortalIntegrationException → 502, ErrorResponse.of(...)
- [Source: common/ErrorResponse.java] — record(error, message, detail, system, deepLink, timestamp)
- [Source: integration/secrets/SecretManagerAdapter.java] — Interface pattern for adapter
- [Source: integration/secrets/DevSecretManagerAdapter.java] — @IfBuildProperty dev mode pattern
- [Source: integration/secrets/SecretManagerConfig.java] — @ConfigMapping pattern
- [Source: integration/secrets/vault/VaultSecretManagerAdapter.java] — @IfBuildProperty(enableIfMissing=true) for real impl
- [Source: implementation-artifacts/2-1-application-environment-data-model.md] — Environment entity fields, findByApplicationOrderByPromotionOrder

## Dev Agent Record

### Agent Model Used
Claude claude-4.6-opus-high-thinking

### Debug Log References
- Initial unit test run failed: AssertJ not available (project uses JUnit 5 assertions) — rewrote all tests with JUnit 5 assertions
- WebApplicationException mock caused UnfinishedStubbing — switched to `new WebApplicationException(statusCode)` constructor
- CompletableFuture::join wraps exceptions in CompletionException — added unwrap logic in getEnvironmentStatuses
- translateStatus check order: OutOfSync matched before Degraded health — reordered to check Degraded/Missing health first (matches spec matrix)

### Completion Notes List
- Implemented full ArgoCD adapter following the SecretManagerAdapter/DevSecretManagerAdapter pattern
- ArgoCdRestAdapter queries ArgoCD REST API via Quarkus REST Client, translates sync+health status to portal domain
- Parallel environment queries via CompletableFuture with CompletionException unwrapping
- Status translation covers full matrix: Synced+Healthy→HEALTHY, Degraded/Missing→UNHEALTHY, OutOfSync/Progressing/Suspended→DEPLOYING, Unknown/404→NOT_DEPLOYED
- DevArgoCdAdapter returns mock data for dev mode (first env HEALTHY, second DEPLOYING, rest NOT_DEPLOYED)
- 26 tests total: 18 unit tests (ArgoCdRestAdapterTest), 4 integration tests (ArgoCdAdapterIT), 4 serialization tests (EnvironmentStatusDtoTest)
- All new tests pass; pre-existing auth test failures (CasbinEnforcer, PermissionFilter, ClusterResource, GlobalExceptionMapper) are unrelated

### File List
New files:
- developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdAdapter.java
- developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdConfig.java
- developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdRestClient.java
- developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdRestAdapter.java
- developer-portal/src/main/java/com/portal/integration/argocd/DevArgoCdAdapter.java
- developer-portal/src/main/java/com/portal/integration/argocd/model/ArgoCdApplication.java
- developer-portal/src/main/java/com/portal/integration/argocd/model/ArgoCdSyncStatus.java
- developer-portal/src/main/java/com/portal/environment/EnvironmentStatusDto.java
- developer-portal/src/main/java/com/portal/environment/PortalEnvironmentStatus.java
- developer-portal/src/test/java/com/portal/integration/argocd/ArgoCdRestAdapterTest.java
- developer-portal/src/test/java/com/portal/integration/argocd/ArgoCdAdapterIT.java
- developer-portal/src/test/java/com/portal/environment/EnvironmentStatusDtoTest.java

Modified files:
- developer-portal/src/main/resources/application.properties
- developer-portal/src/test/resources/application.properties

### Change Log
- 2026-04-06: Implemented Story 2.7 — ArgoCD Adapter & Environment Status. Created ArgoCdAdapter interface with ArgoCdRestAdapter (production) and DevArgoCdAdapter (dev mode). Added EnvironmentStatusDto and PortalEnvironmentStatus to portal environment domain. Full test coverage with 26 tests.
- 2026-04-06: Addressed code review findings — (1) REST client URL now derives from portal.argocd.url to prevent config divergence, (2) malformed finishedAt timestamps caught gracefully instead of masquerading as "unreachable", (3) image version extraction handles digest-pinned and registry:port formats, (4) serialization test now asserts ISO-8601 timestamp format. Added 4 new unit tests. Total: 30 tests.
