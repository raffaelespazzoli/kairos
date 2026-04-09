package com.portal.release;

import com.portal.application.Application;
import com.portal.build.BuildDetailDto;
import com.portal.build.BuildService;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.model.GitTag;
import com.portal.integration.registry.RegistryAdapter;
import com.portal.integration.registry.RegistryConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class ReleaseService {

    private static final Logger LOG = Logger.getLogger(ReleaseService.class);

    @Inject
    BuildService buildService;

    @Inject
    GitProvider gitProvider;

    @Inject
    RegistryAdapter registryAdapter;

    @Inject
    RegistryConfig registryConfig;

    @ConfigProperty(name = "portal.releases.max-tags", defaultValue = "50")
    int maxTags;

    public List<ReleaseSummaryDto> listReleases(Long teamId, Long appId) {
        Application app = resolveTeamApplication(teamId, appId);

        List<GitTag> tags = gitProvider.listTags(app.gitRepoUrl, maxTags);

        return tags.stream()
                .sorted(Comparator.comparing(GitTag::createdAt).reversed())
                .map(tag -> new ReleaseSummaryDto(
                        tag.name(),
                        tag.createdAt(),
                        null,
                        tag.commitSha(),
                        buildImageReference(tag.name())))
                .toList();
    }

    private String buildImageReference(String version) {
        return registryConfig.url()
                .map(url -> url + ":" + version)
                .orElse(null);
    }

    public ReleaseSummaryDto createRelease(Long teamId, Long appId,
                                           CreateReleaseRequest request) {
        Application app = resolveTeamApplication(teamId, appId);

        BuildDetailDto build = buildService.getBuildDetail(teamId, appId, request.buildId());

        if (!"Passed".equals(build.status())) {
            throw new IllegalArgumentException(
                    "Cannot create release from build with status: " + build.status());
        }

        if (build.imageReference() == null) {
            throw new IllegalArgumentException(
                    "Cannot create release from build without a container image artifact");
        }

        createTagIdempotent(app.gitRepoUrl, build.commitSha(), request.version());

        registryAdapter.tagImage(build.imageReference(), request.version());

        return new ReleaseSummaryDto(
                request.version(),
                Instant.now(),
                request.buildId(),
                build.commitSha(),
                build.imageReference());
    }

    /**
     * Creates a Git tag, treating "tag already exists" as a no-op so that
     * a retry after a partial failure (git tag created, registry tag failed)
     * can proceed without error.
     */
    private void createTagIdempotent(String repoUrl, String commitSha, String version) {
        try {
            gitProvider.createTag(repoUrl, commitSha, version);
        } catch (PortalIntegrationException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                LOG.infof("Git tag %s already exists — treating as idempotent retry", version);
                return;
            }
            throw e;
        }
    }

    private Application resolveTeamApplication(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }
        return app;
    }
}
