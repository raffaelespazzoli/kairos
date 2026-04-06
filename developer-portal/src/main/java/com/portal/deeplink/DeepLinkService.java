package com.portal.deeplink;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Generates deep link URLs to native platform tools.
 * Pure URL string generation — no HTTP calls, no caching.
 * Returns Optional.empty() when a tool's base URL is not configured.
 */
@ApplicationScoped
public class DeepLinkService {

    @Inject
    DeepLinkConfig config;

    public Optional<String> generateArgoCdLink(String argocdAppName) {
        return Optional.of(stripTrailingSlash(config.argocdUrl()) + "/applications/" + argocdAppName);
    }

    public Optional<String> generateTektonLink(String pipelineRunId) {
        return config.tektonDashboardUrl()
                .map(url -> stripTrailingSlash(url) + "/#/pipelineruns/" + pipelineRunId);
    }

    public Optional<String> generateGrafanaLink(String namespace) {
        Optional<String> grafanaUrl = config.grafanaUrl();
        Optional<String> dashboardId = config.grafanaDashboardId();
        if (grafanaUrl.isPresent() && dashboardId.isPresent()) {
            return Optional.of(stripTrailingSlash(grafanaUrl.get()) + "/d/" + dashboardId.get()
                    + "?var-namespace=" + namespace);
        }
        return Optional.empty();
    }

    public Optional<String> generateDevSpacesLink(String gitRepoUrl) {
        return config.devspacesUrl()
                .map(url -> stripTrailingSlash(url) + "/#/" + gitRepoUrl);
    }

    public Optional<String> generateVaultLink(String team, String app, String env) {
        return config.vaultUrl()
                .map(url -> stripTrailingSlash(url) + "/ui/vault/secrets/applications/" + team + "/"
                        + team + "-" + app + "-" + env + "/static-secrets");
    }

    private static String stripTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
