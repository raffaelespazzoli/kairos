# Story 4.3: Builds Page & Build Table

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want a builds page showing all my application's builds with inline status, expandable failure details, and a one-click build trigger,
So that I can manage my CI workflow efficiently from a single view.

## Acceptance Criteria

1. **Builds page header and compact build table render in application context**
   - **Given** a developer navigates to the Builds tab for an application
   - **When** `ApplicationBuildsPage` renders
   - **Then** a "Trigger Build" primary button is displayed in the page header
   - **And** a PatternFly Table using the compact variant lists all builds for the application

2. **Each build row uses portal status vocabulary and shows key build metadata**
   - **Given** the build table renders
   - **When** reviewing each row
   - **Then** each row shows: build number/ID, status badge, start time, duration, and artifact reference when present
   - **And** status badges use the portal vocabulary: "Passed", "Failed", "Building...", "Cancelled", and "Pending"
   - **And** no Tekton infrastructure terminology appears in visible UI copy

3. **Trigger Build starts a build and refreshes the view without a page reload**
   - **Given** the developer clicks "Trigger Build"
   - **When** POST `/api/v1/teams/{teamId}/applications/{appId}/builds` succeeds
   - **Then** a new "Building..." row appears at the top of the table using the returned build summary
   - **And** the page remains on the Builds view
   - **And** subsequent status changes are obtained via the existing manual refresh interaction, not polling or page reload

4. **Failed builds expand inline with developer-friendly details and lazy log loading**
   - **Given** a build row has status "Failed"
   - **When** the row is displayed
   - **Then** the row has a subtle danger-tinted treatment and an expandable toggle
   - **And** expanding the row loads build detail on demand from GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}`
   - **And** the expanded panel shows the failed stage name, error summary, a "View Logs" action that loads `text/plain` logs from GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs`, and an "Open in Tekton ↗" deep link when available

5. **Passed builds surface artifact information and the next workflow affordance**
   - **Given** a build row has status "Passed"
   - **When** the row is displayed
   - **Then** the artifact reference is shown in monospace-friendly presentation
   - **And** a "Create Release" inline affordance is rendered for layout continuity with Story 4.4
   - **And** Story 4.3 does not implement release creation business logic

6. **Empty, loading, and error states follow shared portal patterns**
   - **Given** no builds exist for the application
   - **When** the builds page renders
   - **Then** a PatternFly EmptyState is shown with title "No builds yet"
   - **And** the description says "Trigger your first build or push code to start a CI pipeline."
   - **And** the primary action is "Trigger Build"
   - **And** a secondary DevSpaces deep link is shown when the application has a DevSpaces URL
   - **Given** build data is loading or a backend/integration error occurs
   - **Then** the page uses shared loading and inline alert patterns already used elsewhere in the SPA

7. **Keyboard and accessibility behavior works for expandable rows and actions**
   - **Given** the build table is navigated by keyboard
   - **When** a user tabs through rows and actions
   - **Then** expandable rows can be toggled with Enter/Space
   - **And** action buttons and deep links are reachable in logical tab order
   - **And** status is communicated with text plus icon/color rather than color alone

## Tasks / Subtasks

