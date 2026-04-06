package com.portal.integration.argocd;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.PortalEnvironmentStatus;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.argocd.model.ArgoCdSyncStatus;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Production ArgoCD adapter that queries the ArgoCD REST API for application
 * sync and health status, translating responses to portal domain language.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.argocd.provider", stringValue = "argocd", enableIfMissing = true)
public class ArgoCdRestAdapter implements ArgoCdAdapter {

    @Inject
    @RestClient
    ArgoCdRestClient restClient;

    @Inject
    ArgoCdConfig config;

    @Override
    public List<EnvironmentStatusDto> getEnvironmentStatuses(String appName,
            List<Environment> environments) {
        String authHeader = config.token()
                .map(t -> "Bearer " + t)
                .orElse("");

        List<CompletableFuture<EnvironmentStatusDto>> futures = environments.stream()
                .map(env -> CompletableFuture.supplyAsync(() ->
                        fetchSingleEnvironmentStatus(appName, env, authHeader)))
                .toList();

        return futures.stream()
                .map(f -> {
                    try {
                        return f.join();
                    } catch (CompletionException e) {
                        if (e.getCause() instanceof PortalIntegrationException pie) {
                            throw pie;
                        }
                        throw e;
                    }
                })
                .toList();
    }

    private EnvironmentStatusDto fetchSingleEnvironmentStatus(String appName,
            Environment env, String authHeader) {
        String argoAppName = appName + "-run-" + env.name.toLowerCase();
        String deepLink = config.url() + "/applications/" + argoAppName;

        try {
            JsonNode response = restClient.getApplication(argoAppName, authHeader);
            ArgoCdSyncStatus syncStatus = parseStatus(response);
            PortalEnvironmentStatus portalStatus = translateStatus(
                    syncStatus.syncStatus(), syncStatus.healthStatus());

            return new EnvironmentStatusDto(
                    env.name,
                    portalStatus,
                    syncStatus.deployedVersion().orElse(null),
                    syncStatus.operationFinishedAt().orElse(null),
                    argoAppName,
                    deepLink,
                    null);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return new EnvironmentStatusDto(
                        env.name, PortalEnvironmentStatus.NOT_DEPLOYED,
                        null, null, argoAppName, deepLink, null);
            }
            throw new PortalIntegrationException("argocd", "getEnvironmentStatus",
                    "Deployment status unavailable \u2014 ArgoCD returned an error",
                    deepLink, e);
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("argocd", "getEnvironmentStatus",
                    "Deployment status unavailable \u2014 ArgoCD is unreachable",
                    deepLink, e);
        }
    }

    private ArgoCdSyncStatus parseStatus(JsonNode root) {
        JsonNode status = root.path("status");
        String syncStatus = status.path("sync").path("status").asText("Unknown");
        String healthStatus = status.path("health").path("status").asText("Unknown");

        Optional<String> version = Optional.empty();
        JsonNode images = status.path("summary").path("images");
        if (images.isArray() && !images.isEmpty()) {
            version = extractVersionFromImage(images.get(0).asText());
        }

        Optional<Instant> finishedAt = Optional.empty();
        String finishedAtStr = status.path("operationState").path("finishedAt").asText(null);
        if (finishedAtStr != null) {
            try {
                finishedAt = Optional.of(Instant.parse(finishedAtStr));
            } catch (DateTimeParseException ignored) {
                // Malformed timestamp from ArgoCD — treat as absent rather than failing the entire call
            }
        }

        return new ArgoCdSyncStatus(syncStatus, healthStatus, version, finishedAt);
    }

    /**
     * Extracts the tag from a container image reference, handling digest-pinned
     * images ({@code @sha256:...}) and registry:port formats ({@code host:5000/path:tag}).
     */
    static Optional<String> extractVersionFromImage(String image) {
        if (image == null || image.isBlank()) {
            return Optional.empty();
        }
        // Digest-pinned images have no meaningful tag
        if (image.contains("@sha256:")) {
            return Optional.empty();
        }
        // Find the last slash to isolate the name:tag portion from registry:port/path
        int lastSlash = image.lastIndexOf('/');
        String nameTag = (lastSlash >= 0) ? image.substring(lastSlash + 1) : image;
        int colonIdx = nameTag.lastIndexOf(':');
        if (colonIdx > 0 && colonIdx < nameTag.length() - 1) {
            return Optional.of(nameTag.substring(colonIdx + 1));
        }
        return Optional.empty();
    }

    static PortalEnvironmentStatus translateStatus(String syncStatus, String healthStatus) {
        if ("Degraded".equals(healthStatus) || "Missing".equals(healthStatus)) {
            return PortalEnvironmentStatus.UNHEALTHY;
        }
        if ("OutOfSync".equals(syncStatus) || "Progressing".equals(healthStatus)
                || "Suspended".equals(healthStatus)) {
            return PortalEnvironmentStatus.DEPLOYING;
        }
        if ("Synced".equals(syncStatus) && "Healthy".equals(healthStatus)) {
            return PortalEnvironmentStatus.HEALTHY;
        }
        if ("Unknown".equals(syncStatus) && "Unknown".equals(healthStatus)) {
            return PortalEnvironmentStatus.NOT_DEPLOYED;
        }
        return PortalEnvironmentStatus.UNHEALTHY;
    }
}
