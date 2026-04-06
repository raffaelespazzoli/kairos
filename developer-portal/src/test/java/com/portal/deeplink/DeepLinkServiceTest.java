package com.portal.deeplink;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DeepLinkServiceTest {

    @Inject
    DeepLinkService deepLinkService;

    @Test
    void generateArgoCdLinkReturnsUrl() {
        Optional<String> link = deepLinkService.generateArgoCdLink("orders-run-dev");
        assertTrue(link.isPresent());
        assertEquals("https://argocd.test.example.com/applications/orders-run-dev", link.get());
    }

    @Test
    void generateTektonLinkReturnsUrlWhenConfigured() {
        Optional<String> link = deepLinkService.generateTektonLink("run-abc123");
        assertTrue(link.isPresent());
        assertEquals("https://tekton.test.example.com/#/pipelineruns/run-abc123", link.get());
    }

    @Test
    void generateGrafanaLinkReturnsUrlWhenFullyConfigured() {
        Optional<String> link = deepLinkService.generateGrafanaLink("orders-dev");
        assertTrue(link.isPresent());
        assertEquals("https://grafana.test.example.com/d/test-dashboard-1?var-namespace=orders-dev", link.get());
    }

    @Test
    void generateDevSpacesLinkReturnsUrl() {
        Optional<String> link = deepLinkService.generateDevSpacesLink("https://github.com/team/repo");
        assertTrue(link.isPresent());
        assertEquals("https://devspaces.test.example.com/#/https://github.com/team/repo", link.get());
    }

    @Test
    void generateVaultLinkReturnsTeamScopedUrl() {
        Optional<String> link = deepLinkService.generateVaultLink("payments", "checkout", "dev");
        assertTrue(link.isPresent());
        assertEquals(
                "https://vault.test.example.com/ui/vault/secrets/applications/payments/payments-checkout-dev/static-secrets",
                link.get());
    }

    @Test
    void generateArgoCdLinkHandlesSpecialCharacters() {
        Optional<String> link = deepLinkService.generateArgoCdLink("my-app-with-dashes-123");
        assertTrue(link.isPresent());
        assertTrue(link.get().endsWith("/applications/my-app-with-dashes-123"));
    }

    @Test
    void generateVaultLinkComposesPathCorrectly() {
        Optional<String> link = deepLinkService.generateVaultLink("team-a", "svc-b", "staging");
        assertTrue(link.isPresent());
        assertTrue(link.get().contains("/applications/team-a/team-a-svc-b-staging/static-secrets"));
    }

    @Test
    void generateArgoCdLinkReturnsEmptyWhenUrlIsBlank() {
        DeepLinkConfig emptyConfig = new DeepLinkConfig();
        emptyConfig.argocdUrl = "";
        emptyConfig.tektonDashboardUrl = Optional.empty();
        emptyConfig.grafanaUrl = Optional.empty();
        emptyConfig.grafanaDashboardId = Optional.empty();
        emptyConfig.devspacesUrl = Optional.empty();
        emptyConfig.vaultUrl = Optional.empty();

        DeepLinkService svc = new DeepLinkService();
        svc.config = emptyConfig;

        assertTrue(svc.generateArgoCdLink("my-app").isEmpty());
    }

    @Test
    void generateArgoCdLinkReturnsEmptyWhenUrlIsNull() {
        DeepLinkConfig emptyConfig = new DeepLinkConfig();
        emptyConfig.argocdUrl = null;
        emptyConfig.tektonDashboardUrl = Optional.empty();
        emptyConfig.grafanaUrl = Optional.empty();
        emptyConfig.grafanaDashboardId = Optional.empty();
        emptyConfig.devspacesUrl = Optional.empty();
        emptyConfig.vaultUrl = Optional.empty();

        DeepLinkService svc = new DeepLinkService();
        svc.config = emptyConfig;

        assertTrue(svc.generateArgoCdLink("my-app").isEmpty());
    }

    @Test
    void generateDevSpacesLinkReturnsEmptyWhenNotConfigured() {
        DeepLinkConfig emptyConfig = new DeepLinkConfig();
        emptyConfig.argocdUrl = "https://argocd.example.com";
        emptyConfig.tektonDashboardUrl = Optional.empty();
        emptyConfig.grafanaUrl = Optional.empty();
        emptyConfig.grafanaDashboardId = Optional.empty();
        emptyConfig.devspacesUrl = Optional.empty();
        emptyConfig.vaultUrl = Optional.empty();

        DeepLinkService svc = new DeepLinkService();
        svc.config = emptyConfig;

        assertTrue(svc.generateDevSpacesLink("https://github.com/team/repo").isEmpty());
    }

    @Test
    void generateVaultLinkReturnsEmptyWhenNotConfigured() {
        DeepLinkConfig emptyConfig = new DeepLinkConfig();
        emptyConfig.argocdUrl = "https://argocd.example.com";
        emptyConfig.tektonDashboardUrl = Optional.empty();
        emptyConfig.grafanaUrl = Optional.empty();
        emptyConfig.grafanaDashboardId = Optional.empty();
        emptyConfig.devspacesUrl = Optional.empty();
        emptyConfig.vaultUrl = Optional.empty();

        DeepLinkService svc = new DeepLinkService();
        svc.config = emptyConfig;

        assertTrue(svc.generateVaultLink("team", "app", "dev").isEmpty());
    }

    @Test
    void generateDevSpacesLinkPreservesSpecialCharsInRepoUrl() {
        Optional<String> link = deepLinkService.generateDevSpacesLink(
                "https://github.com/org/my-app_v2.git");
        assertTrue(link.isPresent());
        assertEquals("https://devspaces.test.example.com/#/https://github.com/org/my-app_v2.git",
                link.get());
    }

    @Test
    void trailingSlashOnBaseUrlIsNormalized() {
        // DeepLinkService strips trailing slashes before concatenation.
        // This test verifies no double slashes appear in the generated URL.
        // All test URLs from application.properties lack trailing slashes,
        // so we use a separate plain-unit test with controlled config.
        DeepLinkConfig trailingConfig = new DeepLinkConfig();
        trailingConfig.argocdUrl = "https://argocd.example.com/";
        trailingConfig.tektonDashboardUrl = Optional.of("https://tekton.example.com/");
        trailingConfig.grafanaUrl = Optional.of("https://grafana.example.com/");
        trailingConfig.grafanaDashboardId = Optional.of("dash-1");
        trailingConfig.devspacesUrl = Optional.of("https://devspaces.example.com/");
        trailingConfig.vaultUrl = Optional.of("https://vault.example.com/");

        DeepLinkService svc = new DeepLinkService();
        svc.config = trailingConfig;

        assertEquals("https://argocd.example.com/applications/my-app",
                svc.generateArgoCdLink("my-app").orElseThrow());
        assertEquals("https://tekton.example.com/#/pipelineruns/run-1",
                svc.generateTektonLink("run-1").orElseThrow());
        assertEquals("https://grafana.example.com/d/dash-1?var-namespace=ns",
                svc.generateGrafanaLink("ns").orElseThrow());
        assertEquals("https://devspaces.example.com/#/https://github.com/org/repo",
                svc.generateDevSpacesLink("https://github.com/org/repo").orElseThrow());
        assertEquals("https://vault.example.com/ui/vault/secrets/applications/t/t-a-e/static-secrets",
                svc.generateVaultLink("t", "a", "e").orElseThrow());
    }
}