- [x] Task 1: Verify story dependencies and current shell wiring before implementation (AC: #1-#7)
  - [x] Confirm Story 4.1 and Story 4.2 contracts exist or land in the same implementation branch before UI work depends on them
  - [x] Reuse the existing Builds route at `developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx` instead of creating a second page
  - [x] Preserve the existing application-context route/tab/breadcrumb structure already wired for `/teams/:teamId/apps/:appId/builds`

- [x] Task 2: Add frontend build domain types and API helpers (AC: #2, #3, #4, #5)
  - [x] Create `developer-portal/src/main/webui/src/types/build.ts` with strict TypeScript types for `BuildStatus`, `BuildSummary`, and `BuildDetail`
  - [x] Create `developer-portal/src/main/webui/src/api/builds.ts` with `fetchBuilds()`, `triggerBuild()`, `fetchBuildDetail()`, and `fetchBuildLogs()`
  - [x] Keep JSON calls on the shared `apiFetch()` path and handle log retrieval as `text/plain` without bypassing the shared auth/error conventions
  - [x] Model optional fields correctly (`completedAt`, `duration`, `imageReference`, `failedStageName`, `errorSummary`, `currentStage`, `tektonDeepLink`)

- [x] Task 3: Implement builds hooks using existing SPA fetch patterns (AC: #1, #3, #4, #6)
  - [x] Add `developer-portal/src/main/webui/src/hooks/useBuilds.ts` returning `{ data, error, isLoading, refresh }`
  - [x] Add lazy detail/log loading helpers or hooks for failed-row expansion that remain StrictMode-safe
  - [x] Use `AbortController` and cleanup guards for all effect-driven fetch logic
  - [x] Do not add client-side polling, background intervals, or cross-navigation caching

- [x] Task 4: Add reusable build UI components under `components/build/` (AC: #2, #4, #5, #7)
  - [x] Create a build status badge/label component that maps portal statuses to PatternFly labels/icons and accessible text
  - [x] Create a compact build table component with expandable failed rows
  - [x] Create a failed-build detail panel that renders failed stage, error summary, lazy-loaded logs, and the Tekton deep link
  - [x] Render artifact references in copy-friendly monospace presentation
  - [x] Render the "Create Release" button only for passed builds; keep it visibly present for Story 4.4 handoff, but do not implement release POST behavior in this story

- [x] Task 5: Replace the current Builds page stub with the real page flow (AC: #1, #3, #4, #5, #6, #7)
  - [x] Build the page header with title, primary "Trigger Build" action, and the shared refresh affordance
  - [x] Load builds on mount and refresh on demand
  - [x] On successful trigger, insert or refresh the newest returned build summary at the top of the table without navigating away
  - [x] Show PatternFly `Spinner`/shared loading UI during fetches
  - [x] Show inline `Alert`-style error handling for trigger/list/detail/log failures
  - [x] Implement the specified empty state with Trigger Build and DevSpaces actions

- [x] Task 6: Keep surrounding application UX coherent (AC: #1, #6)
  - [x] Ensure the Builds tab remains selected in application navigation when viewing the page
  - [x] Ensure breadcrumb/view labeling still resolves to "Builds"
  - [x] If a small follow-up adjustment is needed on the overview's "Recent Builds" placeholder, keep it minimal and aligned with Epic 4 scope

- [x] Task 7: Verify backend contract alignment and make only minimal supporting changes if required (AC: #2, #3, #4, #5)
  - [x] Confirm the UI consumes the Story 4.1/4.2 DTO shapes and endpoint semantics as documented
  - [x] If there is a mismatch between the ready-for-dev stories and the actual implementation branch, fix the smallest contract delta needed in the same branch with tests
  - [x] Do not add new persistence, polling infrastructure, or alternate build APIs

- [x] Task 8: Add focused frontend and backend tests for the page workflow (AC: #1-#7)
  - [x] Add `ApplicationBuildsPage.test.tsx` covering loading, success, error, empty, trigger-build success, failed-row expansion, lazy log loading, and keyboard interaction
  - [x] Update `App.test.tsx` and any existing Builds-tab tests that still expect the "Coming soon" placeholder
  - [x] Add or extend API/client tests for `text/plain` log handling if client helpers are changed
  - [x] If backend contract adjustments are required, extend the corresponding Quarkus tests (`BuildResourceIT`, service tests, adapter tests) in the same change

### Review Findings

- [x] [Review][Patch] Trigger Build ignores the returned build summary and refreshes the whole page state instead of inserting the new `Building...` row inline as required by AC3 — **Fixed**: `useBuilds` now exposes `prepend()`, page calls `prepend(newBuild)` instead of `refresh()`
- [x] [Review][Patch] Builds page loading state exposes Tekton infrastructure terminology in visible UI copy after the shared spinner hint appears, violating the portal vocabulary requirement in AC2 — **Fixed**: removed `systemName="Tekton"` from `LoadingSpinner`
- [x] [Review][Patch] `useBuilds()` and the build-detail path do not actually pass an `AbortSignal` into the underlying fetch calls, so the implementation does not satisfy the project cleanup rule claimed in Task 3 — **Fixed**: `fetchBuilds` and `fetchBuildDetail` now accept `signal` parameter; hooks wire `AbortController.signal` through to all API calls
- [x] [Review][Patch] Failed-build log loading has no retry path after an error because `View Logs` disappears permanently once clicked, leaving the user stuck unless they collapse and re-expand the row — **Fixed**: log error alert now shows a "Retry" button that re-invokes `loadLogs()`

## Dev Notes

### Critical Dependency: Stories 4.1 and 4.2 Must Land First

This story is a frontend consumer of the build APIs and DTOs defined in Story 4.1 and Story 4.2. The following backend capabilities must exist before the page can be completed:

- POST `/api/v1/teams/{teamId}/applications/{appId}/builds`
- GET `/api/v1/teams/{teamId}/applications/{appId}/builds`
- GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}`
- GET `/api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs`
- `BuildSummaryDto` and `BuildDetailDto` using portal status vocabulary, not raw Tekton terminology
- `tektonDeepLink` on build responses when configured

**Pre-flight check:** verify Story 4.1 and Story 4.2 artifacts are implemented or available in the working branch before starting page work. If they are absent, the UI story is blocked on those contracts.

### What Already Exists - Reuse It, Do Not Recreate It

- `developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx` already exists as the route target and currently contains a stub placeholder.
- `developer-portal/src/main/webui/src/App.tsx` already wires `/teams/:teamId/apps/:appId/builds`.
- `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx` already demonstrates the shared page pattern: app lookup, refresh action, loading spinner, inline error alert, and page-section layout.
- `developer-portal/src/main/webui/src/hooks/useApiFetch.ts` is the shared fetch pattern for `{ data, error, isLoading, refresh }`.
- `developer-portal/src/main/webui/src/components/shared/DeepLinkButton.tsx` already provides the external-link button pattern for Tekton/DevSpaces/ArgoCD/Grafana links.
- Application route chrome already includes Builds tab/breadcrumb support; keep that intact instead of creating parallel navigation.

### Backend/API Contract the Page Must Consume

Use the Story 4.1 and Story 4.2 build endpoints exactly as the build domain contract:

- `GET /api/v1/teams/{teamId}/applications/{appId}/builds` returns the builds collection ordered by `startedAt` descending
- `POST /api/v1/teams/{teamId}/applications/{appId}/builds` triggers a new build and returns the newest build summary
- `GET /api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}` returns failed-stage/current-stage/detail data for drilldown
- `GET /api/v1/teams/{teamId}/applications/{appId}/builds/{buildId}/logs` returns `text/plain`

Frontend guardrails:

- Treat logs as plain text, not JSON.
- `imageReference` is optional even for successful builds; render absence gracefully.
- Cross-team or missing resources surface as `404`; do not build UI logic that assumes `403` for hidden resources.
- Integration failures surface as standardized portal errors and should be displayed inline with shared error components.

### UI/UX Requirements That Override Implementation Shortcuts

- Use PatternFly 6 components only; no custom HTML/CSS for widgets PatternFly already provides.
- Use the compact table variant for the builds list.
- Use the portal status vocabulary in all visible UI text: "Passed", "Failed", "Building...", "Cancelled", "Pending".
- Failed rows must support expandable inline detail rather than forcing separate navigation.
- Show logs lazily from the expanded failed row; do not prefetch logs for every row.
- Keep refresh user-initiated. Do not add auto-refresh, polling, SSE, or WebSockets for MVP.
- Surface errors inline on the page. No modal error dialogs.
- Deep links must remain explicit "Open in Tekton ↗" style links opening in a new tab.
- The page must remain desktop-first and accessible via keyboard.

### Story 4.4 Handoff Rule for "Create Release"

Story 4.3 is responsible for rendering the inline release affordance on passed rows so the build table layout matches the intended workflow. Story 4.4 owns release creation behavior.

Implementation rule:

- Render the "Create Release" affordance only for passed builds.
- Do not implement release POST calls, dialogs, or optimistic release state changes in this story.
- If the team wants to avoid a dead-end interaction before Story 4.4 lands, render the button disabled with explanatory helper text or tooltip rather than wiring incomplete behavior.

### File Structure Requirements

Follow the existing frontend/domain layout from the architecture and current codebase:

- Route/page: `developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx`
- New reusable UI: `developer-portal/src/main/webui/src/components/build/`
- API helpers: `developer-portal/src/main/webui/src/api/builds.ts`
- Hooks: `developer-portal/src/main/webui/src/hooks/useBuilds.ts`
- Types: `developer-portal/src/main/webui/src/types/build.ts`

If contract fixes are required in the backend, keep them in the existing domain packages:

- `developer-portal/src/main/java/com/portal/build/`
- `developer-portal/src/main/java/com/portal/integration/tekton/`

### Architecture Compliance

- Frontend must call backend REST endpoints only; never call Tekton or any platform system directly from the browser.
- Use relative `/api/v1/...` URLs via the shared client.
- Keep the app shell consistent with the existing SPA route hierarchy and application context.
- Use PatternFly design tokens and components rather than custom styling.
- Show loading state, error state, empty state, and success state inline on the page element that owns the interaction.
- Present artifact references and build identifiers in developer-friendly, copyable form.

### Testing Requirements

Frontend:

- Co-locate tests next to the page/components.
- Mock API behavior at the `apiFetch()` layer.
- Cover loading, success, empty, error, trigger success, failed expansion, lazy log loading, and keyboard interaction.
- Verify that inline alerts appear for error cases and that text/plain log content renders correctly.
- Account for React 18 StrictMode behavior in any effect-driven fetch logic.

Backend:

- Only add backend tests if this story forces a contract adjustment.
- If backend changes are made, keep them focused and extend the existing Quarkus test layers rather than adding new testing patterns.

### What Not To Build

- Do not add polling, timers, SSE, or WebSockets.
- Do not add new database tables or persistence for build history; build state remains live from Tekton.
- Do not expose Tekton-native terms like `PipelineRun`, `TaskRun`, or `Step` in UI labels.
- Do not create a second builds route, page shell, or navigation model.
- Do not bypass `apiFetch()` with raw unauthenticated browser fetches.
- Do not implement Story 4.4 release workflows here.

### Previous Story Intelligence

- Story 4.1 established that the portal translates Tekton concepts to the build domain and that members/leads may trigger builds.
- Story 4.2 established the canonical build status mapping, optional image reference, build detail endpoint, and `text/plain` log retrieval.
- Story 4.2 also established that manual refresh, not polling, is the MVP freshness model for build status.
- The build page must preserve the same developer-language error handling and deep-link pattern already required by the prior two stories.

### Git Intelligence

No repository-level git history was available at the workspace root during story creation, so implementation guidance here is based on the current artifacts, architecture, project-context rules, and the existing `developer-portal` structure already present in the workspace.

### Project Structure Notes

- The Builds page already exists as a route target; replace the stub rather than creating a new route.
- The application overview currently contains a "Recent Builds" placeholder card that explicitly points to Epic 4; keep any changes there minimal unless needed for navigation polish.
- Shared components for loading, refresh, errors, and deep links already exist and should be reused to keep the application tabs visually consistent.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` - Epic 4 / Story 4.3]
- [Source: `_bmad-output/planning-artifacts/prd.md` - FR17-FR20, Web Application Specific Requirements, Performance Targets]
- [Source: `_bmad-output/planning-artifacts/architecture.md` - Technology Version Baseline, Frontend Starter, Project Structure, API & Communication Patterns, Structure Patterns, Process Patterns, Requirements to Structure Mapping]
- [Source: `_bmad-output/planning-artifacts/ux-design-specification.md` - Build & Release direction, compact table, expandable failed row detail, empty states, deep-link patterns, accessibility, data freshness]
- [Source: `_bmad-output/project-context.md` - TypeScript rules, React + PatternFly 6 frontend rules, testing rules, code quality rules]
- [Source: `_bmad-output/implementation-artifacts/4-1-tekton-adapter-pipeline-triggering.md`]
- [Source: `_bmad-output/implementation-artifacts/4-2-build-monitoring-log-retrieval.md`]
- [Source: `developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx`]
- [Source: `developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx`]
- [Source: `developer-portal/src/main/webui/src/hooks/useApiFetch.ts`]
- [Source: `developer-portal/src/main/webui/src/components/shared/DeepLinkButton.tsx`]
- [Source: `developer-portal/src/main/webui/src/components/layout/ApplicationTabs.tsx`]
- [Source: `developer-portal/src/main/webui/src/components/layout/AppBreadcrumb.tsx`]
- [Source: `developer-portal/src/main/webui/src/App.tsx`]

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus

### Debug Log References

None required — no debug issues encountered.

### Completion Notes List

- Task 1: Verified Stories 4.1/4.2 are done; confirmed all 4 backend endpoints exist (POST builds, GET builds, GET build detail, GET build logs); confirmed existing route/tab/breadcrumb wiring is intact.
- Task 2: Created `types/build.ts` with strict `BuildStatus` union type, `BuildSummary`, and `BuildDetail` interfaces. Created `api/builds.ts` with 4 API helpers. Added `apiFetchText()` to `api/client.ts` for text/plain log retrieval with same auth/error conventions as `apiFetch()`.
- Task 3: Created `hooks/useBuilds.ts` with 4 hooks: `useBuilds` (list), `useTriggerBuild` (POST), `useBuildDetail` (lazy detail), `useBuildLogs` (lazy logs). All use AbortController cleanup guards and are StrictMode-safe. No polling or caching added.
- Task 4: Created 3 components under `components/build/`: `BuildStatusBadge` (Label with color + icon for all 5 portal statuses), `BuildTable` (compact PF6 table with expandable failed rows), `FailedBuildDetail` (detail panel with lazy log loading, Tekton deep link). "Create Release" rendered as disabled tooltip for Story 4.4 handoff.
- Task 5: Replaced `ApplicationBuildsPage` stub with full implementation: page header with Trigger Build + Refresh, build table, empty state with Trigger Build + DevSpaces link, inline error alerts for all failure modes.
- Task 6: Existing tab/breadcrumb/route structure preserved — no changes needed. Builds tab correctly selected when viewing builds page.
- Task 7: Backend DTO alignment verified — all 8 BuildSummaryDto fields and 11 BuildDetailDto fields match the frontend types exactly. No backend changes required.
- Task 8: Added 34-test `ApplicationBuildsPage.test.tsx` covering loading/success/error/empty states, trigger build, failed-row expansion, lazy log loading, keyboard interaction, and accessibility. Updated `App.test.tsx` to mock `useBuilds` and expect "No builds yet" instead of old placeholder. Full suite: 220 tests passing, 0 regressions.

### File List

- `developer-portal/src/main/webui/src/types/build.ts` (new)
- `developer-portal/src/main/webui/src/api/builds.ts` (new)
- `developer-portal/src/main/webui/src/api/client.ts` (modified — added `apiFetchText()`)
- `developer-portal/src/main/webui/src/hooks/useBuilds.ts` (new)
- `developer-portal/src/main/webui/src/components/build/BuildStatusBadge.tsx` (new)
- `developer-portal/src/main/webui/src/components/build/BuildTable.tsx` (new)
- `developer-portal/src/main/webui/src/components/build/FailedBuildDetail.tsx` (new)
- `developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx` (modified — replaced stub)
- `developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.test.tsx` (new)
- `developer-portal/src/main/webui/src/App.test.tsx` (modified — updated builds route test)
