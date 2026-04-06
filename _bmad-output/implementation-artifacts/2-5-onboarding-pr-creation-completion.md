# Story 2.5: Onboarding PR Creation & Completion

Status: done

## Story

As a developer,
I want the portal to create an infrastructure PR with all required manifests and save my application as onboarded,
So that my application is registered and the platform team can review and approve the provisioning.

## Acceptance Criteria

1. **PR creation sequence on confirm**
   - **Given** the developer confirms the provisioning plan
   - **When** the "Confirm & Create PR" button is clicked
   - **Then** the OnboardingPrBuilder executes the following sequence:
     1. Creates a new branch in the infra repo named `onboard/<team>-<app>`
     2. Commits all generated manifest files (namespace YAMLs + ArgoCD Application YAMLs) to the branch
     3. Creates a pull request from the branch to the infra repo's main branch
   - **And** the PR title follows the format: "Onboard \<team>/\<app> — \<N> namespaces, \<M> ArgoCD applications"
   - **And** the PR description lists all resources being created

2. **Provisioning progress tracker (wizard step 4)**
   - **Given** the wizard is on step 4 (PR creation)
   - **When** the PR creation is in progress
   - **Then** the ProvisioningProgressTracker component shows the steps:
     1. "Creating branch in infra repo" — pending/in-progress/completed
     2. "Committing manifests" — pending/in-progress/completed
     3. "Creating pull request" — pending/in-progress/completed
   - **And** each step transitions through states: ○ pending → ⟳ in-progress → ✓ completed or ✕ failed
   - **And** the progress counter updates in real time (e.g., "2/3 complete")
   - **And** the tracker uses aria-live="polite" for screen reader announcements

3. **Application and environment persistence**
   - **Given** the PR is created successfully
   - **When** the backend processes the completion
   - **Then** the Application entity is saved to the database with name, team, gitRepoUrl, runtimeType, and onboardingPrUrl set to the PR URL
   - **And** Environment entities are created for each environment in the promotion chain with the correct cluster, namespace, and promotion_order
   - **And** onboardedAt is set to the current timestamp

4. **Success state (wizard step 5)**
   - **Given** the PR creation succeeds
   - **When** the wizard advances to step 5 (completion)
   - **Then** a success state is displayed with the message "Application onboarded successfully"
   - **And** a link to the PR is shown: "View onboarding PR ↗" (opens in new tab)
   - **And** a primary action "View \<app-name>" navigates to the application overview
   - **And** a secondary link "Open in DevSpaces ↗" is shown (placeholder — functional in Epic 3)

5. **Failure handling and retry**
   - **Given** any step in the PR creation fails
   - **When** the failure occurs
   - **Then** the failed step shows ✕ with an error message in developer language
   - **And** a "Retry" button is available to retry from the failed step
   - **And** completed steps remain ✓ and are not re-executed
   - **And** a deep link to the Git server is shown if applicable

6. **Authorization**
   - **Given** the onboarding endpoint is called
   - **When** a developer with "member" or "lead" role submits the request
   - **Then** the Casbin permission check allows the operation
   - **And** the TeamContext ensures the application is scoped to the developer's team

## Tasks / Subtasks

