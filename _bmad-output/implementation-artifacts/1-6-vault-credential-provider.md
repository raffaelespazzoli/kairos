# Story 1.6: Vault Credential Provider

Status: done

## Story

As a platform operator,
I want the portal to retrieve cluster credentials from Vault using short-lived tokens with TTL-aware caching,
So that the portal can authenticate to OpenShift clusters securely without storing long-lived credentials.

## Acceptance Criteria

1. **SecretManagerAdapter interface defined**
   - **Given** the SecretManagerAdapter interface is defined
   - **When** reviewing its contract
   - **Then** it exposes a method to retrieve credentials for a given cluster name and role
   - **And** it returns a credential object containing the token/kubeconfig and its TTL

2. **VaultSecretManagerAdapter calls Vault HTTP API**
   - **Given** the VaultSecretManagerAdapter is the active implementation (configured via `portal.secrets.provider=vault`)
   - **When** credentials are requested for a cluster
   - **Then** it calls the Vault HTTP API at the path template `/infra/{cluster}/kubernetes-secret-engine/creds/{role}` with the cluster name interpolated
   - **And** it authenticates to Vault via Kubernetes auth using the pod's service account token

3. **SecretManagerCredentialProvider caches on miss**
   - **Given** the SecretManagerCredentialProvider wraps the active SecretManagerAdapter
   - **When** credentials are requested for a (cluster, role) pair that is not cached
   - **Then** it delegates to the adapter to fetch fresh credentials
   - **And** it stores the credentials in an in-memory cache keyed by (cluster, role)
   - **And** it records the TTL expiry time

4. **Cache hit returns without network call**
   - **Given** credentials for a (cluster, role) pair are already cached
   - **When** the cached credentials have not expired (TTL has not been reached)
   - **Then** the cached credentials are returned without calling Vault
   - **And** no network request is made

5. **TTL-aware proactive refresh**
   - **Given** cached credentials are approaching TTL expiry
   - **When** a request for those credentials is made
   - **Then** fresh credentials are fetched from Vault before the cached ones expire
   - **And** the cache is updated with the new credentials and TTL

6. **Vault unreachable throws PortalIntegrationException**
   - **Given** Vault is unreachable
   - **When** credentials are requested
   - **Then** a PortalIntegrationException is thrown with system="vault" and a developer-friendly message
   - **And** the error propagates to the API layer as a 502 response

7. **No persistent credential storage**
   - **Given** the credential provider is running
   - **When** reviewing the runtime state
   - **Then** no credentials are written to the database or filesystem
   - **And** credentials exist only in the in-memory cache with TTL enforcement

## Tasks / Subtasks

