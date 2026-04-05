# Story 1.7: Admin Cluster Registration

Status: done

## Story

As a platform admin,
I want to register OpenShift clusters in the portal by providing their name and API server URL,
So that registered clusters are available as targets when configuring application environment-to-cluster mapping.

## Acceptance Criteria

1. **Cluster entity and Flyway migration**
   - **Given** a Cluster entity and Flyway migration exist
   - **When** the application starts
   - **Then** the `clusters` table is created with columns: `id` (bigserial PK), `name` (varchar, unique), `api_server_url` (varchar), `created_at` (timestamptz), `updated_at` (timestamptz)

2. **Create cluster — POST 201**
   - **Given** an admin user is authenticated (admin role in JWT)
   - **When** `POST /api/v1/admin/clusters` is called with `{"name": "ocp-dev-01", "apiServerUrl": "https://api.ocp-dev-01.example.com:6443"}`
   - **Then** a new cluster is created in the database
   - **And** a 201 Created response is returned with the cluster data including the generated id

3. **List clusters — GET 200**
   - **Given** an admin user is authenticated
   - **When** `GET /api/v1/admin/clusters` is called
   - **Then** all registered clusters are returned as a JSON array
   - **And** each cluster includes id, name, apiServerUrl, createdAt, and updatedAt

4. **Update cluster — PUT 200**
   - **Given** an admin user is authenticated
   - **When** `PUT /api/v1/admin/clusters/{clusterId}` is called with updated data
   - **Then** the cluster record is updated in the database
   - **And** a 200 OK response is returned with the updated cluster data

5. **Delete cluster — DELETE 204**
   - **Given** an admin user is authenticated
   - **When** `DELETE /api/v1/admin/clusters/{clusterId}` is called
   - **Then** the cluster is removed from the database
   - **And** a 204 No Content response is returned

6. **Duplicate name validation — 400**
   - **Given** an admin attempts to create a cluster with a name that already exists
   - **When** the POST request is processed
   - **Then** a 400 Bad Request response is returned indicating the cluster name must be unique

7. **Non-admin access denied — 403**
   - **Given** a user with "member" or "lead" role
   - **When** they attempt any operation on `/api/v1/admin/clusters`
   - **Then** a 403 Forbidden response is returned

8. **Admin Clusters Page — frontend**
   - **Given** the AdminClustersPage frontend component
   - **When** an admin navigates to the cluster management view
   - **Then** a PatternFly table displays all registered clusters with name, API server URL, and creation date
   - **And** action buttons for Edit and Delete are available per row
   - **And** a primary "Register Cluster" button is available in the page header
   - **And** the Register Cluster action opens a form to enter name and API server URL

## Tasks / Subtasks

