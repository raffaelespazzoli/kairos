# Story 2.1: Application & Environment Data Model

Status: done

## Story

As a developer on the portal team,
I want Application and Environment entities with a data-driven promotion chain model,
So that portal-specific application state can be persisted and the environment promotion flow is flexible.

## Acceptance Criteria

1. **Applications table migration (V3)**
   - **Given** the Application entity needs to be persisted
   - **When** Flyway migration V3__create_applications.sql runs
   - **Then** the `applications` table is created with columns: `id` (bigserial PK), `name` (varchar), `team_id` (bigint FK → teams), `git_repo_url` (varchar), `runtime_type` (varchar), `onboarding_pr_url` (varchar nullable), `onboarded_at` (timestamptz nullable), `created_at` (timestamptz), `updated_at` (timestamptz)
   - **And** a unique constraint exists on `(team_id, name)` — no duplicate app names within a team
   - **And** an index exists on `team_id` for efficient team-scoped queries

2. **Environments table migration (V4)**
   - **Given** the Environment entity needs to be persisted
   - **When** Flyway migration V4__create_environments.sql runs
   - **Then** the `environments` table is created with columns: `id` (bigserial PK), `name` (varchar), `application_id` (bigint FK → applications), `cluster_id` (bigint FK → clusters), `namespace` (varchar), `promotion_order` (integer), `created_at` (timestamptz), `updated_at` (timestamptz)
   - **And** a unique constraint exists on `(application_id, name)` — no duplicate env names per app
   - **And** a unique constraint exists on `(application_id, promotion_order)` — no duplicate ordering per app
   - **And** an index exists on `application_id` for efficient application-scoped queries

3. **Promotion chain modeled as data**
   - **Given** the promotion chain is modeled via the `promotion_order` column
   - **When** environments are created for an application
   - **Then** each environment has a `promotion_order` integer (0, 1, 2, ...) that defines its position in the chain
   - **And** the chain is reconstructed by querying environments ordered by `promotion_order` ascending
   - **And** no hardcoded assumption of exactly three environments exists in the entity model

4. **Application Panache entity**
   - **Given** the Application Panache entity is defined
   - **When** reviewing the Java class
   - **Then** it follows Active Record pattern extending `PanacheEntityBase`
   - **And** it includes a `findByTeam(Long teamId)` query method
   - **And** it maps to the `applications` table with correct column naming (snake_case DB ↔ camelCase Java)

5. **Environment Panache entity**
   - **Given** the Environment Panache entity is defined
   - **When** reviewing the Java class
   - **Then** it includes a `findByApplicationOrderByPromotionOrder(Long applicationId)` query method
   - **And** this method returns environments sorted by `promotion_order`, producing the promotion chain in sequence

## Tasks / Subtasks

