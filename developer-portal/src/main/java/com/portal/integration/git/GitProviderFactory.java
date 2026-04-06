package com.portal.integration.git;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

/**
 * CDI factory that produces the active {@link GitProvider} bean based on the
 * {@code portal.git.provider} runtime configuration. Unlike the secret manager's
 * build-time selection, this factory enables a single artifact to target different
 * Git platforms via environment variable override.
 */
@ApplicationScoped
public class GitProviderFactory {

    @Inject
    GitProviderConfig config;

    @Produces
    @ApplicationScoped
    public GitProvider gitProvider() {
        String provider = config.provider().toLowerCase(Locale.ROOT);
        if ("dev".equals(provider)) {
            return new DevGitProvider();
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return switch (provider) {
            case "github" -> new GitHubProvider(config, httpClient);
            case "gitlab" -> new GitLabProvider(config, httpClient);
            case "gitea" -> new GiteaProvider(config, httpClient);
            case "bitbucket" -> new BitbucketProvider(config, httpClient);
            default -> throw new IllegalArgumentException(
                    "Unknown git provider: " + config.provider()
                            + ". Supported: github, gitlab, gitea, bitbucket, dev");
        };
    }
}
