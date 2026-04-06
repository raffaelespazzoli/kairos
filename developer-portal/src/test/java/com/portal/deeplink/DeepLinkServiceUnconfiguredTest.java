package com.portal.deeplink;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(DeepLinkServiceUnconfiguredTest.EmptyDeepLinkProfile.class)
class DeepLinkServiceUnconfiguredTest {

    public static class EmptyDeepLinkProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "portal.tekton.dashboard-url", "",
                    "portal.grafana.url", "",
                    "portal.grafana.dashboard-id", "",
                    "portal.devspaces.url", "",
                    "portal.vault.url", ""
            );
        }
    }

    @Inject
    DeepLinkService deepLinkService;

    @Test
    void generateArgoCdLinkAlwaysReturnsUrl() {
        Optional<String> link = deepLinkService.generateArgoCdLink("my-app");
        assertTrue(link.isPresent(), "ArgoCD link is always present — URL is required");
    }

    @Test
    void generateTektonLinkReturnsEmptyWhenUnconfigured() {
        Optional<String> link = deepLinkService.generateTektonLink("run-1");
        assertTrue(link.isEmpty());
    }

    @Test
    void generateGrafanaLinkReturnsEmptyWhenUnconfigured() {
        Optional<String> link = deepLinkService.generateGrafanaLink("ns");
        assertTrue(link.isEmpty());
    }

    @Test
    void generateDevSpacesLinkReturnsEmptyWhenUnconfigured() {
        Optional<String> link = deepLinkService.generateDevSpacesLink("https://github.com/org/repo");
        assertTrue(link.isEmpty());
    }

    @Test
    void generateVaultLinkReturnsEmptyWhenUnconfigured() {
        Optional<String> link = deepLinkService.generateVaultLink("team", "app", "env");
        assertTrue(link.isEmpty());
    }
}