- [x] Task 1: Create VaultCredential model (AC: #1)
  - [x] Create `VaultCredential.java` in `com.portal.integration.secrets.vault.model`
  - [x] Fields: `serviceAccountToken` (String), `leaseDuration` (int seconds), `serviceAccountName` (String), `serviceAccountNamespace` (String)
  - [x] Implement `getExpiresAt()` method returning `Instant`
- [x] Task 2: Create SecretManagerAdapter interface (AC: #1)
  - [x] Create `SecretManagerAdapter.java` in `com.portal.integration.secrets`
  - [x] Define `ClusterCredential getCredentials(String clusterName, String role)`
  - [x] Create `ClusterCredential` record: `token` (String), `ttlSeconds` (int), `expiresAt` (Instant)
- [x] Task 3: Create VaultConfig (AC: #2)
  - [x] Create `VaultConfig.java` in `com.portal.integration.secrets.vault`
  - [x] `@ConfigProperty` for `portal.secrets.vault.url`, `portal.secrets.vault.credential-path-template`, `portal.secrets.vault.auth-role`
  - [x] Add config properties to `application.properties` and `application-dev.properties`
- [x] Task 4: Create SecretManagerConfig (AC: #2)
  - [x] Create `SecretManagerConfig.java` in `com.portal.integration.secrets`
  - [x] `@ConfigProperty` for `portal.secrets.provider` (default: `vault`)
- [x] Task 5: Implement VaultSecretManagerAdapter (AC: #2, #6)
  - [x] Create `VaultSecretManagerAdapter.java` in `com.portal.integration.secrets.vault`
  - [x] `@ApplicationScoped` CDI bean, conditionally activated when `portal.secrets.provider=vault`
  - [x] Implement Vault Kubernetes auth login: `POST {vaultUrl}/v1/auth/kubernetes/login` with pod's SA JWT
  - [x] Cache the Vault client token with its own TTL
  - [x] Implement credential fetch: `POST {vaultUrl}/v1/infra/{cluster}/kubernetes-secret-engine/creds/{role}`
  - [x] Parse Vault JSON response: extract `data.service_account_token`, `lease_duration`
  - [x] Wrap Vault errors in PortalIntegrationException with system="vault"
  - [x] Use `java.net.http.HttpClient` (or Quarkus REST Client) for Vault HTTP calls
- [x] Task 6: Implement SecretManagerCredentialProvider (AC: #3, #4, #5)
  - [x] Create `SecretManagerCredentialProvider.java` in `com.portal.integration.secrets`
  - [x] `@ApplicationScoped` CDI bean wrapping the active `SecretManagerAdapter`
  - [x] `ConcurrentHashMap<CacheKey, CachedCredential>` keyed by `(cluster, role)`
  - [x] On miss: delegate to adapter, store result with TTL expiry timestamp
  - [x] On hit (not expired): return cached value, no network call
  - [x] On hit (approaching expiry — within 20% of TTL): fetch fresh credentials proactively
  - [x] Thread-safe: use `computeIfAbsent` or similar to prevent thundering herd on cache miss
- [x] Task 7: Write unit tests for SecretManagerCredentialProvider (AC: #3, #4, #5)
  - [x] Create `SecretManagerCredentialProviderTest.java` in `src/test/java/com/portal/integration/secrets/`
  - [x] Test cache miss → delegates to adapter
  - [x] Test cache hit → returns cached, adapter not called
  - [x] Test TTL expiry → re-fetches from adapter
  - [x] Test proactive refresh within TTL buffer zone
  - [x] Test concurrent requests for same key don't cause duplicate fetches
- [x] Task 8: Write unit tests for VaultSecretManagerAdapter (AC: #2, #6)
  - [x] Create `VaultSecretManagerAdapterTest.java` in `src/test/java/com/portal/integration/secrets/vault/`
  - [x] Mock HTTP responses from Vault
  - [x] Test successful credential fetch with correct path interpolation
  - [x] Test Vault auth token refresh
  - [x] Test Vault unreachable → PortalIntegrationException with system="vault"
  - [x] Test malformed Vault response handling
- [x] Task 9: Verify no persistent credential storage (AC: #7)
  - [x] Ensure no Panache entity or Flyway migration for credentials
  - [x] Ensure no file I/O for credential persistence
  - [x] Document in code that credentials are ephemeral by design

## Dev Notes

### Architecture — Vault Integration Flow

The portal authenticates to OpenShift clusters via Vault-issued short-lived service account tokens. The flow has two stages:

**Stage 1 — Vault Authentication (portal → Vault)**
The portal pod authenticates to Vault using Kubernetes auth. It reads its own service account JWT from `/var/run/secrets/kubernetes.io/serviceaccount/token` and sends it to Vault.

```
POST {vaultUrl}/v1/auth/kubernetes/login
{
  "jwt": "<pod-service-account-jwt>",
  "role": "<portal-vault-auth-role>"
}
→ Response: { "auth": { "client_token": "hvs.xxx", "lease_duration": 3600, ... } }
```

The returned `client_token` is the Vault authentication token used for all subsequent Vault API calls. It has its own TTL (`lease_duration`) — the adapter must cache and refresh this token independently.

**Stage 2 — Cluster Credential Fetch (portal → Vault → cluster SA token)**
Using the Vault client token, the portal requests cluster-specific credentials:

```
POST {vaultUrl}/v1/infra/{cluster}/kubernetes-secret-engine/creds/{role}
Headers: X-Vault-Token: <vault-client-token>
{
  "kubernetes_namespace": "<namespace-if-required>",
  "ttl": "1h"
}
→ Response:
{
  "lease_id": "infra/dev-cluster/kubernetes-secret-engine/creds/portal-role/abc123",
  "renewable": false,
  "lease_duration": 3600,
  "data": {
    "service_account_name": "portal-sa",
    "service_account_namespace": "default",
    "service_account_token": "eyJhbG..."
  }
}
```

The `data.service_account_token` is the Kubernetes bearer token for the target cluster. `lease_duration` is the TTL in seconds.

### Architecture — Class Design

```
SecretManagerAdapter (interface)
  └── VaultSecretManagerAdapter (@ApplicationScoped)
        ├── Vault Kubernetes auth login + token caching
        └── Vault credential fetch at path template

SecretManagerCredentialProvider (@ApplicationScoped)
  ├── Wraps SecretManagerAdapter
  ├── ConcurrentHashMap<CacheKey, CachedCredential>
  └── TTL-aware caching with proactive refresh
```

**SecretManagerAdapter interface:**

```java
package com.portal.integration.secrets;

public interface SecretManagerAdapter {
    ClusterCredential getCredentials(String clusterName, String role);
}
```

**ClusterCredential record:**

```java
package com.portal.integration.secrets;

import java.time.Instant;

public record ClusterCredential(
    String token,
    int ttlSeconds,
    Instant expiresAt
) {
    public static ClusterCredential of(String token, int ttlSeconds) {
        return new ClusterCredential(token, ttlSeconds, Instant.now().plusSeconds(ttlSeconds));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isApproachingExpiry() {
        long totalTtl = ttlSeconds;
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return remainingSeconds < (totalTtl * 0.2);
    }
}
```

**SecretManagerCredentialProvider — cache design:**

```java
package com.portal.integration.secrets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SecretManagerCredentialProvider {

    @Inject
    SecretManagerAdapter adapter;

    private final ConcurrentHashMap<String, ClusterCredential> cache = new ConcurrentHashMap<>();

    public ClusterCredential getCredentials(String clusterName, String role) {
        String cacheKey = clusterName + "::" + role;

        ClusterCredential cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired() && !cached.isApproachingExpiry()) {
            return cached;
        }

        ClusterCredential fresh = adapter.getCredentials(clusterName, role);
        cache.put(cacheKey, fresh);
        return fresh;
    }
}
```

The actual implementation must handle the thundering-herd problem using `computeIfAbsent` or a lock-per-key strategy to prevent multiple concurrent Vault calls for the same (cluster, role) pair.

**VaultSecretManagerAdapter — implementation notes:**

```java
package com.portal.integration.secrets.vault;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.http.HttpClient;

@ApplicationScoped
public class VaultSecretManagerAdapter implements SecretManagerAdapter {

    @Inject
    VaultConfig vaultConfig;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile String vaultClientToken;
    private volatile java.time.Instant vaultTokenExpiry;

    @Override
    public ClusterCredential getCredentials(String clusterName, String role) {
        ensureVaultToken();
        String path = vaultConfig.credentialPathTemplate()
            .replace("{cluster}", clusterName)
            .replace("{role}", role);
        // POST to {vaultUrl}/v1/{path}
        // Parse response, return ClusterCredential.of(token, leaseDuration)
        // On failure: throw new PortalIntegrationException("vault", "get-credentials", message)
    }

    private synchronized void ensureVaultToken() {
        if (vaultClientToken != null && java.time.Instant.now().isBefore(vaultTokenExpiry)) {
            return;
        }
        // Read SA JWT from /var/run/secrets/kubernetes.io/serviceaccount/token
        // POST to {vaultUrl}/v1/auth/kubernetes/login
        // Parse auth.client_token and auth.lease_duration
        // Store in vaultClientToken and vaultTokenExpiry
        // On failure: throw new PortalIntegrationException("vault", "authenticate", message)
    }
}
```

### Architecture — Configuration Properties

Add to `application.properties`:

```properties
# Secret Manager
portal.secrets.provider=${SECRETS_PROVIDER:vault}
portal.secrets.vault.url=${VAULT_URL}
portal.secrets.vault.credential-path-template=/infra/{cluster}/kubernetes-secret-engine/creds/{role}
portal.secrets.vault.auth-role=${VAULT_AUTH_ROLE:portal}
portal.secrets.vault.auth-mount-path=${VAULT_AUTH_MOUNT_PATH:auth/kubernetes}
```

Add to `application-dev.properties`:

```properties
# Vault dev mode (local Vault or WireMock stub)
portal.secrets.vault.url=http://localhost:8200
portal.secrets.vault.auth-role=portal-dev
```

**Config classes:**

```java
package com.portal.integration.secrets.vault;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "portal.secrets.vault")
public interface VaultConfig {
    String url();

    @WithDefault("/infra/{cluster}/kubernetes-secret-engine/creds/{role}")
    String credentialPathTemplate();

    @WithDefault("portal")
    String authRole();

    @WithDefault("auth/kubernetes")
    String authMountPath();
}
```

```java
package com.portal.integration.secrets;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "portal.secrets")
public interface SecretManagerConfig {
    @WithDefault("vault")
    String provider();
}
```

Use `@ConfigMapping` (Quarkus-native) instead of `@ConfigProperty` for grouped configuration — this is the preferred Quarkus pattern for multi-property configuration groups.

### Architecture — HTTP Client Choice

Use `java.net.http.HttpClient` (JDK 11+) for Vault HTTP calls. Reasons:
- Vault HTTP API is simple REST — no need for Quarkus REST Client's annotation-heavy approach
- The adapter makes exactly two types of calls (auth login + credential fetch)
- `HttpClient` is zero-dependency, lightweight, and easy to mock in tests
- Avoids coupling to Quarkus REST Client for an internal infrastructure concern

**Do NOT** use the Quarkiverse Vault extension — the architecture specifies direct HTTP API calls with the custom path template `/infra/{cluster}/kubernetes-secret-engine/creds/{role}`.

### Architecture — CDI Bean Selection

The `SecretManagerAdapter` interface allows pluggable implementations. For MVP only Vault exists. Use CDI `@Alternative` with `@Priority` or Quarkus `@IfBuildProperty` to conditionally activate `VaultSecretManagerAdapter`:

```java
@ApplicationScoped
@IfBuildProperty(name = "portal.secrets.provider", stringValue = "vault", enableIfMissing = true)
public class VaultSecretManagerAdapter implements SecretManagerAdapter { ... }
```

This allows future secret manager implementations (e.g., AWS Secrets Manager) to be added without modifying existing code.

### Architecture — Thread Safety Requirements

- `SecretManagerCredentialProvider.cache` must use `ConcurrentHashMap`
- Vault client token refresh in `VaultSecretManagerAdapter` must be `synchronized` or use `ReentrantLock`
- No `synchronized` on the entire `getCredentials()` call — only on cache operations and token refresh
- Multiple concurrent callers for the same (cluster, role) must not trigger duplicate Vault calls — use `computeIfAbsent` or a per-key lock

### Architecture — Error Handling

All Vault communication errors must throw `PortalIntegrationException`:

```java
throw new PortalIntegrationException(
    "vault",
    "get-credentials",
    "Failed to retrieve credentials for cluster '" + clusterName + "': " + e.getMessage()
);
```

```java
throw new PortalIntegrationException(
    "vault",
    "authenticate",
    "Vault Kubernetes auth login failed: " + e.getMessage()
);
```

These propagate to `GlobalExceptionMapper` (Story 1.5) → 502 response with standardized error JSON. **Do NOT** create additional exception mappers for Vault — `PortalIntegrationException` is the single adapter error type.

### Architecture — Security Constraints

- **NFR6:** No long-lived credentials. Vault tokens and cluster credentials are in-memory only, with TTL enforcement.
- **NFR9:** Portal's Vault credentials scoped to minimum permissions — only the `portal` role with access to the credential path template.
- The pod's service account JWT at `/var/run/secrets/kubernetes.io/serviceaccount/token` is the only bootstrap credential.
- Never log credential tokens — log Vault operations at INFO level with cluster name and role, but redact all token values.
- Never serialize credentials to JSON responses — credentials flow only between adapter → service → adapter (e.g., to Tekton/ArgoCD adapters).

### Architecture — Dev/Test Mode

For development and testing without a real Vault instance:

- **Unit tests:** Mock `SecretManagerAdapter` — inject a test implementation that returns fixed `ClusterCredential` values
- **Integration tests:** Use WireMock to stub Vault HTTP endpoints, or create a `DevSecretManagerAdapter` activated by `@IfBuildProperty(name = "portal.secrets.provider", stringValue = "dev")`
- The `DevSecretManagerAdapter` can return a pre-configured kubeconfig token for local cluster access
- **Dev profile** (`application-dev.properties`): Point `portal.secrets.vault.url` at a local Vault dev server (`vault server -dev`) or WireMock

### What NOT to Build in This Story

- No REST endpoint exposing credentials — this is a backend-internal service only
- No frontend changes — this is entirely backend
- No database schema or Flyway migration — credentials are ephemeral
- No actual calls to OpenShift/Kubernetes clusters — callers (Tekton adapter, ArgoCD adapter) are future stories
- No Vault admin operations (creating roles, configuring secrets engines) — Vault is pre-configured by the platform team
- No health check endpoint for Vault — SmallRye Health integration deferred
- No retry logic with exponential backoff — keep it simple for MVP; throw on failure

### Files to Create

| File | Package/Path | Purpose |
|---|---|---|
| `SecretManagerAdapter.java` | `com.portal.integration.secrets` | Interface for credential retrieval |
| `ClusterCredential.java` | `com.portal.integration.secrets` | Record: token + TTL + expiresAt |
| `SecretManagerCredentialProvider.java` | `com.portal.integration.secrets` | TTL-aware in-memory cache over adapter |
| `SecretManagerConfig.java` | `com.portal.integration.secrets` | Config mapping for `portal.secrets.*` |
| `VaultSecretManagerAdapter.java` | `com.portal.integration.secrets.vault` | Vault HTTP API implementation |
| `VaultConfig.java` | `com.portal.integration.secrets.vault` | Config mapping for `portal.secrets.vault.*` |
| `VaultCredential.java` | `com.portal.integration.secrets.vault.model` | Internal Vault API response model |
| `SecretManagerCredentialProviderTest.java` | `src/test/.../integration/secrets/` | Unit tests for cache behavior |
| `VaultSecretManagerAdapterTest.java` | `src/test/.../integration/secrets/vault/` | Unit tests for Vault HTTP calls |

### Files to Modify

| File | Change |
|---|---|
| `src/main/resources/application.properties` | Add `portal.secrets.*` and `portal.secrets.vault.*` properties |
| `src/main/resources/application-dev.properties` | Add dev-mode Vault config pointing to localhost |

### Previous Story Intelligence (Story 1.5)

- Story 1.5 established the `PortalIntegrationException` class in `com.portal.integration` with `system`, `operation`, `message`, `deepLink` fields. Reuse this directly — do not create a separate Vault exception.
- Story 1.5 established `GlobalExceptionMapper` that maps `PortalIntegrationException` → 502 with standardized `ErrorResponse` JSON. Vault errors will flow through this mapper automatically.
- Story 1.5 established `ErrorResponse` record in `com.portal.common`. No changes needed.
- Backend packages `com.portal.integration` and `com.portal.common` already exist from Story 1.5.

### Project Structure Notes

- `com.portal.integration.secrets/` is the package for the adapter abstraction layer (interface + provider)
- `com.portal.integration.secrets.vault/` is the package for the Vault-specific implementation
- `com.portal.integration.secrets.vault.model/` holds Vault API response DTOs (internal, not exposed via REST)
- This follows the architecture's adapter pattern: `integration/{system}/` with `model/` subdirectory
- The `SecretManagerCredentialProvider` is **not** an adapter — it's a cache layer that wraps any adapter. Future adapters (e.g., Tekton, ArgoCD) will `@Inject SecretManagerCredentialProvider` to get cluster credentials.

### References

- [Source: planning-artifacts/architecture.md § Integration Architecture — Secret manager credential lifecycle] — TTL-aware cache, SecretManagerAdapter interface, Vault implementation, Kubernetes auth
- [Source: planning-artifacts/architecture.md § Configuration Properties] — `portal.secrets.provider`, `portal.secrets.vault.url`, `portal.secrets.vault.credential-path-template`
- [Source: planning-artifacts/architecture.md § Project Structure] — `integration/secrets/`, `integration/secrets/vault/`, file locations
- [Source: planning-artifacts/architecture.md § Naming Patterns] — Adapter classes follow `<System>Adapter` pattern
- [Source: planning-artifacts/architecture.md § Integration Adapters — Complete List] — `SecretManagerAdapter → VaultSecretManagerAdapter`, `SecretManagerCredentialProvider` as cache layer
- [Source: planning-artifacts/architecture.md § Anti-Patterns] — "Storing Vault credentials in database → In-memory TTL cache only via SecretManagerCredentialProvider"
- [Source: planning-artifacts/architecture.md § Cross-Component Dependencies] — "All adapters depend on VaultCredentialProvider for cluster access"
- [Source: planning-artifacts/prd.md § Credential & Cluster Access Model] — Vault path `/infra/<cluster>/kubernetes-secret-engine/<role>`, first-class platform citizen
- [Source: planning-artifacts/prd.md § Security NFRs] — NFR6 (no long-lived creds), NFR9 (minimum permissions)
- [Source: planning-artifacts/epics.md § Epic 1 / Story 1.6] — Full acceptance criteria
- [Source: HashiCorp Vault API Docs § Kubernetes Auth] — `POST /v1/auth/kubernetes/login` with JWT and role
- [Source: HashiCorp Vault API Docs § Kubernetes Secrets Engine] — `POST /v1/{mount}/creds/{role}` returns `service_account_token` and `lease_duration`
- [Source: implementation-artifacts/1-5-api-foundation-error-handling.md] — PortalIntegrationException, GlobalExceptionMapper, ErrorResponse established

## Dev Agent Record

### Agent Model Used

Claude claude-4.6-opus (2026-04-04)

### Debug Log References

No issues encountered during implementation.

### Completion Notes List

- **Task 1:** Created `VaultCredential.java` with all fields (`serviceAccountToken`, `leaseDuration`, `serviceAccountName`, `serviceAccountNamespace`) and `getExpiresAt()` returning `Instant`.
- **Task 2:** Created `SecretManagerAdapter` interface with `getCredentials(String, String)` method and `ClusterCredential` record with `of()`, `isExpired()`, and `isApproachingExpiry()` (20% TTL threshold).
- **Task 3:** Created `VaultConfig` using `@ConfigMapping` (Quarkus-native pattern) with properties for `url`, `credentialPathTemplate`, `authRole`, `authMountPath`. Added all config properties to `application.properties`.
- **Task 4:** Created `SecretManagerConfig` with `@ConfigMapping` for `portal.secrets.provider` defaulting to `vault`.
- **Task 5:** Implemented `VaultSecretManagerAdapter` with: `@IfBuildProperty` conditional activation, Kubernetes auth login with cached Vault token, credential fetch with path interpolation, Jackson-based JSON parsing, all errors wrapped in `PortalIntegrationException(system="vault")`, `java.net.http.HttpClient` for zero-dependency HTTP calls.
- **Task 6:** Implemented `SecretManagerCredentialProvider` with `ConcurrentHashMap` cache keyed by `cluster::role`, cache miss delegation, TTL-aware expiry checks, proactive refresh within 20% TTL buffer, and `compute()` to prevent thundering herd.
- **Task 7:** 7 unit tests covering: cache miss, cache hit, TTL expiry, proactive refresh, separate cache keys, concurrent access (thundering herd prevention), and valid cache non-refresh.
- **Task 8:** 9 unit tests covering: successful fetch, path interpolation, auth token refresh, unreachable Vault, non-success HTTP status, malformed response, auth login failure, missing SA token, and token reuse.
- **Task 9:** Verified: no Panache entity, no Flyway migration, no file I/O for credential persistence. Credentials are ephemeral in-memory only. Documented in Javadoc on `SecretManagerCredentialProvider`.

### Change Log

- 2026-04-04: Implemented Story 1.6 — Vault Credential Provider with TTL-aware caching, Kubernetes auth, all unit tests passing (16 new, 84 total, 0 regressions)
- 2026-04-04: Code review follow-up — guarded null JSON fields (service_account_token, client_token) to throw PortalIntegrationException instead of NPE; widened try/catch in getCredentials and ensureVaultToken to wrap URI/config RuntimeExceptions as integration errors; added 3 tests (87 total, 0 regressions)

### File List

#### New Files
- `developer-portal/src/main/java/com/portal/integration/secrets/SecretManagerAdapter.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/ClusterCredential.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/SecretManagerCredentialProvider.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/SecretManagerConfig.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/vault/VaultSecretManagerAdapter.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/vault/VaultConfig.java`
- `developer-portal/src/main/java/com/portal/integration/secrets/vault/model/VaultCredential.java`
- `developer-portal/src/test/java/com/portal/integration/secrets/SecretManagerCredentialProviderTest.java`
- `developer-portal/src/test/java/com/portal/integration/secrets/vault/VaultSecretManagerAdapterTest.java`

#### Modified Files
- `developer-portal/src/main/resources/application.properties` — Added `portal.secrets.*` and `portal.secrets.vault.*` properties
- `developer-portal/src/test/resources/application.properties` — Added `portal.secrets.provider=dev` and `portal.secrets.vault.url` for tests