- [x] Task 1: Create Flyway migration for clusters table (AC: #1)
  - [x] Create migration SQL file in `src/main/resources/db/migration/`
  - [x] Use the next sequential version number (check existing migrations)
  - [x] Define columns: `id` (bigserial PK), `name` (varchar NOT NULL UNIQUE), `api_server_url` (varchar NOT NULL), `created_at` (timestamptz NOT NULL DEFAULT now()), `updated_at` (timestamptz NOT NULL DEFAULT now())
  - [x] Add unique constraint: `uq_clusters_name`
- [x] Task 2: Create Cluster Panache entity (AC: #1)
  - [x] Create `Cluster.java` in `com.portal.cluster`
  - [x] Extend `PanacheEntityBase` (Active Record pattern with IDENTITY generation)
  - [x] Fields: `name`, `apiServerUrl`, `createdAt`, `updatedAt`
  - [x] Add `@PrePersist` and `@PreUpdate` lifecycle callbacks for timestamps
  - [x] Add static finder: `findByName(String name)`
- [x] Task 3: Create ClusterDto (AC: #2, #3, #4)
  - [x] Create `ClusterDto.java` record in `com.portal.cluster`
  - [x] Fields: `id` (Long), `name` (String), `apiServerUrl` (String), `createdAt` (Instant), `updatedAt` (Instant)
  - [x] Create `CreateClusterRequest` record: `name`, `apiServerUrl`
  - [x] Create `UpdateClusterRequest` record: `name`, `apiServerUrl`
- [x] Task 4: Create ClusterService (AC: #2, #3, #4, #5, #6)
  - [x] Create `ClusterService.java` in `com.portal.cluster`
  - [x] `@ApplicationScoped` CDI bean
  - [x] `listAll()` → List<ClusterDto>
  - [x] `create(CreateClusterRequest)` → ClusterDto (validate unique name, throw 400 on duplicate)
  - [x] `update(Long id, UpdateClusterRequest)` → ClusterDto (throw 404 if not found, 400 on duplicate name)
  - [x] `delete(Long id)` → void (throw 404 if not found)
  - [x] `@Transactional` on mutating methods
- [x] Task 5: Create ClusterResource REST endpoint (AC: #2, #3, #4, #5, #7)
  - [x] Create `ClusterResource.java` in `com.portal.cluster`
  - [x] `@Path("/api/v1/admin/clusters")`
  - [x] `GET /` → 200 with List<ClusterDto>
  - [x] `POST /` → 201 with ClusterDto
  - [x] `PUT /{clusterId}` → 200 with ClusterDto
  - [x] `DELETE /{clusterId}` → 204
  - [x] Casbin enforces admin-only access (PermissionFilter maps `/admin/clusters` → resource `clusters`)
- [x] Task 6: Create frontend Cluster type (AC: #8)
  - [x] Create `cluster.ts` in `src/main/webui/src/types/`
  - [x] Define `Cluster`, `CreateClusterRequest`, `UpdateClusterRequest` interfaces
- [x] Task 7: Create frontend clusters API functions (AC: #8)
  - [x] Create `clusters.ts` in `src/main/webui/src/api/`
  - [x] `fetchClusters()`, `createCluster()`, `updateCluster()`, `deleteCluster()`
  - [x] All use `apiFetch()` from `client.ts`
- [x] Task 8: Implement AdminClustersPage (AC: #8)
  - [x] Replace placeholder `AdminClustersPage.tsx` in `src/main/webui/src/routes/`
  - [x] PatternFly Table with columns: Name, API Server URL, Created
  - [x] Row actions: Edit, Delete (via ActionsColumn or Kebab)
  - [x] "Register Cluster" primary button in page header
  - [x] Modal form for register/edit with name + API server URL fields
  - [x] Delete confirmation modal
  - [x] Use `useApiFetch` hook for data loading + ErrorAlert + LoadingSpinner
  - [x] RefreshButton in the page header
- [x] Task 9: Write backend tests (AC: #1-#7)
  - [x] Create `ClusterResourceIT.java` in `src/test/java/com/portal/cluster/`
  - [x] `@QuarkusTest` + REST Assured
  - [x] Test POST creates cluster → 201
  - [x] Test GET lists all clusters → 200
  - [x] Test PUT updates cluster → 200
  - [x] Test DELETE removes cluster → 204
  - [x] Test duplicate name → 400
  - [x] Test non-admin access → 403 (member and lead roles)
  - [x] Test non-existent cluster → 404
- [x] Task 10: Write frontend tests (AC: #8)
  - [x] Create `AdminClustersPage.test.tsx`
  - [x] Test table renders cluster data
  - [x] Test register button opens modal
  - [x] Test form submission

## Dev Notes

### Backend — Cluster Panache Entity

```java
package com.portal.cluster;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "clusters")
public class Cluster extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String name;

    @Column(name = "api_server_url", nullable = false)
    public String apiServerUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static Cluster findByName(String name) {
        return find("name", name).firstResult();
    }
}
```

**Key conventions:**
- Extends `PanacheEntity` (Active Record pattern — architecture mandate)
- Public fields (Panache convention — no getters/setters needed)
- `@Column(name = "...")` only for multi-word columns where Hibernate's default mapping differs from the DB convention
- Static finder methods on the entity class (Panache pattern)
- Timestamps managed by JPA lifecycle callbacks, stored as `Instant` (UTC)

### Backend — Flyway Migration

```sql
-- V{N}__create_clusters.sql
-- Check existing migrations in src/main/resources/db/migration/ and use the next sequential number.
-- Architecture shows V1, but earlier stories may have created V1 for teams.

CREATE TABLE clusters (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    api_server_url VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_clusters_name UNIQUE (name)
);
```

**Migration numbering:** The architecture shows `V1__create_clusters.sql` but Story 1.2 may have already created `V1__create_teams.sql`. The dev agent **must** check existing migrations in `src/main/resources/db/migration/` and use the next sequential number. Never modify existing migrations.

### Backend — DTOs

```java
package com.portal.cluster;

import java.time.Instant;

public record ClusterDto(
    Long id,
    String name,
    String apiServerUrl,
    Instant createdAt,
    Instant updatedAt
) {
    public static ClusterDto from(Cluster entity) {
        return new ClusterDto(
            entity.id,
            entity.name,
            entity.apiServerUrl,
            entity.createdAt,
            entity.updatedAt
        );
    }
}
```

```java
package com.portal.cluster;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateClusterRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^https://.*") String apiServerUrl
) {}
```

```java
package com.portal.cluster;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateClusterRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^https://.*") String apiServerUrl
) {}
```

**Validation:** Use Bean Validation annotations (`@NotBlank`, `@Pattern`). The `GlobalExceptionMapper` from Story 1.5 already maps `ConstraintViolationException` → 400 with standardized error JSON. No additional validation handling needed.

**API server URL pattern:** Enforce `https://` prefix — all cluster API server URLs must use TLS (NFR8).

### Backend — ClusterService

```java
package com.portal.cluster;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class ClusterService {

    public List<ClusterDto> listAll() {
        return Cluster.<Cluster>listAll().stream()
            .map(ClusterDto::from)
            .toList();
    }

    @Transactional
    public ClusterDto create(CreateClusterRequest request) {
        if (Cluster.findByName(request.name()) != null) {
            throw new IllegalArgumentException("Cluster name '" + request.name() + "' already exists");
        }
        Cluster cluster = new Cluster();
        cluster.name = request.name();
        cluster.apiServerUrl = request.apiServerUrl();
        cluster.persist();
        return ClusterDto.from(cluster);
    }

    @Transactional
    public ClusterDto update(Long id, UpdateClusterRequest request) {
        Cluster cluster = Cluster.findById(id);
        if (cluster == null) {
            throw new NotFoundException("Cluster not found");
        }
        Cluster existing = Cluster.findByName(request.name());
        if (existing != null && !existing.id.equals(id)) {
            throw new IllegalArgumentException("Cluster name '" + request.name() + "' already exists");
        }
        cluster.name = request.name();
        cluster.apiServerUrl = request.apiServerUrl();
        return ClusterDto.from(cluster);
    }

    @Transactional
    public void delete(Long id) {
        Cluster cluster = Cluster.findById(id);
        if (cluster == null) {
            throw new NotFoundException("Cluster not found");
        }
        cluster.delete();
    }
}
```

**Duplicate name handling:** Throw `IllegalArgumentException` for duplicate names — `GlobalExceptionMapper` catches this as a validation-class error and returns 400. Alternatively, catch the DB unique constraint violation, but checking in code produces a better error message.

**Note on `NotFoundException`:** Use `jakarta.ws.rs.NotFoundException` — the `GlobalExceptionMapper` from Story 1.5 already handles it → 404.

### Backend — ClusterResource

```java
package com.portal.cluster;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/v1/admin/clusters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClusterResource {

    @Inject
    ClusterService clusterService;

    @GET
    public List<ClusterDto> list() {
        return clusterService.listAll();
    }

    @POST
    public Response create(@Valid CreateClusterRequest request) {
        ClusterDto created = clusterService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{clusterId}")
    public ClusterDto update(@PathParam("clusterId") Long clusterId, @Valid UpdateClusterRequest request) {
        return clusterService.update(clusterId, request);
    }

    @DELETE
    @Path("/{clusterId}")
    public Response delete(@PathParam("clusterId") Long clusterId) {
        clusterService.delete(clusterId);
        return Response.noContent().build();
    }
}
```

**Authorization:** The `PermissionFilter` (Story 1.3) maps requests to `/api/v1/admin/clusters` → Casbin resource `clusters`. The Casbin policy grants `clusters:read`, `clusters:create`, `clusters:update`, `clusters:delete` only to the `admin` role. Non-admin users get 403 automatically. No additional authorization code needed in the resource.

**PermissionFilter mapping note:** The filter's `extractResource()` method must map the path `api/v1/admin/clusters` → resource type `clusters`. Verify this works with the path-to-resource mapping logic established in Story 1.3. The `mapAction()` method maps HTTP methods: GET → `read`, POST → `create`, PUT → `update`, DELETE → `delete`.

### Backend — GlobalExceptionMapper Coverage

The `GlobalExceptionMapper` from Story 1.5 already handles all error types this story needs:

| Exception | HTTP Status | Error Code | Already Handled? |
|---|---|---|---|
| `ConstraintViolationException` (Bean Validation) | 400 | `validation-error` | Yes (Story 1.5) |
| `IllegalArgumentException` (duplicate name) | 400 | Needs handling | **Add to mapper** |
| `NotFoundException` | 404 | `not-found` | Yes (Story 1.5) |
| `PortalAuthorizationException` | 403 | `forbidden` | Yes (Story 1.3/1.5) |

**Action required:** Check if `GlobalExceptionMapper` handles `IllegalArgumentException`. If not, add a handler that maps it → 400 with `validation-error` code and the exception message as detail. Alternatively, create a custom `DuplicateClusterNameException` extending a validation exception class.

### Frontend — TypeScript Types

```typescript
// src/main/webui/src/types/cluster.ts

export interface Cluster {
  id: number;
  name: string;
  apiServerUrl: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateClusterRequest {
  name: string;
  apiServerUrl: string;
}

export interface UpdateClusterRequest {
  name: string;
  apiServerUrl: string;
}
```

### Frontend — API Functions

```typescript
// src/main/webui/src/api/clusters.ts

import { apiFetch } from './client';
import { Cluster, CreateClusterRequest, UpdateClusterRequest } from '../types/cluster';

export function fetchClusters(): Promise<Cluster[]> {
  return apiFetch<Cluster[]>('/api/v1/admin/clusters');
}

export function createCluster(request: CreateClusterRequest): Promise<Cluster> {
  return apiFetch<Cluster>('/api/v1/admin/clusters', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function updateCluster(clusterId: number, request: UpdateClusterRequest): Promise<Cluster> {
  return apiFetch<Cluster>(`/api/v1/admin/clusters/${clusterId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export function deleteCluster(clusterId: number): Promise<void> {
  return apiFetch<void>(`/api/v1/admin/clusters/${clusterId}`, {
    method: 'DELETE',
  });
}
```

Uses `apiFetch()` from Story 1.5 — automatic Bearer token injection, relative URLs, typed responses.

### Frontend — AdminClustersPage Component

The page uses PatternFly 6 components exclusively. Replace the placeholder created in Story 1.4.

```typescript
// src/main/webui/src/routes/AdminClustersPage.tsx

import {
  PageSection,
  Content,
  Button,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from '@patternfly/react-core';
import { Table, Thead, Tbody, Tr, Th, Td, ActionsColumn } from '@patternfly/react-table';
```

**Page layout structure:**

```
PageSection (header — light variant)
├── Content (h1: "Cluster Management")
└── Toolbar
    ├── ToolbarItem → "Register Cluster" Button (primary)
    └── ToolbarItem → RefreshButton

PageSection (content — filled)
├── LoadingSpinner (if loading)
├── ErrorAlert (if error)
└── Table
    ├── Thead → Tr → Th: Name, API Server URL, Created, (actions)
    └── Tbody → Tr per cluster
        ├── Td: cluster.name
        ├── Td: cluster.apiServerUrl
        ├── Td: formatted createdAt
        └── Td: ActionsColumn → Edit, Delete

Modal (Register/Edit form)
├── Form
│   ├── FormGroup → TextInput: Name
│   └── FormGroup → TextInput: API Server URL
└── ModalFooter → Cancel, Save

Modal (Delete confirmation)
├── "Are you sure you want to delete cluster {name}?"
└── ModalFooter → Cancel, Delete (danger)
```

**PatternFly 6 Table approach:** Use the composable table API (`Table`, `Thead`, `Tbody`, `Tr`, `Th`, `Td`) — not the deprecated `TableComposable`. PF6 composable tables give full control over cell rendering and actions.

**Row actions:** Use `ActionsColumn` component for the kebab/action menu per row. Two actions: "Edit" and "Delete". Edit opens the modal pre-filled with current values. Delete opens a confirmation modal.

**Empty state:** If no clusters are registered, show a PatternFly `EmptyState` with message "No clusters registered" and a "Register Cluster" primary action.

**Data loading pattern:** Use `useApiFetch` hook from Story 1.5:
```typescript
const { data: clusters, error, isLoading, refresh } = useApiFetch<Cluster[]>('/api/v1/admin/clusters');
```

**Mutation pattern:** For create/update/delete, call the API function directly, then call `refresh()` to reload the table data.

### Frontend — Form Validation

- **Name:** Required, non-empty
- **API Server URL:** Required, must start with `https://`
- Show PatternFly `FormGroup` with `validated="error"` and `helperTextInvalid` when validation fails
- Disable Save button until form is valid
- Show error from backend (e.g., duplicate name) via ErrorAlert inside the modal

### Frontend — Admin Route Protection

The route `/admin/clusters` is accessible to all authenticated users in the React Router (Story 1.4), but the backend API returns 403 for non-admins. The frontend should:

1. Use the `useAuth` hook to check `role === 'admin'`
2. If not admin, display a "You don't have permission to access this page" message
3. Optionally: hide the admin navigation link in the sidebar for non-admin users

This is a UX guard only — the real enforcement is server-side via Casbin.

### Backend — Casbin Policy Verification

The Casbin policy from Story 1.3 already defines cluster CRUD permissions for the admin role:

```csv
p, admin, clusters, read
p, admin, clusters, create
p, admin, clusters, update
p, admin, clusters, delete
```

The `PermissionFilter` must correctly map:
- `GET /api/v1/admin/clusters` → `(admin_role, clusters, read)`
- `POST /api/v1/admin/clusters` → `(admin_role, clusters, create)`
- `PUT /api/v1/admin/clusters/{id}` → `(admin_role, clusters, update)`
- `DELETE /api/v1/admin/clusters/{id}` → `(admin_role, clusters, delete)`

Verify that the `extractResource()` and `mapAction()` methods in `PermissionFilter` handle the `/admin/clusters` path correctly. The `/admin/` prefix is a URL convention, not a Casbin concern — the resource type is `clusters`.

### Backend — No Team Scoping

Unlike most portal resources, clusters are **not team-scoped**. They are global platform resources managed by admins. The `ClusterResource`:
- Does NOT inject or use `TeamContext`
- Does NOT filter by team
- Relies solely on Casbin admin role check (Layer 1 authorization)

This is the only resource in the portal that is not tenant-scoped. All other resources (applications, environments, builds, etc.) are team-scoped via `TeamContext`.

### What NOT to Build in This Story

- No cluster health check or connectivity validation — just store name + URL
- No Vault credential validation for registered clusters — that's Story 1.6's domain
- No environment-to-cluster mapping — that's Story 2.1 (Application/Environment data model)
- No cascade delete protection (checking if environments reference the cluster) — add in Story 2.1
- No pagination for the cluster list — admin manages a small number of clusters (NFR10: 50 teams, fewer clusters)
- No search/filter on the clusters table
- No sidebar navigation link for admin (the route exists from Story 1.4; the admin navigates via URL or a masthead admin link)

### Files to Create

| File | Package/Path | Purpose |
|---|---|---|
| `V{N}__create_clusters.sql` | `src/main/resources/db/migration/` | Flyway migration for clusters table |
| `Cluster.java` | `com.portal.cluster` | Panache entity |
| `ClusterDto.java` | `com.portal.cluster` | API response DTO |
| `CreateClusterRequest.java` | `com.portal.cluster` | POST request body |
| `UpdateClusterRequest.java` | `com.portal.cluster` | PUT request body |
| `ClusterService.java` | `com.portal.cluster` | Business logic + validation |
| `ClusterResource.java` | `com.portal.cluster` | JAX-RS REST endpoints |
| `cluster.ts` | `src/main/webui/src/types/` | TypeScript interfaces |
| `clusters.ts` | `src/main/webui/src/api/` | API functions |
| `ClusterResourceIT.java` | `src/test/java/com/portal/cluster/` | Integration tests |
| `AdminClustersPage.test.tsx` | `src/main/webui/src/routes/` | Frontend component tests |

### Files to Modify

| File | Change |
|---|---|
| `AdminClustersPage.tsx` | Replace Story 1.4 placeholder with full implementation |
| `GlobalExceptionMapper.java` | Add `IllegalArgumentException` handler → 400 (if not already present) |
| `application-test.properties` | Add PostgreSQL test datasource config if not present |

### Previous Story Intelligence (Stories 1.1–1.6)

- **Story 1.1:** Project scaffolded. Maven + Quarkus + Quinoa. PostgreSQL + Flyway extensions configured. Frontend dirs exist. `application.properties` has basic datasource config.
- **Story 1.2:** `TeamContext` CDI bean with `teamIdentifier`, `role`, `teamId`. GET `/api/v1/teams` returns user's teams. `TeamResource` exists as reference implementation for REST resource pattern. May have created a Flyway migration for teams table.
- **Story 1.3:** `CasbinEnforcer` + `PermissionFilter` enforce RBAC. Casbin policy defines `clusters` resource with `read/create/update/delete` actions for `admin` role. `PortalAuthorizationException` → 403 via ExceptionMapper. The filter's `extractResource()` and `mapAction()` methods are the critical integration point.
- **Story 1.4:** React Router route tree established. `AdminClustersPage.tsx` exists as a **placeholder** in `src/main/webui/src/routes/`. Route `/admin/clusters` is wired in `App.tsx`. `AppShell`, `Sidebar`, breadcrumbs, tabs all functional. `useAuth` hook provides `role`.
- **Story 1.5:** `apiFetch()` wrapper, `useApiFetch` hook, `ErrorAlert`, `LoadingSpinner`, `RefreshButton` components, `PortalError` type, `GlobalExceptionMapper` (handles 400/403/404/502/500), `ErrorResponse` record, `PortalIntegrationException` — all ready to use.
- **Story 1.6:** `SecretManagerCredentialProvider` + `VaultSecretManagerAdapter` — cluster names registered in this story will be used by the credential provider to fetch Vault credentials at runtime. The cluster `name` field is the key that maps to the Vault path template `/infra/{cluster}/kubernetes-secret-engine/creds/{role}`.

### Project Structure Notes

- `com.portal.cluster/` is a self-contained domain package: entity + resource + service + DTOs
- Follows the architecture's domain-centric package pattern (same as `com.portal.auth/`, `com.portal.application/`)
- No cross-package entity imports — other packages reference clusters by ID (e.g., `Environment.clusterId`)
- Frontend `api/clusters.ts` follows the same pattern as `api/teams.ts` from Story 1.5
- The `AdminClustersPage` route component lives in `routes/` like all page-level components

### References

- [Source: planning-artifacts/architecture.md § Data Architecture] — Cluster entity: name, apiServerUrl
- [Source: planning-artifacts/architecture.md § Project Structure] — `cluster/` package with Cluster.java, ClusterResource.java, ClusterService.java, ClusterDto.java
- [Source: planning-artifacts/architecture.md § Flyway Migrations] — V1__create_clusters.sql, naming convention
- [Source: planning-artifacts/architecture.md § Naming Patterns] — Tables: `snake_case` plural (`clusters`), columns: `snake_case`, FK: `cluster_id`, unique: `uq_clusters_name`
- [Source: planning-artifacts/architecture.md § API Patterns] — `/api/v1/admin/clusters`, REST resource conventions, HTTP status codes
- [Source: planning-artifacts/architecture.md § Authorization] — Three-tier model: admin = Cluster CRUD, Casbin policy
- [Source: planning-artifacts/architecture.md § Cluster Registry] — Admin-only, stored in DB, credentials from Vault at runtime, environments share clusters
- [Source: planning-artifacts/architecture.md § Frontend Structure] — `api/clusters.ts`, `types/cluster.ts`, `routes/AdminClustersPage.tsx`
- [Source: planning-artifacts/architecture.md § Requirements to Structure Mapping] — Cluster Management → `cluster/` package, `AdminClustersPage`, DB only (no adapter)
- [Source: planning-artifacts/prd.md § Credential & Cluster Access Model] — Clusters registered by admins, credentials from Vault at runtime
- [Source: planning-artifacts/epics.md § Epic 1 / Story 1.7] — Full acceptance criteria
- [Source: implementation-artifacts/1-3-casbin-rbac-authorization-layer.md] — Casbin policy with clusters resource, PermissionFilter path mapping
- [Source: implementation-artifacts/1-4-portal-page-shell-navigation.md] — Route tree with `/admin/clusters`, AdminClustersPage placeholder
- [Source: implementation-artifacts/1-5-api-foundation-error-handling.md] — apiFetch(), useApiFetch, ErrorAlert, LoadingSpinner, RefreshButton, GlobalExceptionMapper
- [Source: implementation-artifacts/1-6-vault-credential-provider.md] — Cluster names used as keys in Vault credential path template

## Dev Agent Record

### Agent Model Used

claude-4.6-opus-high-thinking

### Debug Log References

- Fixed `PanacheEntity` → `PanacheEntityBase` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` to match BIGSERIAL column (Hibernate 6 defaults to SEQUENCE strategy with PanacheEntity, causing missing `clusters_seq` error)
- Removed `titleIconVariant` prop from PF6 Modal (not available in PatternFly 6)
- Removed `AuthTestStubResource` test stub (now superseded by real `ClusterResource`)
- Created `DevSecretManagerAdapter` to fix pre-existing CDI resolution issue blocking all `@QuarkusTest` integration tests
- Bean Validation errors return Quarkus REST's built-in 400 format (not GlobalExceptionMapper format); tests adjusted accordingly

### Completion Notes List

- ✅ All 10 tasks completed — full CRUD backend + frontend + tests
- ✅ Backend: Flyway migration V2, Cluster entity, DTOs with Bean Validation, ClusterService, ClusterResource
- ✅ Frontend: TypeScript types, API functions, full AdminClustersPage with PF6 table, register/edit modal, delete confirmation, empty state, form validation, admin role guard
- ✅ Backend tests: 11 integration tests covering POST 201, GET 200, PUT 200, DELETE 204, duplicate name 400, member 403, lead 403, 404 not found, validation 400
- ✅ Frontend tests: 6 tests covering table rendering, empty state, register modal, form submission, loading state, heading
- ✅ All 87 unit tests pass (0 regressions)
- ✅ All 33 integration tests pass (0 regressions)
- ✅ All 90 frontend tests pass (0 regressions)
- ✅ TypeScript compiles cleanly with no errors
- ✅ App.test.tsx updated to expect "Access Denied" for non-admin at /admin/clusters

### File List

**New files:**
- `developer-portal/src/main/resources/db/migration/V2__create_clusters.sql`
- `developer-portal/src/main/java/com/portal/cluster/Cluster.java`
- `developer-portal/src/main/java/com/portal/cluster/ClusterDto.java`
- `developer-portal/src/main/java/com/portal/cluster/CreateClusterRequest.java`
- `developer-portal/src/main/java/com/portal/cluster/UpdateClusterRequest.java`
- `developer-portal/src/main/java/com/portal/cluster/ClusterService.java`
- `developer-portal/src/main/java/com/portal/cluster/ClusterResource.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/DevSecretManagerAdapter.java`
- `developer-portal/src/main/webui/src/types/cluster.ts`
- `developer-portal/src/main/webui/src/api/clusters.ts`
- `developer-portal/src/test/java/com/portal/cluster/ClusterResourceIT.java`
- `developer-portal/src/main/webui/src/routes/AdminClustersPage.test.tsx`

**Modified files:**
- `developer-portal/src/main/webui/src/routes/AdminClustersPage.tsx` (replaced placeholder with full implementation)
- `developer-portal/src/main/webui/src/App.test.tsx` (updated admin page assertion)

**Deleted files:**
- `developer-portal/src/test/java/com/portal/auth/AuthTestStubResource.java` (stub superseded by real ClusterResource)

### Change Log

- 2026-04-04: Story 1.7 implementation complete — Admin Cluster Registration with full CRUD backend, PatternFly 6 admin UI, and comprehensive test coverage
