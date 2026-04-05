# Story 1.2: OIDC Authentication & Team Recognition

Status: done

## Story

As a developer,
I want to log in using my organization's OIDC credentials and have my team automatically recognized from my JWT claims,
So that I can access the portal without any portal-specific registration or setup.

## Acceptance Criteria

1. **Unauthenticated requests rejected**
   - **Given** the Quarkus OIDC extension is configured with the organization's OIDC provider URL
   - **When** an unauthenticated request is made to any `/api/v1/` endpoint
   - **Then** a 401 Unauthorized response is returned

2. **Bearer token validation**
   - **Given** a developer authenticates via the OIDC provider
   - **When** the JWT token is included in the Authorization header as a Bearer token
   - **Then** Quarkus validates the token and the request proceeds
   - **And** the developer's identity is extracted from the token

3. **Team extraction from JWT**
   - **Given** the JWT contains a team claim (configurable via `portal.oidc.team-claim`, default: "team")
   - **When** the TeamContextFilter processes the request
   - **Then** a TeamContext CDI bean is populated with the user's team identity
   - **And** the TeamContext is available for injection in all downstream services

4. **Role extraction from JWT**
   - **Given** the JWT contains a role claim (configurable via `portal.oidc.role-claim`, default: "role")
   - **When** the TeamContextFilter processes the request
   - **Then** the user's role (member, lead, or admin) is extracted and available in the request context

5. **Auto-provisioning teams in the database**
   - **Given** a Team entity and Flyway migration V1__create_teams.sql exist
   - **When** a developer's team from the JWT does not yet exist in the database
   - **Then** the team is automatically created in the teams table with the OIDC group identifier
   - **And** the team name and oidc_group_id are persisted

6. **Teams endpoint**
   - **Given** a developer is authenticated with a valid team context
   - **When** GET `/api/v1/teams` is called
   - **Then** only the teams the developer belongs to (based on JWT claims) are returned
   - **And** each team includes its name and identifier

7. **OIDC provider unreachable**
   - **Given** the OIDC provider is unreachable
   - **When** a developer attempts to authenticate
   - **Then** the portal returns a clear error indicating the OIDC provider cannot be reached

## Tasks / Subtasks