- [x] Task 1: Create OnboardingPrBuilder service (AC: #1)
  - [x] Create `OnboardingPrBuilder.java` in `com.portal.gitops`, `@ApplicationScoped`
  - [x] Inject `GitProvider` and `GitProviderConfig`
  - [x] Method: `PullRequest createOnboardingPr(String teamName, String appName, Map<String, String> manifests)` orchestrates the 3-step sequence:
    1. `gitProvider.createBranch(infraRepoUrl, "onboard/" + teamName + "-" + appName, "main")`
    2. `gitProvider.commitFiles(infraRepoUrl, "onboard/" + teamName + "-" + appName, manifests, "Onboard " + teamName + "/" + appName)`
    3. `gitProvider.createPullRequest(infraRepoUrl, "onboard/" + teamName + "-" + appName, "main", title, description)`
  - [x] PR title: `"Onboard " + teamName + "/" + appName + " — " + namespaceCount + " namespaces, " + argoAppCount + " ArgoCD applications"`
  - [x] PR description: list all file paths being created (one per line)
  - [x] Wrap errors in `PortalIntegrationException(system="git", operation="createOnboardingPr")`

- [x] Task 2: Create OnboardingConfirmRequest and OnboardingResultDto (AC: #1, #3)
  - [x] Create `OnboardingConfirmRequest.java` record in `com.portal.onboarding` with fields: `@NotBlank String appName`, `@NotBlank String gitRepoUrl`, `@NotBlank String runtimeType`, `@NotNull List<String> detectedEnvironments`, `@NotNull Map<String, Long> environmentClusterMap`, `@NotNull Long buildClusterId`
  - [x] Create `OnboardingResultDto.java` record in `com.portal.onboarding` with fields: `Long applicationId`, `String applicationName`, `String onboardingPrUrl`, `int namespacesCreated`, `int argoCdAppsCreated`, `List<String> promotionChain`

- [x] Task 3: Extend OnboardingService with confirm method (AC: #1, #3)
  - [x] Add method to `OnboardingService.java`: `OnboardingResultDto confirmOnboarding(OnboardingConfirmRequest request)`
  - [x] Inject `OnboardingPrBuilder`, `ManifestGenerator`, `TeamContext`
  - [x] Step 1: Build the provisioning plan via existing `buildPlan()` method
  - [x] Step 2: Generate all manifests via `ManifestGenerator.generateAllManifests()`
  - [x] Step 3: Create PR via `OnboardingPrBuilder.createOnboardingPr()`
  - [x] Step 4: Persist Application entity — set name, teamId (from TeamContext), gitRepoUrl, runtimeType, onboardingPrUrl (PR URL), onboardedAt (Instant.now())
  - [x] Step 5: Persist Environment entities — one per environment in the promotion chain with correct clusterId, namespace (`<team>-<app>-<env>`), and promotionOrder
  - [x] Return `OnboardingResultDto` with all details
  - [x] Wrap the entire operation in `@Transactional` — if PR creation succeeds but DB persist fails, the PR still exists (acceptable — idempotent re-onboard would create a new PR)

- [x] Task 4: Add confirm endpoint to OnboardingResource (AC: #1, #6)
  - [x] Add `POST /api/v1/teams/{teamId}/applications/onboard/confirm` to `OnboardingResource.java`
  - [x] Accepts `@Valid OnboardingConfirmRequest`, returns `OnboardingResultDto` as 201 JSON
  - [x] Verify `teamContext.getTeamId()` matches `{teamId}` — return 404 if mismatch
  - [x] PermissionFilter: path `teams/{teamId}/applications/onboard/confirm` — "onboard" is in ACTION_SEGMENTS, "confirm" appears after it → filter finds "onboard" first → Casbin check: `(member, applications, onboard)` → ALLOWED

- [x] Task 5: Create frontend types for onboarding completion (AC: #1, #2, #4)
  - [x] Add to `src/main/webui/src/types/onboarding.ts`:
    - `OnboardingConfirmRequest` (appName, gitRepoUrl, runtimeType, detectedEnvironments, environmentClusterMap, buildClusterId)
    - `OnboardingResult` (applicationId, applicationName, onboardingPrUrl, namespacesCreated, argoCdAppsCreated, promotionChain)
    - `ProvisioningStep` (id, label, status: 'pending' | 'in-progress' | 'completed' | 'failed', error?: string)

- [x] Task 6: Create frontend API function for confirm (AC: #1)
  - [x] Add to `src/main/webui/src/api/onboarding.ts`:
    - `confirmOnboarding(teamId: string, request: OnboardingConfirmRequest): Promise<OnboardingResult>`

- [x] Task 7: Create ProvisioningProgressTracker component (AC: #2, #5)
  - [x] Create `src/main/webui/src/components/onboarding/ProvisioningProgressTracker.tsx`
  - [x] Props: `steps: ProvisioningStep[]`, `totalSteps: number`, `onRetry: () => void`
  - [x] Header: "{appName} onboarding" with counter "{N}/{total} complete"
  - [x] Each step renders with state icon: ○ pending (grey), ⟳ in-progress (yellow Spinner), ✓ completed (green CheckCircleIcon), ✕ failed (red TimesCircleIcon)
  - [x] Failed step expands to show error message + "Retry" button + optional deep link
  - [x] Completed steps remain ✓ and do not re-render as pending on retry
  - [x] Use PF6 `ProgressStepper` / `ProgressStep` as the base component
  - [x] Wrap step list in `aria-live="polite"` region for screen reader announcements
  - [x] Counter in header updates as steps complete

- [x] Task 8: Create OnboardingCompletionPanel component (AC: #4)
  - [x] Create `src/main/webui/src/components/onboarding/OnboardingCompletionPanel.tsx`
  - [x] Props: `result: OnboardingResult`, `teamId: string`
  - [x] Display success message: "Application onboarded successfully"
  - [x] "View onboarding PR ↗" — PF6 Button (link variant) opening PR URL in new tab
  - [x] "View {appName}" — PF6 Button (primary) navigating to `/teams/{teamId}/applications/{appId}`
  - [x] "Open in DevSpaces ↗" — PF6 Button (link variant), disabled with tooltip "Available after Epic 3"
  - [x] Resource summary: "Created N namespaces, M ArgoCD applications"
  - [x] Promotion chain display: e.g., "dev → qa → prod"

- [x] Task 9: Update OnboardingWizardPage steps 4 and 5 (AC: #2, #4, #5)
  - [x] Replace step 4 placeholder with functional ProvisioningProgressTracker
  - [x] Replace step 5 placeholder with OnboardingCompletionPanel
  - [x] Carry state from step 3: plan, clusterAssignments, buildClusterId, appName, gitRepoUrl, runtimeType, detectedEnvironments
  - [x] Step 4 flow:
    1. On entering step 4, build `OnboardingConfirmRequest` from wizard state
    2. Call `confirmOnboarding()` API
    3. Simulate step progression: update step states as API executes (creating branch → committing → creating PR)
    4. On success: advance to step 5 with OnboardingResult
    5. On failure: show failed step with error, enable retry
  - [x] Step 5 flow: render OnboardingCompletionPanel with result
  - [x] Disable wizard "Back" button on step 4 (irreversible once PR creation starts)
  - [x] Disable wizard "Next" on step 4 until all steps complete

- [x] Task 10: Write OnboardingPrBuilder unit tests (AC: #1)
  - [x] Create `OnboardingPrBuilderTest.java` in `src/test/java/com/portal/gitops/`
  - [x] Mock `GitProvider` and `GitProviderConfig`
  - [x] Test successful PR creation: verify 3-step call sequence (createBranch → commitFiles → createPullRequest)
  - [x] Test branch name format: `onboard/<team>-<app>`
  - [x] Test PR title format: `"Onboard <team>/<app> — N namespaces, M ArgoCD applications"`
  - [x] Test PR description lists all file paths
  - [x] Test GitProvider failure on createBranch: verify PortalIntegrationException propagates
  - [x] Test GitProvider failure on commitFiles: verify PortalIntegrationException propagates
  - [x] Test GitProvider failure on createPullRequest: verify PortalIntegrationException propagates

- [x] Task 11: Write OnboardingService confirm method unit tests (AC: #3)
  - [x] Extend `OnboardingServiceTest.java` in `src/test/java/com/portal/onboarding/`
  - [x] Mock `OnboardingPrBuilder`, `ManifestGenerator`, `TeamContext`
  - [x] Test `confirmOnboarding`: verify Application entity persisted with correct fields (name, teamId, gitRepoUrl, runtimeType, onboardingPrUrl, onboardedAt)
  - [x] Test Environment entities persisted: one per environment with correct clusterId, namespace, promotionOrder
  - [x] Test PR URL from OnboardingPrBuilder flows into Application.onboardingPrUrl
  - [x] Test OnboardingResultDto contains correct counts and promotion chain

- [x] Task 12: Write confirm endpoint integration test (AC: #1, #3, #6)
  - [x] Create or extend `OnboardingResourceIT.java` in `src/test/java/com/portal/onboarding/`
  - [x] `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` with member role
  - [x] `@InjectMock GitProvider` to mock Git operations
  - [x] Test POST `/api/v1/teams/{teamId}/applications/onboard/confirm` with valid request → 201 + OnboardingResultDto JSON
  - [x] Verify Application persisted in DB (query by team + name)
  - [x] Verify Environment entities persisted (query by application)
  - [x] Test Git failure → 502 error response, no Application/Environment persisted
  - [x] Test cross-team access → 404
  - [x] Test duplicate app name → 409 conflict (unique constraint)

- [x] Task 13: Write frontend component tests (AC: #2, #4, #5)
  - [x] Create `src/main/webui/src/components/onboarding/ProvisioningProgressTracker.test.tsx`
  - [x] Test all steps pending: grey icons, counter "0/3 complete"
  - [x] Test step 1 in-progress: spinner shown for step 1
  - [x] Test step 1 completed, step 2 in-progress: check icon for step 1, spinner for step 2
  - [x] Test step failed: red icon, error message, retry button visible
  - [x] Test retry button calls onRetry callback
  - [x] Test aria-live region present
  - [x] Create `src/main/webui/src/components/onboarding/OnboardingCompletionPanel.test.tsx`
  - [x] Test success message displayed
  - [x] Test PR link opens in new tab (target="_blank")
  - [x] Test "View app" button navigates correctly
  - [x] Test resource summary shows correct counts
  - [x] Extend `src/main/webui/src/routes/OnboardingWizardPage.test.tsx`
  - [x] Test step 4 renders ProvisioningProgressTracker
  - [x] Test step 5 renders OnboardingCompletionPanel after success

## Dev Notes

### Hard Dependencies

This story **requires** Stories 2.1, 2.2, 2.3, and 2.4 to be implemented first:
- **Story 2.1** — Application and Environment entities for persistence
- **Story 2.2** — GitProvider interface with `createBranch`, `commitFiles`, `createPullRequest` operations
- **Story 2.3** — OnboardingResource, ContractValidator, OnboardingWizardPage with steps 1–2 functional
- **Story 2.4** — OnboardingService with `buildPlan()`, ManifestGenerator with `generateAllManifests()`, OnboardingPlanResult, ProvisioningPlanPreview, wizard step 3 functional

### Package: `com.portal.gitops` — New Code

Story 2.4 created `ManifestGenerator.java` in this package. This story adds `OnboardingPrBuilder.java`:

```
com.portal.gitops/
├── ManifestGenerator.java      # EXISTS (from 2.4)
├── OnboardingPrBuilder.java    # NEW — orchestrates branch → commit → PR
└── (package-info.java)         # EXISTS
```

### Package: `com.portal.onboarding` — Extensions

Story 2.3 created the base package, Story 2.4 extended it. This story adds:

```
com.portal.onboarding/
├── OnboardingResource.java           # MODIFY — add confirm endpoint
├── OnboardingService.java            # MODIFY — add confirmOnboarding method
├── OnboardingConfirmRequest.java     # NEW
├── OnboardingResultDto.java          # NEW
├── OnboardingPlanRequest.java        # EXISTS (from 2.4)
├── OnboardingPlanResult.java         # EXISTS (from 2.4)
├── PlannedNamespace.java             # EXISTS (from 2.4)
├── PlannedArgoCdApp.java             # EXISTS (from 2.4)
├── ContractValidator.java            # EXISTS (from 2.3)
├── ContractValidationResult.java     # EXISTS (from 2.3)
├── ContractCheck.java                # EXISTS (from 2.3)
└── ValidateRepoRequest.java          # EXISTS (from 2.3)
```

### OnboardingPrBuilder — Detailed Implementation

The builder is the final orchestration piece. It receives generated manifests (from ManifestGenerator) and uses GitProvider to create the PR.

```java
@ApplicationScoped
public class OnboardingPrBuilder {

    @Inject
    GitProvider gitProvider;

    @Inject
    GitProviderConfig gitConfig;

    /**
     * Creates an onboarding PR in the infra repo with all generated manifests.
     * Three-step sequence: create branch → commit files → create PR.
     */
    public PullRequest createOnboardingPr(String teamName, String appName,
            Map<String, String> manifests) {
        String infraRepoUrl = gitConfig.infraRepoUrl();
        String branchName = "onboard/" + teamName + "-" + appName;
        String commitMessage = "Onboard " + teamName + "/" + appName;

        int namespaceCount = (int) manifests.keySet().stream()
                .filter(k -> k.endsWith("/namespace.yaml")).count();
        int argoAppCount = manifests.size() - namespaceCount;

        String prTitle = String.format("Onboard %s/%s — %d namespaces, %d ArgoCD applications",
                teamName, appName, namespaceCount, argoAppCount);

        String prDescription = "## Resources Created\n\n"
                + manifests.keySet().stream()
                    .sorted()
                    .map(path -> "- `" + path + "`")
                    .collect(Collectors.joining("\n"));

        gitProvider.createBranch(infraRepoUrl, branchName, "main");
        gitProvider.commitFiles(infraRepoUrl, branchName, manifests, commitMessage);
        return gitProvider.createPullRequest(
                infraRepoUrl, branchName, "main", prTitle, prDescription);
    }
}
```

The infra repo URL comes from `GitProviderConfig.infraRepoUrl()` — configured via `portal.git.infra-repo-url` environment variable.

### OnboardingService — confirmOnboarding Method

Extends the OnboardingService from Story 2.4. The confirm method orchestrates plan → manifests → PR → persist:

```java
@Transactional
public OnboardingResultDto confirmOnboarding(OnboardingConfirmRequest request) {
    String teamName = teamContext.getTeamIdentifier();
    Long teamId = teamContext.getTeamId();

    // Step 1: Rebuild the plan (reuse existing buildPlan logic)
    OnboardingPlanRequest planRequest = new OnboardingPlanRequest(
            request.gitRepoUrl(), request.appName(), request.runtimeType(),
            request.detectedEnvironments(), request.environmentClusterMap(),
            request.buildClusterId());
    OnboardingPlanResult plan = buildPlan(teamName, planRequest);

    // Step 2: Generate manifests
    Map<String, String> manifests = manifestGenerator.generateAllManifests(plan, request.gitRepoUrl());

    // Step 3: Create PR in infra repo
    PullRequest pr = onboardingPrBuilder.createOnboardingPr(teamName, request.appName(), manifests);

    // Step 4: Persist Application
    Application app = new Application();
    app.name = request.appName();
    app.teamId = teamId;
    app.gitRepoUrl = request.gitRepoUrl();
    app.runtimeType = request.runtimeType();
    app.onboardingPrUrl = pr.url();
    app.onboardedAt = Instant.now();
    app.persist();

    // Step 5: Persist Environments
    List<String> orderedEnvs = plan.promotionChain();
    for (int i = 0; i < orderedEnvs.size(); i++) {
        String envName = orderedEnvs.get(i);
        Environment env = new Environment();
        env.name = envName;
        env.applicationId = app.id;
        env.clusterId = request.environmentClusterMap().get(envName);
        env.namespace = teamName.toLowerCase() + "-" + request.appName().toLowerCase() + "-" + envName.toLowerCase();
        env.promotionOrder = i;
        env.persist();
    }

    return new OnboardingResultDto(
            app.id, app.name, pr.url(),
            plan.namespaces().size(), plan.argoCdApps().size(),
            orderedEnvs);
}
```

**Transaction boundary:** The `@Transactional` annotation wraps the DB persist operations. If the PR creation succeeds but DB persist fails (unlikely), the PR still exists in the infra repo — this is acceptable because re-running onboarding would create a new PR. The important thing is that Application + Environments are persisted atomically.

**Cross-domain entity reference:** Application is in `com.portal.application` and Environment is in `com.portal.environment`. OnboardingService references them by importing the entity classes directly — this is allowed because OnboardingService is the orchestration point for onboarding, and the architecture's `onboarding/` package is explicitly permitted to coordinate between `application/` and `environment/` domains per the epics' design. Use the entity classes' static `persist()` method (Panache Active Record pattern).

### REST Endpoint — Confirm

**Endpoint:** `POST /api/v1/teams/{teamId}/applications/onboard/confirm`

**PermissionFilter path analysis:** The path `teams/{teamId}/applications/onboard/confirm`:
- PermissionFilter walks segments backward
- "confirm" is NOT in ACTION_SEGMENTS
- "onboard" IS in ACTION_SEGMENTS → action = "onboard"
- Resource: `applications` (last non-ID, non-action segment)
- Casbin check: `(member, applications, onboard)` → **ALLOWED** per policy.csv

This follows the same pattern as Story 2.4's `/onboard/plan` sub-path — no PermissionFilter changes needed.

**Request body:**
```java
public record OnboardingConfirmRequest(
    @NotBlank String appName,
    @NotBlank String gitRepoUrl,
    @NotBlank String runtimeType,
    @NotNull List<String> detectedEnvironments,
    @NotNull Map<String, Long> environmentClusterMap,
    @NotNull Long buildClusterId
) {}
```

**Response:** `OnboardingResultDto` as JSON (201 Created).

**Error responses:**
- Git failure during PR creation → `PortalIntegrationException` → GlobalExceptionMapper → 502
- Duplicate app name → `PersistenceException` (unique constraint `uq_applications_team_id_name`) → GlobalExceptionMapper → 409
- Cross-team access → 404

### Frontend — ProvisioningProgressTracker Component

Located at `src/main/webui/src/components/onboarding/ProvisioningProgressTracker.tsx`.

**PF6 components used:**
- `ProgressStepper` / `ProgressStep` — base stepper component with variant support
- `Spinner` — for in-progress state
- `Button` — retry action
- `Icon` + status icons (`CheckCircleIcon`, `TimesCircleIcon`, `InProgressIcon`)

**State management:** The tracker receives `steps` as a prop — the parent (OnboardingWizardPage) manages step state transitions. The tracker is a pure display component.

**Step state icons:**
```tsx
const getStepVariant = (status: ProvisioningStep['status']) => {
  switch (status) {
    case 'completed': return 'success';
    case 'failed': return 'danger';
    case 'in-progress': return 'info';
    default: return 'pending';
  }
};
```

**PF6 ProgressStepper pattern:**
```tsx
import { ProgressStepper, ProgressStep } from '@patternfly/react-core';

<div aria-live="polite">
  <ProgressStepper isVertical>
    {steps.map((step) => (
      <ProgressStep
        key={step.id}
        id={step.id}
        titleId={`${step.id}-title`}
        variant={getStepVariant(step.status)}
        description={step.status === 'failed' ? step.error : undefined}
      >
        {step.label}
      </ProgressStep>
    ))}
  </ProgressStepper>
</div>
```

**Failed step expansion:** When a step fails, show the error detail below it and a "Retry" button. The retry only re-runs from the failed step forward (completed steps are preserved).

### Frontend — OnboardingCompletionPanel Component

Located at `src/main/webui/src/components/onboarding/OnboardingCompletionPanel.tsx`.

**UX pattern:** "Celebrate the wins quietly" — a clear success state without confetti or animations. The view reaching a healthy state IS the confirmation (UX-DR19).

**Layout:**
```
┌──────────────────────────────────────────────────┐
│ ✓ Application onboarded successfully             │
│                                                  │
│ Created 4 namespaces, 4 ArgoCD applications      │
│ Promotion chain: dev → qa → prod                 │
│                                                  │
│ [View onboarding PR ↗]  [View payment-svc]       │
│ [Open in DevSpaces ↗]                            │
└──────────────────────────────────────────────────┘
```

**PF6 components used:**
- `EmptyState` / `EmptyStateBody` / `EmptyStateFooter` / `EmptyStateActions` — success layout
- `CheckCircleIcon` with success color — header icon
- `Button` variant="primary" — "View {appName}" navigation
- `Button` variant="link" with `component="a"` target="_blank" — "View onboarding PR ↗"
- `Button` variant="link" isDisabled — "Open in DevSpaces ↗" placeholder

### Frontend — OnboardingWizardPage Steps 4–5 State Flow

The wizard carries state across steps:

```
Step 1 (repo URL) → repoUrl
Step 2 (validation) → validationResult { runtimeType, detectedEnvironments }
Step 3 (plan) → plan, clusterAssignments, buildClusterId
Step 4 (PR creation) → provisioningSteps[], confirmResult
Step 5 (complete) → OnboardingResult from step 4
```

**Step 4 flow:**
1. On entering step 4, build `OnboardingConfirmRequest` from accumulated wizard state
2. Initialize 3 provisioning steps as "pending"
3. Set step 1 "Creating branch" to "in-progress"
4. Call `confirmOnboarding()` API
5. **Simulated progression:** The backend API is a single POST that does all 3 Git operations atomically. The frontend simulates step-by-step progression using timed state updates:
   - T+0: Step 1 = in-progress
   - T+500ms: Step 1 = completed, Step 2 = in-progress
   - T+1000ms: Step 2 = completed, Step 3 = in-progress
   - On API response (success): Step 3 = completed → advance to step 5
   - On API response (failure): determine which step failed from error, mark as failed
6. On success: store `OnboardingResult` → advance to step 5
7. On failure: show failed step with error, enable retry

**Why simulated progression:** The backend executes the 3 Git operations in a single synchronous request. Real-time per-step updates would require WebSockets or SSE, which is out of scope for MVP. The timed simulation gives the developer visual feedback that work is happening. The final state (success/failure) is always accurate from the API response.

**Retry behavior:** On retry, only the failed step and subsequent steps are shown as "pending". Completed steps remain "✓ completed". The frontend re-calls the full `confirmOnboarding()` API (which is idempotent — Git providers handle branch-already-exists gracefully or the developer deletes the branch first).

**Disable wizard Back on step 4:** Once PR creation starts, going back would leave orphaned branches. Disable the "Back" button programmatically on step 4.

### Frontend Types — Additions to `types/onboarding.ts`

```tsx
export interface OnboardingConfirmRequest {
  appName: string;
  gitRepoUrl: string;
  runtimeType: string;
  detectedEnvironments: string[];
  environmentClusterMap: Record<string, number>;
  buildClusterId: number;
}

export interface OnboardingResult {
  applicationId: number;
  applicationName: string;
  onboardingPrUrl: string;
  namespacesCreated: number;
  argoCdAppsCreated: number;
  promotionChain: string[];
}

export interface ProvisioningStep {
  id: string;
  label: string;
  status: 'pending' | 'in-progress' | 'completed' | 'failed';
  error?: string;
}
```

### Frontend API — Addition to `api/onboarding.ts`

```tsx
export function confirmOnboarding(
  teamId: string,
  request: OnboardingConfirmRequest
): Promise<OnboardingResult> {
  return apiFetch<OnboardingResult>(
    `/api/v1/teams/${teamId}/applications/onboard/confirm`,
    {
      method: 'POST',
      body: JSON.stringify(request),
    }
  );
}
```

### Backend Unit Test — OnboardingPrBuilder

```java
class OnboardingPrBuilderTest {
    private OnboardingPrBuilder builder;
    private GitProvider mockGitProvider;
    private GitProviderConfig mockConfig;

    @BeforeEach
    void setUp() throws Exception {
        mockGitProvider = mock(GitProvider.class);
        mockConfig = mock(GitProviderConfig.class);
        when(mockConfig.infraRepoUrl()).thenReturn("https://github.com/org/infra-repo");
        when(mockGitProvider.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PullRequest("https://github.com/org/infra-repo/pull/42", 42, "Onboard team/app"));

        builder = new OnboardingPrBuilder();
        // Inject mocks via reflection (same pattern as ContractValidatorTest)
        Field gpField = OnboardingPrBuilder.class.getDeclaredField("gitProvider");
        gpField.setAccessible(true);
        gpField.set(builder, mockGitProvider);
        Field cfgField = OnboardingPrBuilder.class.getDeclaredField("gitConfig");
        cfgField.setAccessible(true);
        cfgField.set(builder, mockConfig);
    }

    @Test
    void createOnboardingPrCallsThreeStepsInOrder() {
        Map<String, String> manifests = Map.of(
            "ocp-dev-01/payments-app-dev/namespace.yaml", "ns-yaml",
            "ocp-dev-01/payments-app-dev/argocd-app-run-dev.yaml", "argo-yaml"
        );

        PullRequest result = builder.createOnboardingPr("payments", "app", manifests);

        var inOrder = inOrder(mockGitProvider);
        inOrder.verify(mockGitProvider).createBranch(
            "https://github.com/org/infra-repo", "onboard/payments-app", "main");
        inOrder.verify(mockGitProvider).commitFiles(
            eq("https://github.com/org/infra-repo"), eq("onboard/payments-app"),
            eq(manifests), anyString());
        inOrder.verify(mockGitProvider).createPullRequest(
            eq("https://github.com/org/infra-repo"), eq("onboard/payments-app"),
            eq("main"), contains("1 namespaces, 1 ArgoCD"), anyString());

        assertThat(result.url()).isEqualTo("https://github.com/org/infra-repo/pull/42");
    }
}
```

### Backend Integration Test — Confirm Endpoint

```java
@QuarkusTest
class OnboardingConfirmIT {

    @InjectMock
    GitProvider gitProvider;

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "default"),
        @Claim(key = "role", value = "member")
    })
    void confirmReturns201WithPersistedApplication() {
        // Pre-create clusters in DB for environment mapping
        when(gitProvider.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new PullRequest("https://git.example.com/pr/1", 1, "Onboard"));

        given()
            .contentType("application/json")
            .body("""
                {
                    "appName": "my-app",
                    "gitRepoUrl": "https://github.com/team/app",
                    "runtimeType": "Quarkus/Java",
                    "detectedEnvironments": ["dev", "qa", "prod"],
                    "environmentClusterMap": {"dev": 1, "qa": 2, "prod": 3},
                    "buildClusterId": 1
                }
            """)
        .when()
            .post("/api/v1/teams/{teamId}/applications/onboard/confirm", getTeamId())
        .then()
            .statusCode(201)
            .body("applicationName", is("my-app"))
            .body("onboardingPrUrl", is("https://git.example.com/pr/1"))
            .body("promotionChain", contains("dev", "qa", "prod"));

        // Verify Application persisted
        // Verify Environment entities persisted with correct promotion_order
    }
}
```

### Frontend Test — ProvisioningProgressTracker

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProvisioningProgressTracker } from './ProvisioningProgressTracker';

describe('ProvisioningProgressTracker', () => {
  const baseSteps: ProvisioningStep[] = [
    { id: 'branch', label: 'Creating branch in infra repo', status: 'pending' },
    { id: 'commit', label: 'Committing manifests', status: 'pending' },
    { id: 'pr', label: 'Creating pull request', status: 'pending' },
  ];

  it('shows all steps pending with 0/3 counter', () => {
    render(<ProvisioningProgressTracker steps={baseSteps} totalSteps={3} onRetry={vi.fn()} />);
    expect(screen.getByText(/0\/3 complete/)).toBeInTheDocument();
  });

  it('shows retry button when a step fails', () => {
    const failedSteps = [
      { ...baseSteps[0], status: 'completed' as const },
      { ...baseSteps[1], status: 'failed' as const, error: 'Git server error' },
      baseSteps[2],
    ];
    render(<ProvisioningProgressTracker steps={failedSteps} totalSteps={3} onRetry={vi.fn()} />);
    expect(screen.getByText('Git server error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('has aria-live region', () => {
    const { container } = render(
      <ProvisioningProgressTracker steps={baseSteps} totalSteps={3} onRetry={vi.fn()} />
    );
    expect(container.querySelector('[aria-live="polite"]')).toBeInTheDocument();
  });
});
```

### Frontend Test — OnboardingCompletionPanel

```tsx
describe('OnboardingCompletionPanel', () => {
  const mockResult: OnboardingResult = {
    applicationId: 1,
    applicationName: 'payment-svc',
    onboardingPrUrl: 'https://github.com/org/infra/pull/42',
    namespacesCreated: 4,
    argoCdAppsCreated: 4,
    promotionChain: ['dev', 'qa', 'prod'],
  };

  it('displays success message and PR link', () => {
    render(<OnboardingCompletionPanel result={mockResult} teamId="1" />);
    expect(screen.getByText(/onboarded successfully/i)).toBeInTheDocument();
    const prLink = screen.getByRole('link', { name: /view onboarding pr/i });
    expect(prLink).toHaveAttribute('href', 'https://github.com/org/infra/pull/42');
    expect(prLink).toHaveAttribute('target', '_blank');
  });
});
```

### Onboarding Ends at PR Creation

Per AR18: "Onboarding workflow ends at PR creation to centralized infra repo — portal does not track PR status or real-time resource materialization." The portal does NOT:
- Monitor whether the PR is merged
- Track namespace creation by ArgoCD
- Show real resource materialization progress

The progress tracker shows the portal's own operations (branch → commit → PR), NOT infrastructure provisioning. The wizard completion state is "PR created successfully" — the infra team reviews and merges asynchronously.

### Error Messages — Developer Language

Error messages from PortalIntegrationException must use developer-friendly language per the architecture's domain translation rules. Examples:
- Branch creation failure: "Could not create branch in infrastructure repository — the Git server returned an error"
- Commit failure: "Failed to commit manifest files — check that the portal has write access to the infrastructure repository"
- PR creation failure: "Pull request creation failed — a branch with this name may already exist"

Never expose Git API internals, HTTP status codes, or infrastructure terms in error messages shown to the user.

### Existing Code to Reuse

| Component | Location | Usage |
|-----------|----------|-------|
| `GitProvider` | `integration/git/GitProvider.java` | createBranch, commitFiles, createPullRequest |
| `GitProviderConfig` | `integration/git/GitProviderConfig.java` | `infraRepoUrl()` for the target repo |
| `PullRequest` model | `integration/git/model/PullRequest.java` | Return type from createPullRequest — has `url()`, `number()`, `title()` |
| `ManifestGenerator` | `gitops/ManifestGenerator.java` | `generateAllManifests()` returns `Map<String, String>` of filePath → yamlContent |
| `OnboardingService` | `onboarding/OnboardingService.java` | MODIFY — add `confirmOnboarding()` method, already has `buildPlan()` |
| `OnboardingResource` | `onboarding/OnboardingResource.java` | MODIFY — add `/onboard/confirm` endpoint |
| `OnboardingPlanRequest` | `onboarding/OnboardingPlanRequest.java` | Reuse for rebuilding plan in confirm flow |
| `OnboardingPlanResult` | `onboarding/OnboardingPlanResult.java` | Plan result with namespaces, argoCdApps, promotionChain |
| `Application` entity | `application/Application.java` | Persist with name, teamId, gitRepoUrl, runtimeType, onboardingPrUrl, onboardedAt |
| `Environment` entity | `environment/Environment.java` | Persist with name, applicationId, clusterId, namespace, promotionOrder |
| `TeamContext` | `auth/TeamContext.java` | `getTeamIdentifier()` for team name, `getTeamId()` for FK |
| `PermissionFilter` | `auth/PermissionFilter.java` | Already handles `/onboard/*` paths → action="onboard" |
| `GlobalExceptionMapper` | `common/GlobalExceptionMapper.java` | PortalIntegrationException → 502, PersistenceException → 409 |
| `PortalIntegrationException` | `integration/PortalIntegrationException.java` | Error wrapping for Git failures |
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Display errors in failure state |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Loading state during API call |
| `apiFetch` | `api/client.ts` | HTTP calls with auth |
| Casbin policy | `casbin/policy.csv` | `(member, applications, onboard)` already allows |

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/gitops/
└── OnboardingPrBuilder.java

src/main/java/com/portal/onboarding/
├── OnboardingConfirmRequest.java
└── OnboardingResultDto.java

src/test/java/com/portal/gitops/
└── OnboardingPrBuilderTest.java
```

**Modified backend files:**
```
src/main/java/com/portal/onboarding/OnboardingResource.java  (add confirm endpoint)
src/main/java/com/portal/onboarding/OnboardingService.java   (add confirmOnboarding method)
src/test/java/com/portal/onboarding/OnboardingResourceIT.java  (add confirm tests)
src/test/java/com/portal/onboarding/OnboardingServiceTest.java  (add confirm tests)
```

**New frontend files:**
```
src/main/webui/src/components/onboarding/
├── ProvisioningProgressTracker.tsx
├── ProvisioningProgressTracker.test.tsx
├── OnboardingCompletionPanel.tsx
└── OnboardingCompletionPanel.test.tsx
```

**Modified frontend files:**
```
src/main/webui/src/types/onboarding.ts  (add confirm types)
src/main/webui/src/api/onboarding.ts  (add confirmOnboarding function)
src/main/webui/src/routes/OnboardingWizardPage.tsx  (implement steps 4 and 5)
src/main/webui/src/routes/OnboardingWizardPage.test.tsx  (add steps 4-5 tests)
```

### Previous Story Intelligence

**Story 2.4 (Onboarding Plan & Manifest Generation):**
- OnboardingService at `com.portal.onboarding` with `buildPlan()` method
- ManifestGenerator at `com.portal.gitops` with `generateAllManifests(plan, gitRepoUrl)` returning `Map<String, String>` (filePath → yamlContent)
- OnboardingPlanResult has `namespaces()`, `argoCdApps()`, `promotionChain()`, `generatedManifests()`
- OnboardingResource has `POST /onboard/plan` endpoint — `/onboard/confirm` follows the same sub-path pattern
- Wizard step 3 functional with ProvisioningPlanPreview — steps 4–5 are placeholder
- "Confirm & Create PR" button visible but leads to placeholder — this story makes it functional
- Qute templates in `src/main/resources/templates/gitops/` for manifest generation
- Environment ordering via convention map (dev=0, qa=1, staging=2, prod=5)

**Story 2.3 (Application Registration & Contract Validation):**
- OnboardingResource at `@Path("/api/v1/teams/{teamId}/applications")` with `@POST @Path("/onboard")`
- ContractValidator validates repo against .helm contract
- Frontend wizard has steps 1 (repo URL) and 2 (contract validation) functional
- PF6 Wizard with WizardStep components — controlled navigation
- TeamContext verification pattern: `if (!teamContext.getTeamId().equals(teamId)) throw new NotFoundException()`
- `@InjectMock GitProvider` pattern for integration tests

**Story 2.2 (Git Provider Abstraction):**
- GitProvider interface: `createBranch(repoUrl, branchName, fromBranch)`, `commitFiles(repoUrl, branch, files, message)`, `createPullRequest(repoUrl, branch, targetBranch, title, description)`
- PullRequest model: `url()`, `number()`, `title()`
- DevGitProvider for dev/test: createPullRequest returns `new PullRequest("https://dev-git/pr/1", 1, title)`
- GitProviderConfig with `infraRepoUrl()` for the centralized infra repo
- Error handling: all failures wrapped in `PortalIntegrationException(system="git")`

**Story 2.1 (Application & Environment Data Model):**
- Application entity: `name`, `teamId`, `gitRepoUrl`, `runtimeType`, `onboardingPrUrl` (nullable), `onboardedAt` (nullable)
- Environment entity: `name`, `applicationId`, `clusterId`, `namespace`, `promotionOrder`
- `PanacheEntityBase` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` mandatory
- `findByTeam(Long teamId)` on Application, `findByApplicationOrderByPromotionOrder(Long appId)` on Environment
- Unique constraint `uq_applications_team_id_name` — catches duplicate app names per team

**Epic 1 patterns:**
- `@ApplicationScoped` for all service beans
- `@RequestScoped` for TeamContext
- Inject via `@Inject` field injection
- Unit tests mock dependencies via reflection (`Field.setAccessible(true)`)
- Integration tests use `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` + REST Assured
- Frontend tests use Vitest + RTL; query by role/label, not CSS class
- PF6 components exclusively; PF CSS tokens for styling

### What NOT to Build in This Story

- **No PR status tracking** — onboarding ends at PR creation (AR18)
- **No real infrastructure provisioning monitoring** — the progress tracker shows portal operations, not ArgoCD sync
- **No DevSpaces integration** — the "Open in DevSpaces" link is a placeholder (Epic 3)
- **No first build trigger** — mentioned in UX spec but is an Epic 4 feature
- **No application overview page** — the "View {appName}" link navigates to it, but the page itself is Story 2.6+
- **No environment status from ArgoCD** — that's Story 2.7
- **No WebSocket/SSE for real-time updates** — simulated progression with timed state changes is sufficient for MVP

### References

- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.5] — Full acceptance criteria
- [Source: planning-artifacts/architecture.md § GitOps Contract Specification] — Infra repo structure, onboarding workflow ends at PR creation
- [Source: planning-artifacts/architecture.md § Git provider abstraction] — GitProvider interface: createBranch, commitFiles, createPullRequest
- [Source: planning-artifacts/architecture.md § Onboarding Workflow] — 6-step flow: validate → generate → PR → review → merge → sync
- [Source: planning-artifacts/architecture.md § Data Architecture] — Application entity: onboardingPrUrl, onboardedAt; Environment entity: promotionOrder
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure] — gitops/OnboardingPrBuilder.java, onboarding/ package
- [Source: planning-artifacts/architecture.md § Architectural Boundaries] — "OnboardingPrBuilder orchestrates the full PR workflow using ManifestGenerator + GitProvider"
- [Source: planning-artifacts/architecture.md § Configuration Properties] — portal.git.infra-repo-url, portal.git.token
- [Source: planning-artifacts/architecture.md § Gap Analysis] — "Onboarding UX adjustment: wizard final state should be PR created successfully with link"
- [Source: planning-artifacts/ux-design-specification.md § Journey 1 (lines 605-691)] — Wizard steps 4-5, retry, completion
- [Source: planning-artifacts/ux-design-specification.md § Provisioning Progress Tracker (lines 993-1031)] — Component anatomy, per-step states, accessibility
- [Source: planning-artifacts/ux-design-specification.md § Feedback Patterns (lines 1186-1221)] — Onboarding complete: wizard final step with next action links
- [Source: planning-artifacts/ux-design-specification.md § Error Recovery (line 887)] — "Onboarding partial failures offer retry for failed steps only"
- [Source: planning-artifacts/ux-design-specification.md § Deep Link Patterns (lines 1255-1275)] — Link button variant with ↗ suffix, new tab, labeled with target tool
- [Source: planning-artifacts/ux-design-specification.md § Emotional Design (line 173)] — "Celebrate the wins quietly — clear success state, not confetti"
- [Source: project-context.md § Technology Stack] — Quarkus 3.34.x, PF6 v6.4.1, React 18, TypeScript strict
- [Source: project-context.md § Framework-Specific Rules] — PF6 ProgressStepper, Button with component="a", EmptyState
- [Source: project-context.md § Testing Rules] — Unit: Class.Test.java, IT: Class.IT.java, Frontend: Component.test.tsx
- [Source: project-context.md § Anti-Patterns] — No cross-package entity imports (exception: onboarding orchestration), REST → Service → Adapter chain
- [Source: project-context.md § GitOps Contract] — Onboarding creates PR to infra repo, not direct commit
- [Source: implementation-artifacts/2-4-onboarding-plan-manifest-generation.md] — OnboardingService.buildPlan(), ManifestGenerator, OnboardingPlanResult, wizard step 3
- [Source: implementation-artifacts/2-3-application-registration-contract-validation.md] — OnboardingResource, ContractValidator, wizard steps 1-2, TeamContext verification
- [Source: implementation-artifacts/2-2-git-provider-abstraction.md] — GitProvider interface, PullRequest model, DevGitProvider, error handling pattern
- [Source: implementation-artifacts/2-1-application-environment-data-model.md] — Application/Environment entities, Panache pattern, unique constraints

## Dev Agent Record

### Agent Model Used

claude-4-sonnet (reconstructed during Epic 2 retrospective — original agent session did not update this file)

### Debug Log References

No debug log was preserved — this record was reconstructed post-hoc during the Epic 2 retrospective. This gap led to the "Story Completion Gate" rule being added to project-context.md.

### Completion Notes List

- All 13 tasks completed: OnboardingPrBuilder, OnboardingConfirmRequest/ResultDto, OnboardingService.confirmOnboarding(), confirm endpoint, frontend types/API, ProvisioningProgressTracker, OnboardingCompletionPanel, wizard steps 4-5 integration, unit tests, integration tests, frontend component tests
- Simulated step progression implemented as designed (timed state updates during single API call)
- Wizard Back button disabled on step 4 as specified
- DevSpaces link rendered as disabled placeholder per spec

### File List

**New backend files:**
- `src/main/java/com/portal/gitops/OnboardingPrBuilder.java`
- `src/main/java/com/portal/onboarding/OnboardingConfirmRequest.java`
- `src/main/java/com/portal/onboarding/OnboardingResultDto.java`
- `src/test/java/com/portal/gitops/OnboardingPrBuilderTest.java`

**Modified backend files:**
- `src/main/java/com/portal/onboarding/OnboardingResource.java`
- `src/main/java/com/portal/onboarding/OnboardingService.java`
- `src/test/java/com/portal/onboarding/OnboardingResourceIT.java`
- `src/test/java/com/portal/onboarding/OnboardingServiceTest.java`

**New frontend files:**
- `src/main/webui/src/components/onboarding/ProvisioningProgressTracker.tsx`
- `src/main/webui/src/components/onboarding/ProvisioningProgressTracker.test.tsx`
- `src/main/webui/src/components/onboarding/OnboardingCompletionPanel.tsx`
- `src/main/webui/src/components/onboarding/OnboardingCompletionPanel.test.tsx`

**Modified frontend files:**
- `src/main/webui/src/types/onboarding.ts`
- `src/main/webui/src/api/onboarding.ts`
- `src/main/webui/src/routes/OnboardingWizardPage.tsx`
- `src/main/webui/src/routes/OnboardingWizardPage.test.tsx`
