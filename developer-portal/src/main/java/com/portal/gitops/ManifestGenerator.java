package com.portal.gitops;

import com.portal.onboarding.OnboardingPlanResult;
import com.portal.onboarding.PlannedArgoCdApp;
import com.portal.onboarding.PlannedNamespace;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates Kubernetes and ArgoCD manifests from Qute templates
 * following the infra repo GitOps contract.
 */
@ApplicationScoped
public class ManifestGenerator {

    @Inject
    Engine engine;

    public String generateNamespaceYaml(String namespace, String team, String app, String env) {
        Template template = engine.getTemplate("gitops/namespace.yaml");
        return template
                .data("namespace", namespace)
                .data("team", team)
                .data("app", app)
                .data("env", env)
                .data("size", "default")
                .render();
    }

    public String generateBuildArgoCdAppYaml(String appName, String namespace,
                                              String clusterApiUrl, String gitRepoUrl) {
        Template template = engine.getTemplate("gitops/argocd-app-build.yaml");
        return template
                .data("appName", appName)
                .data("namespace", namespace)
                .data("clusterApiUrl", clusterApiUrl)
                .data("gitRepoUrl", gitRepoUrl)
                .render();
    }

    public String generateRunArgoCdAppYaml(String appName, String namespace,
                                            String clusterApiUrl, String gitRepoUrl, String env) {
        Template template = engine.getTemplate("gitops/argocd-app-run.yaml");
        return template
                .data("appName", appName)
                .data("namespace", namespace)
                .data("clusterApiUrl", clusterApiUrl)
                .data("gitRepoUrl", gitRepoUrl)
                .data("env", env)
                .render();
    }

    /**
     * Generates all manifests for the onboarding plan.
     *
     * @param plan the assembled onboarding plan (PlannedArgoCdApp.clusterName is the display name)
     * @param gitRepoUrl the application git repository URL
     * @param clusterApiUrls mapping from cluster display name to API server URL for ArgoCD destination
     * @return map of filePath → yamlContent following the GitOps contract
     */
    public Map<String, String> generateAllManifests(OnboardingPlanResult plan, String gitRepoUrl,
                                                     Map<String, String> clusterApiUrls) {
        Map<String, String> manifests = new LinkedHashMap<>();

        for (PlannedNamespace ns : plan.namespaces()) {
            String envLabel = ns.isBuild() ? "build" : ns.environmentName();
            String yaml = generateNamespaceYaml(ns.name(), plan.teamName(), plan.appName(), envLabel);
            String path = ns.clusterName() + "/" + ns.name() + "/namespace.yaml";
            manifests.put(path, yaml);
        }

        for (PlannedArgoCdApp app : plan.argoCdApps()) {
            String apiUrl = clusterApiUrls.getOrDefault(app.clusterName(), app.clusterName());
            String yaml;
            String fileName;
            if (app.isBuild()) {
                yaml = generateBuildArgoCdAppYaml(plan.appName(), app.namespace(), apiUrl, gitRepoUrl);
                fileName = "argocd-app-build.yaml";
            } else {
                String env = extractEnvFromValuesFile(app.valuesFile());
                yaml = generateRunArgoCdAppYaml(plan.appName(), app.namespace(), apiUrl, gitRepoUrl, env);
                fileName = "argocd-app-run-" + env + ".yaml";
            }
            String path = app.clusterName() + "/" + app.namespace() + "/" + fileName;
            manifests.put(path, yaml);
        }

        return manifests;
    }

    private String extractEnvFromValuesFile(String valuesFile) {
        return valuesFile
                .replace("values-run-", "")
                .replace(".yaml", "");
    }
}
