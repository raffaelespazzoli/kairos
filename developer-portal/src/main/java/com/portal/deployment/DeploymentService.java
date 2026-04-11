package com.portal.deployment;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.deeplink.DeepLinkService;
import com.portal.environment.Environment;
import com.portal.environment.EnvironmentStatusDto;
import com.portal.environment.PortalEnvironmentStatus;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.argocd.ArgoCdAdapter;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.GitProviderConfig;
import com.portal.integration.git.model.GitCommit;
import com.portal.auth.PortalAuthorizationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DeploymentService {

    private static final Logger LOG = Logger.getLogger(DeploymentService.class);
    private static final int MAX_COMMITS_PER_ENV = 25;
    private static final int MAX_COMMITS_ALL_ENVS = 50;

    @Inject
    GitProvider gitProvider;

    @Inject
    TeamContext teamContext;

    @Inject
    GitProviderConfig gitConfig;

    @Inject
    ArgoCdAdapter argoCdAdapter;

    @Inject
    DeepLinkService deepLinkService;

    public DeploymentStatusDto deployRelease(Long teamId, Long appId, DeployRequest request) {
        Application app = requireTeamApplication(teamId, appId);
        Environment env = requireApplicationEnvironment(appId, request.environmentId());

        if (Boolean.TRUE.equals(env.isProduction)) {
            String role = teamContext.getRole();
            if (!"lead".equals(role) && !"admin".equals(role)) {
                throw new PortalAuthorizationException(role, "deployments", "deploy-prod");
            }
        }

        String envName = env.name.toLowerCase();
        String valuesPath = ".helm/run/values-run-" + envName + ".yaml";

        String defaultBranch = gitConfig.defaultBranch();
        String currentYaml;
        try {
            currentYaml = gitProvider.readFile(app.gitRepoUrl, defaultBranch, valuesPath);
        } catch (PortalIntegrationException e) {
            throw new PortalIntegrationException("git", "deploy-release",
                    "Deployment to " + envName + " failed — could not read values file from repository",
                    null, e);
        }

        String updatedYaml = updateImageTag(currentYaml, request.releaseVersion());

        String commitMessage = "deploy: " + request.releaseVersion() + " to " + envName
                + "\n\nDeployed-By: " + teamContext.getTeamIdentifier();

        try {
            gitProvider.commitFiles(app.gitRepoUrl, defaultBranch,
                    Map.of(valuesPath, updatedYaml), commitMessage);
        } catch (PortalIntegrationException e) {
            throw new PortalIntegrationException("git", "deploy-release",
                    "Deployment to " + envName + " failed — could not commit to repository",
                    null, e);
        }

        return new DeploymentStatusDto(
                UUID.randomUUID().toString(),
                request.releaseVersion(),
                env.name,
                "Deploying",
                Instant.now()
        );
    }

    String updateImageTag(String yamlContent, String newTag) {
        Yaml yaml = new Yaml();
        Map<String, Object> values;
        try {
            values = yaml.load(yamlContent);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "values file contains invalid YAML — cannot deploy: " + e.getMessage(), e);
        }

        if (values == null) {
            throw new IllegalArgumentException(
                    "values file is empty — cannot deploy");
        }

        Object imageSection = values.get("image");
        if (!(imageSection instanceof Map)) {
            throw new IllegalArgumentException(
                    "values file missing 'image' section — cannot deploy");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> image = (Map<String, Object>) imageSection;

        if (!image.containsKey("tag")) {
            throw new IllegalArgumentException(
                    "values file missing 'image.tag' key — cannot deploy");
        }

        image.put("tag", newTag);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml dumper = new Yaml(options);
        return dumper.dump(values);
    }

    public List<DeploymentHistoryDto> listDeployments(Long teamId, Long appId, Long environmentId) {
        Application app = requireTeamApplication(teamId, appId);

        if (environmentId != null) {
            Environment env = requireApplicationEnvironment(appId, environmentId);
            return listDeploymentsForEnvironment(app, env, false);
        }

        List<Environment> environments = Environment.findByApplicationOrderByPromotionOrder(app.id);
        List<DeploymentHistoryDto> allDeployments = new ArrayList<>();
        for (Environment env : environments) {
            allDeployments.addAll(listDeploymentsForEnvironment(app, env, true));
        }
        allDeployments.sort(Comparator.comparing(DeploymentHistoryDto::startedAt).reversed());
        if (allDeployments.size() > MAX_COMMITS_ALL_ENVS) {
            return allDeployments.subList(0, MAX_COMMITS_ALL_ENVS);
        }
        return allDeployments;
    }

    private List<DeploymentHistoryDto> listDeploymentsForEnvironment(Application app, Environment env,
                                                                      boolean swallowGitErrors) {
        String envName = env.name.toLowerCase();
        String valuesPath = ".helm/run/values-run-" + envName + ".yaml";
        String argocdAppName = app.name + "-run-" + envName;
        String argocdLink = deepLinkService.generateArgoCdLink(argocdAppName).orElse(null);

        List<GitCommit> commits;
        try {
            commits = gitProvider.listCommits(app.gitRepoUrl, valuesPath, MAX_COMMITS_PER_ENV);
        } catch (PortalIntegrationException e) {
            if (swallowGitErrors) {
                LOG.warnf("Failed to fetch deployment history from Git for %s/%s: %s",
                        app.name, envName, e.getMessage());
                return List.of();
            }
            throw new PortalIntegrationException("git", "list-deployments",
                    "Deployment history unavailable — could not read commit history for " + envName,
                    null, e);
        }

        List<DeploymentHistoryDto> deployments = new ArrayList<>();
        for (GitCommit commit : commits) {
            parseDeployCommit(commit, env.name, argocdLink).ifPresent(deployments::add);
        }

        if (!deployments.isEmpty()) {
            DeploymentHistoryDto latest = deployments.get(0);
            deployments.set(0, enrichLatestDeployment(latest, app, env));
        }

        return deployments;
    }

    Optional<DeploymentHistoryDto> parseDeployCommit(GitCommit commit, String envName, String argocdDeepLink) {
        String subject = commit.message().lines().findFirst().orElse("");

        if (!subject.startsWith("deploy: ")) {
            return Optional.empty();
        }

        String[] parts = subject.substring("deploy: ".length()).split(" to ", 2);
        if (parts.length < 2) {
            return Optional.empty();
        }

        String version = parts[0].trim();
        String deployedBy = extractTrailer(commit.message(), "Deployed-By")
                .orElse(commit.author());

        return Optional.of(new DeploymentHistoryDto(
                commit.sha(),
                version,
                "Deployed",
                commit.timestamp(),
                null,
                deployedBy,
                envName,
                argocdDeepLink
        ));
    }

    private Optional<String> extractTrailer(String message, String trailerKey) {
        String prefix = trailerKey + ": ";
        return message.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst();
    }

    private DeploymentHistoryDto enrichLatestDeployment(DeploymentHistoryDto dto,
                                                         Application app, Environment env) {
        try {
            List<EnvironmentStatusDto> statuses =
                    argoCdAdapter.getEnvironmentStatuses(app.name, List.of(env));
            if (statuses.isEmpty()) {
                return dto;
            }
            EnvironmentStatusDto liveStatus = statuses.get(0);
            return mapLiveStatusToDeployment(dto, liveStatus);
        } catch (PortalIntegrationException e) {
            LOG.warnf("ArgoCD unreachable for enrichment of %s/%s — defaulting to Deploying: %s",
                    app.name, env.name, e.getMessage());
            return new DeploymentHistoryDto(
                    dto.deploymentId(), dto.releaseVersion(), "Deploying",
                    dto.startedAt(), null, dto.deployedBy(),
                    dto.environmentName(), dto.argocdDeepLink());
        }
    }

    private DeploymentHistoryDto mapLiveStatusToDeployment(DeploymentHistoryDto dto,
                                                            EnvironmentStatusDto liveStatus) {
        PortalEnvironmentStatus status = liveStatus.status();
        return switch (status) {
            case HEALTHY -> new DeploymentHistoryDto(
                    dto.deploymentId(), dto.releaseVersion(), "Deployed",
                    dto.startedAt(), liveStatus.lastDeployedAt(), dto.deployedBy(),
                    dto.environmentName(), dto.argocdDeepLink());
            case UNHEALTHY -> new DeploymentHistoryDto(
                    dto.deploymentId(), dto.releaseVersion(), "Failed",
                    dto.startedAt(), liveStatus.lastDeployedAt(), dto.deployedBy(),
                    dto.environmentName(), dto.argocdDeepLink());
            case DEPLOYING, NOT_DEPLOYED -> new DeploymentHistoryDto(
                    dto.deploymentId(), dto.releaseVersion(), "Deploying",
                    dto.startedAt(), null, dto.deployedBy(),
                    dto.environmentName(), dto.argocdDeepLink());
        };
    }

    private Application requireTeamApplication(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }
        return app;
    }

    private Environment requireApplicationEnvironment(Long appId, Long envId) {
        Environment env = Environment.findById(envId);
        if (env == null || !env.applicationId.equals(appId)) {
            throw new NotFoundException();
        }
        return env;
    }
}
