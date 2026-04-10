package com.portal.deployment;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.environment.Environment;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.GitProviderConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DeploymentService {

    @Inject
    GitProvider gitProvider;

    @Inject
    TeamContext teamContext;

    @Inject
    GitProviderConfig gitConfig;

    public DeploymentStatusDto deployRelease(Long teamId, Long appId, DeployRequest request) {
        Application app = requireTeamApplication(teamId, appId);
        Environment env = requireApplicationEnvironment(appId, request.environmentId());

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
