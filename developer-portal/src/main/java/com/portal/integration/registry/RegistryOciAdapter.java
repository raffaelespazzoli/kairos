package com.portal.integration.registry;

import com.portal.integration.PortalIntegrationException;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Production {@link RegistryAdapter} using the OCI Distribution API.
 * Re-tags an existing image by fetching its manifest and pushing it under a new tag.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.registry.provider", stringValue = "oci", enableIfMissing = true)
public class RegistryOciAdapter implements RegistryAdapter {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String OCI_MANIFEST_TYPE = "application/vnd.oci.image.manifest.v1+json";
    private static final String DOCKER_MANIFEST_TYPE = "application/vnd.docker.distribution.manifest.v2+json";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject
    RegistryConfig config;

    @Override
    public void tagImage(String imageReference, String newTag) {
        ImageRef ref = parseImageReference(imageReference);
        String baseUrl = config.url()
                .filter(u -> !u.isBlank())
                .orElse(ref.registryUrl());

        String manifestUrl = baseUrl + "/v2/" + ref.repository() + "/manifests/" + ref.tag();
        String manifest = fetchManifest(manifestUrl);

        String putUrl = baseUrl + "/v2/" + ref.repository() + "/manifests/" + newTag;
        pushManifest(putUrl, manifest);
    }

    private String fetchManifest(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", OCI_MANIFEST_TYPE + ", " + DOCKER_MANIFEST_TYPE)
                    .timeout(REQUEST_TIMEOUT)
                    .GET();

            config.token().ifPresent(t -> builder.header("Authorization", "Bearer " + t));

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortalIntegrationException("registry", "tagImage",
                        "Registry returned HTTP " + response.statusCode()
                                + " when fetching manifest");
            }
            return response.body();
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PortalIntegrationException("registry", "tagImage",
                    "Registry is unreachable \u2014 could not fetch image manifest", null, e);
        }
    }

    private void pushManifest(String url, String manifest) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", OCI_MANIFEST_TYPE)
                    .timeout(REQUEST_TIMEOUT)
                    .PUT(HttpRequest.BodyPublishers.ofString(manifest));

            config.token().ifPresent(t -> builder.header("Authorization", "Bearer " + t));

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortalIntegrationException("registry", "tagImage",
                        "Registry returned HTTP " + response.statusCode()
                                + " when pushing manifest with new tag");
            }
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PortalIntegrationException("registry", "tagImage",
                    "Registry is unreachable \u2014 could not tag image", null, e);
        }
    }

    static ImageRef parseImageReference(String imageReference) {
        int tagSep = imageReference.lastIndexOf(':');
        String withoutTag;
        String tag;
        if (tagSep > 0 && !imageReference.substring(tagSep).contains("/")) {
            withoutTag = imageReference.substring(0, tagSep);
            tag = imageReference.substring(tagSep + 1);
        } else {
            withoutTag = imageReference;
            tag = "latest";
        }

        int firstSlash = withoutTag.indexOf('/');
        if (firstSlash < 0) {
            throw new PortalIntegrationException("registry", "tagImage",
                    "Invalid image reference format: " + imageReference);
        }

        String registry = "https://" + withoutTag.substring(0, firstSlash);
        String repository = withoutTag.substring(firstSlash + 1);

        return new ImageRef(registry, repository, tag);
    }

    record ImageRef(String registryUrl, String repository, String tag) {}
}
