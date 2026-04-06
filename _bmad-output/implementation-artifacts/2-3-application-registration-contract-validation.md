# Story 2.3: Application Registration & Contract Validation

Status: done

## Story

As a developer,
I want to register a new application by providing its Git repository URL and see a clear checklist of which contract requirements pass or fail,
So that I know exactly what needs to be fixed before my application can be onboarded.

## Acceptance Criteria

1. **Step 1 — Repository URL submission**
   - **Given** the OnboardingWizardPage renders step 1
   - **When** the developer enters a Git repository URL and submits
   - **Then** the backend calls `GitProvider.validateRepoAccess()` to confirm the portal can read the repository
   - **And** if the repo is unreachable, an error is displayed: "Cannot access repository — check the URL and ensure the portal has read access"
   - **And** if accessible, the wizard advances to step 2

2. **Step 2 — Contract validation checks**
   - **Given** the wizard is on step 2 (contract validation)
   - **When** the backend ContractValidator runs
   - **Then** it checks the following requirements via GitProvider file/directory reads:
     1. `.helm/build/` directory exists and contains `Chart.yaml`
     2. `.helm/run/` directory exists and contains `Chart.yaml`
     3. `values-build.yaml` exists in `.helm/`
     4. At least one `values-run-<env>.yaml` exists in `.helm/`
     5. Runtime detection: presence of `pom.xml` (Quarkus/Java), `package.json` (Node.js), or `*.csproj` (.NET)

3. **All checks pass UI**
   - **Given** the ContractValidationChecklist component renders the results
   - **When** all checks pass
   - **Then** each item shows a green check icon with the requirement name and what was found (e.g., "Runtime Detected: Quarkus via pom.xml")
   - **And** the header shows "5/5 passed" with a green accent
   - **And** the "Next" button is enabled

4. **Some checks fail UI**
   - **Given** one or more checks fail
   - **When** the checklist renders
   - **Then** failed items show a red X icon with the requirement name and a specific fix instruction (e.g., "Helm Build Chart — Not found. Create `.helm/build/` directory with a valid Chart.yaml")
   - **And** passed items still show green check
   - **And** the header shows "N/5 passed" with a red accent
   - **And** a "Retry Validation" button is available for the developer to re-validate after fixing their repo
   - **And** the "Next" button is disabled until all checks pass

5. **Environment detection**
   - **Given** the ContractValidator detects environment-specific values files
   - **When** files matching `values-run-*.yaml` are found in `.helm/`
   - **Then** the environment names are extracted (e.g., `values-run-dev.yaml` → "dev", `values-run-qa.yaml` → "qa")
   - **And** these detected environments are passed forward to the provisioning plan step as the default promotion chain

6. **Accessibility**
   - **Given** the checklist items render
   - **When** reviewing accessibility
   - **Then** each item uses role="listitem" with an aria-label combining status, requirement name, and detail text

7. **REST endpoint**
   - **Given** `POST /api/v1/teams/{teamId}/applications/onboard` is called with the Git repo URL
   - **When** the backend processes it
   - **Then** a ContractValidationResult is returned containing the pass/fail status of each check, detected runtime type, and detected environment names

## Tasks / Subtasks

