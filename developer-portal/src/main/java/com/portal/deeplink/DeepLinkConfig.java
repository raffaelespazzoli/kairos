package com.portal.deeplink;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Configuration bean for deep link URL generation.
 * Each property maps to a native platform tool's base URL.
 * Optional properties return empty when the tool is not deployed.
 */
@ApplicationScoped
public class DeepLinkConfig {

    @ConfigProperty(name = "portal.argocd.url")
    String argocdUrl;

    @ConfigProperty(name = "portal.tekton.dashboard-url")
    Optional<String> tektonDashboardUrl;

    @ConfigProperty(name = "portal.grafana.url")
    Optional<String> grafanaUrl;

    @ConfigProperty(name = "portal.grafana.dashboard-id")
    Optional<String> grafanaDashboardId;

    @ConfigProperty(name = "portal.devspaces.url")
    Optional<String> devspacesUrl;

    @ConfigProperty(name = "portal.vault.url")
    Optional<String> vaultUrl;

    public String argocdUrl() {
        return argocdUrl;
    }

    public Optional<String> tektonDashboardUrl() {
        return tektonDashboardUrl;
    }

    public Optional<String> grafanaUrl() {
        return grafanaUrl;
    }

    public Optional<String> grafanaDashboardId() {
        return grafanaDashboardId;
    }

    public Optional<String> devspacesUrl() {
        return devspacesUrl;
    }

    public Optional<String> vaultUrl() {
        return vaultUrl;
    }
}
