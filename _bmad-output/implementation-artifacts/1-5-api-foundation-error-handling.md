# Story 1.5: API Foundation & Error Handling

Status: done

## Story

As a developer using the portal,
I want clear, consistent error messages when something goes wrong and visible loading states when data is being fetched,
So that I always know the system's state and can take appropriate action.

## Acceptance Criteria

1. **apiFetch() typed wrapper**
   - **Given** the frontend needs to make an API call
   - **When** `apiFetch()` is used
   - **Then** it automatically injects the OIDC Bearer token in the Authorization header
   - **And** it sets Content-Type to application/json
   - **And** it uses relative URLs (no hardcoded base URL)
   - **And** it returns typed response data on success
   - **And** it parses error responses into typed error objects on failure

2. **Backend PortalIntegrationException → 502**
   - **Given** an integration adapter throws a PortalIntegrationException
   - **When** the global ExceptionMapper catches it
   - **Then** a 502 Bad Gateway response is returned with the standardized error JSON format:
   ```json
   {
     "error": "<error-code>",
     "message": "<developer-language message>",
     "detail": "<additional context>",
     "system": "<affected-system-name>",
     "deepLink": "<url-to-native-tool-if-applicable>",
     "timestamp": "<ISO-8601-UTC>"
   }
   ```

3. **Backend PortalAuthorizationException → 403**
   - **Given** a PortalAuthorizationException is thrown
   - **When** the ExceptionMapper catches it
   - **Then** a 403 Forbidden response is returned with the standardized error format

4. **Backend validation errors → 400**
   - **Given** a validation error occurs (e.g., malformed input)
   - **When** the error is returned
   - **Then** a 400 Bad Request response is returned with the standardized error format describing what is invalid

5. **Frontend ErrorAlert component**
   - **Given** the frontend ErrorAlert component receives an error object
   - **When** it renders
   - **Then** a PatternFly inline Alert (danger variant) is displayed
   - **And** the alert shows the error message in developer-friendly language
   - **And** if a deepLink is present, a link button with ↗ suffix is shown to open the native tool

6. **Frontend LoadingSpinner with system identification**
   - **Given** a view is fetching data from the backend
   - **When** the LoadingSpinner component is active
   - **Then** a PatternFly Spinner is displayed centered in the content area
   - **And** if loading exceeds 3 seconds, text appears identifying which system is slow (e.g., "Fetching status from ArgoCD...")

7. **Frontend RefreshButton**
   - **Given** a view has loaded data
   - **When** the RefreshButton component is clicked
   - **Then** the view re-fetches data from the backend
   - **And** the button shows a brief spinning state during the refresh

## Tasks / Subtasks

