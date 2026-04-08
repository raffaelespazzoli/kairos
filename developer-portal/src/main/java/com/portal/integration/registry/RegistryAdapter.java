package com.portal.integration.registry;

/**
 * Adapter for container registry operations required by the release flow.
 * Production implementation uses the OCI Distribution API to re-tag images;
 * dev-mode mock bypasses registry calls entirely.
 */
public interface RegistryAdapter {

    /**
     * Tags an existing container image with a new version tag.
     * Fetches the manifest for the existing image reference and pushes
     * the same manifest under the new tag.
     *
     * @param imageReference the full image reference (e.g., "registry.example.com/team/app:abc1234")
     * @param newTag         the version tag to apply (e.g., "v1.4.2")
     * @throws com.portal.integration.PortalIntegrationException if the registry is unreachable
     *         or the tag operation fails
     */
    void tagImage(String imageReference, String newTag);
}
