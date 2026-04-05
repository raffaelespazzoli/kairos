package com.portal.integration.secrets.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerAdapter;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Vault implementation of {@link SecretManagerAdapter}. Authenticates to Vault
 * via Kubernetes auth (pod service account JWT) and fetches short-lived cluster
 * credentials from the Kubernetes secret engine.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.secrets.provider", stringValue = "vault", enableIfMissing = true)
public class VaultSecretManagerAdapter implements SecretManagerAdapter {

    static final Path SA_TOKEN_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    VaultConfig vaultConfig;

    private final HttpClient httpClient;

    private volatile String vaultClientToken;
    private volatile Instant vaultTokenExpiry;

    public VaultSecretManagerAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    VaultSecretManagerAdapter(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ClusterCredential getCredentials(String clusterName, String role) {
        ensureVaultToken();

        try {
            String credPath = vaultConfig.credentialPathTemplate()
                    .replace("{cluster}", clusterName)
                    .replace("{role}", role);

            String url = vaultConfig.url() + "/v1" + credPath;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", vaultClientToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .timeout(HTTP_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortalIntegrationException(
                        "vault", "get-credentials",
                        "Vault returned HTTP " + response.statusCode()
                                + " for cluster '" + clusterName + "', role '" + role + "'");
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode data = json.get("data");
            if (data == null || data.isNull()) {
                throw new PortalIntegrationException(
                        "vault", "get-credentials",
                        "Vault response missing 'data' field for cluster '" + clusterName + "'");
            }

            JsonNode tokenNode = data.get("service_account_token");
            if (tokenNode == null || tokenNode.isNull() || tokenNode.asText().isBlank()) {
                throw new PortalIntegrationException(
                        "vault", "get-credentials",
                        "Vault response missing 'service_account_token' for cluster '" + clusterName + "'");
            }
            String token = tokenNode.asText();
            int leaseDuration = json.path("lease_duration").asInt(3600);

            return ClusterCredential.of(token, leaseDuration);

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PortalIntegrationException(
                    "vault", "get-credentials",
                    "Failed to retrieve credentials for cluster '" + clusterName + "': " + e.getMessage(),
                    null, e);
        } catch (RuntimeException e) {
            throw new PortalIntegrationException(
                    "vault", "get-credentials",
                    "Failed to build Vault request for cluster '" + clusterName + "': " + e.getMessage(),
                    null, e);
        }
    }

    synchronized void ensureVaultToken() {
        if (vaultClientToken != null && Instant.now().isBefore(vaultTokenExpiry)) {
            return;
        }

        String saJwt = readServiceAccountToken();

        try {
            String loginUrl = vaultConfig.url() + "/v1/" + vaultConfig.authMountPath() + "/login";

            String body = objectMapper.createObjectNode()
                    .put("jwt", saJwt)
                    .put("role", vaultConfig.authRole())
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(loginUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(HTTP_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortalIntegrationException(
                        "vault", "authenticate",
                        "Vault Kubernetes auth login failed with HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode auth = json.get("auth");
            if (auth == null || auth.isNull()) {
                throw new PortalIntegrationException(
                        "vault", "authenticate",
                        "Vault login response missing 'auth' field");
            }

            JsonNode clientTokenNode = auth.get("client_token");
            if (clientTokenNode == null || clientTokenNode.isNull() || clientTokenNode.asText().isBlank()) {
                throw new PortalIntegrationException(
                        "vault", "authenticate",
                        "Vault login response missing 'client_token'");
            }
            this.vaultClientToken = clientTokenNode.asText();
            int leaseDuration = auth.path("lease_duration").asInt(3600);
            this.vaultTokenExpiry = Instant.now().plusSeconds(leaseDuration)
                    .minus(Duration.ofSeconds(30));

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PortalIntegrationException(
                    "vault", "authenticate",
                    "Vault Kubernetes auth login failed: " + e.getMessage(), null, e);
        } catch (RuntimeException e) {
            throw new PortalIntegrationException(
                    "vault", "authenticate",
                    "Failed to build Vault login request: " + e.getMessage(), null, e);
        }
    }

    String readServiceAccountToken() {
        try {
            return Files.readString(SA_TOKEN_PATH).trim();
        } catch (IOException e) {
            throw new PortalIntegrationException(
                    "vault", "authenticate",
                    "Cannot read service account token from " + SA_TOKEN_PATH + ": " + e.getMessage(),
                    null, e);
        }
    }

    void setVaultClientToken(String token, Instant expiry) {
        this.vaultClientToken = token;
        this.vaultTokenExpiry = expiry;
    }
}
