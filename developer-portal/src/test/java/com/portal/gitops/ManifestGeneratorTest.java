package com.portal.gitops;

import com.portal.onboarding.OnboardingPlanResult;
import com.portal.onboarding.PlannedArgoCdApp;
import com.portal.onboarding.PlannedNamespace;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ManifestGeneratorTest {

    @Inject
    ManifestGenerator manifestGenerator;

    @Test
    void generateNamespaceYamlContainsLabels() {
        String yaml = manifestGenerator.generateNamespaceYaml(
                "payments-payment-svc-dev", "payments", "payment-svc", "dev");

        assertAll(
                () -> assertTrue(yaml.contains("name: payments-payment-svc-dev")),
                () -> assertTrue(yaml.contains("team: payments")),
                () -> assertTrue(yaml.contains("app: payment-svc")),
                () -> assertTrue(yaml.contains("env: dev")),
                () -> assertTrue(yaml.contains("size: default"))
        );
    }

    @Test
    void generateNamespaceYamlIsValidKubernetesResource() {
        String yaml = manifestGenerator.generateNamespaceYaml(
                "team-app-qa", "team", "app", "qa");

        assertTrue(yaml.contains("apiVersion: v1"));
        assertTrue(yaml.contains("kind: Namespace"));
    }

    @Test
    void generateBuildArgoCdAppYamlReferencesCorrectPaths() {
        String yaml = manifestGenerator.generateBuildArgoCdAppYaml(
                "payment-svc", "payments-payment-svc-build",
                "https://api.ocp-dev-01:6443", "https://github.com/team/app");

        assertAll(
                () -> assertTrue(yaml.contains("name: payment-svc-build")),
                () -> assertTrue(yaml.contains("path: .helm/build")),
                () -> assertTrue(yaml.contains("values-build.yaml")),
                () -> assertTrue(yaml.contains("namespace: payments-payment-svc-build")),
                () -> assertTrue(yaml.contains("server: https://api.ocp-dev-01:6443")),
                () -> assertTrue(yaml.contains("repoURL: https://github.com/team/app"))
        );
    }

    @Test
    void generateBuildArgoCdAppYamlIsValidArgoCdResource() {
        String yaml = manifestGenerator.generateBuildArgoCdAppYaml(
                "my-app", "ns-build", "https://api:6443", "https://git.example.com/repo");

        assertTrue(yaml.contains("apiVersion: argoproj.io/v1alpha1"));
        assertTrue(yaml.contains("kind: Application"));
        assertTrue(yaml.contains("namespace: argocd"));
    }

    @Test
    void generateRunArgoCdAppYamlReferencesCorrectPaths() {
        String yaml = manifestGenerator.generateRunArgoCdAppYaml(
                "payment-svc", "payments-payment-svc-dev",
                "https://api.ocp-dev-01:6443", "https://github.com/team/app", "dev");

        assertAll(
                () -> assertTrue(yaml.contains("name: payment-svc-run-dev")),
                () -> assertTrue(yaml.contains("path: .helm/run")),
                () -> assertTrue(yaml.contains("values-run-dev.yaml")),
                () -> assertTrue(yaml.contains("namespace: payments-payment-svc-dev")),
                () -> assertTrue(yaml.contains("server: https://api.ocp-dev-01:6443"))
        );
    }

    @Test
    void generateAllManifestsReturnsCorrectFilePaths() {
        List<PlannedNamespace> namespaces = List.of(
                new PlannedNamespace("team-app-build", "ocp-dev-01", "build", true),
                new PlannedNamespace("team-app-dev", "ocp-dev-01", "dev", false),
                new PlannedNamespace("team-app-qa", "ocp-qa-01", "qa", false)
        );
        List<PlannedArgoCdApp> argoCdApps = List.of(
                new PlannedArgoCdApp("app-build", "ocp-dev-01", "team-app-build",
                        ".helm/build", "values-build.yaml", true),
                new PlannedArgoCdApp("app-run-dev", "ocp-dev-01", "team-app-dev",
                        ".helm/run", "values-run-dev.yaml", false),
                new PlannedArgoCdApp("app-run-qa", "ocp-qa-01", "team-app-qa",
                        ".helm/run", "values-run-qa.yaml", false)
        );

        OnboardingPlanResult plan = new OnboardingPlanResult(
                "app", "team", namespaces, argoCdApps, List.of("dev", "qa"), Map.of());

        Map<String, String> clusterApiUrls = Map.of(
                "ocp-dev-01", "https://api.ocp-dev-01:6443",
                "ocp-qa-01", "https://api.ocp-qa-01:6443"
        );

        Map<String, String> manifests = manifestGenerator.generateAllManifests(
                plan, "https://github.com/team/app", clusterApiUrls);

        assertTrue(manifests.containsKey("ocp-dev-01/team-app-build/namespace.yaml"));
        assertTrue(manifests.containsKey("ocp-dev-01/team-app-dev/namespace.yaml"));
        assertTrue(manifests.containsKey("ocp-qa-01/team-app-qa/namespace.yaml"));
        assertTrue(manifests.containsKey("ocp-dev-01/team-app-build/argocd-app-build.yaml"));
        assertTrue(manifests.containsKey("ocp-dev-01/team-app-dev/argocd-app-run-dev.yaml"));
        assertTrue(manifests.containsKey("ocp-qa-01/team-app-qa/argocd-app-run-qa.yaml"));
        assertEquals(6, manifests.size());
    }

    @Test
    void generateAllManifestsUsesClusterApiUrlInArgoCdApps() {
        List<PlannedNamespace> namespaces = List.of(
                new PlannedNamespace("t-a-dev", "ocp-dev-01", "dev", false)
        );
        List<PlannedArgoCdApp> argoCdApps = List.of(
                new PlannedArgoCdApp("a-run-dev", "ocp-dev-01", "t-a-dev",
                        ".helm/run", "values-run-dev.yaml", false)
        );
        OnboardingPlanResult plan = new OnboardingPlanResult(
                "a", "t", namespaces, argoCdApps, List.of("dev"), Map.of());

        Map<String, String> clusterApiUrls = Map.of("ocp-dev-01", "https://api.ocp-dev-01:6443");

        Map<String, String> manifests = manifestGenerator.generateAllManifests(
                plan, "https://github.com/team/app", clusterApiUrls);

        String argoCdYaml = manifests.get("ocp-dev-01/t-a-dev/argocd-app-run-dev.yaml");
        assertNotNull(argoCdYaml);
        assertTrue(argoCdYaml.contains("server: https://api.ocp-dev-01:6443"));
    }

    @Test
    void generatedYamlIsWellFormed() {
        String nsYaml = manifestGenerator.generateNamespaceYaml("ns", "t", "a", "dev");
        assertFalse(nsYaml.isBlank());
        assertTrue(nsYaml.lines().allMatch(line ->
                !line.endsWith(" ") || line.isBlank()
        ));

        String buildYaml = manifestGenerator.generateBuildArgoCdAppYaml(
                "app", "ns", "https://api:6443", "https://git/repo");
        assertFalse(buildYaml.isBlank());
        assertTrue(buildYaml.contains("apiVersion:"));

        String runYaml = manifestGenerator.generateRunArgoCdAppYaml(
                "app", "ns", "https://api:6443", "https://git/repo", "qa");
        assertFalse(runYaml.isBlank());
        assertTrue(runYaml.contains("apiVersion:"));
    }
}
