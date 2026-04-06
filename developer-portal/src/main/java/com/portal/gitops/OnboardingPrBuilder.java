package com.portal.gitops;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.GitProviderConfig;
import com.portal.integration.git.model.PullRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the three-step onboarding PR workflow:
 * create branch → commit manifests → create pull request.
 */
@ApplicationScoped
public class OnboardingPrBuilder {

    @Inject
    GitProvider gitProvider;

    @Inject
    GitProviderConfig gitConfig;

    /**
     * Creates an onboarding PR in the infra repo with all generated manifests.
     *
     * @param teamName the team identifier (used in branch naming)
     * @param appName  the application name (used in branch naming and PR title)
     * @param manifests map of filePath → yamlContent to commit
     * @return the created PullRequest with URL, number, and title
     * @throws PortalIntegrationException if any Git operation fails
     */
    public PullRequest createOnboardingPr(String teamName, String appName,
                                           Map<String, String> manifests) {
        String infraRepoUrl = gitConfig.infraRepoUrl();
        String branchName = "onboard/" + teamName + "-" + appName;
        String commitMessage = "Onboard " + teamName + "/" + appName;

        int namespaceCount = (int) manifests.keySet().stream()
                .filter(k -> k.endsWith("/namespace.yaml")).count();
        int argoAppCount = manifests.size() - namespaceCount;

        String prTitle = String.format("Onboard %s/%s — %d namespaces, %d ArgoCD applications",
                teamName, appName, namespaceCount, argoAppCount);

        String prDescription = "## Resources Created\n\n"
                + manifests.keySet().stream()
                    .sorted()
                    .map(path -> "- `" + path + "`")
                    .collect(Collectors.joining("\n"));

        try {
            gitProvider.createBranch(infraRepoUrl, branchName, "main");
        } catch (PortalIntegrationException e) {
            throw new PortalIntegrationException("git", "createOnboardingPr",
                    "Could not create branch in infrastructure repository — the Git server returned an error",
                    null, e);
        }

        try {
            gitProvider.commitFiles(infraRepoUrl, branchName, manifests, commitMessage);
        } catch (PortalIntegrationException e) {
            throw new PortalIntegrationException("git", "createOnboardingPr",
                    "Failed to commit manifest files — check that the portal has write access to the infrastructure repository",
                    null, e);
        }

        try {
            return gitProvider.createPullRequest(infraRepoUrl, branchName, "main", prTitle, prDescription);
        } catch (PortalIntegrationException e) {
            throw new PortalIntegrationException("git", "createOnboardingPr",
                    "Pull request creation failed — a branch with this name may already exist",
                    null, e);
        }
    }
}
