# Story 4.2: Build Monitoring & Log Retrieval

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want to monitor my build's progress with clear status and view logs when something fails,
So that I can understand what happened without switching to the Tekton UI.

## Acceptance Criteria

1. **GET builds list returns all builds for an application, most recent first**
   - **Given** a build has been triggered
   - **When** GET `/api/v1/teams/{teamId}/applications/{appId}/builds` is called
   - **Then** a list of builds is returned, ordered by startedAt descending (most recent first)
   - **And** each build includes: buildId, status, startedAt, completedAt (if finished), duration, and artifact reference (if successful)

2. **Tekton PipelineRun status is translated to portal domain vocabulary**
   - **Given** the TektonAdapter queries a PipelineRun status
   - **When** translating to portal domain
   - **Then** PipelineRun condition reason "Running" maps to build status "Building"
   - **And** PipelineRun condition reason "Succeeded" maps to "Passed"
   - **And** PipelineRun condition reason "Failed" (or any failure reason) maps to "Failed"
   - **And** PipelineRun condition reason "PipelineRunCancelled" maps to "Cancelled"

3. **Passed builds include the container image reference**
   - **Given** a build has status "Passed"
   - **When** the build details are returned
   - **Then** the resulting container image reference is included (e.g., `registry.example.com/team/app:commit-sha`)
   - **And** the image reference is extracted from the PipelineRun results

4. **Failed builds include failure stage and error summary**
   - **Given** a build has status "Failed"
   - **When** GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}` is called
   - **Then** the response includes the failed stage name in developer language (e.g., "Unit Tests", "Image Build")
   - **And** a summary error message is included (e.g., "Test failure in ProcessorTest.testNullRefHandling")

5. **Build logs are retrievable as a text stream**
   - **Given** a developer requests build logs
   - **When** GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs` is called
   - **Then** the TektonAdapter retrieves logs from the PipelineRun's TaskRun pods
   - **And** logs are returned as `text/plain` content type
   - **And** the response is fast enough that the developer does not need to switch to the Tekton UI

6. **In-progress builds show current stage and elapsed time**
   - **Given** a build is currently in progress ("Building")
   - **When** the status is queried
   - **Then** the current stage is indicated in developer language (e.g., "Running unit tests")
   - **And** the elapsed duration is included

7. **Tekton deep links are populated on all build responses**
   - **Given** a Tekton deep link is available
   - **When** build details are returned
   - **Then** the tektonDeepLink field is populated using `DeepLinkService.generateTektonLink(pipelineRunId)`

## Tasks / Subtasks

