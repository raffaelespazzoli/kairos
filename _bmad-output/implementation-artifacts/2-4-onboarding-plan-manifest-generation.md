# Story 2.4: Onboarding Plan & Manifest Generation

Status: done

## Story

As a developer,
I want to see exactly what infrastructure will be provisioned before I confirm onboarding,
So that I can review the plan with confidence and understand the topology of my application's environments.

## Acceptance Criteria

1. **Provisioning plan assembly**
   - **Given** the contract validation has passed and environments have been detected
   - **When** the wizard advances to step 3 (provisioning plan preview)
   - **Then** the OnboardingService assembles a provisioning plan showing:
     - Namespaces to be created: one per environment + one for build, each showing the target cluster
     - Namespace naming convention: `<team>-<app>-<env>` (e.g., `payments-payment-svc-dev`)
     - ArgoCD Applications to be created: one build + one per environment
     - The environment promotion chain in order (e.g., dev → qa → prod)

2. **Provisioning plan display with cluster selection**
   - **Given** the provisioning plan is displayed
   - **When** the developer reviews it
   - **Then** each namespace shows its name and the cluster it targets (e.g., `payments-payment-svc-dev → ocp-dev-01`)
   - **And** the developer can select which registered cluster each environment maps to
   - **And** the build namespace shows its target cluster separately

3. **Namespace YAML generation via Qute**
   - **Given** the ManifestGenerator uses Qute templates
   - **When** generating Namespace YAML
   - **Then** each Namespace object includes labels: `team: <team>`, `app: <app>`, `env: <env>`, `size: default`
   - **And** the YAML conforms to the GitOps contract: `<cluster>/<namespace>/namespace.yaml`

4. **ArgoCD build application manifest**
   - **Given** the ManifestGenerator generates ArgoCD Application manifests
   - **When** generating for a build ArgoCD Application
   - **Then** it sources from the app repo's `.helm/build/` chart with `values-build.yaml`
   - **And** targets the build namespace on the assigned cluster
   - **And** the file path conforms to: `<cluster>/<namespace>/argocd-app-build.yaml`

5. **ArgoCD run application manifests per environment**
   - **Given** the ManifestGenerator generates ArgoCD Application manifests
   - **When** generating for a run ArgoCD Application per environment
   - **Then** it sources from the app repo's `.helm/run/` chart with `values-run-<env>.yaml`
   - **And** targets the environment namespace on the assigned cluster
   - **And** the file path conforms to: `<cluster>/<namespace>/argocd-app-run-<env>.yaml`

6. **Frontend plan preview**
   - **Given** the provisioning plan is displayed on the frontend
   - **When** reviewing the plan
   - **Then** the total count of resources is visible (e.g., "4 namespaces, 4 ArgoCD applications")
   - **And** a "Confirm & Create PR" primary button is available
   - **And** a "Back" secondary button returns to the contract validation step

7. **Promotion chain ordering**
   - **Given** the default promotion chain is derived from detected values-run files
   - **When** the plan is assembled
   - **Then** environments are ordered by convention (dev < qa < staging < prod) not alphabetically
   - **And** promotion_order is assigned sequentially (0, 1, 2, ...)
   - **And** the chain is stored as data, not hardcoded

## Tasks / Subtasks