- [x] Task 1: Configure Quarkus OIDC extension (AC: #1, #2)
  - [x] Update application.properties with OIDC config (auth-server-url, client-id, application-type=service)
  - [x] Add %dev profile OIDC config for Dev Services Keycloak or mock
  - [x] Add %test profile OIDC config for test security
  - [x] Verify 401 returned for unauthenticated requests to /api/v1/**
- [x] Task 2: Create Team entity + Flyway migration (AC: #5)
  - [x] Create `V1__create_teams.sql` in `src/main/resources/db/migration/`
  - [x] Create `Team.java` Panache entity in `com.portal.team`
  - [x] Switch from `hibernate-orm.database.generation=drop-and-create` to Flyway for schema management
- [x] Task 3: Implement TeamContextFilter (AC: #3, #4)
  - [x] Create `TeamContextFilter.java` as JAX-RS ContainerRequestFilter in `com.portal.auth`
  - [x] Create `TeamContext.java` @RequestScoped CDI bean in `com.portal.auth`
  - [x] Inject `JsonWebToken` to extract team claim via configurable `portal.oidc.team-claim`
  - [x] Extract role from configurable `portal.oidc.role-claim`
  - [x] Populate TeamContext with team identity and role
- [x] Task 4: Implement team auto-provisioning (AC: #5)
  - [x] Create `TeamService.java` in `com.portal.team`
  - [x] Implement find-or-create logic: lookup team by oidc_group_id, create if not found
  - [x] Call TeamService from TeamContextFilter after claim extraction
- [x] Task 5: Implement GET /api/v1/teams endpoint (AC: #6)
  - [x] Create `TeamResource.java` in `com.portal.team`
  - [x] Create `TeamSummaryDto.java` in `com.portal.team`
  - [x] Return only teams the authenticated user belongs to (scoped by JWT team claim)
- [x] Task 6: Add custom OIDC config properties (AC: #3, #4)
  - [x] Register `portal.oidc.team-claim` with default "team" via @ConfigProperty
  - [x] Register `portal.oidc.role-claim` with default "role" via @ConfigProperty
- [x] Task 7: Handle OIDC provider unreachable (AC: #7)
  - [x] Add ExceptionMapper or error handling for OIDC connectivity failures
  - [x] Return standardized error JSON with system="oidc-provider"
- [x] Task 8: Write tests (AC: #1-#7)
  - [x] Create `TeamContextFilterTest.java` unit test
  - [x] Create `TeamResourceIT.java` integration test using @QuarkusTest + @TestSecurity
  - [x] Test 401 for unauthenticated access
  - [x] Test team auto-creation on first login
  - [x] Test GET /api/v1/teams returns correct teams

## Dev Notes

### Architecture — Two-Layer Authorization Model

This story implements the foundation for the portal's two-layer authorization:

| Layer | Concern | Mechanism | This Story |
|---|---|---|---|
| **Layer 1: Permission** | "What can this role do?" | Casbin (jCasbin) | Role extracted, Casbin enforcement in Story 1.3 |
| **Layer 2: Tenant isolation** | "Which data can this user see?" | TeamContext CDI bean | **Fully implemented here** |

The request processing pipeline after this story:

1. Quarkus OIDC validates JWT on every request → 401 if invalid
2. `TeamContextFilter` extracts team + role from JWT claims → populates `TeamContext`
3. (Story 1.3) `PermissionFilter` checks Casbin → 403 if denied
4. Resources/services use `TeamContext` to scope all data access

### OIDC Configuration — application.properties

```properties
# OIDC Bearer token validation
quarkus.oidc.auth-server-url=${OIDC_SERVER_URL:http://localhost:8180/realms/portal}
quarkus.oidc.client-id=developer-portal
quarkus.oidc.application-type=service

# Portal JWT claim mapping
portal.oidc.role-claim=${OIDC_ROLE_CLAIM:role}
portal.oidc.team-claim=${OIDC_TEAM_CLAIM:team}
```

**Critical:** `application-type=service` means Quarkus validates bearer tokens only — no login redirect, no session cookies. The SPA handles the OIDC login flow and sends the JWT in the Authorization header.

### TeamContextFilter — Implementation Pattern

```java
package com.portal.auth;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Provider
@Priority(Priorities.AUTHENTICATION + 10)
@ApplicationScoped
public class TeamContextFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    TeamContext teamContext;

    @ConfigProperty(name = "portal.oidc.team-claim", defaultValue = "team")
    String teamClaim;

    @ConfigProperty(name = "portal.oidc.role-claim", defaultValue = "role")
    String roleClaim;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Extract team from JWT claim
        String team = jwt.getClaim(teamClaim);
        // Extract role from JWT claim
        String role = jwt.getClaim(roleClaim);

        teamContext.setTeamIdentifier(team);
        teamContext.setRole(role != null ? role : "member");

        // Auto-provision team (delegate to TeamService)
    }
}
```

**Priority:** `Priorities.AUTHENTICATION + 10` ensures this runs right after Quarkus OIDC validates the token. The PermissionFilter (Story 1.3) should run at `Priorities.AUTHORIZATION` to come after team context is established.

### TeamContext — CDI Bean

```java
package com.portal.auth;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class TeamContext {
    private String teamIdentifier;
    private String role;
    private Long teamId;  // DB ID, set after auto-provisioning

    // getters + setters
}
```

**Scope:** `@RequestScoped` — created per HTTP request, populated by `TeamContextFilter`, injected into services/resources that need team-scoped data access.

### Team Entity — Panache

```java
package com.portal.team;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team extends PanacheEntity {
    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "oidc_group_id", nullable = false, unique = true)
    public String oidcGroupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public java.time.Instant updatedAt;

    public static Team findByOidcGroupId(String oidcGroupId) {
        return find("oidcGroupId", oidcGroupId).firstResult();
    }
}
```

**Panache note:** `PanacheEntity` provides auto-generated `id` (Long). Use Active Record pattern (static finders on entity) for simple queries — this is the Quarkus Panache convention.

### Flyway Migration — V1__create_teams.sql

```sql
CREATE TABLE teams (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    oidc_group_id VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_teams_oidc_group_id ON teams (oidc_group_id);
```

**Migration naming:** This is `V1` because it's the first migration in the implementation sequence. Story 1.7 (clusters) will use `V2__create_clusters.sql`.

**Switch from drop-and-create:** Remove `%dev.quarkus.hibernate-orm.database.generation=drop-and-create` from application.properties. Flyway now manages the schema. Add:
```properties
quarkus.flyway.migrate-at-start=true
```

### TeamResource — REST Endpoint

```java
package com.portal.team;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import com.portal.auth.TeamContext;

@Path("/api/v1/teams")
@Produces(MediaType.APPLICATION_JSON)
public class TeamResource {

    @Inject
    TeamContext teamContext;

    @Inject
    TeamService teamService;

    @GET
    public List<TeamSummaryDto> getTeams() {
        // Return only teams the authenticated user belongs to
        // For MVP: single team from JWT claim
        return teamService.getTeamsForUser(teamContext.getTeamIdentifier());
    }
}
```

### TeamSummaryDto

```java
package com.portal.team;

public record TeamSummaryDto(Long id, String name, String oidcGroupId) {
    public static TeamSummaryDto from(Team team) {
        return new TeamSummaryDto(team.id, team.name, team.oidcGroupId);
    }
}
```

### Error Response Format

All error responses must follow the standardized JSON format (from architecture):

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

For this story:
- **401 Unauthorized** — Quarkus OIDC handles this automatically (missing/invalid JWT)
- **OIDC provider unreachable** — Return 502 with `"system": "oidc-provider"` and a message like "Authentication service is currently unreachable — please try again shortly"

The mandatory error message format from UX: (1) what happened, (2) why if known, (3) what to do.

### Testing Strategy

**Integration tests with `@TestSecurity`:**

```java
@QuarkusTest
class TeamResourceIT {

    @Test
    void unauthenticatedRequestReturns401() {
        given()
            .when().get("/api/v1/teams")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "testUser", roles = "member",
        attributes = @SecurityAttribute(key = "team", value = "payments"))
    void authenticatedRequestReturnsTeams() {
        given()
            .when().get("/api/v1/teams")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1));
    }
}
```

**Note on `@TestSecurity`:** The `@TestSecurity` annotation mocks the security identity for the test. For JWT claim extraction in `TeamContextFilter`, you may need to use `@OidcSecurity` with `@TokenIntrospection` or configure a test OIDC token. The `quarkus-test-security-oidc` dependency provides `@OidcSecurity` for more realistic JWT-based testing:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-security-oidc</artifactId>
    <scope>test</scope>
</dependency>
```

With `@OidcSecurity` you can inject custom claims:
```java
@OidcSecurity(claims = {
    @Claim(key = "team", value = "payments"),
    @Claim(key = "role", value = "member")
})
```

**Test profile config (`application-test.properties`):**
```properties
%test.quarkus.oidc.auth-server-url=http://localhost:0
# or disable OIDC for unit tests that use @TestSecurity
```

### JWT Claims — Expected Shape

The portal expects these claims in the JWT payload:

```json
{
  "sub": "user-123",
  "preferred_username": "developer@example.com",
  "team": "payments",
  "role": "member",
  "iat": 1712150400,
  "exp": 1712154000
}
```

- `team` claim (configurable name) — single string identifying the user's team (maps to `oidc_group_id` in DB)
- `role` claim (configurable name) — one of: `member`, `lead`, `admin`
- If role claim is missing/null, default to `member`
- If team claim is missing, return 403 (cannot determine team scope)

### Cross-Team Data Access Rule

From architecture: when a user requests a resource belonging to a different team, return **404 Not Found** (not 403). This prevents leaking that the resource exists in another team. While the full enforcement is in later stories, the `TeamContext` bean established here is the mechanism that enables this scoping.

### What NOT to Build in This Story

- No Casbin RBAC enforcement (Story 1.3) — roles are extracted but not enforced yet
- No PermissionFilter (Story 1.3)
- No frontend login flow / `useAuth` hook (Story 1.4+ / frontend auth)
- No `apiFetch()` with bearer token injection (Story 1.5)
- No multi-team support — assume single team per JWT for MVP
- No user entity / user table — identity comes entirely from JWT
- No token refresh logic — Quarkus OIDC handles token validation only

### Previous Story Intelligence (Story 1.1)

From Story 1.1's implementation:
- Project is a Quarkus 3.34.2 + Quinoa monorepo
- Backend packages under `com.portal.<domain>` — use `com.portal.auth` and `com.portal.team`
- `src/main/resources/db/migration/` directory exists (created empty in 1.1)
- `src/main/resources/casbin/` directory exists (empty, for Story 1.3)
- OIDC placeholder config exists in application.properties — **replace** with real config
- `%dev.quarkus.hibernate-orm.database.generation=drop-and-create` needs to be **removed** and replaced with Flyway
- Dev Services PostgreSQL is configured
- Testing: JUnit 5 + @QuarkusTest + REST Assured for backend; `<Class>Test.java` for unit, `<Class>IT.java` for integration

### Files to Create/Modify

**Create:**
- `src/main/java/com/portal/auth/TeamContextFilter.java`
- `src/main/java/com/portal/auth/TeamContext.java`
- `src/main/java/com/portal/team/Team.java`
- `src/main/java/com/portal/team/TeamResource.java`
- `src/main/java/com/portal/team/TeamService.java`
- `src/main/java/com/portal/team/TeamSummaryDto.java`
- `src/main/resources/db/migration/V1__create_teams.sql`
- `src/test/java/com/portal/auth/TeamContextFilterTest.java`
- `src/test/java/com/portal/team/TeamResourceIT.java`

**Modify:**
- `src/main/resources/application.properties` — OIDC config, Flyway, remove drop-and-create
- `pom.xml` — add `quarkus-test-security-oidc` test dependency if not present

### Project Structure Notes

- `com.portal.auth` holds cross-cutting auth infrastructure: `TeamContextFilter`, `TeamContext`, and later `PermissionFilter` (Story 1.3)
- `com.portal.team` holds the Team domain: entity, resource, service, DTO — follows domain-centric package structure per AR19
- DTOs are Java records (immutable, concise) per modern Java conventions
- `Team` entity uses Panache Active Record pattern (static finders on entity class)

### References

- [Source: planning-artifacts/architecture.md] — Two-layer auth model, TeamContextFilter/TeamContext design, OIDC config properties, Team entity attributes, error format, REST patterns, database conventions
- [Source: planning-artifacts/architecture.md § HTTP Status Conventions] — 401 for missing/invalid JWT, 404 (not 403) for cross-team resource access
- [Source: planning-artifacts/prd.md § FR1-FR4] — OIDC authentication, team recognition from group metadata, team-scoped visibility
- [Source: planning-artifacts/prd.md § NFR5] — No portal-specific accounts; all auth via OIDC
- [Source: planning-artifacts/ux-design-specification.md] — "OIDC auto-context" entry pattern, masthead team display, error message format
- [Source: planning-artifacts/epics.md § Epic 1 / Story 1.2] — Acceptance criteria, story statement
- [Source: implementation-artifacts/1-1-project-scaffolding-monorepo-setup.md] — Previous story structure, existing config, Flyway directory, package conventions

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus (Cursor Agent)

### Debug Log References

- PanacheEntity uses SEQUENCE strategy by default; switched to PanacheEntityBase with IDENTITY to match BIGSERIAL in Flyway migration
- OIDC extension fails at startup when server unreachable in tests; resolved by setting `quarkus.oidc.tenant-enabled=false` in test config since @TestSecurity/@OidcSecurity annotations provide mock security
- HTTP auth permission policy `quarkus.http.auth.permission.api.policy=authenticated` required for 401 on unauthenticated requests (OIDC alone does not block anonymous requests to unprotected paths)
- quarkus-junit artifact renamed to quarkus-junit5 (was incorrect in Story 1.1 scaffolding)

### Completion Notes List

- ✅ OIDC bearer token validation configured with configurable auth-server-url and claim names
- ✅ HTTP auth permission policy enforces authentication on all /api/v1/* endpoints → 401 for unauthenticated
- ✅ TeamContextFilter extracts team + role from JWT claims, populates RequestScoped TeamContext
- ✅ Missing team claim returns 403 with standardized error JSON
- ✅ Missing role claim defaults to "member"
- ✅ Team entity with Flyway V1 migration (BIGSERIAL PK, oidc_group_id unique index)
- ✅ Removed drop-and-create; Flyway now manages schema
- ✅ TeamService implements find-or-create auto-provisioning from OIDC group identifier
- ✅ GET /api/v1/teams returns only the authenticated user's team(s) as TeamSummaryDto
- ✅ OidcExceptionMapper returns 502 with standardized error JSON for OIDC connectivity failures
- ✅ 7 unit tests (TeamContextFilterTest): JWT extraction, role defaults, missing claim handling, configurable claims, auto-provisioning trigger
- ✅ 4 integration tests (TeamResourceIT): 401 unauthenticated, team auto-creation, correct team return, single-team scoping
- ✅ All 11 tests pass, zero regressions

### Change Log

- 2026-04-04: Story 1.2 implementation complete — OIDC auth, TeamContext, Team entity, auto-provisioning, REST endpoint, error handling, tests

### File List

**Created:**
- `developer-portal/src/main/java/com/portal/auth/TeamContext.java`
- `developer-portal/src/main/java/com/portal/auth/TeamContextFilter.java`
- `developer-portal/src/main/java/com/portal/auth/OidcExceptionMapper.java`
- `developer-portal/src/main/java/com/portal/team/Team.java`
- `developer-portal/src/main/java/com/portal/team/TeamService.java`
- `developer-portal/src/main/java/com/portal/team/TeamResource.java`
- `developer-portal/src/main/java/com/portal/team/TeamSummaryDto.java`
- `developer-portal/src/main/resources/db/migration/V1__create_teams.sql`
- `developer-portal/src/test/java/com/portal/auth/TeamContextFilterTest.java`
- `developer-portal/src/test/java/com/portal/team/TeamResourceIT.java`
- `developer-portal/src/test/resources/application.properties`

**Modified:**
- `developer-portal/src/main/resources/application.properties` — OIDC config, Flyway, HTTP auth policy, removed drop-and-create
- `developer-portal/pom.xml` — added quarkus-flyway, quarkus-test-security-oidc, mockito-core; fixed quarkus-junit → quarkus-junit5