- [x] Task 1: Create Flyway migration V3__create_applications.sql (AC: #1)
  - [x] Create `src/main/resources/db/migration/V3__create_applications.sql`
  - [x] Define columns: `id` (bigserial PK), `name` (varchar NOT NULL), `team_id` (bigint NOT NULL FK → teams), `git_repo_url` (varchar NOT NULL), `runtime_type` (varchar NOT NULL), `onboarding_pr_url` (varchar nullable), `onboarded_at` (timestamptz nullable), `created_at` (timestamptz NOT NULL DEFAULT now()), `updated_at` (timestamptz NOT NULL DEFAULT now())
  - [x] Add unique constraint `uq_applications_team_id_name` on `(team_id, name)`
  - [x] Add index `idx_applications_team_id` on `team_id`
  - [x] Add foreign key `fk_applications_team_id` referencing `teams(id)`

- [x] Task 2: Create Flyway migration V4__create_environments.sql (AC: #2)
  - [x] Create `src/main/resources/db/migration/V4__create_environments.sql`
  - [x] Define columns: `id` (bigserial PK), `name` (varchar NOT NULL), `application_id` (bigint NOT NULL FK → applications), `cluster_id` (bigint NOT NULL FK → clusters), `namespace` (varchar NOT NULL), `promotion_order` (integer NOT NULL), `created_at` (timestamptz NOT NULL DEFAULT now()), `updated_at` (timestamptz NOT NULL DEFAULT now())
  - [x] Add unique constraint `uq_environments_application_id_name` on `(application_id, name)`
  - [x] Add unique constraint `uq_environments_application_id_promotion_order` on `(application_id, promotion_order)`
  - [x] Add index `idx_environments_application_id` on `application_id`
  - [x] Add foreign key `fk_environments_application_id` referencing `applications(id)`
  - [x] Add foreign key `fk_environments_cluster_id` referencing `clusters(id)`

- [x] Task 3: Create Application Panache entity (AC: #4)
  - [x] Create `Application.java` in `com.portal.application`
  - [x] Extend `PanacheEntityBase` with `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`
  - [x] Public fields: `name`, `teamId` (Long), `gitRepoUrl`, `runtimeType`, `onboardingPrUrl` (nullable), `onboardedAt` (Instant, nullable), `createdAt`, `updatedAt`
  - [x] `@Column(name = "...")` annotations for multi-word columns: `team_id`, `git_repo_url`, `runtime_type`, `onboarding_pr_url`, `onboarded_at`, `created_at`, `updated_at`
  - [x] `@PrePersist` and `@PreUpdate` lifecycle callbacks for timestamps
  - [x] Static finder: `findByTeam(Long teamId)` returning `List<Application>`

- [x] Task 4: Create Environment Panache entity (AC: #5, #3)
  - [x] Create `Environment.java` in `com.portal.environment`
  - [x] Extend `PanacheEntityBase` with `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`
  - [x] Public fields: `name`, `applicationId` (Long), `clusterId` (Long), `namespace`, `promotionOrder` (Integer), `createdAt`, `updatedAt`
  - [x] `@Column(name = "...")` annotations for multi-word columns: `application_id`, `cluster_id`, `promotion_order`, `created_at`, `updated_at`
  - [x] `@PrePersist` and `@PreUpdate` lifecycle callbacks for timestamps
  - [x] Static finder: `findByApplicationOrderByPromotionOrder(Long applicationId)` returning `List<Environment>` ordered by `promotionOrder ASC`

- [x] Task 5: Create DTOs for Application and Environment (AC: #4, #5)
  - [x] Create `ApplicationSummaryDto.java` record in `com.portal.application` with fields: `id`, `name`, `teamId`, `gitRepoUrl`, `runtimeType`, `onboardingPrUrl`, `onboardedAt`, `createdAt`, `updatedAt` — plus `from(Application)` factory
  - [x] Create `EnvironmentDto.java` record in `com.portal.environment` with fields: `id`, `name`, `applicationId`, `clusterId`, `namespace`, `promotionOrder`, `createdAt`, `updatedAt` — plus `from(Environment)` factory

- [x] Task 6: Write unit tests for Application entity (AC: #4)
  - [x] Create `ApplicationTest.java` in `src/test/java/com/portal/application/`
  - [x] Test that `@PrePersist` sets timestamps
  - [x] Test DTO mapping via `ApplicationSummaryDto.from()`

- [x] Task 7: Write unit tests for Environment entity (AC: #5, #3)
  - [x] Create `EnvironmentTest.java` in `src/test/java/com/portal/environment/`
  - [x] Test that `@PrePersist` sets timestamps
  - [x] Test DTO mapping via `EnvironmentDto.from()`

- [x] Task 8: Write integration tests for persistence (AC: #1, #2, #3, #4, #5)
  - [x] Create `ApplicationEntityIT.java` in `src/test/java/com/portal/application/`
  - [x] `@QuarkusTest` with `QuarkusTransaction.requiringNew()` for transaction management
  - [x] Test Application persist and retrieve
  - [x] Test `findByTeam()` returns only apps for given team
  - [x] Test unique constraint `(team_id, name)` rejects duplicates
  - [x] Create `EnvironmentEntityIT.java` in `src/test/java/com/portal/environment/`
  - [x] Test Environment persist and retrieve
  - [x] Test `findByApplicationOrderByPromotionOrder()` returns sorted results
  - [x] Test unique constraint `(application_id, name)` rejects duplicates
  - [x] Test unique constraint `(application_id, promotion_order)` rejects duplicates
  - [x] Test promotion chain: create 3+ environments with different orders, verify sorted retrieval

## Dev Notes

### Entity Pattern — PanacheEntityBase (NOT PanacheEntity)

All entities in this project extend `PanacheEntityBase` with explicit `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Do NOT use `PanacheEntity` — Hibernate 6 defaults to SEQUENCE strategy with `PanacheEntity`, which causes a missing `<table>_seq` error because the DB uses `BIGSERIAL` (identity).

Reference implementation — `Cluster.java`:

```java
@Entity
@Table(name = "clusters")
public class Cluster extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

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
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
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

### Cross-Domain References — ID Only

The architecture mandates NO cross-package entity imports. Application and Environment are in separate domain packages:
- `com.portal.application.Application` — references team via `Long teamId` (NOT a `Team` entity field)
- `com.portal.environment.Environment` — references application via `Long applicationId` and cluster via `Long clusterId` (NOT entity imports)

JPA `@ManyToOne` relationships are NOT used across domain boundaries. Use plain `Long` FK fields with `@Column` annotations.

### Flyway Migration Sequence

Existing migrations:
- `V1__create_teams.sql` — `teams` table
- `V2__create_clusters.sql` — `clusters` table

This story adds:
- `V3__create_applications.sql` — `applications` table (FK to `teams`)
- `V4__create_environments.sql` — `environments` table (FKs to `applications` and `clusters`)

NEVER modify existing migrations. Always create new versioned files.

### DTO Pattern

DTOs are Java records with a static `from(Entity)` factory. Reference — `ClusterDto.java`:

```java
public record ClusterDto(
    Long id,
    String name,
    String apiServerUrl,
    Instant createdAt,
    Instant updatedAt
) {
    public static ClusterDto from(Cluster entity) {
        return new ClusterDto(entity.id, entity.name, entity.apiServerUrl,
            entity.createdAt, entity.updatedAt);
    }
}
```

### Application Entity — Specifics

```java
package com.portal.application;

@Entity
@Table(name = "applications")
public class Application extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(name = "team_id", nullable = false)
    public Long teamId;

    @Column(name = "git_repo_url", nullable = false)
    public String gitRepoUrl;

    @Column(name = "runtime_type", nullable = false)
    public String runtimeType;

    @Column(name = "onboarding_pr_url")
    public String onboardingPrUrl;    // nullable

    @Column(name = "onboarded_at")
    public Instant onboardedAt;       // nullable

    // createdAt, updatedAt with @PrePersist/@PreUpdate

    public static List<Application> findByTeam(Long teamId) {
        return list("teamId", Sort.by("name"), teamId);
    }
}
```

`onboardingPrUrl` and `onboardedAt` are nullable — they are set when onboarding completes (Story 2.5). During initial creation (Story 2.3), these fields are null.

### Environment Entity — Specifics

```java
package com.portal.environment;

@Entity
@Table(name = "environments")
public class Environment extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(name = "application_id", nullable = false)
    public Long applicationId;

    @Column(name = "cluster_id", nullable = false)
    public Long clusterId;

    @Column(nullable = false)
    public String namespace;

    @Column(name = "promotion_order", nullable = false)
    public Integer promotionOrder;

    // createdAt, updatedAt with @PrePersist/@PreUpdate

    public static List<Environment> findByApplicationOrderByPromotionOrder(Long applicationId) {
        return list("applicationId", Sort.by("promotionOrder"), applicationId);
    }
}
```

`promotionOrder` is an Integer (0-based) that defines the environment's position in the promotion chain. The chain is data-driven — no hardcoded assumption of exactly 3 environments. Future stories will allow 2, 3, 4, or more environments per app.

### Integration Test Pattern

Backend integration tests use `@QuarkusTest` + `@TestSecurity` + `@OidcSecurity`. Reference — `ClusterResourceIT.java`:

```java
@QuarkusTest
class ClusterResourceIT {
    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
        @Claim(key = "team", value = "platform"),
        @Claim(key = "role", value = "admin")
    })
    void createClusterReturns201() { ... }
}
```

For this story, entity integration tests do NOT need REST endpoints — they test persistence directly. Use `@Inject` to get entity manager or transactional helpers, or use Panache static methods within `@QuarkusTest`. The `@TestSecurity` annotation is still required because the auth filters run for all requests.

For direct entity tests without REST:
- Inject an `EntityManager` or use `@Transactional` test methods
- Use Panache static methods (`Application.persist()`, `Application.findByTeam()`, etc.)
- Verify constraints by attempting duplicate inserts and catching `PersistenceException`

### Test Configuration

Test config is at `src/test/resources/application.properties` — Dev Services auto-provisions PostgreSQL, Flyway runs migrations. OIDC tenant is disabled. Quinoa is disabled. Secret manager uses dev provider.

### What NOT to Build in This Story

- **No REST endpoints** — Application and Environment CRUD endpoints come in Story 2.3 (registration) and Story 2.6 (list/navigation)
- **No frontend** — No UI components; this is purely backend data model
- **No services beyond what's needed for testing** — Full ApplicationService/EnvironmentService come in later stories
- **No onboarding logic** — That's Story 2.3–2.5
- **No ArgoCD integration** — Environment status from ArgoCD comes in Story 2.7
- **No cascade delete handling** — Applications referencing clusters is tracked for future story
- **No environment-to-cluster validation** — Verifying the referenced cluster exists happens during onboarding (Story 2.4)

### Project Structure Notes

Files go in the established domain-package structure:
- `com.portal.application/` — Application.java, ApplicationSummaryDto.java (package-info.java already exists)
- `com.portal.environment/` — Environment.java, EnvironmentDto.java (package-info.java already exists)
- `src/main/resources/db/migration/` — V3 and V4 SQL files
- Test mirrors: `src/test/java/com/portal/application/`, `src/test/java/com/portal/environment/`

### Previous Story Intelligence (Story 1.7)

Key learnings from the last completed story:
- `PanacheEntityBase` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` is mandatory — `PanacheEntity` causes seq errors
- Bean Validation errors return Quarkus REST's built-in 400 format (not GlobalExceptionMapper format)
- `@Column(name = "...")` is needed for multi-word columns where Hibernate's default mapping differs
- `DevSecretManagerAdapter` exists for dev profile to avoid Vault dependency in tests
- All 87 unit, 33 integration, and 90 frontend tests pass — do not introduce regressions

### Casbin & Authorization Context

This story's entities are team-scoped (Application via `team_id`, Environment transitively via Application). The Casbin policy already defines `applications` and `environments` resource permissions — but REST endpoints exercising those permissions come in later stories (2.3, 2.6). This story creates the data layer only.

### References

- [Source: planning-artifacts/architecture.md § Data Architecture] — Portal persistence scope: Application (name, team, gitRepoUrl, runtimeType, onboardingPrUrl, onboardedAt), Environment (name, cluster, namespace, promotionOrder)
- [Source: planning-artifacts/architecture.md § Project Structure] — `application/` and `environment/` domain packages
- [Source: planning-artifacts/architecture.md § Structure Patterns] — Domain-centric packages, no cross-package entity imports
- [Source: planning-artifacts/architecture.md § Database Naming] — Tables: `snake_case` plural, columns: `snake_case`, FKs: `<table_singular>_id`, indexes: `idx_<table>_<columns>`, unique: `uq_<table>_<columns>`
- [Source: planning-artifacts/architecture.md § API & Communication Patterns] — `/api/v1/teams/{teamId}/applications/{appId}/environments` (for future stories)
- [Source: planning-artifacts/architecture.md § Complete Project Directory Structure] — V3__create_applications.sql, V4__create_environments.sql filenames
- [Source: planning-artifacts/architecture.md § Architectural Boundaries] — Panache entities internal to domain package, DTOs for API, cross-domain references by ID only
- [Source: planning-artifacts/epics.md § Epic 2 / Story 2.1] — Full acceptance criteria
- [Source: implementation-artifacts/1-7-admin-cluster-registration.md] — Entity pattern, migration pattern, DTO pattern, test pattern
- [Source: project-context.md § Technology Stack] — Quarkus 3.34.x, Hibernate Panache, PostgreSQL, Flyway
- [Source: project-context.md § Critical Implementation Rules] — Panache Active Record, @Column for multi-word, camelCase Java ↔ snake_case DB

## Dev Agent Record

### Agent Model Used
Claude claude-4.6-opus (Cursor Agent)

### Debug Log References
- Integration tests initially failed with `ARJUNA016051: thread is already associated with a transaction!` when using `UserTransaction.begin()/commit()` — resolved by switching to `QuarkusTransaction.requiringNew().call()` which properly creates isolated transaction contexts in `@QuarkusTest`
- 3 pre-existing failures in `GlobalExceptionMapperIT` (integration/validation/unexpected → 404 instead of 502/400/500) confirmed as pre-existing and unrelated to this story's changes

### Completion Notes List
- ✅ Flyway V3 and V4 migrations run successfully, creating `applications` and `environments` tables with all specified columns, constraints, indexes, and foreign keys
- ✅ Application entity follows PanacheEntityBase + IDENTITY pattern with `findByTeam()` returning sorted by name
- ✅ Environment entity follows same pattern with `findByApplicationOrderByPromotionOrder()` returning sorted by promotion_order ASC
- ✅ Promotion chain is fully data-driven — no hardcoded assumption of environment count; tested with 3 and 4 environments
- ✅ Cross-domain references use Long IDs only (no `@ManyToOne`), matching architecture mandate
- ✅ DTOs are Java records with static `from(Entity)` factories
- ✅ 94 unit tests pass (7 new: 4 Application + 3 Environment), 0 regressions
- ✅ 9 new integration tests pass: persist/retrieve, finder methods, unique constraints, promotion chain ordering
- ✅ `sameNameAllowedInDifferentTeams` test verifies the composite unique constraint `(team_id, name)` allows same app name across teams

### File List
- `developer-portal/src/main/resources/db/migration/V3__create_applications.sql` (new)
- `developer-portal/src/main/resources/db/migration/V4__create_environments.sql` (new)
- `developer-portal/src/main/java/com/portal/application/Application.java` (new)
- `developer-portal/src/main/java/com/portal/application/ApplicationSummaryDto.java` (new)
- `developer-portal/src/main/java/com/portal/environment/Environment.java` (new)
- `developer-portal/src/main/java/com/portal/environment/EnvironmentDto.java` (new)
- `developer-portal/src/test/java/com/portal/application/ApplicationTest.java` (new)
- `developer-portal/src/test/java/com/portal/application/ApplicationEntityIT.java` (new)
- `developer-portal/src/test/java/com/portal/environment/EnvironmentTest.java` (new)
- `developer-portal/src/test/java/com/portal/environment/EnvironmentEntityIT.java` (new)