- [x] Task 1: Add Qute extension to pom.xml (AC: #3, #4, #5)
  - [x] The `quarkus-qute` extension is already bundled with Quarkus but must be declared as a dependency in `pom.xml` to enable the template engine: `<artifactId>quarkus-qute</artifactId>`
  - [x] Verify Qute template resolution works from `src/main/resources/templates/` (Quarkus default location)

- [x] Task 2: Create Qute templates for manifest generation (AC: #3, #4, #5)
  - [x] Create `src/main/resources/templates/gitops/namespace.yaml` — Namespace manifest
  - [x] Create `src/main/resources/templates/gitops/argocd-app-build.yaml` — ArgoCD Application for build
  - [x] Create `src/main/resources/templates/gitops/argocd-app-run.yaml` — ArgoCD Application for run per env
  - [x] Templates produce valid Kubernetes YAML conforming to the GitOps contract

- [x] Task 3: Create OnboardingPlan DTOs (AC: #1, #6)
  - [x] Create `OnboardingPlanRequest.java` record in `com.portal.onboarding` with fields: `String gitRepoUrl`, `String runtimeType`, `List<String> detectedEnvironments`, `Map<String, Long> environmentClusterMap` (envName → clusterId), `Long buildClusterId`
  - [x] Create `PlannedNamespace.java` record in `com.portal.onboarding` with fields: `String name`, `String clusterName`, `String environmentName`, `boolean isBuild`
  - [x] Create `PlannedArgoCdApp.java` record in `com.portal.onboarding` with fields: `String name`, `String clusterName`, `String namespace`, `String chartPath`, `String valuesFile`, `boolean isBuild`
  - [x] Create `OnboardingPlanResult.java` record in `com.portal.onboarding` with fields: `String appName`, `String teamName`, `List<PlannedNamespace> namespaces`, `List<PlannedArgoCdApp> argoCdApps`, `List<String> promotionChain`, `Map<String, String> generatedManifests` (filePath → yamlContent)

- [x] Task 4: Create ManifestGenerator service (AC: #3, #4, #5)
  - [x] Create `ManifestGenerator.java` in `com.portal.gitops`, `@ApplicationScoped`
  - [x] Inject Qute `Engine` or use `@Location` template injection for each template
  - [x] Method: `String generateNamespaceYaml(String namespace, String team, String app, String env)` — renders namespace.yaml
  - [x] Method: `String generateBuildArgoCdAppYaml(String appName, String namespace, String cluster, String gitRepoUrl)` — renders argocd-app-build.yaml
  - [x] Method: `String generateRunArgoCdAppYaml(String appName, String namespace, String cluster, String gitRepoUrl, String env)` — renders argocd-app-run.yaml
  - [x] Method: `Map<String, String> generateAllManifests(OnboardingPlanResult plan, String gitRepoUrl, Map<String, String> clusterApiUrls)` — returns map of filePath → yamlContent for all namespaces and ArgoCD apps

- [x] Task 5: Create OnboardingService (AC: #1, #2, #7)
  - [x] Create `OnboardingService.java` in `com.portal.onboarding`, `@ApplicationScoped`
  - [x] Inject `ClusterService` (for validating cluster IDs), `ManifestGenerator`, `TeamContext`
  - [x] Method: `OnboardingPlanResult buildPlan(String appName, OnboardingPlanRequest request)` — assembles the provisioning plan:
    1. Validate all cluster IDs exist via ClusterService
    2. Order environments using convention ordering (dev=0, qa=1, staging=2, prod=3, others alphabetically after)
    3. Build namespace list: one per env (`<team>-<app>-<env>`) + one for build (`<team>-<app>-build`)
    4. Build ArgoCD app list: one build + one per env
    5. Generate all manifests via ManifestGenerator
    6. Return OnboardingPlanResult

- [x] Task 6: Add plan endpoint to OnboardingResource (AC: #1, #6)
  - [x] Add `POST /api/v1/teams/{teamId}/applications/onboard/plan` to `OnboardingResource.java`
  - [x] Accepts `@Valid OnboardingPlanRequest`, returns `OnboardingPlanResult` as 200 JSON
  - [x] Verify `teamContext.getTeamId()` matches `{teamId}` — return 404 if mismatch
  - [x] Added "plan" to `ACTION_SEGMENTS` in `PermissionFilter.java` so PermissionFilter correctly resolves resource as "applications" and action as "onboard"

- [x] Task 7: Create frontend types for onboarding plan (AC: #6)
  - [x] Add to `src/main/webui/src/types/onboarding.ts`: `PlannedNamespace`, `PlannedArgoCdApp`, `OnboardingPlanRequest`, `OnboardingPlanResult`

- [x] Task 8: Create frontend API function for plan (AC: #6)
  - [x] Add to `src/main/webui/src/api/onboarding.ts`: `buildPlan(teamId: string, request: OnboardingPlanRequest): Promise<OnboardingPlanResult>`

- [x] Task 9: Create ProvisioningPlanPreview component (AC: #2, #6)
  - [x] Create `src/main/webui/src/components/onboarding/ProvisioningPlanPreview.tsx`
  - [x] Props: `plan: OnboardingPlanResult`, `clusters: ClusterDto[]`, `onClusterChange: (envName: string, clusterId: number) => void`, `onBuildClusterChange: (clusterId: number) => void`
  - [x] Header section: app name, total resource count ("N namespaces, M ArgoCD applications")
  - [x] Namespaces section using PF6 `DescriptionList`: each namespace with name → cluster assignment
  - [x] Cluster selection: PF6 `Select` dropdown per environment + build to choose target cluster
  - [x] ArgoCD applications section: list of planned ArgoCD apps with chart path and values file
  - [x] Promotion chain section: visual display of the environment order (dev → qa → prod)

- [x] Task 10: Update OnboardingWizardPage step 3 (AC: #1, #2, #6)
  - [x] Replace step 3 placeholder in `OnboardingWizardPage.tsx` with functional ProvisioningPlanPreview
  - [x] After contract validation passes (step 2), carry forward: `gitRepoUrl`, `runtimeType`, `detectedEnvironments`
  - [x] On step 3 mount: fetch clusters list via `fetchClusters()`, initialize default cluster assignments
  - [x] Manage state: `plan`, `clusterAssignments`, `buildClusterId`, `isPlanLoading`
  - [x] When cluster assignment changes: re-call plan API with updated cluster map to regenerate manifests
  - [x] "Confirm & Create PR" button (step 4) — disabled in this story, enabled in Story 2.5
  - [x] "Back" button returns to step 2

- [x] Task 11: Write ManifestGenerator unit tests (AC: #3, #4, #5)
  - [x] Create `ManifestGeneratorTest.java` in `src/test/java/com/portal/gitops/`
  - [x] Test `generateNamespaceYaml`: verify output contains correct metadata.name, labels (team, app, env, size)
  - [x] Test `generateBuildArgoCdAppYaml`: verify output references `.helm/build/` chart, `values-build.yaml`, correct namespace and cluster
  - [x] Test `generateRunArgoCdAppYaml`: verify output references `.helm/run/` chart, `values-run-<env>.yaml`, correct namespace and cluster
  - [x] Test YAML validity: parse output with a YAML parser to confirm well-formed YAML
  - [x] Test `generateAllManifests`: verify correct file paths in returned map (`<cluster>/<namespace>/namespace.yaml`, etc.)

- [x] Task 12: Write OnboardingService unit tests (AC: #1, #7)
  - [x] Create `OnboardingServiceTest.java` in `src/test/java/com/portal/onboarding/`
  - [x] Mock `ClusterService`, `ManifestGenerator`, `TeamContext`
  - [x] Test `buildPlan`: verify namespace naming convention `<team>-<app>-<env>`
  - [x] Test promotion chain ordering: given ["prod", "dev", "qa"] → ordered to ["dev", "qa", "prod"] with promotion_order 0, 1, 2
  - [x] Test build namespace included: `<team>-<app>-build`
  - [x] Test ArgoCD app count: 1 build + N environments
  - [x] Test invalid cluster ID: verify error thrown when referencing non-existent cluster

- [x] Task 13: Write plan endpoint integration test (AC: #1, #6)
  - [x] Create `OnboardingPlanIT.java` in `src/test/java/com/portal/onboarding/`
  - [x] `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` with member role
  - [x] Test POST `/api/v1/teams/{teamId}/applications/onboard/plan` with valid request → 200 + OnboardingPlanResult JSON
  - [x] Verify response contains expected namespaces, ArgoCD apps, promotion chain
  - [x] Test missing cluster ID → 404
  - [x] Test cross-team access → 404

- [x] Task 14: Write frontend component tests (AC: #2, #6, #9)
  - [x] Create `src/main/webui/src/components/onboarding/ProvisioningPlanPreview.test.tsx`
  - [x] Test plan renders namespace list with cluster names
  - [x] Test resource count displayed correctly
  - [x] Test cluster dropdown triggers onClusterChange callback
  - [x] Test promotion chain displayed in correct order
  - [x] Create or extend `src/main/webui/src/routes/OnboardingWizardPage.test.tsx`
  - [x] Test step 3 renders ProvisioningPlanPreview when navigated to

## Dev Notes

### Hard Dependencies

This story **requires** Stories 2.1, 2.2, and 2.3 to be implemented first:
- **Story 2.1** — Application and Environment entities (data model for persistence in 2.5, but namespace naming uses team/app conventions)
- **Story 2.2** — GitProvider interface (not directly used in 2.4, but OnboardingService will use it in 2.5 for PR creation)
- **Story 2.3** — ContractValidator, OnboardingResource, ContractValidationResult with `detectedEnvironments` and `runtimeType`; OnboardingWizardPage with steps 1–2 functional

### Package: `com.portal.gitops`

This is a NEW package (only `package-info.java` exists). Create:

```
com.portal.gitops/
├── ManifestGenerator.java
└── (OnboardingPrBuilder.java — Story 2.5, NOT this story)
```

### Package: `com.portal.onboarding` — Extensions

Story 2.3 created the base `com.portal.onboarding` package. This story adds:

```
com.portal.onboarding/
├── OnboardingResource.java        # MODIFY — add plan endpoint
├── OnboardingService.java         # NEW
├── OnboardingPlanRequest.java     # NEW
├── OnboardingPlanResult.java      # NEW
├── PlannedNamespace.java          # NEW
├── PlannedArgoCdApp.java          # NEW
├── ContractValidator.java         # EXISTS (from 2.3)
├── ContractValidationResult.java  # EXISTS (from 2.3)
├── ContractCheck.java             # EXISTS (from 2.3)
└── ValidateRepoRequest.java       # EXISTS (from 2.3)
```

### REST Endpoint Design — Plan Endpoint

**Endpoint:** `POST /api/v1/teams/{teamId}/applications/onboard/plan`

**PermissionFilter integration:** The current PermissionFilter extracts the resource from the URL path. The path `teams/{teamId}/applications/onboard/plan`:
- `ACTION_SEGMENTS` includes "onboard" (added in Story 2.3)
- The segment "plan" comes AFTER "onboard" — PermissionFilter finds "onboard" first
- Resource extraction: the filter finds `applications` as the last non-ID, non-action segment
- Casbin check: `(member, applications, onboard)` → **ALLOWED** per policy.csv

**Verify this works by checking PermissionFilter's segment-walking logic.** If "plan" after "onboard" causes issues, use an alternative: make the plan data part of the same `/onboard` endpoint by accepting a `phase` field in the request body, or use a query parameter `?phase=plan`. The simplest approach is a sub-path `/onboard/plan` — test it.

**Request body:**
```java
public record OnboardingPlanRequest(
    @NotBlank String gitRepoUrl,
    @NotBlank String appName,
    @NotBlank String runtimeType,
    @NotNull List<String> detectedEnvironments,
    @NotNull Map<String, Long> environmentClusterMap,
    @NotNull Long buildClusterId
) {}
```

**Response:** `OnboardingPlanResult` as JSON (200 OK).

### Qute Template Setup

Qute templates must be placed in `src/main/resources/templates/` for Quarkus to discover them. Use the `gitops/` subdirectory for organization.

**Template injection pattern:**

```java
@ApplicationScoped
public class ManifestGenerator {

    @Inject
    Engine engine;

    public String generateNamespaceYaml(String namespace, String team, String app, String env) {
        Template template = engine.getTemplate("gitops/namespace.yaml");
        return template.data("namespace", namespace)
                       .data("team", team)
                       .data("app", app)
                       .data("env", env)
                       .data("size", "default")
                       .render();
    }
}
```

Alternatively, use `@Location` annotation for type-safe template injection:

```java
@Location("gitops/namespace.yaml")
Template namespaceTemplate;
```

**File naming:** Quarkus Qute resolves templates by path without the `.qute.yaml` suffix in code. A file named `namespace.yaml.qute.yaml` or just `namespace.yaml` in the templates directory is resolved by `engine.getTemplate("gitops/namespace.yaml")`. Use the `.qute.yaml` extension for IDE syntax highlighting of the Qute expressions within YAML.

### Qute Template Content — Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: {namespace}
  labels:
    team: {team}
    app: {app}
    env: {env}
    size: {size}
```

In Qute syntax, use `{variableName}` for simple variable substitution. Ensure no trailing whitespace or extra newlines that would produce invalid YAML.

### Qute Template Content — ArgoCD Application (Build)

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: {appName}-build
  namespace: argocd
spec:
  project: default
  source:
    repoURL: {gitRepoUrl}
    targetRevision: HEAD
    path: .helm/build
    helm:
      valueFiles:
        - values-build.yaml
  destination:
    server: {clusterApiUrl}
    namespace: {namespace}
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

### Qute Template Content — ArgoCD Application (Run per Environment)

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: {appName}-run-{env}
  namespace: argocd
spec:
  project: default
  source:
    repoURL: {gitRepoUrl}
    targetRevision: HEAD
    path: .helm/run
    helm:
      valueFiles:
        - values-run-{env}.yaml
  destination:
    server: {clusterApiUrl}
    namespace: {namespace}
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

### ManifestGenerator — File Path Convention

The generated manifests must follow the infra repo GitOps contract:

```
<cluster>/<namespace>/namespace.yaml
<cluster>/<namespace>/argocd-app-build.yaml        (for build)
<cluster>/<namespace>/argocd-app-run-<env>.yaml     (for each environment)
```

Example for a "payments" team, "payment-svc" app, with dev on `ocp-dev-01` and build on `ocp-dev-01`:

```
ocp-dev-01/payments-payment-svc-dev/namespace.yaml
ocp-dev-01/payments-payment-svc-dev/argocd-app-run-dev.yaml
ocp-dev-01/payments-payment-svc-build/namespace.yaml
ocp-dev-01/payments-payment-svc-build/argocd-app-build.yaml
ocp-qa-01/payments-payment-svc-qa/namespace.yaml
ocp-qa-01/payments-payment-svc-qa/argocd-app-run-qa.yaml
ocp-prod-01/payments-payment-svc-prod/namespace.yaml
ocp-prod-01/payments-payment-svc-prod/argocd-app-run-prod.yaml
```

The `generateAllManifests` method returns `Map<String, String>` where key = file path, value = YAML content. This map is passed to `GitProvider.commitFiles()` in Story 2.5.

### OnboardingService — Environment Ordering Convention

The epics specify: "environments are ordered alphabetically by default (dev < prod < qa → reordered to dev, qa, prod by convention)." This means alphabetical ordering is NOT correct — the ordering must follow deployment convention:

```java
private static final Map<String, Integer> ENV_ORDER = Map.of(
    "dev", 0,
    "development", 0,
    "qa", 1,
    "test", 1,
    "staging", 2,
    "stage", 2,
    "uat", 3,
    "preprod", 4,
    "prod", 5,
    "production", 5
);

private List<String> orderEnvironments(List<String> envNames) {
    return envNames.stream()
        .sorted(Comparator.comparingInt(
            (String env) -> ENV_ORDER.getOrDefault(env.toLowerCase(), 100))
            .thenComparing(String::compareToIgnoreCase))
        .toList();
}
```

Unrecognized environment names sort after all known ones, alphabetically among themselves.

### OnboardingService — Namespace Naming

Convention: `<team>-<app>-<env>` using lowercase, hyphen-separated.

```java
private String buildNamespaceName(String teamName, String appName, String envOrBuild) {
    return String.join("-",
        teamName.toLowerCase(),
        appName.toLowerCase(),
        envOrBuild.toLowerCase()
    );
}
```

Examples:
- `payments-payment-svc-dev`
- `payments-payment-svc-build`
- `payments-payment-svc-qa`
- `payments-payment-svc-prod`

### Cluster Validation

The OnboardingService must validate that all cluster IDs in the request actually exist. Inject `ClusterService` or query `Cluster` entity directly:

```java
private Cluster resolveCluster(Long clusterId) {
    Cluster cluster = Cluster.findById(clusterId);
    if (cluster == null) {
        throw new NotFoundException("Cluster with ID " + clusterId + " not found");
    }
    return cluster;
}
```

The ArgoCD Application manifests need the cluster's `apiServerUrl` for the `destination.server` field. Retrieve it from the `Cluster` entity.

### Frontend — ProvisioningPlanPreview Component

Located at `src/main/webui/src/components/onboarding/ProvisioningPlanPreview.tsx`.

**Props interface:**
```tsx
interface ProvisioningPlanPreviewProps {
  plan: OnboardingPlanResult;
  clusters: ClusterDto[];
  onClusterChange: (envName: string, clusterId: number) => void;
  onBuildClusterChange: (clusterId: number) => void;
}
```

**PF6 components used:**
- `Card` — container for the plan sections
- `DescriptionList` / `DescriptionListGroup` — namespace → cluster display
- `Label` — resource counts
- `Select` / `SelectOption` (PF6 `MenuToggle` + `SelectList` pattern) — cluster dropdown per environment
- `List` / `ListItem` — ArgoCD application list
- `Icon` + `ArrowRightIcon` — promotion chain flow visualization

**Cluster dropdown pattern (PF6):**
PF6 replaced the legacy `Select` with a composable pattern using `MenuToggle` + `Select` + `SelectOption` + `SelectList`. Follow the PF6 documentation for the current API:

```tsx
import { Select, SelectOption, SelectList, MenuToggle } from '@patternfly/react-core';
```

The dropdown shows registered cluster names. When changed, `onClusterChange(envName, clusterId)` is called, which triggers a re-build of the plan with updated cluster mappings.

### Frontend — OnboardingWizardPage Step 3 State Flow

The wizard carries state across steps:

```
Step 1 (repo URL) → repoUrl
Step 2 (validation) → validationResult { runtimeType, detectedEnvironments }
Step 3 (plan) → plan, clusterAssignments, buildClusterId
Step 4 (PR creation) → Story 2.5
Step 5 (complete) → Story 2.5
```

Step 3 flow:
1. On entering step 3, fetch available clusters: `GET /api/v1/admin/clusters`
2. Initialize default cluster assignments (first available cluster for all envs + build)
3. Call `POST /api/v1/teams/{teamId}/applications/onboard/plan` with the initial assignments
4. Display `ProvisioningPlanPreview` with the plan
5. When user changes a cluster assignment → re-call plan API with updated map
6. "Confirm & Create PR" navigates to step 4 (disabled/placeholder in this story)

**Cluster fetching:** Reuse existing `fetchClusters()` from `api/clusters.ts`.

### Frontend — Types to Add to `types/onboarding.ts`

```tsx
export interface PlannedNamespace {
  name: string;
  clusterName: string;
  environmentName: string;
  isBuild: boolean;
}

export interface PlannedArgoCdApp {
  name: string;
  clusterName: string;
  namespace: string;
  chartPath: string;
  valuesFile: string;
  isBuild: boolean;
}

export interface OnboardingPlanRequest {
  gitRepoUrl: string;
  appName: string;
  runtimeType: string;
  detectedEnvironments: string[];
  environmentClusterMap: Record<string, number>;
  buildClusterId: number;
}

export interface OnboardingPlanResult {
  appName: string;
  teamName: string;
  namespaces: PlannedNamespace[];
  argoCdApps: PlannedArgoCdApp[];
  promotionChain: string[];
  generatedManifests: Record<string, string>;
}
```

### Frontend — API Function to Add to `api/onboarding.ts`

```tsx
export function buildPlan(
  teamId: string,
  request: OnboardingPlanRequest
): Promise<OnboardingPlanResult> {
  return apiFetch<OnboardingPlanResult>(
    `/api/v1/teams/${teamId}/applications/onboard/plan`,
    {
      method: 'POST',
      body: JSON.stringify(request),
    }
  );
}
```

### Backend Unit Test — ManifestGenerator

```java
@QuarkusTest
class ManifestGeneratorTest {

    @Inject
    ManifestGenerator manifestGenerator;

    @Test
    void generateNamespaceYamlContainsLabels() {
        String yaml = manifestGenerator.generateNamespaceYaml(
            "payments-payment-svc-dev", "payments", "payment-svc", "dev");
        assertThat(yaml).contains("name: payments-payment-svc-dev");
        assertThat(yaml).contains("team: payments");
        assertThat(yaml).contains("app: payment-svc");
        assertThat(yaml).contains("env: dev");
        assertThat(yaml).contains("size: default");
    }
}
```

**Note:** ManifestGenerator depends on the Qute engine which requires the Quarkus runtime. Either use `@QuarkusTest` for these tests, or create a standalone Qute `Engine` in test setup:

```java
Engine engine = Engine.builder().addDefaults().build();
```

For simplicity, `@QuarkusTest` is recommended since Qute auto-configuration handles template resolution.

### Backend Unit Test — OnboardingService

```java
class OnboardingServiceTest {
    private OnboardingService service;
    private ManifestGenerator mockManifestGenerator;

    @BeforeEach
    void setUp() throws Exception {
        mockManifestGenerator = mock(ManifestGenerator.class);
        when(mockManifestGenerator.generateAllManifests(any(), anyString()))
            .thenReturn(Map.of("path/file.yaml", "content"));
        service = new OnboardingService();
        // Inject mocks via reflection (same pattern as ContractValidatorTest)
    }

    @Test
    void buildPlanOrdersEnvironmentsByConvention() {
        // given environments in wrong order
        var request = new OnboardingPlanRequest(
            "https://github.com/team/app", "my-app", "Quarkus/Java",
            List.of("prod", "dev", "qa"),
            Map.of("dev", 1L, "qa", 2L, "prod", 3L), 1L);
        
        var result = service.buildPlan("payments", request);

        assertThat(result.promotionChain()).containsExactly("dev", "qa", "prod");
    }
}
```

### Backend Integration Test Pattern

```java
@QuarkusTest
class OnboardingPlanIT {

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "default"),
        @Claim(key = "role", value = "member")
    })
    void planReturns200WithNamespacesAndArgoCdApps() {
        // Pre-create clusters in DB
        // POST plan request
        given()
            .contentType("application/json")
            .body("""
                {
                    "gitRepoUrl": "https://github.com/team/app",
                    "appName": "my-app",
                    "runtimeType": "Quarkus/Java",
                    "detectedEnvironments": ["dev", "qa", "prod"],
                    "environmentClusterMap": {"dev": 1, "qa": 2, "prod": 3},
                    "buildClusterId": 1
                }
            """)
        .when()
            .post("/api/v1/teams/{teamId}/applications/onboard/plan", getTeamId())
        .then()
            .statusCode(200)
            .body("namespaces.size()", is(4))  // 3 envs + 1 build
            .body("argoCdApps.size()", is(4))  // 1 build + 3 envs
            .body("promotionChain", contains("dev", "qa", "prod"));
    }
}
```

### Frontend Test Pattern

```tsx
import { render, screen } from '@testing-library/react';
import { ProvisioningPlanPreview } from './ProvisioningPlanPreview';

describe('ProvisioningPlanPreview', () => {
  const mockPlan: OnboardingPlanResult = {
    appName: 'payment-svc',
    teamName: 'payments',
    namespaces: [
      { name: 'payments-payment-svc-dev', clusterName: 'ocp-dev-01', environmentName: 'dev', isBuild: false },
      { name: 'payments-payment-svc-build', clusterName: 'ocp-dev-01', environmentName: 'build', isBuild: true },
    ],
    argoCdApps: [
      { name: 'payment-svc-build', clusterName: 'ocp-dev-01', namespace: 'payments-payment-svc-build', chartPath: '.helm/build', valuesFile: 'values-build.yaml', isBuild: true },
      { name: 'payment-svc-run-dev', clusterName: 'ocp-dev-01', namespace: 'payments-payment-svc-dev', chartPath: '.helm/run', valuesFile: 'values-run-dev.yaml', isBuild: false },
    ],
    promotionChain: ['dev'],
    generatedManifests: {},
  };

  it('displays resource count', () => {
    render(<ProvisioningPlanPreview plan={mockPlan} clusters={[]} onClusterChange={vi.fn()} onBuildClusterChange={vi.fn()} />);
    expect(screen.getByText(/2 namespaces/)).toBeInTheDocument();
    expect(screen.getByText(/2 ArgoCD applications/)).toBeInTheDocument();
  });
});
```

### TeamContext in OnboardingService

OnboardingService needs the team name for namespace naming. `TeamContext` provides `teamIdentifier` (the OIDC group string, e.g., "payments"). Inject it:

```java
@Inject
TeamContext teamContext;

public OnboardingPlanResult buildPlan(String appName, OnboardingPlanRequest request) {
    String teamName = teamContext.getTeamIdentifier();
    // ... use teamName for namespace naming
}
```

### What NOT to Build in This Story

- **No Application entity creation** — the Application record is saved in Story 2.5 after PR creation
- **No Environment entity creation** — same, Story 2.5
- **No PR creation** — that's Story 2.5 (step 4 of wizard, OnboardingPrBuilder)
- **No GitProvider calls** — manifest generation is in-memory; Git operations happen in Story 2.5
- **No OnboardingPrBuilder** — that orchestrates branch creation + commit + PR in Story 2.5
- **No ProvisioningProgressTracker** — that shows real-time PR creation progress in Story 2.5
- **No actual infrastructure provisioning** — the portal only creates a PR to the infra repo
- **Step 4 and 5 of wizard remain placeholder** — "Confirm & Create PR" button is visible but leads to placeholder in this story

### Existing Code to Reuse

| Component | Location | Usage |
|-----------|----------|-------|
| `Cluster` entity | `cluster/Cluster.java` | Validate cluster IDs, get `apiServerUrl` |
| `ClusterService` | `cluster/ClusterService.java` | List clusters, validate existence |
| `ClusterDto` | `cluster/ClusterDto.java` | Frontend cluster data shape |
| `TeamContext` | `auth/TeamContext.java` | Team name for namespace naming |
| `PermissionFilter` | `auth/PermissionFilter.java` | AUTH — verify /onboard/plan path works |
| `GlobalExceptionMapper` | `common/GlobalExceptionMapper.java` | NotFoundException → 404, PortalIntegrationException → 502 |
| `OnboardingResource` | `onboarding/OnboardingResource.java` | MODIFY — add plan endpoint (from Story 2.3) |
| `ContractValidationResult` | `onboarding/ContractValidationResult.java` | Input from step 2 carries `detectedEnvironments`, `runtimeType` |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Display errors in step 3 |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Loading state while plan builds |
| `apiFetch` | `api/client.ts` | HTTP calls with auth |
| `fetchClusters` | `api/clusters.ts` | Get available clusters for dropdown |
| `ClusterDto` (TS) | `types/cluster.ts` | Cluster type for frontend |
| Casbin policy | `casbin/policy.csv` | `(member, applications, onboard)` already allows |

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/gitops/
├── ManifestGenerator.java

src/main/java/com/portal/onboarding/
├── OnboardingService.java
├── OnboardingPlanRequest.java
├── OnboardingPlanResult.java
├── PlannedNamespace.java
└── PlannedArgoCdApp.java

src/main/resources/templates/gitops/
├── namespace.yaml.qute.yaml
├── argocd-app-build.yaml.qute.yaml
└── argocd-app-run.yaml.qute.yaml

src/test/java/com/portal/gitops/
└── ManifestGeneratorTest.java

src/test/java/com/portal/onboarding/
└── OnboardingServiceTest.java
```

**Modified backend files:**
```
src/main/java/com/portal/onboarding/OnboardingResource.java  (add plan endpoint)
pom.xml  (add quarkus-qute dependency)
```

**New frontend files:**
```
src/main/webui/src/components/onboarding/
├── ProvisioningPlanPreview.tsx
└── ProvisioningPlanPreview.test.tsx
```

**Modified frontend files:**
```
src/main/webui/src/types/onboarding.ts  (add plan types)
src/main/webui/src/api/onboarding.ts  (add buildPlan function)
src/main/webui/src/routes/OnboardingWizardPage.tsx  (implement step 3)
src/main/webui/src/routes/OnboardingWizardPage.test.tsx  (add step 3 tests)
```

### Previous Story Intelligence

**Story 2.3 (Application Registration & Contract Validation):**
- OnboardingResource at `@Path("/api/v1/teams/{teamId}/applications")`
- POST `/onboard` returns `ContractValidationResult`
- PermissionFilter handles `/onboard` as an ACTION_SEGMENT → Casbin action = "onboard"
- ContractValidationResult contains: `allPassed`, `checks`, `runtimeType`, `detectedEnvironments`
- Frontend wizard has steps 1 (repo URL) and 2 (contract validation) functional; steps 3–5 are placeholder
- PF6 Wizard with `WizardStep` components — controlled navigation
- `apiFetch` wrapper handles auth and error parsing

**Story 2.2 (Git Provider Abstraction):**
- GitProvider interface with 6 operations: validateRepoAccess, readFile, listDirectory, createBranch, commitFiles, createPullRequest
- GitProviderFactory produces the active provider via CDI
- DevGitProvider for dev/test profiles
- `portal.git.infra-repo-url` config key for the infra repo

**Story 2.1 (Application & Environment Data Model):**
- Application entity: `name`, `teamId`, `gitRepoUrl`, `runtimeType`, `onboardingPrUrl` (nullable), `onboardedAt` (nullable)
- Environment entity: `name`, `applicationId`, `clusterId`, `namespace`, `promotionOrder`
- Promotion chain is data-driven via `promotionOrder` column
- `PanacheEntityBase` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` is mandatory

**Epic 1 patterns:**
- `@ApplicationScoped` for all service beans
- `@RequestScoped` for TeamContext
- Inject via `@Inject` field injection
- Unit tests mock dependencies via reflection (`Field.setAccessible(true)`)
- Integration tests use `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` + REST Assured
- Frontend tests use Vitest + RTL; query by role/label, not CSS class
- PF6 components exclusively; PF CSS tokens for styling

### References

- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.4] — Full acceptance criteria
- [Source: planning-artifacts/architecture.md § GitOps Contract Specification] — Infra repo structure: `<cluster>/<namespace>/namespace.yaml` + `argocd-application.yaml`
- [Source: planning-artifacts/architecture.md § GitOps manifest generation] — Qute template engine for YAML generation, templates version-controlled
- [Source: planning-artifacts/architecture.md § Onboarding Workflow] — Portal generates Namespace + ArgoCD Application manifests → creates PR to infra repo
- [Source: planning-artifacts/architecture.md § ArgoCD Applications created per onboarded app] — 1 build + N run ArgoCD Applications
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure] — `gitops/` package with ManifestGenerator, OnboardingPrBuilder, templates/
- [Source: planning-artifacts/architecture.md § Architectural Boundaries] — GitOps boundary: all manifest generation in gitops/ package
- [Source: planning-artifacts/ux-design-specification.md § Journey 1: Application Onboarding] — Wizard step 3: provisioning plan preview
- [Source: planning-artifacts/ux-design-specification.md § Provisioning Progress Tracker] — Component spec (for Story 2.5, not this story)
- [Source: planning-artifacts/ux-design-specification.md § Component Strategy § Custom Components] — ProvisioningPlanPreview UX requirements
- [Source: project-context.md § Framework-Specific Rules] — Qute templates, PF6 components, testing patterns
- [Source: project-context.md § GitOps Contract] — `.helm/build/`, `.helm/run/`, `values-build.yaml`, `values-run-<env>.yaml`
- [Source: project-context.md § Anti-Patterns] — No cross-package entity imports, REST → Service → Adapter chain
- [Source: implementation-artifacts/2-3-application-registration-contract-validation.md] — OnboardingResource, ContractValidator, wizard steps 1–2
- [Source: implementation-artifacts/2-2-git-provider-abstraction.md] — GitProvider interface, commitFiles for future PR creation
- [Source: implementation-artifacts/2-1-application-environment-data-model.md] — Application/Environment entities, namespace conventions
- [Source: cluster/Cluster.java] — Cluster entity with apiServerUrl for ArgoCD destination
- [Source: cluster/ClusterService.java] — Cluster CRUD service
- [Source: auth/PermissionFilter.java] — ACTION_SEGMENTS includes "onboard"
- [Source: casbin/policy.csv] — `(member, applications, onboard)` allows members
- [Source: common/GlobalExceptionMapper.java] — NotFoundException → 404, error response format

## Dev Agent Record

### Agent Model Used
Claude claude-4.6-opus-high-thinking

### Debug Log References
- Template naming: `.qute.yaml` suffix was not resolved by Quarkus Qute engine; renamed templates to plain `.yaml` extension for proper resolution
- Panache static method mocking: `Cluster.findById()` (inherited from PanacheEntityBase) could not be mocked with `mockStatic(Cluster.class)` in unit tests; refactored OnboardingService to use injected `ClusterService.findById()` instead, which also corrects a cross-package entity import anti-pattern
- PermissionFilter: Added "plan" to `ACTION_SEGMENTS` so `/onboard/plan` path correctly resolves resource as "applications" and action as "onboard" (verified via Casbin logs in IT test)
- ManifestGenerator `generateAllManifests` signature extended with `Map<String, String> clusterApiUrls` parameter to separate cluster display names (used in file paths and DTOs) from API server URLs (used in ArgoCD manifest destination.server)

### Completion Notes List
- ✅ All 14 tasks completed
- ✅ 167 backend tests pass (9 new OnboardingServiceTest + 8 new ManifestGeneratorTest + 5 new OnboardingPlanIT + existing tests)
- ✅ 110 frontend tests pass (7 new ProvisioningPlanPreview tests + 5 wizard page tests + existing tests)
- ✅ Zero regressions — all pre-existing tests continue to pass
- ✅ Qute templates generate valid Kubernetes Namespace and ArgoCD Application YAML
- ✅ Environment ordering follows convention (dev < qa < staging < prod) not alphabetical
- ✅ Namespace naming convention: `<team>-<app>-<env>` verified in tests
- ✅ File path convention: `<cluster>/<namespace>/namespace.yaml` and `<cluster>/<namespace>/argocd-app-*.yaml`
- ✅ PermissionFilter correctly allows member role to access `/onboard/plan`
- ✅ Cross-team access returns 404 (not 403)
- ✅ Frontend wizard step 3 is functional: loads clusters, builds plan, allows cluster re-assignment
- ✅ Steps 4–5 remain placeholder for Story 2.5

### Change Log
- 2026-04-05: Implemented Story 2.4 — Onboarding Plan & Manifest Generation (all 14 tasks)
- 2026-04-06: Fixed 3 code-review findings:
  1. (High) Added `p, member, clusters, read` to Casbin policy so members can load clusters during onboarding; updated PermissionFilterIT tests
  2. (Med) Added `Confirm & Create PR` button to plan step footer (disabled until Story 2.5)
  3. (Med) Added `slugify()` to OnboardingService and frontend extractAppName to produce DNS-1123 compliant K8s resource names

### File List

**New backend files:**
- developer-portal/src/main/java/com/portal/gitops/ManifestGenerator.java
- developer-portal/src/main/java/com/portal/onboarding/OnboardingService.java
- developer-portal/src/main/java/com/portal/onboarding/OnboardingPlanRequest.java
- developer-portal/src/main/java/com/portal/onboarding/OnboardingPlanResult.java
- developer-portal/src/main/java/com/portal/onboarding/PlannedNamespace.java
- developer-portal/src/main/java/com/portal/onboarding/PlannedArgoCdApp.java
- developer-portal/src/main/resources/templates/gitops/namespace.yaml
- developer-portal/src/main/resources/templates/gitops/argocd-app-build.yaml
- developer-portal/src/main/resources/templates/gitops/argocd-app-run.yaml
- developer-portal/src/test/java/com/portal/gitops/ManifestGeneratorTest.java
- developer-portal/src/test/java/com/portal/onboarding/OnboardingServiceTest.java
- developer-portal/src/test/java/com/portal/onboarding/OnboardingPlanIT.java

**Modified backend files:**
- developer-portal/pom.xml (added quarkus-qute dependency)
- developer-portal/src/main/java/com/portal/onboarding/OnboardingResource.java (added plan endpoint, extracted verifyTeamAccess)
- developer-portal/src/main/java/com/portal/onboarding/OnboardingService.java (added slugify for DNS-safe names)
- developer-portal/src/main/java/com/portal/auth/PermissionFilter.java (added "plan" to ACTION_SEGMENTS)
- developer-portal/src/main/java/com/portal/cluster/ClusterService.java (added findById method)
- developer-portal/src/main/resources/casbin/policy.csv (added member clusters read)
- developer-portal/src/test/java/com/portal/auth/PermissionFilterIT.java (updated for new cluster policy)
- developer-portal/src/test/java/com/portal/onboarding/OnboardingServiceTest.java (added slugify test)

**New frontend files:**
- developer-portal/src/main/webui/src/components/onboarding/ProvisioningPlanPreview.tsx
- developer-portal/src/main/webui/src/components/onboarding/ProvisioningPlanPreview.test.tsx

**Modified frontend files:**
- developer-portal/src/main/webui/src/types/onboarding.ts (added plan types)
- developer-portal/src/main/webui/src/api/onboarding.ts (added buildPlan function)
- developer-portal/src/main/webui/src/routes/OnboardingWizardPage.tsx (implemented step 3)
- developer-portal/src/main/webui/src/routes/OnboardingWizardPage.test.tsx (added step 3 tests, mocks for new API)