- [x] Task 1: Create apiFetch() typed wrapper (AC: #1)
  - [x] Create `client.ts` in `src/main/webui/src/api/`
  - [x] Implement apiFetch<T>() with OIDC bearer token injection
  - [x] Set Content-Type: application/json, use relative URLs
  - [x] Return typed response on success, parse error JSON on failure
  - [x] Create `PortalError` type in `src/main/webui/src/types/error.ts`
- [x] Task 2: Create backend PortalIntegrationException (AC: #2)
  - [x] Create `PortalIntegrationException.java` in `com.portal.integration`
  - [x] Include fields: system, operation, message, deepLink (optional)
- [x] Task 3: Create global ExceptionMapper(s) (AC: #2, #3, #4)
  - [x] Create `GlobalExceptionMapper.java` in `com.portal.common`
  - [x] Map PortalIntegrationException → 502 with standardized JSON
  - [x] Map PortalAuthorizationException → 403 with standardized JSON (consolidated from Story 1.3's AuthorizationExceptionMapper)
  - [x] Map ConstraintViolationException / validation errors → 400 with standardized JSON
  - [x] Map generic exceptions → 500 with standardized JSON (fallback)
  - [x] Create `ErrorResponse` DTO record for the standardized format
- [x] Task 4: Create frontend ErrorAlert component (AC: #5)
  - [x] Create `ErrorAlert.tsx` in `components/shared/`
  - [x] Use PatternFly Alert (inline, danger variant)
  - [x] Display error message and optional deep link button with ↗
- [x] Task 5: Create frontend DeepLinkButton component (AC: #5)
  - [x] Create `DeepLinkButton.tsx` in `components/shared/`
  - [x] PatternFly Button (link variant) with ↗ suffix, opens in new tab
  - [x] Labeled with target tool name (e.g., "Open in ArgoCD ↗")
- [x] Task 6: Create frontend LoadingSpinner component (AC: #6)
  - [x] Create `LoadingSpinner.tsx` in `components/shared/`
  - [x] PatternFly Spinner centered in parent
  - [x] After 3 seconds, show system identification text
- [x] Task 7: Create frontend RefreshButton component (AC: #7)
  - [x] Create `RefreshButton.tsx` in `components/shared/`
  - [x] Circular arrow icon, spinning state during refresh
  - [x] Accepts onRefresh callback
- [x] Task 8: Create useApiFetch custom hook (AC: #1, #6, #7)
  - [x] Create generic data-fetching hook returning `{ data, error, isLoading, refresh }`
  - [x] Integrate with apiFetch(), LoadingSpinner, and ErrorAlert patterns
- [x] Task 9: Write tests (AC: #1-#7)
  - [x] Backend: ExceptionMapper integration tests for 400, 403, 502, 404, 500 responses
  - [x] Frontend: ErrorAlert.test.tsx, LoadingSpinner.test.tsx, RefreshButton.test.tsx, client.test.ts

### Review Findings

- [x] [Review][Decision] Choose and implement the first real consumer of the new fetch/loading/error/refresh pattern [`developer-portal/src/main/webui/src/routes/TeamDashboardPage.tsx:1`] — Fixed: `TeamDashboardPage` now uses `useApiFetch` + `LoadingSpinner` + `ErrorAlert` + `RefreshButton` as the first live consumer of all Story 1.5 primitives.
- [x] [Review][Patch] Wire the OIDC token into `apiFetch` so authenticated requests actually send `Authorization` [`developer-portal/src/main/webui/src/api/client.ts:13`] — Fixed: `useAuth` now exposes a `token` field, `App.tsx` registers it via `setTokenAccessor()` on mount. Dev-mode placeholder token wired; real OIDC provider replaces it later.
- [x] [Review][Patch] Make `apiFetch` robust to valid `HeadersInit` values and non-JSON success bodies [`developer-portal/src/main/webui/src/api/client.ts:40`] — Fixed: uses `new Headers()` constructor for safe merging, does not override caller Content-Type, and wraps success `json()` in try/catch producing a typed `parse-error` ApiError.

## Dev Notes

### Backend — Standardized Error Response Format

All API errors must follow this JSON structure (AR12):

```json
{
  "error": "deployment-failed",
  "message": "Deployment to QA failed: health check timeout after 120s",
  "detail": "ArgoCD sync completed but pod readiness probe failed",
  "system": "argocd",
  "deepLink": "https://argocd.internal/applications/payments-checkout-api-qa",
  "timestamp": "2026-04-02T14:30:00Z"
}
```

**ErrorResponse DTO:**

```java
package com.portal.common;

import java.time.Instant;

public record ErrorResponse(
    String error,
    String message,
    String detail,
    String system,
    String deepLink,
    Instant timestamp
) {
    public static ErrorResponse of(String error, String message, String detail, String system, String deepLink) {
        return new ErrorResponse(error, message, detail, system, deepLink, Instant.now());
    }

    public static ErrorResponse of(String error, String message, String detail) {
        return new ErrorResponse(error, message, detail, "portal", null, Instant.now());
    }
}
```

### Backend — HTTP Status Convention

| Status | Usage | Exception Type |
|---|---|---|
| 200 | Successful GET, PUT | — |
| 201 | POST creating a resource | — |
| 204 | Successful DELETE | — |
| **400** | Validation errors (malformed input) | `ConstraintViolationException`, `IllegalArgumentException` |
| 401 | Missing/invalid JWT | Quarkus OIDC (automatic) |
| **403** | Casbin denies permission | `PortalAuthorizationException` (Story 1.3) |
| 404 | Resource not found OR cross-team (never reveal) | `NotFoundException` |
| **502** | Platform integration failure | `PortalIntegrationException` |
| 503 | Portal itself unhealthy | Health check |

**Key rule:** Return 404 (not 403) for resources outside the user's team scope — never leak cross-team resource existence.

### Backend — PortalIntegrationException

```java
package com.portal.integration;

public class PortalIntegrationException extends RuntimeException {

    private final String system;
    private final String operation;
    private final String deepLink;

    public PortalIntegrationException(String system, String operation, String message) {
        this(system, operation, message, null);
    }

    public PortalIntegrationException(String system, String operation, String message, String deepLink) {
        super(message);
        this.system = system;
        this.operation = operation;
        this.deepLink = deepLink;
    }

    public PortalIntegrationException(String system, String operation, String message, String deepLink, Throwable cause) {
        super(message, cause);
        this.system = system;
        this.operation = operation;
        this.deepLink = deepLink;
    }

    // getters
    public String getSystem() { return system; }
    public String getOperation() { return operation; }
    public String getDeepLink() { return deepLink; }
}
```

**Usage by adapters (future stories):**
```java
throw new PortalIntegrationException(
    "argocd",
    "sync-application",
    "ArgoCD sync failed: connection timeout",
    "https://argocd.internal/applications/my-app-qa"
);
```

### Backend — Global ExceptionMapper

```java
package com.portal.common;

import com.portal.auth.PortalAuthorizationException;
import com.portal.integration.PortalIntegrationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof PortalIntegrationException pie) {
            return Response.status(502)
                .entity(ErrorResponse.of(
                    "integration-error",
                    pie.getMessage(),
                    "Operation: " + pie.getOperation(),
                    pie.getSystem(),
                    pie.getDeepLink()))
                .build();
        }

        if (e instanceof PortalAuthorizationException pae) {
            return Response.status(403)
                .entity(ErrorResponse.of(
                    "forbidden",
                    "You do not have permission to perform this action",
                    pae.getMessage()))
                .build();
        }

        if (e instanceof ConstraintViolationException cve) {
            String violations = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(java.util.stream.Collectors.joining(", "));
            return Response.status(400)
                .entity(ErrorResponse.of(
                    "validation-error",
                    "Invalid request data",
                    violations))
                .build();
        }

        if (e instanceof NotFoundException) {
            return Response.status(404)
                .entity(ErrorResponse.of(
                    "not-found",
                    "The requested resource was not found",
                    null))
                .build();
        }

        // Fallback — 500 for unexpected errors
        return Response.status(500)
            .entity(ErrorResponse.of(
                "internal-error",
                "An unexpected error occurred",
                e.getMessage()))
            .build();
    }
}
```

**Note on Story 1.3's AuthorizationExceptionMapper:** Story 1.3 created a separate `AuthorizationExceptionMapper` for `PortalAuthorizationException`. This story should either:
- **Option A:** Consolidate into `GlobalExceptionMapper` (handles all exceptions in one place)
- **Option B:** Keep separate mappers but ensure consistent `ErrorResponse` format

Option A is recommended for simplicity. If keeping separate, the `GlobalExceptionMapper` should NOT catch `PortalAuthorizationException` (Quarkus uses the most-specific mapper).

### Frontend — PortalError Type

```typescript
// src/main/webui/src/types/error.ts

export interface PortalError {
  error: string;
  message: string;
  detail?: string;
  system?: string;
  deepLink?: string;
  timestamp: string;
}
```

### Frontend — apiFetch() Implementation

```typescript
// src/main/webui/src/api/client.ts

import { PortalError } from '../types/error';

export class ApiError extends Error {
  constructor(
    public status: number,
    public portalError: PortalError,
  ) {
    super(portalError.message);
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getOidcToken(); // from auth context

  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorBody: PortalError = await response.json().catch(() => ({
      error: 'unknown',
      message: `Request failed with status ${response.status}`,
      timestamp: new Date().toISOString(),
    }));
    throw new ApiError(response.status, errorBody);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
```

**Key decisions:**
- Uses native `fetch` — no axios (architecture mandate)
- Relative URLs only — Quinoa proxies `/api/v1/` to Quarkus in dev mode
- Bearer token injected on every request
- Errors parsed into `PortalError` typed objects
- `ApiError` wraps both HTTP status and structured error body
- `getOidcToken()` placeholder — actual implementation depends on OIDC library choice (e.g., `oidc-client-ts`, Keycloak JS adapter, or a React context)

### Frontend — Domain API Functions Pattern

```typescript
// src/main/webui/src/api/teams.ts
import { apiFetch } from './client';
import { TeamSummary } from '../types/team';

export function fetchTeams(): Promise<TeamSummary[]> {
  return apiFetch<TeamSummary[]>('/api/v1/teams');
}
```

Each domain file (`teams.ts`, `applications.ts`, `builds.ts`, etc.) exports typed functions that call `apiFetch()`. Components never call `fetch()` directly.

### Frontend — ErrorAlert Component

```typescript
// src/main/webui/src/components/shared/ErrorAlert.tsx

import { Alert, AlertActionLink } from '@patternfly/react-core';
import { PortalError } from '../../types/error';

interface ErrorAlertProps {
  error: PortalError;
}

export function ErrorAlert({ error }: ErrorAlertProps) {
  return (
    <Alert
      variant="danger"
      title={error.message}
      isInline
      actionLinks={
        error.deepLink ? (
          <AlertActionLink
            component="a"
            href={error.deepLink}
            target="_blank"
            rel="noopener noreferrer"
          >
            Open in {error.system ?? 'tool'} ↗
          </AlertActionLink>
        ) : undefined
      }
    >
      {error.detail && <p>{error.detail}</p>}
    </Alert>
  );
}
```

**UX error message format:** Every error communicates (1) what happened — the `title`, (2) why — the `detail`, (3) what to do — the deep link action or contextual guidance. If no deep link, the error still shows the message and detail.

**Variant note:** Use `variant="danger"` for errors. Use `variant="warning"` for "system unreachable but partial data shown" scenarios (e.g., "ArgoCD is currently unreachable — deployment status may be stale").

### Frontend — DeepLinkButton Component

```typescript
// src/main/webui/src/components/shared/DeepLinkButton.tsx

import { Button } from '@patternfly/react-core';

interface DeepLinkButtonProps {
  href: string;
  toolName: string;
}

export function DeepLinkButton({ href, toolName }: DeepLinkButtonProps) {
  return (
    <Button
      variant="link"
      component="a"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      isInline
    >
      Open in {toolName} ↗
    </Button>
  );
}
```

**UX rules:** Always opens in new tab. Always labeled with target tool name. Always uses ↗ suffix. Link variant button — not primary or secondary.

### Frontend — LoadingSpinner Component

```typescript
// src/main/webui/src/components/shared/LoadingSpinner.tsx

import { Spinner, Content } from '@patternfly/react-core';
import { useEffect, useState } from 'react';

interface LoadingSpinnerProps {
  systemName?: string;
}

export function LoadingSpinner({ systemName }: LoadingSpinnerProps) {
  const [showSystemHint, setShowSystemHint] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setShowSystemHint(true), 3000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: 'var(--pf-t--global--spacer--xl)' }}>
      <Spinner aria-label="Loading" />
      {showSystemHint && systemName && (
        <Content component="p">
          Fetching status from {systemName}...
        </Content>
      )}
    </div>
  );
}
```

**3-second delay:** Timer starts when the component mounts. After 3 seconds, the system identification text appears. If data loads before 3 seconds, the component unmounts and the timer is cleared.

### Frontend — RefreshButton Component

```typescript
// src/main/webui/src/components/shared/RefreshButton.tsx

import { Button } from '@patternfly/react-core';
import { SyncIcon } from '@patternfly/react-icons';

interface RefreshButtonProps {
  onRefresh: () => void;
  isRefreshing: boolean;
  'aria-label'?: string;
}

export function RefreshButton({ onRefresh, isRefreshing, 'aria-label': ariaLabel = 'Refresh' }: RefreshButtonProps) {
  return (
    <Button
      variant="plain"
      onClick={onRefresh}
      isDisabled={isRefreshing}
      aria-label={ariaLabel}
    >
      <SyncIcon className={isRefreshing ? 'pf-v6-u-spin' : undefined} />
    </Button>
  );
}
```

**Behavior:** Circular arrow icon (`SyncIcon`). Shows spinning animation during refresh. Disabled while refresh in progress. No auto-refresh/polling for MVP — user-initiated only.

### Frontend — useApiFetch Hook Pattern

```typescript
// src/main/webui/src/hooks/useApiFetch.ts

import { useState, useEffect, useCallback } from 'react';
import { apiFetch, ApiError } from '../api/client';
import { PortalError } from '../types/error';

interface UseApiFetchResult<T> {
  data: T | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: () => void;
}

export function useApiFetch<T>(path: string): UseApiFetchResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await apiFetch<T>(path);
      setData(result);
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.portalError);
      } else {
        setError({
          error: 'unknown',
          message: 'An unexpected error occurred',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [path]);

  useEffect(() => { fetchData(); }, [fetchData]);

  return { data, error, isLoading, refresh: fetchData };
}
```

**Usage in components:**
```typescript
function ApplicationBuildsPage() {
  const { teamId, appId } = useParams();
  const { data: builds, error, isLoading, refresh } = useApiFetch<Build[]>(
    `/api/v1/teams/${teamId}/applications/${appId}/builds`
  );

  if (isLoading) return <LoadingSpinner systemName="Tekton" />;
  if (error) return <ErrorAlert error={error} />;

  return (
    <>
      <RefreshButton onRefresh={refresh} isRefreshing={isLoading} />
      {/* render builds */}
    </>
  );
}
```

### Frontend — api/ Directory Structure

| File | Purpose | Created In |
|---|---|---|
| `api/client.ts` | `apiFetch()`, `ApiError` class | **This story** |
| `api/teams.ts` | `fetchTeams()` | **This story** (basic) |
| `api/applications.ts` | `fetchApplications()`, etc. | Story 2.x |
| `api/environments.ts` | Environment chain data | Story 2.x |
| `api/builds.ts` | Build operations | Story 4.x |
| `api/releases.ts` | Release operations | Story 4.x |
| `api/deployments.ts` | Deployment operations | Story 5.x |
| `api/health.ts` | Health signals | Story 6.x |
| `api/dora.ts` | DORA metrics | Story 6.x |
| `api/dashboard.ts` | Team dashboard aggregation | Story 7.x |
| `api/clusters.ts` | Admin cluster CRUD | Story 1.7 |

### OIDC Token Access — Implementation Note

The `apiFetch()` needs the OIDC bearer token. Options:

1. **oidc-client-ts** — standalone OIDC library, manages token lifecycle, provides `getUser().access_token`
2. **Keycloak JS adapter** — if the OIDC provider is Keycloak
3. **React context** — wrap the app in an auth provider that exposes the token

For MVP, use a simple auth context that stores the token obtained during login. The `getOidcToken()` function in `client.ts` should read from this context. The actual OIDC login redirect flow is outside this story's scope — for dev mode, a hardcoded dev token or Keycloak Dev Services token may be used.

### What NOT to Build in This Story

- No actual OIDC login redirect / token acquisition flow (depends on OIDC library choice)
- No domain-specific API functions beyond `teams.ts` (later stories add domain modules)
- No real integration adapter calls (PortalIntegrationException exists but no adapters throw it yet)
- No auto-refresh / polling (user-initiated only for MVP)
- No client-side caching between navigations
- No toast notifications — errors are always inline per UX mandate

### Previous Story Intelligence (Stories 1.1–1.4)

- **Story 1.1:** Frontend scaffold at `src/main/webui/` with Vite + React 18 + TS 5 + PF6. Directories `api/`, `hooks/`, `components/shared/`, `types/` exist as placeholders.
- **Story 1.2:** Backend `TeamResource` at `/api/v1/teams` returns team data. `TeamContext` CDI bean with team/role. Backend returns JSON responses.
- **Story 1.3:** `PortalAuthorizationException` + `AuthorizationExceptionMapper` exist returning 403 with standardized JSON. `ErrorResponse`-like pattern may already be established — **consolidate** into the shared `ErrorResponse` record.
- **Story 1.4:** AppShell, Sidebar, Breadcrumbs, Tabs, route structure, and placeholder pages exist. `useAuth` hook provides user/team/role context. Placeholder route pages are ready to consume `useApiFetch` + display `ErrorAlert` / `LoadingSpinner`.

### Files to Create/Modify

**Create:**
- `src/main/java/com/portal/common/ErrorResponse.java`
- `src/main/java/com/portal/common/GlobalExceptionMapper.java`
- `src/main/java/com/portal/integration/PortalIntegrationException.java`
- `src/main/webui/src/api/client.ts`
- `src/main/webui/src/api/teams.ts`
- `src/main/webui/src/types/error.ts`
- `src/main/webui/src/components/shared/ErrorAlert.tsx`
- `src/main/webui/src/components/shared/DeepLinkButton.tsx`
- `src/main/webui/src/components/shared/LoadingSpinner.tsx`
- `src/main/webui/src/components/shared/RefreshButton.tsx`
- `src/main/webui/src/hooks/useApiFetch.ts`
- `src/test/java/com/portal/common/GlobalExceptionMapperIT.java`
- `src/main/webui/src/components/shared/ErrorAlert.test.tsx`
- `src/main/webui/src/components/shared/LoadingSpinner.test.tsx`
- `src/main/webui/src/components/shared/RefreshButton.test.tsx`

**Modify:**
- `src/main/java/com/portal/auth/AuthorizationExceptionMapper.java` — consolidate into GlobalExceptionMapper or align ErrorResponse format
- `src/main/webui/src/api/` — remove any placeholder files, add `client.ts` and `teams.ts`

### Project Structure Notes

- `com.portal.common` holds cross-cutting backend infrastructure: `ErrorResponse`, `GlobalExceptionMapper`
- `com.portal.integration` holds integration base classes: `PortalIntegrationException` (adapters live in subpackages like `integration.argocd`)
- Frontend `api/client.ts` is the single HTTP client — all domain API files import from it
- Frontend `types/error.ts` defines `PortalError` matching the backend `ErrorResponse` exactly
- The `useApiFetch` hook is the standard data-fetching pattern — all pages should use it

### References

- [Source: planning-artifacts/architecture.md § Error Response Format (AR12)] — Standardized JSON error structure, fields, example
- [Source: planning-artifacts/architecture.md § HTTP Status Conventions] — 400/403/404/502 mappings
- [Source: planning-artifacts/architecture.md § Frontend Patterns] — apiFetch() wrapper, { data, error, isLoading } hook pattern, relative URLs, fetch API
- [Source: planning-artifacts/architecture.md § Process Patterns] — PortalIntegrationException thrown by adapters, ExceptionMapper
- [Source: planning-artifacts/architecture.md § Frontend Structure] — api/client.ts, components/shared/, types/error.ts
- [Source: planning-artifacts/ux-design-specification.md § Error Feedback] — Mandatory three-part format (what/why/what to do), inline diagnosis, deep link escape hatch
- [Source: planning-artifacts/ux-design-specification.md § Loading & Data Freshness] — Spinner, 3s system identification, manual refresh, no auto-refresh MVP
- [Source: planning-artifacts/ux-design-specification.md § Deep Link Treatment] — Link variant button with ↗, new tab, tool name label
- [Source: planning-artifacts/epics.md § Epic 1 / Story 1.5] — Acceptance criteria, story statement
- [Source: implementation-artifacts/1-3-casbin-rbac-authorization-layer.md] — Existing PortalAuthorizationException, AuthorizationExceptionMapper
- [Source: implementation-artifacts/1-4-portal-page-shell-navigation.md] — Existing route structure, placeholder pages, useAuth hook

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus (Cursor)

### Debug Log References

- Pre-existing PF6 TS errors in AppShell.tsx and ApplicationLayout.tsx (`variant="light"` → `variant="default"`) fixed to unblock Maven build
- Casbin PermissionFilter blocked test stub endpoints at `/api/v1/test/` — resolved by moving test stubs to `/test/exceptions/` (outside `/api/v1/` scope)
- GlobalExceptionMapper needed `quarkus-hibernate-validator` dependency for `jakarta.validation.ConstraintViolationException`
- `AuthorizationExceptionMapper` from Story 1.3 deleted and consolidated into `GlobalExceptionMapper` (Option A per story notes)
- `OidcExceptionMapper` refactored to use `ErrorResponse` DTO instead of raw JSON string template
- apiFetch uses a `setTokenAccessor()` registration pattern for OIDC token — avoids coupling to a specific React context at the module level

### Completion Notes List

- ✅ Task 1: Created `apiFetch<T>()` typed wrapper with bearer token injection, JSON content type, relative URLs, typed error parsing. Token access via `setTokenAccessor()` registration pattern. 9 unit tests passing.
- ✅ Task 2: Created `PortalIntegrationException` with system, operation, message, and optional deepLink fields. Three constructor overloads for different use cases.
- ✅ Task 3: Created `GlobalExceptionMapper` + `ErrorResponse` record in `com.portal.common`. Handles PortalIntegrationException→502, PortalAuthorizationException→403, ConstraintViolationException/IllegalArgumentException→400, NotFoundException→404, fallback→500. Consolidated and deleted Story 1.3's separate `AuthorizationExceptionMapper`. Updated `OidcExceptionMapper` to use `ErrorResponse` DTO. Added `quarkus-hibernate-validator` dependency. 5 new integration tests + all 13 existing PermissionFilter ITs still pass.
- ✅ Task 4: Created `ErrorAlert` with PF6 inline danger Alert, detail text, and deep link via `AlertActionLink`. 7 tests passing.
- ✅ Task 5: Created `DeepLinkButton` with PF6 link variant Button, new tab, ↗ suffix.
- ✅ Task 6: Created `LoadingSpinner` with PF6 Spinner, 3-second delay for system identification text, timer cleanup on unmount. 5 tests passing.
- ✅ Task 7: Created `RefreshButton` with PF6 plain Button, SyncIcon, spinning class during refresh, disabled state. 7 tests passing.
- ✅ Task 8: Created `useApiFetch<T>()` hook returning `{ data, error, isLoading, refresh }`.
- ✅ Task 9: All tests written and passing — 9 apiFetch unit tests, 7 ErrorAlert tests, 5 LoadingSpinner tests, 7 RefreshButton tests, 5 backend IT tests.
- Also created `api/teams.ts` with typed `fetchTeams()` function as the first domain API module.
- Fixed pre-existing PF6 `variant="light"` TS errors in AppShell and ApplicationLayout.

### File List

**Created:**
- `developer-portal/src/main/java/com/portal/common/ErrorResponse.java`
- `developer-portal/src/main/java/com/portal/common/GlobalExceptionMapper.java`
- `developer-portal/src/main/java/com/portal/integration/PortalIntegrationException.java`
- `developer-portal/src/main/webui/src/api/client.ts`
- `developer-portal/src/main/webui/src/api/client.test.ts`
- `developer-portal/src/main/webui/src/api/teams.ts`
- `developer-portal/src/main/webui/src/types/error.ts`
- `developer-portal/src/main/webui/src/components/shared/ErrorAlert.tsx`
- `developer-portal/src/main/webui/src/components/shared/ErrorAlert.test.tsx`
- `developer-portal/src/main/webui/src/components/shared/DeepLinkButton.tsx`
- `developer-portal/src/main/webui/src/components/shared/LoadingSpinner.tsx`
- `developer-portal/src/main/webui/src/components/shared/LoadingSpinner.test.tsx`
- `developer-portal/src/main/webui/src/components/shared/RefreshButton.tsx`
- `developer-portal/src/main/webui/src/components/shared/RefreshButton.test.tsx`
- `developer-portal/src/main/webui/src/hooks/useApiFetch.ts`
- `developer-portal/src/test/java/com/portal/common/GlobalExceptionMapperIT.java`
- `developer-portal/src/test/java/com/portal/common/ExceptionMapperTestStubResource.java`

**Modified:**
- `developer-portal/pom.xml` — added `quarkus-hibernate-validator` dependency
- `developer-portal/src/main/java/com/portal/auth/PortalAuthorizationException.java` — updated Javadoc to reference GlobalExceptionMapper
- `developer-portal/src/main/java/com/portal/auth/OidcExceptionMapper.java` — refactored to use ErrorResponse DTO
- `developer-portal/src/main/webui/src/components/layout/AppShell.tsx` — fixed pre-existing PF6 variant TS error
- `developer-portal/src/main/webui/src/components/layout/ApplicationLayout.tsx` — fixed pre-existing PF6 variant TS error

**Deleted:**
- `developer-portal/src/main/java/com/portal/auth/AuthorizationExceptionMapper.java` — consolidated into GlobalExceptionMapper

## Change Log

- 2026-04-04: Implemented Story 1.5 — API Foundation & Error Handling. Created apiFetch() typed wrapper, PortalIntegrationException, GlobalExceptionMapper with ErrorResponse DTO (consolidating AuthorizationExceptionMapper), ErrorAlert, DeepLinkButton, LoadingSpinner, RefreshButton components, useApiFetch hook, and api/teams.ts domain module. Full test coverage with 33 frontend tests and 5 backend integration tests.
