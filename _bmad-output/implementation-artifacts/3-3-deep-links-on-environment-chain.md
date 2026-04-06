# Story 3.3: Deep Links on Environment Chain

Status: ready-for-dev

## Story

As a developer,
I want each environment card in the chain to link directly to the ArgoCD Application for that environment,
So that I can investigate deployment details in ArgoCD when needed without searching for the right resource.

## Acceptance Criteria

1. **Given** an environment chain card is displayed with any status (Healthy, Unhealthy, Deploying, or Not Deployed), **When** the card is expanded (clicked), **Then** a "Open in ArgoCD ↗" DeepLinkButton is displayed in the expanded detail area, scoped to the ArgoCD Application for that specific environment (e.g., `<app>-run-<env>`), and clicking opens the ArgoCD Application UI in a new tab.

2. **Given** an environment card has status "Unhealthy", **When** the card renders in its collapsed state, **Then** the "Open in ArgoCD ↗" deep link is also visible directly on the card (not only in expanded detail), providing immediate access to investigation without an extra click.

3. **Given** the backend returns environment status DTOs (from Story 2.7), **When** the DTO includes deep link URLs, **Then** the `argocdDeepLink` field is populated by `ArgoCdRestAdapter` (existing), **And** a new `grafanaDeepLink` field is added to DTOs/types for future use in Epic 6 (null until Grafana configuration and health views are built).

4. **Given** the ArgoCD URL is not configured, **When** environment cards render, **Then** no ArgoCD deep link buttons are shown, and no error is displayed — deep links are optional enhancements.

5. **Given** a developer navigates the environment chain with keyboard, **When** a card is focused and expanded, **Then** the deep link buttons are reachable via Tab key within the expanded card, **And** each deep link button has an `aria-label`: "Open [environment name] in ArgoCD".

## Tasks / Subtasks

