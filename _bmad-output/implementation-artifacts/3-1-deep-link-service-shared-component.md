# Story 3.1: Deep Link Service & Shared Component

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want contextual deep links to native platform tools that land me in the exact right place,
So that I can seamlessly transition from the portal's abstraction to the native tool when I need deeper investigation.

## Acceptance Criteria

1. **DeepLinkService CDI bean reads configured base URLs**
   - **Given** the DeepLinkService CDI bean is configured with base URLs for all native tools
   - **When** reviewing the configuration
   - **Then** the following properties are read from application configuration:
     - `portal.argocd.url` — ArgoCD UI base URL
     - `portal.tekton.dashboard-url` — Tekton Dashboard base URL
     - `portal.grafana.url` — Grafana base URL
     - `portal.grafana.dashboard-id` — Grafana dashboard ID for application health
     - `portal.devspaces.url` — DevSpaces base URL
     - `portal.vault.url` — Vault UI base URL (for secret management navigation)

2. **ArgoCD deep link generation**
   - **Given** the DeepLinkService is called for an ArgoCD deep link
   - **When** `generateArgoCdLink(String argocdAppName)` is called
   - **Then** it returns `{argocdUrl}/applications/{argocdAppName}`

3. **Tekton deep link generation**
   - **Given** the DeepLinkService is called for a Tekton deep link
   - **When** `generateTektonLink(String pipelineRunId)` is called
   - **Then** it returns `{tektonDashboardUrl}/#/pipelineruns/{pipelineRunId}`

4. **Grafana deep link generation**
   - **Given** the DeepLinkService is called for a Grafana deep link
   - **When** `generateGrafanaLink(String namespace)` is called
   - **Then** it returns `{grafanaUrl}/d/{dashboardId}?var-namespace={namespace}`

5. **DevSpaces deep link generation**
   - **Given** the DeepLinkService is called for a DevSpaces deep link
   - **When** `generateDevSpacesLink(String gitRepoUrl)` is called
   - **Then** it returns `{devspacesUrl}/#/{gitRepoUrl}`

6. **Vault deep link generation**
   - **Given** the DeepLinkService is called for a Vault deep link
   - **When** `generateVaultLink(String team, String app, String env)` is called
   - **Then** it returns `{vaultUrl}/ui/vault/secrets/applications/{team}/{team}-{app}-{env}/static-secrets`

7. **DeepLinkService used in DTOs**
   - **Given** a deep link URL needs to be included in an API response
   - **When** the backend assembles DTOs that reference native tools
   - **Then** the DeepLinkService is injected and used to generate the link
   - **And** the link is included in the DTO's `deepLink` or `links` field

8. **DeepLinkButton React component renders correctly**
   - **Given** the DeepLinkButton React component is used in the frontend
   - **When** it renders
   - **Then** it displays as a PatternFly Button with link variant
   - **And** the label includes the target tool name with ↗ suffix (e.g., "Open in ArgoCD ↗")
   - **And** clicking opens the URL in a new browser tab (target="_blank", rel="noopener noreferrer")
   - **And** the component accepts props: href (string), label (string), toolName (string)

9. **DeepLinkButton hides when URL not available**
   - **Given** a deep link URL is not available (tool not configured or context insufficient)
   - **When** the DeepLinkButton would render
   - **Then** it is not rendered — no broken or empty links are shown

## Tasks / Subtasks