- [x] Task 1: Create backend DTOs for contract validation (AC: #7)
  - [x] Create `ValidateRepoRequest.java` record in `com.portal.onboarding` with field: `@NotBlank String gitRepoUrl`
  - [x] Create `ContractCheck.java` record in `com.portal.onboarding` with fields: `String name`, `boolean passed`, `String detail`, `String fixInstruction` (nullable)
  - [x] Create `ContractValidationResult.java` record in `com.portal.onboarding` with fields: `boolean allPassed`, `List<ContractCheck> checks`, `String runtimeType` (nullable), `List<String> detectedEnvironments`

- [x] Task 2: Create ContractValidator service (AC: #2, #5)
  - [x] Create `ContractValidator.java` in `com.portal.onboarding`, `@ApplicationScoped`
  - [x] Inject `GitProvider` (from Story 2.2)
  - [x] Method: `ContractValidationResult validate(String repoUrl)` — runs all 5 checks
  - [x] Check 1 — Helm Build Chart: `listDirectory(repoUrl, "main", ".helm/build")` → verify `Chart.yaml` present. Pass detail: "Helm build chart found". Fail fix: "Create `.helm/build/` directory with a valid Chart.yaml"
  - [x] Check 2 — Helm Run Chart: `listDirectory(repoUrl, "main", ".helm/run")` → verify `Chart.yaml` present. Pass detail: "Helm run chart found". Fail fix: "Create `.helm/run/` directory with a valid Chart.yaml"
  - [x] Check 3 — Build Values: `readFile(repoUrl, "main", ".helm/values-build.yaml")`. Pass detail: "Build values file found". Fail fix: "Create `values-build.yaml` in `.helm/` directory"
  - [x] Check 4 — Environment Values: `listDirectory(repoUrl, "main", ".helm")` → filter for `values-run-*.yaml`. Pass detail: "N environment(s) detected: dev, qa, prod". Fail fix: "Create at least one `values-run-<env>.yaml` file in `.helm/`"
  - [x] Check 5 — Runtime Detection: check root for `pom.xml` (→ "Quarkus/Java"), `package.json` (→ "Node.js"), `*.csproj` (→ ".NET"). Pass detail: "Runtime Detected: {type} via {file}". Fail fix: "No supported runtime detected. Ensure `pom.xml`, `package.json`, or `*.csproj` exists in the repository root"
  - [x] Extract environment names from `values-run-{env}.yaml` filenames and return in `detectedEnvironments`
  - [x] Catch `PortalIntegrationException` per check (mark as failed, do not abort remaining checks)

- [x] Task 3: Create OnboardingResource REST endpoint (AC: #1, #7)
  - [x] Create `OnboardingResource.java` in `com.portal.onboarding`
  - [x] `@Path("/api/v1/teams/{teamId}/applications")`
  - [x] `POST /onboard` — accepts `@Valid ValidateRepoRequest`, returns `ContractValidationResult`
  - [x] Inject `GitProvider`, `ContractValidator`, `TeamContext`
  - [x] Step 1: call `gitProvider.validateRepoAccess(request.gitRepoUrl())` — on failure, PortalIntegrationException propagates to GlobalExceptionMapper → 502 with "Cannot access repository"
  - [x] Step 2: call `contractValidator.validate(request.gitRepoUrl())` → return result as 200 JSON
  - [x] Verify `teamContext.getTeamId()` matches `{teamId}` — return 404 if mismatch (security: no cross-team leakage)

- [x] Task 4: Create frontend types for onboarding (AC: #3, #4, #5, #7)
  - [x] Create `src/main/webui/src/types/onboarding.ts`
  - [x] Types: `ContractCheck` (name, passed, detail, fixInstruction), `ContractValidationResult` (allPassed, checks, runtimeType, detectedEnvironments), `ValidateRepoRequest` (gitRepoUrl)

- [x] Task 5: Create frontend API functions for onboarding (AC: #1, #7)
  - [x] Create `src/main/webui/src/api/onboarding.ts`
  - [x] `validateRepo(teamId: string, gitRepoUrl: string): Promise<ContractValidationResult>` — POST to `/api/v1/teams/${teamId}/applications/onboard`

- [x] Task 6: Implement OnboardingWizardPage with PF6 Wizard (AC: #1)
  - [x] Replace placeholder in `src/main/webui/src/routes/OnboardingWizardPage.tsx`
  - [x] Use PF6 `Wizard` component with controlled step navigation
  - [x] Step 1 "Repository URL": `TextInput` for Git repo URL + `Button` to validate. Show `Spinner` during validation. Show `ErrorAlert` if repo unreachable. On success → advance to step 2
  - [x] Step 2 "Contract Validation": render `ContractValidationChecklist` with result. "Retry Validation" button to re-run. "Next" disabled until `allPassed === true`
  - [x] Steps 3–5: placeholder steps ("Provisioning Plan", "Create PR", "Complete") with "Coming soon" — future stories 2.4 and 2.5
  - [x] Use `useParams()` to get `teamId` from route
  - [x] Manage state: `repoUrl`, `validationResult`, `error`, `isValidating`, `activeStep`

- [x] Task 7: Create ContractValidationChecklist component (AC: #3, #4, #5, #6)
  - [x] Create `src/main/webui/src/components/onboarding/ContractValidationChecklist.tsx`
  - [x] Props: `result: ContractValidationResult`, `onRetry: () => void`, `isRetrying: boolean`
  - [x] Header: "N/5 passed" — green `Label` when all pass, red `Label` when any fail
  - [x] List of items using PF6 `List` with `role="list"`, each item has `role="listitem"`
  - [x] Each item: PF6 `Icon` — green `CheckCircleIcon` for passed, red `TimesCircleIcon` for failed
  - [x] Passed items show: icon + check name + detail text
  - [x] Failed items show: icon + check name + fix instruction text
  - [x] Each item's `aria-label`: `"{Passed|Failed}: {name} — {detail or fixInstruction}"`
  - [x] "Retry Validation" `Button` (variant="secondary") shown when any check fails, calls `onRetry`
  - [x] Show detected environments if present: "Detected environments: dev, qa, prod"

- [x] Task 8: Write backend unit tests for ContractValidator (AC: #2, #5)
  - [x] Create `ContractValidatorTest.java` in `src/test/java/com/portal/onboarding/`
  - [x] Mock `GitProvider` — control `listDirectory` and `readFile` responses
  - [x] Test all 5 checks pass: verify `allPassed == true`, all checks have `passed == true`
  - [x] Test check 1 fails (no Chart.yaml in .helm/build/): verify check is failed with fix instruction
  - [x] Test check 4 detects multiple environments: verify `detectedEnvironments` contains extracted names
  - [x] Test check 5 runtime detection: pom.xml → "Quarkus/Java", package.json → "Node.js"
  - [x] Test single check failure doesn't abort others: remaining checks still run
  - [x] Test PortalIntegrationException in one check: mark as failed, continue remaining

- [x] Task 9: Write backend integration test for OnboardingResource (AC: #1, #7)
  - [x] Create `OnboardingResourceIT.java` in `src/test/java/com/portal/onboarding/`
  - [x] `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` with team claims
  - [x] Use `@InjectMock` on `GitProvider` to control mock responses
  - [x] Test POST `/api/v1/teams/{teamId}/applications/onboard` with valid request → 200 + ContractValidationResult JSON
  - [x] Test repo unreachable (GitProvider throws PortalIntegrationException) → 502 error response
  - [x] Test missing gitRepoUrl → 400 validation error
  - [x] Test unauthorized role → 403 (but actually member CAN onboard, so test admin/lead/member all succeed)

- [x] Task 10: Write frontend component tests (AC: #3, #4, #5, #6)
  - [x] Create `src/main/webui/src/components/onboarding/ContractValidationChecklist.test.tsx`
  - [x] Test all checks passed: green icons, "5/5 passed" header, no retry button
  - [x] Test some checks failed: red icons for failures, "N/5 passed" header, retry button present
  - [x] Test retry button calls onRetry callback
  - [x] Test accessibility: each item has correct aria-label
  - [x] Test detected environments displayed
  - [x] Create `src/main/webui/src/routes/OnboardingWizardPage.test.tsx`
  - [x] Test wizard renders step 1 by default
  - [x] Test submit triggers API call
  - [x] Test error display when repo unreachable

### Review Findings

- [x] [Review][Patch] Repo access failures return provider-specific text instead of the required onboarding error — **Fixed**: OnboardingResource now catches PortalIntegrationException from validateRepoAccess and re-throws with AC1-mandated message
- [x] [Review][Patch] Wizard never advances to contract validation after a successful repo check — **Fixed**: Added AutoAdvance component using PF6 useWizardContext().goToStepById() on rising edge of validationResult
- [x] [Review][Patch] Contract validation can report a passed runtime/environment check while returning `null` or `[]` in the result payload — **Fixed**: Refactored to use private inner records (EnvironmentCheckResult, RuntimeCheckResult) so env names and runtime type are extracted from the same GitProvider call as the check itself
- [x] [Review][Patch] Editing the repository URL after a successful validation leaves stale results enabled for step 2 — **Fixed**: TextInput onChange handler now clears validationResult, plan, and planStepEntered when URL is edited after a successful validation

## Dev Notes

### Hard Dependency: Story 2.2 (GitProvider)

This story **requires** Story 2.2 (Git Provider Abstraction) to be implemented first. The `ContractValidator` and `OnboardingResource` inject `GitProvider` to validate repo access and read files/directories. In tests, `DevGitProvider` (from 2.2) is used via `portal.git.provider=dev` config, and `@InjectMock` overrides it for specific test scenarios.

If Story 2.2 is not yet complete, this story cannot be developed.

### Package: `com.portal.onboarding`

This is a NEW package — no code exists yet. The architecture specifies a dedicated `onboarding/` domain package separate from `application/`. Files:

```
com.portal.onboarding/
├── OnboardingResource.java
├── ContractValidator.java
├── ContractValidationResult.java
├── ContractCheck.java
└── ValidateRepoRequest.java
```

No `package-info.java` exists yet — create it or let the class declarations establish the package.

### REST Endpoint Design

**Endpoint:** `POST /api/v1/teams/{teamId}/applications/onboard`

**Why this path (not `/onboard/validate`):** The PermissionFilter extracts the Casbin resource by finding the last non-ID, non-action path segment. "onboard" is already in `ACTION_SEGMENTS`. The path `teams/{teamId}/applications/onboard` yields:
- Resource: `applications` (last non-ID, non-action segment)
- Action: `onboard` (because path contains `/onboard` + POST method)
- Casbin check: `(member, applications, onboard)` → **ALLOWED** per policy.csv line 4

Do NOT add `/validate` as a sub-path — it would require adding "validate" to `ACTION_SEGMENTS` in PermissionFilter.

The OnboardingResource class is `@Path("/api/v1/teams/{teamId}/applications")` with a `@POST @Path("/onboard")` method. This keeps the resource path clean for future application endpoints that will share the same base path.

**Request body:**
```java
public record ValidateRepoRequest(@NotBlank String gitRepoUrl) {}
```

**Response:** `ContractValidationResult` as JSON (200 OK). If repo unreachable, `PortalIntegrationException` propagates → GlobalExceptionMapper returns 502.

### Contract Validation — Detailed Logic

The `ContractValidator` runs 5 checks sequentially. Each check is independent — a failure in one does NOT abort the remaining checks. Use try/catch per check.

**Default branch:** Use `"main"` as the default branch for all file reads. This is a simplification for MVP — later stories may allow specifying the branch.

**Check 1 — Helm Build Chart:**
```java
List<String> buildFiles = gitProvider.listDirectory(repoUrl, "main", ".helm/build");
boolean hasBuildChart = buildFiles.contains("Chart.yaml");
```

**Check 2 — Helm Run Chart:**
```java
List<String> runFiles = gitProvider.listDirectory(repoUrl, "main", ".helm/run");
boolean hasRunChart = runFiles.contains("Chart.yaml");
```

**Check 3 — Build Values:**
```java
try {
    gitProvider.readFile(repoUrl, "main", ".helm/values-build.yaml");
    // passed
} catch (PortalIntegrationException e) {
    // failed — file not found
}
```

**Check 4 — Environment Values:**
```java
List<String> helmFiles = gitProvider.listDirectory(repoUrl, "main", ".helm");
List<String> envFiles = helmFiles.stream()
    .filter(f -> f.startsWith("values-run-") && f.endsWith(".yaml"))
    .toList();
List<String> envNames = envFiles.stream()
    .map(f -> f.replace("values-run-", "").replace(".yaml", ""))
    .toList();
boolean hasEnvValues = !envFiles.isEmpty();
```

**Check 5 — Runtime Detection:**
```java
List<String> rootFiles = gitProvider.listDirectory(repoUrl, "main", "");
if (rootFiles.contains("pom.xml")) runtimeType = "Quarkus/Java";
else if (rootFiles.contains("package.json")) runtimeType = "Node.js";
else if (rootFiles.stream().anyMatch(f -> f.endsWith(".csproj"))) runtimeType = ".NET";
```

### PortalIntegrationException Handling Per Check

Each check wraps its GitProvider calls in try/catch. If a `PortalIntegrationException` is thrown (e.g., directory not found returns 404 from git server), the check is marked as failed with the fix instruction. The remaining checks continue.

The **repo access validation** in the OnboardingResource is a separate step BEFORE contract validation. If `validateRepoAccess()` throws, the exception propagates directly (not caught) → GlobalExceptionMapper returns 502.

### Frontend — PF6 Wizard Pattern

PatternFly 6 `Wizard` component with controlled navigation. Key import:

```tsx
import { Wizard, WizardStep } from '@patternfly/react-core';
```

The wizard has 5 steps but only 2 are functional in this story:

```tsx
<Wizard>
  <WizardStep name="Repository URL" id="repo-url">
    {/* Step 1 content */}
  </WizardStep>
  <WizardStep name="Contract Validation" id="contract-validation"
    isDisabled={!validationResult}>
    {/* Step 2 content */}
  </WizardStep>
  <WizardStep name="Provisioning Plan" id="plan" isDisabled>
    <p>Coming soon — provisioning plan preview.</p>
  </WizardStep>
  <WizardStep name="Create PR" id="create-pr" isDisabled>
    <p>Coming soon — infrastructure PR creation.</p>
  </WizardStep>
  <WizardStep name="Complete" id="complete" isDisabled>
    <p>Coming soon — onboarding completion.</p>
  </WizardStep>
</Wizard>
```

**Step 1 — Repository URL:**
- PF6 `Form` with `FormGroup` + `TextInput` for URL
- `Button` (primary) to trigger validation
- `Spinner` while validating
- `ErrorAlert` from shared components if repo unreachable
- On success: programmatically advance to step 2

**Step 2 — Contract Validation:**
- Render `ContractValidationChecklist` component
- "Retry Validation" button re-calls the API
- "Next" button (wizard built-in) disabled via step config until `allPassed`

### Frontend — ContractValidationChecklist Component

Dedicated component in `components/onboarding/`:

```
src/main/webui/src/components/onboarding/
├── ContractValidationChecklist.tsx
└── ContractValidationChecklist.test.tsx
```

**Props interface:**
```tsx
interface ContractValidationChecklistProps {
  result: ContractValidationResult;
  onRetry: () => void;
  isRetrying: boolean;
}
```

**Icon usage:**
```tsx
import { CheckCircleIcon, TimesCircleIcon } from '@patternfly/react-icons';
```
- Passed: `<CheckCircleIcon color="var(--pf-t--global--color--status--success--default)" />`
- Failed: `<TimesCircleIcon color="var(--pf-t--global--color--status--danger--default)" />`

Use PF6 CSS design tokens (custom properties) for colors, NOT hardcoded hex values.

**Header:**
```tsx
<Label color={result.allPassed ? 'green' : 'red'}>
  {passedCount}/5 passed
</Label>
```

### Frontend — API Function Pattern

Follow `api/clusters.ts` pattern:

```tsx
import { apiFetch } from './client';
import type { ContractValidationResult, ValidateRepoRequest } from '../types/onboarding';

export function validateRepo(
  teamId: string,
  gitRepoUrl: string
): Promise<ContractValidationResult> {
  return apiFetch<ContractValidationResult>(
    `/api/v1/teams/${teamId}/applications/onboard`,
    {
      method: 'POST',
      body: JSON.stringify({ gitRepoUrl } satisfies ValidateRepoRequest),
    }
  );
}
```

### Frontend Types — `types/onboarding.ts`

```tsx
export interface ContractCheck {
  name: string;
  passed: boolean;
  detail: string;
  fixInstruction: string | null;
}

export interface ContractValidationResult {
  allPassed: boolean;
  checks: ContractCheck[];
  runtimeType: string | null;
  detectedEnvironments: string[];
}

export interface ValidateRepoRequest {
  gitRepoUrl: string;
}
```

### Backend Integration Test Pattern

Follow `ClusterResourceIT.java` pattern with `@InjectMock`:

```java
@QuarkusTest
class OnboardingResourceIT {

    @InjectMock
    GitProvider gitProvider;

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "default"),
        @Claim(key = "role", value = "member")
    })
    void validateReturns200WithContractResult() {
        // Mock GitProvider behavior
        when(gitProvider.listDirectory(anyString(), anyString(), eq(".helm/build")))
            .thenReturn(List.of("Chart.yaml", "templates"));
        // ... mock other checks
        
        given()
            .contentType("application/json")
            .body("""
                {"gitRepoUrl": "https://github.com/team/app"}
            """)
        .when()
            .post("/api/v1/teams/{teamId}/applications/onboard", getTeamId())
        .then()
            .statusCode(200)
            .body("allPassed", is(true))
            .body("checks.size()", is(5));
    }
}
```

**Note on `@InjectMock`:** This is Quarkus's CDI mock mechanism. Since `GitProvider` is produced by `GitProviderFactory`, `@InjectMock` replaces the produced bean with a Mockito mock. This works because the test profile uses `portal.git.provider=dev` but `@InjectMock` overrides the CDI bean regardless.

### Backend Unit Test Pattern

Follow `SecretManagerCredentialProviderTest.java` pattern with Mockito:

```java
class ContractValidatorTest {
    private ContractValidator validator;
    private GitProvider mockGitProvider;

    @BeforeEach
    void setUp() throws Exception {
        mockGitProvider = mock(GitProvider.class);
        validator = new ContractValidator();
        // Inject mock via reflection
        Field field = ContractValidator.class.getDeclaredField("gitProvider");
        field.setAccessible(true);
        field.set(validator, mockGitProvider);
    }
}
```

### Frontend Test Pattern

Follow `RefreshButton.test.tsx` pattern:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ContractValidationChecklist } from './ContractValidationChecklist';

describe('ContractValidationChecklist', () => {
  it('shows all checks passed with green header', () => {
    const result = { allPassed: true, checks: [...], ... };
    render(<ContractValidationChecklist result={result} onRetry={vi.fn()} isRetrying={false} />);
    expect(screen.getByText('5/5 passed')).toBeInTheDocument();
  });
});
```

For the wizard page test, wrap with `MemoryRouter` since it uses `useParams`:

```tsx
import { MemoryRouter, Route, Routes } from 'react-router-dom';

render(
  <MemoryRouter initialEntries={['/teams/default/onboard']}>
    <Routes>
      <Route path="/teams/:teamId/onboard" element={<OnboardingWizardPage />} />
    </Routes>
  </MemoryRouter>
);
```

### TeamContext Verification

The OnboardingResource should verify that the `{teamId}` path param matches the authenticated user's team. The `TeamContext` bean is populated by `TeamContextFilter` from the JWT `team` claim. Pattern:

```java
@Inject TeamContext teamContext;

if (!teamContext.getTeamId().equals(teamId)) {
    throw new NotFoundException("Team not found");
}
```

Return 404 (not 403) for cross-team access per security requirements.

### What NOT to Build in This Story

- **No Application entity creation** — the Application record is saved in Story 2.5 after PR creation
- **No Environment entity creation** — same, Story 2.5
- **No provisioning plan** — that's Story 2.4 (step 3 of the wizard)
- **No PR creation** — that's Story 2.5 (step 4 of the wizard)
- **No OnboardingService** — the service layer for orchestrating the full flow comes in Story 2.4–2.5. Story 2.3 only needs ContractValidator + OnboardingResource
- **No manifest generation** — that's Story 2.4 (ManifestGenerator, Qute templates)
- **No gitops/ package code** — comes in Story 2.4–2.5
- **No PermissionFilter changes** — "onboard" is already in ACTION_SEGMENTS, current filter works correctly

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/onboarding/
├── OnboardingResource.java
├── ContractValidator.java
├── ContractValidationResult.java
├── ContractCheck.java
└── ValidateRepoRequest.java

src/test/java/com/portal/onboarding/
├── ContractValidatorTest.java
└── OnboardingResourceIT.java
```

**New frontend files:**
```
src/main/webui/src/types/onboarding.ts
src/main/webui/src/api/onboarding.ts
src/main/webui/src/components/onboarding/
├── ContractValidationChecklist.tsx
└── ContractValidationChecklist.test.tsx
```

**Modified frontend files:**
```
src/main/webui/src/routes/OnboardingWizardPage.tsx (replace placeholder)
src/main/webui/src/routes/OnboardingWizardPage.test.tsx (new test file)
```

### Previous Story Intelligence

**Story 2.2 (Git Provider Abstraction):**
- GitProvider interface with 6 operations: validateRepoAccess, readFile, listDirectory, createBranch, commitFiles, createPullRequest
- DevGitProvider returns canned responses: validateRepoAccess no-ops, readFile returns "", listDirectory returns empty list
- For integration tests: `@InjectMock GitProvider` overrides the DevGitProvider CDI bean
- For unit tests: mock GitProvider directly

**Epic 1 learnings:**
- PF6 Wizard is `@patternfly/react-core` — already in package.json dependencies
- Frontend tests use Vitest + RTL — query by role/label, not CSS class
- Backend ITs use `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity` + REST Assured
- All existing tests pass — do not introduce regressions
- `ErrorAlert` component already exists for displaying PortalError inline
- `LoadingSpinner` component already exists for loading states
- `apiFetch` wrapper handles token injection and error parsing

### Existing Code to Reuse

| Component | Location | Usage |
|-----------|----------|-------|
| `ErrorAlert` | `components/shared/ErrorAlert.tsx` | Display repo access error in step 1 |
| `LoadingSpinner` | `components/shared/LoadingSpinner.tsx` | Show during validation |
| `apiFetch` | `api/client.ts` | HTTP calls with auth |
| `ApiError` | `api/client.ts` | Error type from apiFetch |
| `PortalError` | `types/error.ts` | Error shape |
| `useAuth` | `hooks/useAuth.ts` | Team context for API calls |
| `GlobalExceptionMapper` | `common/GlobalExceptionMapper.java` | Handles PortalIntegrationException → 502 |
| `PortalIntegrationException` | `integration/PortalIntegrationException.java` | Error type from GitProvider |
| `PermissionFilter` | `auth/PermissionFilter.java` | Already handles /onboard path → action="onboard" |
| `TeamContext` | `auth/TeamContext.java` | Team scoping in resource |

### References

- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.3] — Full acceptance criteria
- [Source: planning-artifacts/architecture.md § GitOps Contract Specification] — Contract checks: .helm/build, .helm/run, values-build.yaml, values-run-*.yaml, runtime detection
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure] — `onboarding/` package: OnboardingResource, ContractValidator, ContractValidationResult
- [Source: planning-artifacts/architecture.md § REST API Endpoints] — `/api/v1/teams/{teamId}/applications/{appId}/onboard`
- [Source: planning-artifacts/architecture.md § Structure Patterns] — Frontend: routes/, components/, api/, hooks/, types/ organization
- [Source: planning-artifacts/architecture.md § Format Patterns] — HTTP status codes, error response format, direct response (no wrapper)
- [Source: planning-artifacts/architecture.md § Frontend Component Details] — ContractValidationChecklist.tsx in components/onboarding/
- [Source: project-context.md § Framework-Specific Rules] — PF6 components exclusively, PF CSS tokens for styling, no custom CSS
- [Source: project-context.md § Testing Rules] — Unit: `<Class>Test.java`, IT: `<Class>IT.java`, Frontend: `<Component>.test.tsx`
- [Source: project-context.md § Anti-Patterns] — No cross-package entity imports, no hardcoded URLs, no custom CSS for PF-provided elements
- [Source: auth/PermissionFilter.java] — ACTION_SEGMENTS includes "onboard", mapAction returns "onboard" for POST paths containing /onboard
- [Source: casbin/policy.csv] — `p, member, applications, onboard` allows member role to onboard
- [Source: implementation-artifacts/2-2-git-provider-abstraction.md] — GitProvider interface, DevGitProvider, error handling pattern

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus-high-thinking

### Debug Log References

None — implementation proceeded without halts or blockers.

### Completion Notes List

- **Task 1**: Created 3 Java records (`ValidateRepoRequest`, `ContractCheck`, `ContractValidationResult`) in new `com.portal.onboarding` package.
- **Task 2**: Implemented `ContractValidator` service with 5 independent checks (helm build chart, helm run chart, build values, environment values, runtime detection). Each check catches `PortalIntegrationException` independently — a failure in one does not abort the rest. Environment names extracted from `values-run-*.yaml` filenames.
- **Task 3**: Created `OnboardingResource` at `POST /api/v1/teams/{teamId}/applications/onboard`. Validates team context (returns 404 for cross-team access), validates repo access via `GitProvider.validateRepoAccess()`, then runs contract validation. Unhandled `PortalIntegrationException` from repo access propagates to `GlobalExceptionMapper` → 502.
- **Task 4**: Created TypeScript interfaces matching Java DTOs exactly (`ContractCheck`, `ContractValidationResult`, `ValidateRepoRequest`) in `types/onboarding.ts`.
- **Task 5**: Created `validateRepo()` API function using `apiFetch` wrapper following existing `clusters.ts` pattern.
- **Task 6**: Replaced `OnboardingWizardPage` placeholder with PF6 `Wizard` component. Step 1: repo URL input with validation. Step 2: contract validation checklist with retry. Steps 3-5: placeholders for future stories.
- **Task 7**: Created `ContractValidationChecklist` component with PF6 design tokens for icons, `Label` for pass/fail header, `List` with proper ARIA attributes, and retry button.
- **Task 8**: 10 unit tests covering all checks pass, individual check failures, multiple environments, runtime detection (pom.xml, package.json, .csproj), exception handling per check, and no-runtime/no-env failure cases.
- **Task 9**: 7 integration tests covering happy path (200), repo unreachable (502), missing URL (400), cross-team (404), lead/admin access, and partial failures. Added `quarkus-junit5-mockito` dependency for `@InjectMock`.
- **Task 10**: 7 ContractValidationChecklist tests (all pass header, failed checks, retry callback, aria-labels, detected environments) + 4 OnboardingWizardPage tests (renders step 1, disabled button, API call, error display).

**Pre-existing test failures**: 3 tests in `GlobalExceptionMapperIT` fail with 404s — these are pre-existing and unrelated to this story (test stub resource at `/test/exceptions/` not being discovered by Quarkus test runtime).

### File List

**New backend files:**
- `developer-portal/src/main/java/com/portal/onboarding/ValidateRepoRequest.java`
- `developer-portal/src/main/java/com/portal/onboarding/ContractCheck.java`
- `developer-portal/src/main/java/com/portal/onboarding/ContractValidationResult.java`
- `developer-portal/src/main/java/com/portal/onboarding/ContractValidator.java`
- `developer-portal/src/main/java/com/portal/onboarding/OnboardingResource.java`
- `developer-portal/src/test/java/com/portal/onboarding/ContractValidatorTest.java`
- `developer-portal/src/test/java/com/portal/onboarding/OnboardingResourceIT.java`

**New frontend files:**
- `developer-portal/src/main/webui/src/types/onboarding.ts`
- `developer-portal/src/main/webui/src/api/onboarding.ts`
- `developer-portal/src/main/webui/src/components/onboarding/ContractValidationChecklist.tsx`
- `developer-portal/src/main/webui/src/components/onboarding/ContractValidationChecklist.test.tsx`
- `developer-portal/src/main/webui/src/routes/OnboardingWizardPage.test.tsx`

**Modified files:**
- `developer-portal/src/main/webui/src/routes/OnboardingWizardPage.tsx` (replaced placeholder with full wizard)
- `developer-portal/pom.xml` (added `quarkus-junit5-mockito` test dependency for `@InjectMock`)