- [x] Task 1: Extend TektonAdapter interface with query methods (AC: #1, #2, #3, #4, #5, #6)
  - [x] Add `listBuilds(String appName, String namespace, String clusterApiUrl, String clusterToken)` → returns `List<BuildSummaryDto>`
  - [x] Add `getBuildDetail(String buildId, String namespace, String clusterApiUrl, String clusterToken)` → returns `BuildDetailDto`
  - [x] Add `getBuildLogs(String buildId, String namespace, String clusterApiUrl, String clusterToken)` → returns `String`

- [x] Task 2: Create BuildDetailDto (AC: #3, #4, #6)
  - [x] Create `BuildDetailDto.java` in `com.portal.build` — extends BuildSummaryDto fields with: completedAt, duration, imageReference, failedStageName, errorSummary, currentStage

- [x] Task 3: Implement status translation utility (AC: #2)
  - [x] Create private helper in TektonKubeAdapter that maps PipelineRun conditions to portal status strings
  - [x] Handle all condition states: Running → "Building", Succeeded → "Passed", Failed/error reasons → "Failed", Cancelled → "Cancelled", pending/unknown → "Pending"

- [x] Task 4: Implement `listBuilds` in TektonKubeAdapter (AC: #1, #2, #7)
  - [x] List PipelineRuns in namespace filtered by label `tekton.dev/pipeline={appName}`
  - [x] Translate each PipelineRun to BuildSummaryDto using status translation
  - [x] Sort by startTime descending
  - [x] Populate tektonDeepLink for each build

- [x] Task 5: Implement `getBuildDetail` in TektonKubeAdapter (AC: #3, #4, #6, #7)
  - [x] Get PipelineRun by name
  - [x] Extract image reference from PipelineRun results (for "Passed" builds)
  - [x] Find failed TaskRun via childReferences and extract failure info (for "Failed" builds)
  - [x] Determine current running TaskRun for in-progress builds

- [x] Task 6: Implement `getBuildLogs` in TektonKubeAdapter (AC: #5)
  - [x] Resolve TaskRun names from PipelineRun's childReferences
  - [x] For each TaskRun, find the corresponding Pod (label `tekton.dev/taskRun={taskRunName}`)
  - [x] Retrieve logs from each Pod's step containers via KubernetesClient
  - [x] Concatenate logs with step/task headers as delimiters

- [x] Task 7: Implement `listBuilds`, `getBuildDetail`, `getBuildLogs` in DevTektonAdapter (AC: #1-#7)
  - [x] Return deterministic mock data for dev-mode testing
  - [x] Mock data must cover all states: Building, Passed (with image ref), Failed (with stage + error), Cancelled

- [x] Task 8: Add list/detail/log methods to BuildService (AC: #1, #3, #4, #5)
  - [x] `listBuilds(Long teamId, Long appId)` — team-scoped, delegates to adapter
  - [x] `getBuildDetail(Long teamId, Long appId, String buildId)` — team-scoped, delegates to adapter
  - [x] `getBuildLogs(Long teamId, Long appId, String buildId)` — team-scoped, delegates to adapter

- [x] Task 9: Add GET endpoints to BuildResource (AC: #1, #4, #5)
  - [x] GET `/api/v1/teams/{teamId}/applications/{appId}/builds` → list builds
  - [x] GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}` → build detail
  - [x] GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs` → build logs (`text/plain`)

- [x] Task 10: Write backend tests (AC: #1-#7)
  - [x] Create/extend `TektonKubeAdapterTest.java` — test status translation, list, detail, logs with mocked KubernetesClient
  - [x] Create/extend `BuildServiceTest.java` — test team scoping, delegation to adapter
  - [x] Create/extend `BuildResourceIT.java` — integration tests for all three GET endpoints

### Review Findings

- [x] [Review][Patch] Successful builds in the list response are missing the required artifact reference — added `imageReference` field to `BuildSummaryDto`, populated for Passed builds via `extractImageReference()`
- [x] [Review][Patch] `translateStatus()` maps every `Unknown` condition to `Building` — now only `Running`/`Started` reasons map to Building; other Unknown reasons return Pending
- [x] [Review][Patch] Build detail and log lookups do not verify that the requested `buildId` belongs to the requested application — `BuildService` now verifies `applicationName` from adapter response matches the requested app; added `getBuildDetailRejectsCrossAppBuild` test
- [x] [Review][Patch] Log retrieval exposes raw container/Kubernetes error messages directly in the API response body — replaced with generic `[Log unavailable]` message

## Dev Notes

### Critical Dependency: Story 4.1 Must Be Implemented First

This story extends the TektonAdapter, BuildService, and BuildResource created in Story 4.1. All Story 4.1 artifacts must exist before this story begins:
- `TektonAdapter.java` interface in `com.portal.integration.tekton`
- `TektonKubeAdapter.java` production implementation
- `DevTektonAdapter.java` dev-mode mock
- `TektonConfig.java` configuration class
- `BuildService.java` in `com.portal.build`
- `BuildResource.java` in `com.portal.build`
- `BuildSummaryDto.java` in `com.portal.build`
- `Application.java` with `buildClusterId` and `buildNamespace` fields
- `V5__add_build_config_to_applications.sql` migration
- `quarkus-kubernetes-client` and `tekton-client` dependencies in pom.xml

**Pre-flight check:** Verify all Story 4.1 files exist and all tests pass before starting. If any are missing, Story 4.1 must be completed first.

### Tekton PipelineRun Status Model — How It Works

A PipelineRun's status lives in `.status.conditions[]` — specifically the condition with `type: Succeeded`:

```yaml
status:
  startTime: "2026-04-07T12:30:00Z"
  completionTime: "2026-04-07T12:35:00Z"   # null while running
  conditions:
  - type: Succeeded
    status: "True"       # "True" = succeeded, "False" = failed, "Unknown" = running
    reason: Succeeded    # Running, Succeeded, Failed, PipelineRunCancelled, etc.
    message: "Tasks Completed: 3 (Failed: 0, Cancelled 0), Skipped: 0"
  childReferences:
  - name: myapp-xk7f2-build-image    # TaskRun name
    pipelineTaskName: build-image     # pipeline task name (developer-friendly)
    kind: TaskRun
  results:
  - name: IMAGE_URL
    value: "registry.example.com/team/myapp:abc1234"
```

**Status translation map:**

| `conditions[Succeeded].status` | `conditions[Succeeded].reason` | Portal Status |
|---|---|---|
| `"Unknown"` | `Running` | "Building" |
| `"Unknown"` | `Started` | "Building" |
| `"True"` | `Succeeded` | "Passed" |
| `"False"` | `Failed` | "Failed" |
| `"False"` | `PipelineRunCancelled` | "Cancelled" |
| `"False"` | `PipelineRunTimeout` | "Failed" |
| `"False"` | `PipelineValidationFailed` | "Failed" |
| `"False"` | any other | "Failed" |

**In Fabric8 Java:**

```java
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.kubernetes.api.model.Condition;

PipelineRun run = tektonClient.v1().pipelineRuns()
        .inNamespace(namespace).withName(buildId).get();

Condition succeeded = run.getStatus().getConditions().stream()
        .filter(c -> "Succeeded".equals(c.getType()))
        .findFirst().orElse(null);

String portalStatus;
if (succeeded == null) {
    portalStatus = "Pending";
} else if ("Unknown".equals(succeeded.getStatus())) {
    portalStatus = "Building";
} else if ("True".equals(succeeded.getStatus())) {
    portalStatus = "Passed";
} else if ("PipelineRunCancelled".equals(succeeded.getReason())) {
    portalStatus = "Cancelled";
} else {
    portalStatus = "Failed";
}
```

### Listing PipelineRuns for an Application

PipelineRuns created via Story 4.1 use `withGenerateName(appName + "-")` and `pipelineRef.name = appName`. To list builds for an application:

```java
List<PipelineRun> runs = tektonClient.v1().pipelineRuns()
        .inNamespace(namespace)
        .withLabel("tekton.dev/pipeline", appName)
        .list()
        .getItems();
```

The label `tekton.dev/pipeline` is automatically added by Tekton when a PipelineRun references a Pipeline by name. This is the canonical way to filter PipelineRuns by pipeline.

Sort by `startTime` descending in Java after fetching:

```java
runs.sort((a, b) -> {
    String ta = a.getStatus() != null ? a.getStatus().getStartTime() : null;
    String tb = b.getStatus() != null ? b.getStatus().getStartTime() : null;
    if (ta == null && tb == null) return 0;
    if (ta == null) return 1;
    if (tb == null) return -1;
    return tb.compareTo(ta); // descending
});
```

### Extracting Image Reference from PipelineRun Results

When a build succeeds, the image reference is in the PipelineRun's `.status.results[]`. The result name depends on the pipeline definition — common conventions: `IMAGE_URL`, `IMAGE_DIGEST`, or `image-url`. Extract it:

```java
String imageRef = null;
if (run.getStatus() != null && run.getStatus().getResults() != null) {
    imageRef = run.getStatus().getResults().stream()
            .filter(r -> r.getName().equalsIgnoreCase("IMAGE_URL")
                    || r.getName().equalsIgnoreCase("image-url"))
            .map(PipelineRunResult::getValue)
            .findFirst()
            .orElse(null);
}
```

If no result is found, `imageReference` in the DTO should be `null` — do NOT throw an error. Some pipelines may not output an image reference result. The DTO field is optional.

### Identifying the Failed TaskRun

When a PipelineRun fails, find which TaskRun failed using `childReferences`:

```java
// PipelineRun failed — find which task failed
List<ChildStatusReference> childRefs = run.getStatus().getChildReferences();
if (childRefs != null) {
    for (ChildStatusReference ref : childRefs) {
        if ("TaskRun".equals(ref.getKind())) {
            io.fabric8.tekton.pipeline.v1.TaskRun taskRun = tektonClient.v1()
                    .taskRuns().inNamespace(namespace).withName(ref.getName()).get();
            if (taskRun != null && taskRun.getStatus() != null) {
                Condition taskCond = taskRun.getStatus().getConditions().stream()
                        .filter(c -> "Succeeded".equals(c.getType()))
                        .findFirst().orElse(null);
                if (taskCond != null && "False".equals(taskCond.getStatus())) {
                    // This TaskRun failed
                    String failedStage = ref.getPipelineTaskName(); // e.g., "run-tests"
                    String errorMessage = taskCond.getMessage();
                    // Translate stage name to developer language
                    break;
                }
            }
        }
    }
}
```

**Stage name translation:** The `pipelineTaskName` from Tekton is a slug (e.g., `run-tests`, `build-image`). Translate to developer language by replacing hyphens with spaces and title-casing: `"run-tests"` → `"Run Tests"`, `"build-image"` → `"Build Image"`. Do this in the adapter, not in the service or DTO.

### Identifying the Current Running Stage (In-Progress Builds)

For builds with status "Building", determine which TaskRun is currently executing:

```java
if ("Building".equals(portalStatus) && childRefs != null) {
    for (ChildStatusReference ref : childRefs) {
        if ("TaskRun".equals(ref.getKind())) {
            io.fabric8.tekton.pipeline.v1.TaskRun taskRun = tektonClient.v1()
                    .taskRuns().inNamespace(namespace).withName(ref.getName()).get();
            if (taskRun != null && taskRun.getStatus() != null) {
                Condition taskCond = taskRun.getStatus().getConditions().stream()
                        .filter(c -> "Succeeded".equals(c.getType()))
                        .findFirst().orElse(null);
                if (taskCond != null && "Unknown".equals(taskCond.getStatus())) {
                    String currentStage = humanizeTaskName(ref.getPipelineTaskName());
                    break;
                }
            }
        }
    }
}
```

### Retrieving Build Logs via Kubernetes API

Tekton TaskRuns execute as Kubernetes Pods. Each TaskRun Step runs as a container in the Pod. To retrieve logs:

1. **Find Pods for the PipelineRun:** Use the label `tekton.dev/pipelineRun={buildId}` to find all Pods.
2. **For each Pod, get logs from each step container:** Step containers are named `step-{stepName}`.
3. **Concatenate with headers.**

```java
String getBuildLogs(String buildId, String namespace,
                    String clusterApiUrl, String clusterToken) {
    // Use KubernetesClient (NOT TektonClient) for pod logs
    try (KubernetesClient kubeClient = createClient(clusterApiUrl, clusterToken)) {
        List<Pod> pods = kubeClient.pods().inNamespace(namespace)
                .withLabel("tekton.dev/pipelineRun", buildId)
                .list().getItems();

        StringBuilder logs = new StringBuilder();
        for (Pod pod : pods) {
            String taskRunName = pod.getMetadata().getLabels()
                    .getOrDefault("tekton.dev/taskRun", "unknown");
            String pipelineTask = pod.getMetadata().getLabels()
                    .getOrDefault("tekton.dev/pipelineTask", "unknown");

            for (Container container : pod.getSpec().getContainers()) {
                if (container.getName().startsWith("step-")) {
                    String stepName = container.getName().substring(5);
                    logs.append("=== ").append(humanizeTaskName(pipelineTask))
                        .append(" / ").append(stepName).append(" ===\n");
                    try {
                        String podLog = kubeClient.pods().inNamespace(namespace)
                                .withName(pod.getMetadata().getName())
                                .inContainer(container.getName())
                                .getLog();
                        logs.append(podLog).append("\n");
                    } catch (Exception e) {
                        logs.append("[Log unavailable: ").append(e.getMessage())
                            .append("]\n");
                    }
                }
            }
        }
        return logs.toString();
    }
}
```

**Important:** Use `KubernetesClient.pods()` for log retrieval, not the TektonClient — Tekton CRDs do not have a log endpoint. Logs come from the underlying Kubernetes Pods.

**The response content type is `text/plain`, not JSON.** The BuildResource endpoint for logs must use `@Produces("text/plain")`.

### BuildDetailDto — `com.portal.build`

```java
package com.portal.build;

import java.time.Instant;

public record BuildDetailDto(
    String buildId,
    String status,
    Instant startedAt,
    Instant completedAt,
    String duration,
    String applicationName,
    String imageReference,
    String failedStageName,
    String errorSummary,
    String currentStage,
    String tektonDeepLink
) {}
```

**Field semantics:**
- `buildId` — PipelineRun name (e.g., `payment-svc-xk7f2`)
- `status` — portal vocabulary: "Building", "Passed", "Failed", "Cancelled", "Pending"
- `startedAt` — PipelineRun `.status.startTime` parsed to Instant
- `completedAt` — PipelineRun `.status.completionTime` parsed to Instant (null if still running)
- `duration` — human-readable duration string (e.g., "2m 34s"); computed from startedAt to completedAt (or now if running)
- `applicationName` — application name for context
- `imageReference` — container image reference from PipelineRun results (null if not Passed or not available)
- `failedStageName` — developer-language name of the failed task (null unless status is "Failed")
- `errorSummary` — error message from the failed TaskRun condition (null unless status is "Failed")
- `currentStage` — developer-language name of the currently running task (null unless status is "Building")
- `tektonDeepLink` — Tekton Dashboard URL for this PipelineRun

**The list endpoint returns `List<BuildSummaryDto>` (lighter weight), while the detail endpoint returns `BuildDetailDto` (richer).** BuildSummaryDto was defined in Story 4.1 as:

```java
public record BuildSummaryDto(
    String buildId,
    String status,
    Instant startedAt,
    String applicationName,
    String tektonDeepLink
) {}
```

Story 4.2 extends BuildSummaryDto with two additional fields for the list view: `completedAt` and `duration`. Either modify BuildSummaryDto to add optional fields or create a new list item DTO. **Recommended approach**: modify `BuildSummaryDto` to include `completedAt` (nullable) and `duration` (nullable) — adding nullable fields to an existing record is backward compatible for JSON serialization. Jackson omits nulls or includes them depending on config; either is fine.

Updated BuildSummaryDto:

```java
public record BuildSummaryDto(
    String buildId,
    String status,
    Instant startedAt,
    Instant completedAt,
    String duration,
    String applicationName,
    String tektonDeepLink
) {}
```

Update the `triggerBuild` method in both `TektonKubeAdapter` and `DevTektonAdapter` to pass `null` for `completedAt` and `duration` when creating a new build (since it just started).

### BuildResource Updates — Three New Endpoints

```java
@GET
public List<BuildSummaryDto> listBuilds(@PathParam("teamId") Long teamId,
                                         @PathParam("appId") Long appId) {
    return buildService.listBuilds(teamId, appId);
}

@GET
@Path("/{buildId}")
public BuildDetailDto getBuildDetail(@PathParam("teamId") Long teamId,
                                      @PathParam("appId") Long appId,
                                      @PathParam("buildId") String buildId) {
    return buildService.getBuildDetail(teamId, appId, buildId);
}

@GET
@Path("/{buildId}/logs")
@Produces("text/plain")
public String getBuildLogs(@PathParam("teamId") Long teamId,
                            @PathParam("appId") Long appId,
                            @PathParam("buildId") String buildId) {
    return buildService.getBuildLogs(teamId, appId, buildId);
}
```

**Notes:**
- List and detail endpoints use the class-level `@Produces(MediaType.APPLICATION_JSON)` from Story 4.1
- Logs endpoint overrides with `@Produces("text/plain")` — this is the first non-JSON endpoint in the portal
- Casbin `builds, read` permission covers GET endpoints — already in policy.csv (member+lead+admin)
- The `PermissionFilter` maps GET → `read` action for the `builds` resource

### BuildService Updates — Three New Methods

```java
public List<BuildSummaryDto> listBuilds(Long teamId, Long appId) {
    Application app = resolveTeamApplication(teamId, appId);
    Cluster buildCluster = resolveBuildCluster(app);
    ClusterCredential credential =
            credentialProvider.getCredentials(buildCluster.name, "portal");

    return tektonAdapter.listBuilds(
            app.name,
            app.buildNamespace,
            buildCluster.apiServerUrl,
            credential.token());
}

public BuildDetailDto getBuildDetail(Long teamId, Long appId, String buildId) {
    Application app = resolveTeamApplication(teamId, appId);
    Cluster buildCluster = resolveBuildCluster(app);
    ClusterCredential credential =
            credentialProvider.getCredentials(buildCluster.name, "portal");

    return tektonAdapter.getBuildDetail(
            buildId,
            app.buildNamespace,
            buildCluster.apiServerUrl,
            credential.token());
}

public String getBuildLogs(Long teamId, Long appId, String buildId) {
    Application app = resolveTeamApplication(teamId, appId);
    Cluster buildCluster = resolveBuildCluster(app);
    ClusterCredential credential =
            credentialProvider.getCredentials(buildCluster.name, "portal");

    return tektonAdapter.getBuildLogs(
            buildId,
            app.buildNamespace,
            buildCluster.apiServerUrl,
            credential.token());
}
```

**Extract shared logic into private helpers** to avoid duplicating the app lookup + cluster resolution + credential fetch across all four methods (including `triggerBuild` from Story 4.1):

```java
private Application resolveTeamApplication(Long teamId, Long appId) {
    Application app = Application.findById(appId);
    if (app == null || !app.teamId.equals(teamId)) {
        throw new NotFoundException();
    }
    if (app.buildClusterId == null || app.buildNamespace == null) {
        throw new IllegalStateException(
                "Application does not have build configuration — "
                + "it may have been onboarded before CI integration was available");
    }
    return app;
}

private Cluster resolveBuildCluster(Application app) {
    Cluster buildCluster = Cluster.findById(app.buildClusterId);
    if (buildCluster == null) {
        throw new IllegalStateException("Build cluster no longer exists");
    }
    return buildCluster;
}
```

Refactor the existing `triggerBuild` method to use these helpers too.

### Duration Computation

Compute human-readable duration from start to end (or now if running):

```java
private String computeDuration(Instant start, Instant end) {
    if (start == null) return null;
    Instant effectiveEnd = (end != null) ? end : Instant.now();
    long seconds = java.time.Duration.between(start, effectiveEnd).getSeconds();
    if (seconds < 60) return seconds + "s";
    long minutes = seconds / 60;
    long remainingSeconds = seconds % 60;
    if (minutes < 60) return minutes + "m " + remainingSeconds + "s";
    long hours = minutes / 60;
    long remainingMinutes = minutes % 60;
    return hours + "h " + remainingMinutes + "m";
}
```

Put this in `TektonKubeAdapter` as a private utility — duration computation happens at the adapter level where time parsing occurs.

### Parsing Tekton Timestamps

Tekton uses RFC 3339 timestamps (ISO 8601): `"2026-04-07T12:30:00Z"`. Parse with:

```java
Instant startTime = Instant.parse(run.getStatus().getStartTime());
```

Handle nulls — `startTime` and `completionTime` may be null if the PipelineRun hasn't started or hasn't completed.

### Error Handling in Adapter

All three new adapter methods follow the same error pattern as `triggerBuild`:

```java
try (KubernetesClient kubeClient = createClient(clusterApiUrl, clusterToken)) {
    // ... Tekton API calls ...
} catch (PortalIntegrationException e) {
    throw e;
} catch (Exception e) {
    throw new PortalIntegrationException("tekton", "<operation>",
            "Build information could not be retrieved — the build cluster is unreachable",
            deepLinkService.generateTektonLink(buildId).orElse(null), e);
}
```

Use operation names: `"listBuilds"`, `"getBuildDetail"`, `"getBuildLogs"`.

### What Already Exists — DO NOT Recreate

| Component | Location | Status |
|-----------|----------|--------|
| `TektonAdapter.java` | `com.portal.integration.tekton` | EXISTS — created in Story 4.1 (interface with `triggerBuild`) |
| `TektonKubeAdapter.java` | `com.portal.integration.tekton` | EXISTS — production implementation |
| `DevTektonAdapter.java` | `com.portal.integration.tekton` | EXISTS — dev-mode mock |
| `TektonConfig.java` | `com.portal.integration.tekton` | EXISTS — `provider()` + `dashboardUrl()` |
| `BuildService.java` | `com.portal.build` | EXISTS — with `triggerBuild()` |
| `BuildResource.java` | `com.portal.build` | EXISTS — with POST trigger endpoint |
| `BuildSummaryDto.java` | `com.portal.build` | EXISTS — record to be extended |
| `DeepLinkService` | `com.portal.deeplink` | EXISTS — `generateTektonLink(pipelineRunId)` |
| `SecretManagerCredentialProvider` | `com.portal.integration.secrets` | EXISTS — TTL cache over Vault |
| `PortalIntegrationException` | `com.portal.integration` | EXISTS — with system, operation, deepLink fields |
| `GlobalExceptionMapper` | `com.portal.common` | EXISTS — maps `PortalIntegrationException` → 502 |
| `PermissionFilter` | `com.portal.auth` | EXISTS — Casbin authorization check |
| `TeamContext` | `com.portal.auth` | EXISTS — request-scoped team+role bean |
| Casbin `builds, read` | `casbin/policy.csv` | EXISTS — member+lead+admin can read builds |
| `Application.buildClusterId` | `com.portal.application` | EXISTS — added in Story 4.1 |
| `Application.buildNamespace` | `com.portal.application` | EXISTS — added in Story 4.1 |
| `quarkus-kubernetes-client` | pom.xml | EXISTS — added in Story 4.1 |
| `tekton-client` (Fabric8) | pom.xml | EXISTS — added in Story 4.1 |

### What NOT to Build

- **No frontend components** — Story 4.3 creates the Builds page, Build table, expandable rows, and log viewer
- **No frontend API functions** — Story 4.3 creates `api/builds.ts` and `hooks/useBuilds.ts`
- **No frontend types** — Story 4.3 creates `types/build.ts`
- **No release creation** — Story 4.4 handles release creation from successful builds
- **No container registry integration** — Story 4.4 introduces `RegistryAdapter`
- **No database table for builds** — Builds are fetched live from Tekton API; the portal is not the source of truth
- **No WebSocket or SSE for live updates** — Story 4.3 uses manual refresh (no page reload, but user clicks refresh)
- **No build cancellation endpoint** — not in acceptance criteria for this story
- **No pagination** — MVP does not paginate; builds list returns all PipelineRuns in the namespace (typically 10-50)

### Anti-Patterns to Avoid

- **DO NOT** create a build database table — builds are ephemeral Tekton resources, fetched live every time
- **DO NOT** cache build status — every request goes to the Tekton API for fresh data (portal is not source of truth)
- **DO NOT** expose Tekton terminology in DTOs or API responses — "PipelineRun" → "Build", "TaskRun" → stage, "Step" → step. No Tekton CRD names in responses
- **DO NOT** use `v1beta1` Tekton classes — use `io.fabric8.tekton.pipeline.v1` exclusively (PipelineRun, TaskRun, etc.)
- **DO NOT** call `Cluster.findById()` or `Application.findById()` from the adapter — entity lookups in BuildService only; adapter receives resolved primitives
- **DO NOT** store credentials longer than the request — `clusterToken` is used once per adapter call
- **DO NOT** reuse KubernetesClient across requests — create per call with `try-with-resources`
- **DO NOT** return an empty 200 for a PipelineRun that doesn't exist — throw `NotFoundException` so it maps to 404
- **DO NOT** throw raw `KubernetesClientException` — catch and wrap in `PortalIntegrationException`
- **DO NOT** use `@Produces("text/plain")` on the class level — only on the logs endpoint method
- **DO NOT** assume the PipelineRun results field always contains an image URL — it's pipeline-dependent and may be absent

### Testing Strategy

**TektonKubeAdapterTest.java** — Extend with tests for new methods:

```java
class TektonKubeAdapterTest {
    // Test status translation:
    // - Running PipelineRun → "Building"
    // - Succeeded PipelineRun → "Passed"
    // - Failed PipelineRun → "Failed" with failedStageName + errorSummary
    // - Cancelled PipelineRun → "Cancelled"
    // - PipelineRun with no conditions → "Pending"

    // Test listBuilds:
    // - Returns builds sorted by startTime descending
    // - Filters by pipeline label
    // - Populates deep links

    // Test getBuildDetail:
    // - Passed build includes imageReference from results
    // - Failed build includes failedStageName and errorSummary
    // - Building build includes currentStage
    // - Non-existent buildId → null or exception

    // Test getBuildLogs:
    // - Retrieves logs from all step containers across all TaskRun pods
    // - Handles missing pod gracefully
    // - Concatenates logs with task/step headers

    // Test duration computation:
    // - < 60s → "42s"
    // - >= 60s → "2m 34s"
    // - >= 60m → "1h 5m"
}
```

**Testability:** Use the `createClient()` factory method pattern from Story 4.1 (package-private method that tests override to return mocked clients).

**BuildServiceTest.java** — Extend with tests:

```java
@QuarkusTest
class BuildServiceTest {
    // Test listBuilds:
    // - Returns adapter result for valid team+app
    // - Throws NotFoundException for cross-team app
    // - Throws IllegalStateException when build config missing

    // Test getBuildDetail:
    // - Returns detail for valid team+app+buildId
    // - Throws NotFoundException for cross-team app

    // Test getBuildLogs:
    // - Returns logs string for valid team+app+buildId
    // - Throws NotFoundException for cross-team app
}
```

**BuildResourceIT.java** — Extend with integration tests:

```java
@QuarkusTest
class BuildResourceIT {
    @Test
    @TestSecurity(user = "test-user")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "1"),
        @Claim(key = "role", value = "member")
    })
    void listBuildsReturns200() {
        // given().when().get("/api/v1/teams/1/applications/{appId}/builds")
        //   .then().statusCode(200)
        //   .body("$.size()", greaterThanOrEqualTo(0));
    }

    @Test
    void getBuildDetailReturns200() {
        // GET /api/v1/teams/1/applications/{appId}/builds/{buildId}
        // Verify: status, startedAt, applicationName, tektonDeepLink
    }

    @Test
    void getBuildLogsReturns200WithTextPlain() {
        // GET /api/v1/teams/1/applications/{appId}/builds/{buildId}/logs
        // Verify: content-type is text/plain
        // Verify: body contains log output
    }

    @Test
    void getBuildDetailReturns404ForOtherTeamApp() {
        // App belongs to team 2, request from team 1 → 404
    }

    @Test
    void listBuildsReturns404ForNonExistentApp() {
        // Non-existent appId → 404
    }
}
```

### Previous Story Intelligence

**Story 4.1 (Tekton Adapter & Pipeline Triggering):**
- Established the `TektonAdapter` → `TektonKubeAdapter` / `DevTektonAdapter` pattern with `@IfBuildProperty` switching
- `createClient()` factory method enables testability — override in test subclass
- `DeepLinkService.generateTektonLink()` returns `Optional<String>` — pass `.orElse(null)` to DTOs
- PipelineRef.name = appName convention — reuse for label filtering
- `try-with-resources` on every KubernetesClient — connection pools must be closed
- Error messages in developer language — "Build could not be started" pattern

**Epic 3 Retrospective (2026-04-07) — Action Items for Epic 4:**
- Every story must leave the test suite at **0 failures** — no carve-outs
- When a component's API changes, update **ALL consumers** including tests in the same story
- Pre-flight test gate enforced — verify all tests pass before starting work
- Add gotchas to `project-context.md` immediately when discovered

**Epic 4 Readiness Assessment:**
- 314+ backend tests passing, 186/186 frontend tests passing
- First non-JSON API response in this story (build logs as `text/plain`)
- First read-only Kubernetes API usage (previous was write-only: create PipelineRun)

### Git Intelligence

Recent commit patterns:
- `861aa61` — Epic 3 retro: test fixes, pre-flight gate
- `661c40b` — Story 3.3: Deep links on env chain
- `0b23efb` — Stories 3.1/3.2: DeepLinkService, DevSpaces, Vault
- Each story is one atomic commit with all files
- Convention: `Story X.Y: <title>` as commit message

### Data Flow for Build List

```
GET /api/v1/teams/{teamId}/applications/{appId}/builds
  → PermissionFilter: Casbin check (builds, read)
  → TeamContextFilter: extract team from JWT
  → BuildResource.listBuilds(teamId, appId)
  → BuildService.listBuilds(teamId, appId)
    → Application.findById(appId) — verify team ownership + build config
    → Cluster.findById(app.buildClusterId)
    → SecretManagerCredentialProvider.getCredentials(cluster.name, "portal")
    → TektonAdapter.listBuilds(appName, buildNamespace, clusterApiUrl, token)
      → KubernetesClient → TektonClient → list PipelineRuns with label filter
      → Translate each PipelineRun status → BuildSummaryDto
      → Sort by startTime descending
      → Populate tektonDeepLink via DeepLinkService
  → 200 OK: [{ buildId, status, startedAt, completedAt, duration, applicationName, tektonDeepLink }]
```

### Data Flow for Build Logs

```
GET /api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs
  → PermissionFilter: Casbin check (builds, read)
  → BuildResource.getBuildLogs(teamId, appId, buildId)
  → BuildService.getBuildLogs(teamId, appId, buildId)
    → (same app/cluster/credential resolution)
    → TektonAdapter.getBuildLogs(buildId, buildNamespace, clusterApiUrl, token)
      → KubernetesClient → list Pods with label tekton.dev/pipelineRun={buildId}
      → For each Pod: iterate step containers, get log for each
      → Concatenate with headers: "=== Task Name / step-name ==="
  → 200 OK: text/plain body with concatenated logs
```

### Project Structure Notes

**Modified backend files (extend from Story 4.1):**
```
src/main/java/com/portal/integration/tekton/TektonAdapter.java      (add 3 methods)
src/main/java/com/portal/integration/tekton/TektonKubeAdapter.java   (implement 3 methods + helpers)
src/main/java/com/portal/integration/tekton/DevTektonAdapter.java    (implement 3 mock methods)
src/main/java/com/portal/build/BuildService.java                     (add 3 methods + refactor helpers)
src/main/java/com/portal/build/BuildResource.java                    (add 3 GET endpoints)
src/main/java/com/portal/build/BuildSummaryDto.java                  (add completedAt + duration fields)
```

**New backend files:**
```
src/main/java/com/portal/build/BuildDetailDto.java
```

**Modified test files:**
```
src/test/java/com/portal/integration/tekton/TektonKubeAdapterTest.java  (extend)
src/test/java/com/portal/build/BuildServiceTest.java                     (extend)
src/test/java/com/portal/build/BuildResourceIT.java                      (extend)
```

### References

- [Source: planning-artifacts/epics.md § Story 4.2 (line 1123-1167)] — Full acceptance criteria
- [Source: planning-artifacts/epics.md § Story 4.1 (line 1088-1121)] — Predecessor story context
- [Source: planning-artifacts/epics.md § Story 4.3 (line 1168-1215)] — Downstream consumer (builds page)
- [Source: planning-artifacts/architecture.md § Integration Adapters (line 1064)] — TektonAdapter: Kubernetes API, Phase 3
- [Source: planning-artifacts/architecture.md § API Resource Structure (line 468-481)] — `/builds` endpoint pattern
- [Source: planning-artifacts/architecture.md § Data Flow (line 1095-1113)] — Service → Adapter flow
- [Source: planning-artifacts/architecture.md § Project Structure (line 809-813)] — `build/` package layout
- [Source: planning-artifacts/architecture.md § Project Structure (line 872-877)] — `integration/tekton/` layout
- [Source: planning-artifacts/architecture.md § Parallel Integration Calls (line 500)] — CompletableFuture pattern
- [Source: planning-artifacts/architecture.md § Error Response Format (line 485-496)] — Standardized error JSON
- [Source: planning-artifacts/ux-design-specification.md § Direction H: Build & Release (line 553-577)] — UX patterns for build table
- [Source: planning-artifacts/ux-design-specification.md § Status Badge Vocabulary (line 233-236)] — Universal status indicators
- [Source: planning-artifacts/ux-design-specification.md § Error States (line 1203-1212)] — Failed build display requirements
- [Source: planning-artifacts/ux-design-specification.md § Deep Links (line 1259-1265)] — Tekton deep link scoping
- [Source: project-context.md § Domain Language Translation] — PipelineRun → Build, TaskRun → stage
- [Source: project-context.md § Anti-Patterns] — Adapter throws PortalIntegrationException
- [Source: project-context.md § Testing Rules] — @InjectMock for adapter mocking
- [Source: project-context.md § Framework-Specific Rules] — Resource → Service → Adapter
- [Source: project-context.md § Data Model] — Portal is NOT source of truth; builds fetched live
- [Source: implementation-artifacts/4-1-tekton-adapter-pipeline-triggering.md] — Story 4.1 complete context
- [Source: implementation-artifacts/epic-1-retro-2026-04-06.md § Action Items] — Zero-failure test gate
- [Source: tekton.dev/docs/pipelines/pipelineruns/] — PipelineRun status conditions model
- [Source: Fabric8 kubernetes-client docs] — Pod log retrieval via `pods().getLog()` and `inContainer()`

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus-high-thinking (via Cursor)

### Debug Log References

- Tekton Condition type: `io.fabric8.knative.pkg.apis.Condition` (NOT `io.fabric8.kubernetes.api.model.Condition`) — Fabric8 Tekton model uses KNative conditions for PipelineRun/TaskRun status
- PipelineRunList.getItems() returns immutable list — must wrap in `new ArrayList<>()` before sorting
- PermissionFilter `isId()` heuristic requires build IDs to be >8 chars and contain hyphens to be recognized as IDs; added "logs" to ACTION_SEGMENTS so `/builds/{buildId}/logs` resolves resource as "builds" not "logs"
- BuildSummaryDto extended with `completedAt` (nullable) and `duration` (nullable) — backward compatible; updated all existing callers including tests
- Refactored BuildService with `resolveTeamApplication()` and `resolveBuildCluster()` private helpers; changed exception from `IllegalArgumentException` to `IllegalStateException` per story spec

### Completion Notes List

- All 10 tasks completed successfully
- 372 tests pass (0 failures, 0 errors) — up from baseline after adding 34 new tests
- Implemented 3 new TektonAdapter interface methods: listBuilds, getBuildDetail, getBuildLogs
- Created BuildDetailDto record with full build detail fields
- Implemented PipelineRun status translation: Running→Building, Succeeded→Passed, Failed→Failed, Cancelled→Cancelled, null→Pending
- TektonKubeAdapter implements all 3 methods with full Kubernetes/Tekton API integration
- DevTektonAdapter provides deterministic mock data covering all build states
- BuildService delegates with team-scoped app/cluster resolution
- BuildResource exposes 3 new GET endpoints including first text/plain endpoint (logs)
- Duration computation utility: seconds, minutes+seconds, hours+minutes formats
- Task name humanization: hyphen-separated slugs → Title Case
- Error handling: NotFoundException for missing builds, PortalIntegrationException for cluster failures

### File List

**New files:**
- developer-portal/src/main/java/com/portal/build/BuildDetailDto.java

**Modified files:**
- developer-portal/src/main/java/com/portal/integration/tekton/TektonAdapter.java
- developer-portal/src/main/java/com/portal/integration/tekton/TektonKubeAdapter.java
- developer-portal/src/main/java/com/portal/integration/tekton/DevTektonAdapter.java
- developer-portal/src/main/java/com/portal/build/BuildService.java
- developer-portal/src/main/java/com/portal/build/BuildResource.java
- developer-portal/src/main/java/com/portal/build/BuildSummaryDto.java
- developer-portal/src/main/java/com/portal/auth/PermissionFilter.java
- developer-portal/src/test/java/com/portal/integration/tekton/TektonKubeAdapterTest.java
- developer-portal/src/test/java/com/portal/build/BuildServiceTest.java
- developer-portal/src/test/java/com/portal/build/BuildResourceIT.java