- [x] Task 1: Create DeepLinkConfig configuration bean (AC: #1)
  - [x] Create `DeepLinkConfig.java` in `com.portal.deeplink` as `@ApplicationScoped` bean with `@ConfigProperty` injections
  - [x] Map `portal.argocd.url` as required String, all others as `Optional<String>`
  - [x] Expose accessor methods for each property

- [x] Task 2: Create DeepLinkService CDI bean (AC: #1, #2, #3, #4, #5, #6)
  - [x] Create `DeepLinkService.java` in `com.portal.deeplink`, `@ApplicationScoped`
  - [x] Inject `DeepLinkConfig`
  - [x] Implement `generateArgoCdLink(String argocdAppName)` → `Optional<String>`
  - [x] Implement `generateTektonLink(String pipelineRunId)` → `Optional<String>`
  - [x] Implement `generateGrafanaLink(String namespace)` → `Optional<String>`
  - [x] Implement `generateDevSpacesLink(String gitRepoUrl)` → `Optional<String>`
  - [x] Implement `generateVaultLink(String team, String app, String env)` → `Optional<String>`
  - [x] Return `Optional.empty()` when the tool's base URL is not configured

- [x] Task 3: Add configuration properties to application.properties (AC: #1)
  - [x] Add `portal.tekton.dashboard-url`, `portal.grafana.url`, `portal.grafana.dashboard-id`, `portal.devspaces.url`, `portal.vault.url` with env-var defaults
  - [x] Add dev-profile defaults for local development
  - [x] Do NOT change existing `portal.argocd.url` — it already exists and is used by `ArgoCdConfig`

- [x] Task 4: Update DeepLinkButton React component (AC: #8, #9)
  - [x] Add optional `label` prop to `DeepLinkButton` — if provided, renders label text instead of auto-generated "Open in {toolName} ↗"
  - [x] Add null/undefined `href` guard — return `null` when `href` is falsy (no broken links)
  - [x] Keep existing `href`+`toolName` behavior as default fallback

- [x] Task 5: Write DeepLinkService unit tests (AC: #2, #3, #4, #5, #6)
  - [x] Create `DeepLinkServiceTest.java` in `src/test/java/com/portal/deeplink/`
  - [x] Test each generate method with configured URL → correct URL returned
  - [x] Test each generate method with unconfigured URL → `Optional.empty()`
  - [x] Test URL escaping edge cases (special characters in inputs)

- [x] Task 6: Write DeepLinkButton frontend tests (AC: #8, #9)
  - [x] Update `DeepLinkButton.test.tsx` (or create if not existing)
  - [x] Test renders with href + toolName → link displayed
  - [x] Test renders with custom label → label text used
  - [x] Test renders with null/undefined href → nothing rendered
  - [x] Test opens in new tab (target="_blank")

### Review Findings

- [x] [Review][Decision] Clarify AC7 ownership for `DeepLinkService` DTO wiring — Fixed: injected `DeepLinkService` into `EnvironmentService` and replaced adapter-generated ArgoCD deep links with `DeepLinkService.generateArgoCdLink()` in the chain DTO assembly. `ArgoCdRestAdapter` stays untouched.
- [x] [Review][Patch] Normalize configured base URLs before concatenation [`developer-portal/src/main/java/com/portal/deeplink/DeepLinkService.java:19`] — Fixed: added `stripTrailingSlash()` utility in `DeepLinkService` applied to all base URLs before concatenation. Added trailing-slash test coverage.

## Dev Notes

### What This Story Creates vs What Already Exists

**CRITICAL: The `DeepLinkButton` component already exists** at `src/main/webui/src/components/shared/DeepLinkButton.tsx`. It was created in Epic 1 and used in Story 2.8 for ArgoCD links on the environment chain. This story **updates** it — it does NOT create a new one.

**ArgoCD deep links are already generated** inside `ArgoCdRestAdapter` (line 66) using `config.url() + "/applications/" + argoAppName`. Story 3.1 centralizes this logic into `DeepLinkService`. However, **do NOT refactor ArgoCdRestAdapter in this story** — Story 3.3 explicitly handles migrating environment chain deep links to use `DeepLinkService`. The adapter's inline deep link generation stays as-is for now.

### Backend: `com.portal.deeplink` Package — All New

Per the architecture doc, `DeepLinkService` lives in its own `deeplink/` package, not under `integration/`:

```
com.portal.deeplink/
├── DeepLinkConfig.java    # NEW — @ConfigMapping interface
└── DeepLinkService.java   # NEW — @ApplicationScoped CDI bean
```

### DeepLinkConfig — Configuration Interface

**WARNING — DO NOT use `@ConfigMapping(prefix = "portal")`** — this broad prefix would require mapping ALL `portal.*` properties (`portal.oidc.*`, `portal.secrets.*`, `portal.git.*`, etc.) or risk SmallRye validation failures for unmapped properties. Instead, use `@ConfigProperty` injection directly in the service for each URL property. This is simpler, explicit, and avoids any ConfigMapping namespace overlap with `ArgoCdConfig`.

Create `DeepLinkConfig.java` as a simple `@ApplicationScoped` CDI bean wrapping `@ConfigProperty` injections:

```java
package com.portal.deeplink;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Optional;

@ApplicationScoped
public class DeepLinkConfig {

    @ConfigProperty(name = "portal.argocd.url")
    String argocdUrl;

    @ConfigProperty(name = "portal.tekton.dashboard-url")
    Optional<String> tektonDashboardUrl;

    @ConfigProperty(name = "portal.grafana.url")
    Optional<String> grafanaUrl;

    @ConfigProperty(name = "portal.grafana.dashboard-id")
    Optional<String> grafanaDashboardId;

    @ConfigProperty(name = "portal.devspaces.url")
    Optional<String> devspacesUrl;

    @ConfigProperty(name = "portal.vault.url")
    Optional<String> vaultUrl;

    public String argocdUrl() { return argocdUrl; }
    public Optional<String> tektonDashboardUrl() { return tektonDashboardUrl; }
    public Optional<String> grafanaUrl() { return grafanaUrl; }
    public Optional<String> grafanaDashboardId() { return grafanaDashboardId; }
    public Optional<String> devspacesUrl() { return devspacesUrl; }
    public Optional<String> vaultUrl() { return vaultUrl; }
}
```

**Why `@ConfigProperty` instead of `@ConfigMapping`:** The deep link properties span multiple config namespaces (`portal.argocd`, `portal.tekton`, `portal.grafana`, `portal.devspaces`, `portal.vault`). A `@ConfigMapping` interface would need prefix `"portal"` to capture all of them, but that prefix is too broad and would collide with other `portal.*` properties mapped by `ArgoCdConfig`, `SecretManagerConfig`, `GitProviderConfig`, etc. Using `@ConfigProperty` reads each property directly without namespace ownership conflicts.

**Config overlap notes:**
- `portal.argocd.url` is also read by `ArgoCdConfig` — this is fine; `@ConfigProperty` is a read-only view, no conflict with `@ConfigMapping` on the same property
- `portal.vault.url` (Vault UI) is separate from `portal.secrets.vault.url` (Vault API) — intentionally different URLs for UI vs API access

### DeepLinkService — CDI Bean

```java
package com.portal.deeplink;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class DeepLinkService {

    @Inject
    DeepLinkConfig config;

    public Optional<String> generateArgoCdLink(String argocdAppName) {
        return Optional.of(config.argocdUrl() + "/applications/" + argocdAppName);
    }

    public Optional<String> generateTektonLink(String pipelineRunId) {
        return config.tektonDashboardUrl()
                .map(url -> url + "/#/pipelineruns/" + pipelineRunId);
    }

    public Optional<String> generateGrafanaLink(String namespace) {
        Optional<String> grafanaUrl = config.grafanaUrl();
        Optional<String> dashboardId = config.grafanaDashboardId();
        if (grafanaUrl.isPresent() && dashboardId.isPresent()) {
            return Optional.of(grafanaUrl.get() + "/d/" + dashboardId.get()
                    + "?var-namespace=" + namespace);
        }
        return Optional.empty();
    }

    public Optional<String> generateDevSpacesLink(String gitRepoUrl) {
        return config.devspacesUrl()
                .map(url -> url + "/#/" + gitRepoUrl);
    }

    public Optional<String> generateVaultLink(String team, String app, String env) {
        return config.vaultUrl()
                .map(url -> url + "/ui/vault/secrets/applications/" + team + "/"
                        + team + "-" + app + "-" + env + "/static-secrets");
    }
}
```

**Design decisions:**
- ArgoCD returns `Optional.of(...)` always because `portal.argocd.url` is non-optional (already required by `ArgoCdConfig` and the ArgoCD adapter). If ArgoCD URL is missing, Quarkus fails to start — consistent with current behavior.
- All other tools return `Optional.empty()` when their base URL is not configured — these tools are optional and may not be deployed.
- Grafana requires BOTH `url` AND `dashboardId` to generate a link — if either is missing, returns empty.
- No URL encoding applied to inputs — ArgoCD app names, namespace names, and Git URLs are already URL-safe by convention in the platform. If encoding becomes needed, add it as a follow-up.

### Configuration Properties — Additions to application.properties

Add these properties (do NOT touch existing `portal.argocd.*` properties):

```properties
# Tekton
portal.tekton.dashboard-url=${TEKTON_DASHBOARD_URL:}

# Grafana (deep links)
portal.grafana.url=${GRAFANA_URL:}
portal.grafana.dashboard-id=${GRAFANA_DASHBOARD_ID:}

# DevSpaces
portal.devspaces.url=${DEVSPACES_URL:}

# Vault UI (separate from portal.secrets.vault.url which is the API endpoint)
portal.vault.url=${VAULT_UI_URL:}
```

**Dev profile additions:**

```properties
%dev.portal.tekton.dashboard-url=https://dev-tekton-dashboard.local
%dev.portal.grafana.url=https://dev-grafana.local
%dev.portal.grafana.dashboard-id=app-health-overview
%dev.portal.devspaces.url=https://dev-devspaces.local
%dev.portal.vault.url=https://dev-vault.local
```

### Frontend: DeepLinkButton Update

The existing component at `src/main/webui/src/components/shared/DeepLinkButton.tsx`:

```typescript
interface DeepLinkButtonProps {
  href: string;
  toolName: string;
}
```

Updated interface:

```typescript
interface DeepLinkButtonProps {
  href?: string | null;
  toolName: string;
  label?: string;
}
```

Updated component logic:
- If `href` is falsy (`null`, `undefined`, `""`), return `null` — component renders nothing
- If `label` is provided, use it as the button text
- If `label` is not provided, use `"Open in {toolName} ↗"` (current behavior)
- The ↗ suffix is part of the label text, not auto-appended — callers control the full label when using `label` prop

**Impact on existing consumers:** Currently `EnvironmentCard.tsx` passes `href={entry.argocdDeepLink}` which can be `string | null`. After this change, null hrefs cause the button to not render — which is the desired behavior (AC #9). No changes needed in `EnvironmentCard.tsx`.

### Existing Components — DO NOT Recreate

| Component | Location | Usage in This Story |
|-----------|----------|---------------------|
| `DeepLinkButton` | `components/shared/DeepLinkButton.tsx` | **UPDATE** — add `label` prop and null-href guard |
| `ArgoCdConfig` | `integration/argocd/ArgoCdConfig.java` | **DO NOT CHANGE** — existing ArgoCD config stays; `DeepLinkConfig` reads the same `portal.argocd.url` independently |
| `ArgoCdRestAdapter` | `integration/argocd/ArgoCdRestAdapter.java` | **DO NOT CHANGE** — inline deep link generation stays; Story 3.3 migrates to DeepLinkService |
| `DevArgoCdAdapter` | `integration/argocd/DevArgoCdAdapter.java` | **DO NOT CHANGE** — inline deep link stays for now |
| `EnvironmentCard` | `components/environment/EnvironmentCard.tsx` | **DO NOT CHANGE** — already passes `href={entry.argocdDeepLink}` to DeepLinkButton; null handling now built into DeepLinkButton |
| `PortalIntegrationException` | `integration/PortalIntegrationException.java` | Reference — has `deepLink` field; DeepLinkService can be used to populate this in future stories |

### Testing Strategy

**Backend — `DeepLinkServiceTest.java`:**

```java
@QuarkusTest
class DeepLinkServiceTest {

    @Inject
    DeepLinkService deepLinkService;

    // ArgoCD always returns a link (url is required)
    @Test
    void generateArgoCdLinkReturnsUrl() {
        Optional<String> link = deepLinkService.generateArgoCdLink("orders-run-dev");
        assertTrue(link.isPresent());
        assertTrue(link.get().endsWith("/applications/orders-run-dev"));
    }

    // Tekton returns link when configured
    @Test
    void generateTektonLinkReturnsUrlWhenConfigured() {
        // Uses dev-profile url from application.properties
        Optional<String> link = deepLinkService.generateTektonLink("run-abc123");
        assertTrue(link.isPresent());
        assertTrue(link.get().contains("/#/pipelineruns/run-abc123"));
    }

    // Grafana requires both url and dashboardId
    @Test
    void generateGrafanaLinkReturnsUrlWhenFullyConfigured() {
        Optional<String> link = deepLinkService.generateGrafanaLink("orders-dev");
        assertTrue(link.isPresent());
        assertTrue(link.get().contains("?var-namespace=orders-dev"));
    }

    // DevSpaces returns link when configured
    @Test
    void generateDevSpacesLinkReturnsUrl() {
        Optional<String> link = deepLinkService.generateDevSpacesLink("https://github.com/team/repo");
        assertTrue(link.isPresent());
        assertTrue(link.get().contains("/#/https://github.com/team/repo"));
    }

    // Vault returns scoped link
    @Test
    void generateVaultLinkReturnsTeamScopedUrl() {
        Optional<String> link = deepLinkService.generateVaultLink("payments", "checkout", "dev");
        assertTrue(link.isPresent());
        assertTrue(link.get().contains("/applications/payments/payments-checkout-dev/static-secrets"));
    }
}
```

Use `@QuarkusTest` (not plain Mockito) because `DeepLinkConfig` is a `@ConfigMapping` interface that Quarkus must produce — mocking `@ConfigMapping` beans manually is fragile. The dev-profile values in `application.properties` provide the test data.

Alternatively, use plain unit tests with a manually constructed `DeepLinkConfig` mock if `@QuarkusTest` overhead is undesirable for a pure-logic class:

```java
class DeepLinkServiceTest {

    DeepLinkService service;

    @BeforeEach
    void setUp() {
        DeepLinkConfig config = createMockConfig(
            "https://argocd.test", "https://tekton.test",
            "https://grafana.test", "dashboard-1",
            "https://devspaces.test", "https://vault.test");
        service = new DeepLinkService();
        // inject config via reflection or make it package-private
    }
}
```

**Recommendation:** Use `@QuarkusTest` for simplicity — consistent with existing test patterns (e.g., `EnvironmentServiceTest` uses Quarkus DI). The service has no side effects, so the test is fast even with Quarkus bootstrap.

**Test file for unconfigured tools:** Add a separate test class or test profile with empty config values to verify `Optional.empty()` return. One approach: create `application-deeplink-empty.properties` test profile with empty URLs, and a `@TestProfile` test class.

**Frontend — `DeepLinkButton.test.tsx`:**

```typescript
describe('DeepLinkButton', () => {
  it('renders with href and toolName', () => {
    render(<DeepLinkButton href="https://argocd/app" toolName="ArgoCD" />);
    expect(screen.getByRole('link', { name: /ArgoCD/ })).toHaveAttribute('href', 'https://argocd/app');
  });

  it('renders custom label when provided', () => {
    render(<DeepLinkButton href="https://vault/path" toolName="Vault" label="Manage secrets in Vault ↗" />);
    expect(screen.getByText('Manage secrets in Vault ↗')).toBeInTheDocument();
  });

  it('renders nothing when href is null', () => {
    const { container } = render(<DeepLinkButton href={null} toolName="ArgoCD" />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when href is undefined', () => {
    const { container } = render(<DeepLinkButton toolName="ArgoCD" />);
    expect(container.firstChild).toBeNull();
  });

  it('opens in new tab', () => {
    render(<DeepLinkButton href="https://argocd/app" toolName="ArgoCD" />);
    const link = screen.getByRole('link', { name: /ArgoCD/ });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
```

### Anti-Patterns to Avoid

- **DO NOT** create a new `DeepLinkButton` component — update the existing one in `components/shared/`
- **DO NOT** refactor `ArgoCdRestAdapter` to use `DeepLinkService` — Story 3.3 handles that migration
- **DO NOT** add REST endpoints in this story — `DeepLinkService` is an internal CDI bean, not exposed via REST
- **DO NOT** use `@ConfigMapping(prefix = "portal")` — the prefix is too broad and collides with other portal config; use `@ConfigProperty` per-property injection via `DeepLinkConfig` bean
- **DO NOT** use Spring annotations — use Quarkus/CDI (`@ApplicationScoped`, `@Inject`)
- **DO NOT** make `DeepLinkService` call any external APIs — it's pure URL string generation, no HTTP calls
- **DO NOT** cache anything — URL generation is stateless string concatenation
- **DO NOT** put DeepLinkService under `integration/` — the architecture specifies `deeplink/` as a separate top-level package

### What NOT to Build

- **No REST endpoints** — DeepLinkService is a CDI bean consumed by other services, not directly by the frontend
- **No database changes** — no new tables, no Flyway migrations
- **No frontend API functions** — deep links are returned as fields in existing/future DTOs (environment chain, build DTOs, etc.)
- **No new frontend pages or routes** — only the DeepLinkButton component update
- **No ArgoCdRestAdapter refactoring** — handled in Story 3.3
- **No environment chain integration** — handled in Story 3.3
- **No DevSpaces button placement** — handled in Story 3.2
- **No Vault secrets page** — handled in Story 3.2

### Project Structure Notes

**New backend files:**
```
src/main/java/com/portal/deeplink/
├── DeepLinkConfig.java
└── DeepLinkService.java
```

**Modified backend files:**
```
src/main/resources/application.properties  (add new config properties)
```

**Modified frontend files:**
```
src/main/webui/src/components/shared/DeepLinkButton.tsx  (add label prop + null guard)
```

**New test files:**
```
src/test/java/com/portal/deeplink/
└── DeepLinkServiceTest.java

src/main/webui/src/components/shared/
└── DeepLinkButton.test.tsx  (create or update)
```

### Previous Story Intelligence

**Story 2.8 (Environment Chain Visualization) — Most Recent:**
- Created `EnvironmentCard.tsx` which uses `DeepLinkButton` with `href={entry.argocdDeepLink}` and `toolName="ArgoCD"` — this is the primary existing consumer
- `EnvironmentChainEntryDto` already has `argocdDeepLink` field (String, nullable)
- `EnvironmentMapper.merge()` passes through the deep link from `EnvironmentStatusDto`
- Frontend test selectors query deep links by role "link" with `/ArgoCD/` name pattern
- Review finding: nested controls use `stopPropagation` to prevent card toggle — DeepLinkButton in expanded card body already works correctly

**Story 2.7 (ArgoCD Adapter):**
- `ArgoCdRestAdapter.fetchSingleEnvironmentStatus()` generates ArgoCD deep links inline: `config.url() + "/applications/" + argoAppName` (line 66)
- `DevArgoCdAdapter` generates mock deep links: `"https://dev-argocd/applications/" + argoAppName`
- `EnvironmentStatusDto` has `argocdDeepLink` field — populated by the adapter, passed through to DTO
- Deep link is also included in `PortalIntegrationException` for error responses

**Story 1.5 (API Foundation):**
- `PortalIntegrationException` has a `deepLink` field — error responses include deep links to the affected system
- `GlobalExceptionMapper` maps `PortalIntegrationException` → 502 JSON with `deepLink` field
- Error JSON format: `{ error, message, detail, system, deepLink, timestamp }`

### Git Intelligence

Recent commits show epic-level implementation patterns:
- `05d3bd5` (story 2.8): Environment chain — uses DeepLinkButton, EnvironmentChainEntryDto with argocdDeepLink
- `0028042` (story 2.7): ArgoCD adapter — inline deep link generation pattern
- `4736b65`: Epic 1 retrospective — project conventions stabilized

### References

- [Source: planning-artifacts/epics.md § Story 3.1 (line 960)] — Full acceptance criteria
- [Source: planning-artifacts/epics.md § Story 3.2-3.3 (line 1014-1081)] — Future consumer stories for context
- [Source: planning-artifacts/architecture.md § deeplink/ package (line 837)] — DeepLinkService.java, DeepLinkConfig.java
- [Source: planning-artifacts/architecture.md § DeepLinkButton.tsx (line 982)] — Frontend shared component
- [Source: planning-artifacts/architecture.md § Deep link boundary (line 1045)] — URL generation only, no API calls
- [Source: planning-artifacts/architecture.md § Deep-link-only systems table (line 1068)] — URL patterns for all tools
- [Source: planning-artifacts/architecture.md § Configuration properties (line 1145)] — Deep link config block
- [Source: planning-artifacts/architecture.md § Data flow mapping table (line 1085)] — deeplink/ → DeepLinkButton mapping
- [Source: planning-artifacts/ux-design-specification.md § Deep link patterns (line 1255)] — Scoping and visual treatment
- [Source: planning-artifacts/ux-design-specification.md § Action hierarchy (line 1171)] — Deep link actions use link variant + ↗
- [Source: planning-artifacts/ux-design-specification.md § Error feedback (line 1199)] — Deep links in error states
- [Source: project-context.md § PatternFly 6] — PF6 v6.4.1, Button component="a" with target="_blank"
- [Source: project-context.md § Framework Rules] — Deep links rendered as PF6 Button with component="a"
- [Source: project-context.md § Domain-centric packages] — com.portal.deeplink separate from integration/
- [Source: implementation-artifacts/2-8-environment-chain-visualization.md § DeepLinkButton usage] — Existing consumer pattern
- [Source: developer-portal/src/main/webui/src/components/shared/DeepLinkButton.tsx] — Existing component to update
- [Source: developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdRestAdapter.java:66] — Current inline deep link pattern
- [Source: developer-portal/src/main/java/com/portal/integration/argocd/ArgoCdConfig.java] — Existing @ConfigMapping pattern to follow
- [Source: developer-portal/src/main/resources/application.properties] — Current config properties

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus (2026-04-06)

### Debug Log References

- Backend tests: 309 pass, 0 fail (full suite)
- DeepLinkService tests: 12 pass (7 configured + 5 unconfigured profile)
- DeepLinkButton tests: 7 pass
- Pre-existing frontend failures (6 files, 17 tests) in layout/navigation tests — not related to this story

### Completion Notes List

- Created `DeepLinkConfig` as `@ApplicationScoped` bean with `@ConfigProperty` injections (not `@ConfigMapping`) to avoid namespace overlap with `ArgoCdConfig`
- `portal.argocd.url` is required (String); all other tool URLs are `Optional<String>`
- ArgoCD `generateArgoCdLink` always returns `Optional.of(...)` since ArgoCD URL is non-optional
- Grafana requires both URL and dashboard-id to generate a link; returns empty if either is missing
- Created `DeepLinkServiceUnconfiguredTest` with a `QuarkusTestProfile` that overrides tool URLs to empty strings, verifying `Optional.empty()` return behavior
- Updated `DeepLinkButton` with backward-compatible changes: `href` is now optional (null/undefined/empty returns null), added `label` prop with nullish coalescing fallback to default text
- Existing `EnvironmentCard` consumer unaffected — null href now correctly hides the button (AC #9)

### File List

- `developer-portal/src/main/java/com/portal/deeplink/DeepLinkConfig.java` (new)
- `developer-portal/src/main/java/com/portal/deeplink/DeepLinkService.java` (new)
- `developer-portal/src/main/resources/application.properties` (modified)
- `developer-portal/src/test/resources/application.properties` (modified)
- `developer-portal/src/main/webui/src/components/shared/DeepLinkButton.tsx` (modified)
- `developer-portal/src/main/webui/src/components/shared/DeepLinkButton.test.tsx` (new)
- `developer-portal/src/test/java/com/portal/deeplink/DeepLinkServiceTest.java` (new)
- `developer-portal/src/test/java/com/portal/deeplink/DeepLinkServiceUnconfiguredTest.java` (new)
- `developer-portal/src/main/java/com/portal/environment/EnvironmentService.java` (modified — review fix: AC7 DeepLinkService wiring)