- [ ] Task 1: Add `grafanaDeepLink` field to backend DTOs (AC: #3)
  - [ ] 1.1 Add `grafanaDeepLink` parameter to `EnvironmentStatusDto` record
  - [ ] 1.2 Add `grafanaDeepLink` parameter to `EnvironmentChainEntryDto` record
  - [ ] 1.3 Update `EnvironmentMapper.merge()` to pass through `grafanaDeepLink`
  - [ ] 1.4 Update `ArgoCdRestAdapter.getEnvironmentStatuses()` to pass `null` for `grafanaDeepLink` in the constructed `EnvironmentStatusDto`
  - [ ] 1.5 Update `DevArgoCdAdapter` to pass `null` for `grafanaDeepLink`
- [ ] Task 2: Add `aria-label` prop to `DeepLinkButton` (AC: #5)
  - [ ] 2.1 Add optional `ariaLabel` prop to `DeepLinkButtonProps` interface
  - [ ] 2.2 Render `aria-label` attribute on the PatternFly `Button` component when provided
- [ ] Task 3: Update `EnvironmentCard.tsx` to pass aria-labels (AC: #5)
  - [ ] 3.1 Pass `ariaLabel="Open {environmentName} in ArgoCD"` to `DeepLinkButton` in expanded detail area
  - [ ] 3.2 Pass same `ariaLabel` to `DeepLinkButton` in UNHEALTHY card footer
- [ ] Task 4: Add `grafanaDeepLink` to frontend types (AC: #3)
  - [ ] 4.1 Add `grafanaDeepLink: string | null` to `EnvironmentChainEntry` interface in `types/environment.ts`
- [ ] Task 5: Update backend tests (AC: #1, #3)
  - [ ] 5.1 Update `EnvironmentStatusDtoTest` serialization to include `grafanaDeepLink`
  - [ ] 5.2 Update `EnvironmentServiceTest` to verify `grafanaDeepLink` is null in merged entries
  - [ ] 5.3 Update `EnvironmentResourceIT` to verify `grafanaDeepLink` field is present in JSON response
  - [ ] 5.4 Update `ArgoCdRestAdapterTest` to pass `null` for new parameter
- [ ] Task 6: Update frontend tests (AC: #2, #5)
  - [ ] 6.1 Update `EnvironmentCard.test.tsx`: verify aria-label "Open dev in ArgoCD" on expanded deep link
  - [ ] 6.2 Update `EnvironmentCard.test.tsx`: verify aria-label on UNHEALTHY card's footer deep link
  - [ ] 6.3 Verify Tab key reaches deep link buttons within expanded card (existing keyboard test may cover this)

## Dev Notes

### CRITICAL: Most Functionality Already Exists

Stories 2.7 and 2.8 already implemented the core deep link behavior for the environment chain. **Do NOT re-implement from scratch.** The delta work is small:

| AC | Current State | Remaining Work |
|---|---|---|
| #1 (expanded ArgoCD link) | **DONE** — `EnvironmentCard.tsx` shows `DeepLinkButton` in expanded `CardBody` | None — verify only |
| #2 (UNHEALTHY collapsed link) | **DONE** — `EnvironmentCard.tsx` shows `DeepLinkButton` in `CardFooter` for UNHEALTHY | None — verify only |
| #3 (backend DTOs + grafanaDeepLink) | `argocdDeepLink` exists; `grafanaDeepLink` **missing** | Add `grafanaDeepLink` field (null for now) |
| #4 (no config = no links) | **DONE** — conditional `{entry.argocdDeepLink && ...}` rendering | None — verify only |
| #5 (keyboard + aria-label) | Tab navigation works; **aria-labels missing** | Add `ariaLabel` prop to `DeepLinkButton`, pass from `EnvironmentCard` |

### Existing Backend Deep Link Flow

```
ArgoCdRestAdapter.getEnvironmentStatuses()
  → builds argocdDeepLink: config.url() + "/applications/" + argoAppName
  → argoAppName = appName + "-run-" + env.name.toLowerCase()
  → returns List<EnvironmentStatusDto> (includes argocdDeepLink per env)

EnvironmentService.getEnvironmentChain()
  → calls argoCdAdapter.getEnvironmentStatuses()
  → catches PortalIntegrationException → argocdError message, statuses = empty
  → EnvironmentMapper.merge(environments, statuses, clusterNames) → EnvironmentChainEntryDto list

EnvironmentResource.getEnvironmentChain()
  → returns EnvironmentChainResponse(entries, argocdError)
```

No `DeepLinkService` class exists. Story 3.1 specifies creating one. This story does NOT require creating `DeepLinkService` — the current adapter-level generation is sufficient. `grafanaDeepLink` will be null until either DeepLinkService is created (3.1) or Grafana integration lands (Epic 6).

### Existing Frontend Deep Link Flow

```
EnvironmentCard.tsx:
  Expanded area → {entry.argocdDeepLink && <DeepLinkButton href={...} toolName="ArgoCD" />}
  UNHEALTHY footer → {entry.argocdDeepLink && <DeepLinkButton href={...} toolName="ArgoCD" />}
  Grafana placeholder → disabled Button "View in Grafana ↗" (already present, keep as-is)
```

### File Locations — Exact Paths

**Backend files to modify:**
- `developer-portal/src/main/java/com/portal/environment/EnvironmentStatusDto.java` — add `grafanaDeepLink` parameter
- `developer-portal/src/main/java/com/portal/environment/EnvironmentChainEntryDto.java` — add `grafanaDeepLink` parameter
- `developer-portal/src/main/java/com/portal/environment/EnvironmentMapper.java` — pass `grafanaDeepLink` in merge
- `developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdRestAdapter.java` — add null for grafanaDeepLink in EnvironmentStatusDto construction
- `developer-portal/src/main/java/com/portal/integration/argocd/DevArgoCdAdapter.java` — add null for grafanaDeepLink

**Frontend files to modify:**
- `developer-portal/src/main/webui/src/components/shared/DeepLinkButton.tsx` — add `ariaLabel` prop
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.tsx` — pass `ariaLabel` to DeepLinkButton instances
- `developer-portal/src/main/webui/src/types/environment.ts` — add `grafanaDeepLink` field

**Test files to modify:**
- `developer-portal/src/test/java/com/portal/environment/EnvironmentStatusDtoTest.java`
- `developer-portal/src/test/java/com/portal/environment/EnvironmentServiceTest.java`
- `developer-portal/src/test/java/com/portal/environment/EnvironmentResourceIT.java`
- `developer-portal/src/test/java/com/portal/integration/argocd/ArgoCdRestAdapterTest.java`
- `developer-portal/src/main/webui/src/components/environment/EnvironmentCard.test.tsx`

### Project Structure Notes

- All backend changes are within the `com.portal.environment` and `com.portal.integration.argocd` packages — no cross-domain imports needed
- Frontend changes are in `components/shared/`, `components/environment/`, and `types/` — consistent with existing organization
- No new files created — all changes are modifications to existing files
- No new dependencies required

### Architecture Compliance

- `EnvironmentStatusDto` and `EnvironmentChainEntryDto` are Java records — when adding a field, all constructors and any manual construction sites must be updated (records have a single canonical constructor)
- Record field addition is a breaking change to all call sites — search for `new EnvironmentStatusDto(` and `new EnvironmentChainEntryDto(` across the codebase to find every construction point
- `DeepLinkButton` follows PF6 `Button` component with `component="a"` and `target="_blank"` — adding `aria-label` uses PF6's standard `aria-label` prop on `Button`
- No `@ts-ignore` or `any` types — `grafanaDeepLink: string | null` is fully typed
- The disabled "View in Grafana ↗" button in `EnvironmentCard.tsx` is a placeholder from story 2.8; leave it as-is (it will be replaced when Epic 6 implements real Grafana links)

### Library & Framework Requirements

- **PatternFly 6.x** — `Button` component accepts `aria-label` as a standard HTML attribute prop; no special PF6 API needed
- **Java records** — all parameters are positional; adding `grafanaDeepLink` as the last parameter minimizes diff but still requires updating every call site
- **Quarkus REST Jackson** — new record fields are automatically serialized to JSON; no `@JsonProperty` annotation needed
- **Vitest + React Testing Library** — use `getByLabelText()` or `getByRole('link', { name: '...' })` to assert aria-labels

### Testing Requirements

**Backend tests — JUnit 5 assertions only (no AssertJ):**
- `EnvironmentStatusDtoTest`: verify JSON includes `"grafanaDeepLink":null`
- `EnvironmentServiceTest`: verify merged entries have `grafanaDeepLink` as null
- `EnvironmentResourceIT`: verify GET response JSON includes `grafanaDeepLink` field
- `ArgoCdRestAdapterTest`: update all `new EnvironmentStatusDto(...)` constructions to include null for grafanaDeepLink; ~30 tests may reference the constructor

**Frontend tests — Vitest + React Testing Library:**
- `EnvironmentCard.test.tsx`: after expanding a card, verify `getByRole('link', { name: 'Open dev in ArgoCD' })` exists
- For UNHEALTHY card: verify aria-label present on footer deep link without expanding
- Ensure Tab key navigation test covers deep link buttons (may already be covered)

### Previous Story Intelligence

**From Story 2.7 (ArgoCD Adapter):**
- `CompletableFuture::join` wraps exceptions in `CompletionException` — already handled in `ArgoCdRestAdapter`
- JUnit 5 assertions only — project does NOT use AssertJ
- `WebApplicationException` stubbing: use `new WebApplicationException(statusCode)`
- Test profile uses `portal.argocd.provider=argocd` with `@InjectMock @RestClient ArgoCdRestClient`

**From Story 2.8 (Environment Chain Visualization):**
- `stopPropagation` on expanded `CardBody` and `CardFooter` click events prevents accidental card toggle
- Selector `[aria-hidden="true"]` in tests matched too many PF icons — scope selectors to direct children of specific containers
- Pre-existing failures in `CasbinEnforcerTest` / `PermissionFilterTest` are unrelated — do not attempt to fix

### Dependency Note

Story 3.1 (Deep Link Service & Shared Component) is also in backlog. That story creates a formal `DeepLinkService` CDI bean for all deep link URL generation. This story 3.3 does NOT depend on 3.1 — the current approach of generating ArgoCD deep links within `ArgoCdRestAdapter` is sufficient. When 3.1 is eventually implemented, `ArgoCdRestAdapter` may be refactored to delegate URL generation to `DeepLinkService`, but that is out of scope here.

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Story 3.3 acceptance criteria, lines 1048-1081]
- [Source: _bmad-output/planning-artifacts/architecture.md — Deep link boundary and URL patterns, lines 1045-1076]
- [Source: _bmad-output/planning-artifacts/architecture.md — Configuration properties, lines 1143-1149]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md — Progressive escape principle, line 120]
- [Source: _bmad-output/project-context.md — Deep links rendered as PF6 Button, line 118]
- [Source: _bmad-output/implementation-artifacts/2-7-argocd-adapter-environment-status.md — Patterns and learnings]
- [Source: _bmad-output/implementation-artifacts/2-8-environment-chain-visualization.md — Patterns and learnings]

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
