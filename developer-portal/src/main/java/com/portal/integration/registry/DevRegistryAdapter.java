package com.portal.integration.registry;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * No-op registry adapter for development and testing.
 * Activated when {@code portal.registry.provider=dev} is configured.
 */
@ApplicationScoped
@IfBuildProperty(name = "portal.registry.provider", stringValue = "dev")
public class DevRegistryAdapter implements RegistryAdapter {

    @Override
    public void tagImage(String imageReference, String newTag) {
        // no-op
    }
}
