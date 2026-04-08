package com.portal.integration.registry;

import com.portal.integration.PortalIntegrationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistryOciAdapterTest {

    @Test
    void parseImageReferenceExtractsRegistryRepoAndTag() {
        RegistryOciAdapter.ImageRef ref = RegistryOciAdapter.parseImageReference(
                "registry.example.com/team/app:abc1234");
        assertEquals("https://registry.example.com", ref.registryUrl());
        assertEquals("team/app", ref.repository());
        assertEquals("abc1234", ref.tag());
    }

    @Test
    void parseImageReferenceHandlesNestedRepository() {
        RegistryOciAdapter.ImageRef ref = RegistryOciAdapter.parseImageReference(
                "registry.example.com/org/team/app:v1.0.0");
        assertEquals("https://registry.example.com", ref.registryUrl());
        assertEquals("org/team/app", ref.repository());
        assertEquals("v1.0.0", ref.tag());
    }

    @Test
    void parseImageReferenceDefaultsToLatestWhenNoTag() {
        RegistryOciAdapter.ImageRef ref = RegistryOciAdapter.parseImageReference(
                "registry.example.com/team/app");
        assertEquals("https://registry.example.com", ref.registryUrl());
        assertEquals("team/app", ref.repository());
        assertEquals("latest", ref.tag());
    }

    @Test
    void parseImageReferenceHandlesPortInRegistry() {
        RegistryOciAdapter.ImageRef ref = RegistryOciAdapter.parseImageReference(
                "registry.example.com:5000/team/app:sha256");
        assertEquals("https://registry.example.com:5000", ref.registryUrl());
        assertEquals("team/app", ref.repository());
        assertEquals("sha256", ref.tag());
    }

    @Test
    void parseImageReferenceThrowsForInvalidFormat() {
        assertThrows(PortalIntegrationException.class,
                () -> RegistryOciAdapter.parseImageReference("invalid"));
    }
}
