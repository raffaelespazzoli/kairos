package com.portal.onboarding;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that an application repository meets the portal's GitOps contract.
 * Runs 5 independent checks; a failure in one does not abort the remaining checks.
 */
@ApplicationScoped
public class ContractValidator {

    private static final String DEFAULT_BRANCH = "main";

    @Inject
    GitProvider gitProvider;

    private record EnvironmentCheckResult(ContractCheck check, List<String> environmentNames) {}

    private record RuntimeCheckResult(ContractCheck check, String runtimeType) {}

    public ContractValidationResult validate(String repoUrl) {
        List<ContractCheck> checks = new ArrayList<>();

        checks.add(checkHelmBuildChart(repoUrl));
        checks.add(checkHelmRunChart(repoUrl));
        checks.add(checkBuildValues(repoUrl));

        EnvironmentCheckResult envResult = checkEnvironmentValues(repoUrl);
        checks.add(envResult.check());

        RuntimeCheckResult runtimeResult = checkRuntimeDetection(repoUrl);
        checks.add(runtimeResult.check());

        boolean allPassed = checks.stream().allMatch(ContractCheck::passed);

        return new ContractValidationResult(allPassed, checks,
                runtimeResult.runtimeType(), envResult.environmentNames());
    }

    private ContractCheck checkHelmBuildChart(String repoUrl) {
        try {
            List<String> files = gitProvider.listDirectory(repoUrl, DEFAULT_BRANCH, ".helm/build");
            if (files.contains("Chart.yaml")) {
                return new ContractCheck("Helm Build Chart", true, "Helm build chart found", null);
            }
            return new ContractCheck("Helm Build Chart", false, "Chart.yaml not found in .helm/build/",
                    "Create `.helm/build/` directory with a valid Chart.yaml");
        } catch (PortalIntegrationException e) {
            return new ContractCheck("Helm Build Chart", false, "Directory .helm/build/ not accessible",
                    "Create `.helm/build/` directory with a valid Chart.yaml");
        }
    }

    private ContractCheck checkHelmRunChart(String repoUrl) {
        try {
            List<String> files = gitProvider.listDirectory(repoUrl, DEFAULT_BRANCH, ".helm/run");
            if (files.contains("Chart.yaml")) {
                return new ContractCheck("Helm Run Chart", true, "Helm run chart found", null);
            }
            return new ContractCheck("Helm Run Chart", false, "Chart.yaml not found in .helm/run/",
                    "Create `.helm/run/` directory with a valid Chart.yaml");
        } catch (PortalIntegrationException e) {
            return new ContractCheck("Helm Run Chart", false, "Directory .helm/run/ not accessible",
                    "Create `.helm/run/` directory with a valid Chart.yaml");
        }
    }

    private ContractCheck checkBuildValues(String repoUrl) {
        try {
            gitProvider.readFile(repoUrl, DEFAULT_BRANCH, ".helm/values-build.yaml");
            return new ContractCheck("Build Values", true, "Build values file found", null);
        } catch (PortalIntegrationException e) {
            return new ContractCheck("Build Values", false, "values-build.yaml not found in .helm/",
                    "Create `values-build.yaml` in `.helm/` directory");
        }
    }

    private EnvironmentCheckResult checkEnvironmentValues(String repoUrl) {
        try {
            List<String> helmFiles = gitProvider.listDirectory(repoUrl, DEFAULT_BRANCH, ".helm");
            List<String> envNames = helmFiles.stream()
                    .filter(f -> f.startsWith("values-run-") && f.endsWith(".yaml"))
                    .map(f -> f.replace("values-run-", "").replace(".yaml", ""))
                    .toList();
            if (!envNames.isEmpty()) {
                String detail = envNames.size() + " environment(s) detected: " + String.join(", ", envNames);
                return new EnvironmentCheckResult(
                        new ContractCheck("Environment Values", true, detail, null),
                        envNames);
            }
            return new EnvironmentCheckResult(
                    new ContractCheck("Environment Values", false,
                            "No values-run-*.yaml files found in .helm/",
                            "Create at least one `values-run-<env>.yaml` file in `.helm/`"),
                    List.of());
        } catch (PortalIntegrationException e) {
            return new EnvironmentCheckResult(
                    new ContractCheck("Environment Values", false,
                            "Directory .helm/ not accessible",
                            "Create at least one `values-run-<env>.yaml` file in `.helm/`"),
                    List.of());
        }
    }

    private RuntimeCheckResult checkRuntimeDetection(String repoUrl) {
        try {
            List<String> rootFiles = gitProvider.listDirectory(repoUrl, DEFAULT_BRANCH, "");
            if (rootFiles.contains("pom.xml")) {
                return new RuntimeCheckResult(
                        new ContractCheck("Runtime Detection", true,
                                "Runtime Detected: Quarkus/Java via pom.xml", null),
                        "Quarkus/Java");
            }
            if (rootFiles.contains("package.json")) {
                return new RuntimeCheckResult(
                        new ContractCheck("Runtime Detection", true,
                                "Runtime Detected: Node.js via package.json", null),
                        "Node.js");
            }
            if (rootFiles.stream().anyMatch(f -> f.endsWith(".csproj"))) {
                String csprojFile = rootFiles.stream().filter(f -> f.endsWith(".csproj")).findFirst().orElse("*.csproj");
                return new RuntimeCheckResult(
                        new ContractCheck("Runtime Detection", true,
                                "Runtime Detected: .NET via " + csprojFile, null),
                        ".NET");
            }
            return new RuntimeCheckResult(
                    new ContractCheck("Runtime Detection", false,
                            "No supported runtime detected",
                            "No supported runtime detected. Ensure `pom.xml`, `package.json`, or `*.csproj` exists in the repository root"),
                    null);
        } catch (PortalIntegrationException e) {
            return new RuntimeCheckResult(
                    new ContractCheck("Runtime Detection", false,
                            "Repository root not accessible",
                            "No supported runtime detected. Ensure `pom.xml`, `package.json`, or `*.csproj` exists in the repository root"),
                    null);
        }
    }

}
