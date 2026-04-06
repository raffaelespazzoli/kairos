package com.portal.integration.git;

import com.portal.integration.git.model.PullRequest;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for Git hosting platform operations required by the onboarding flow.
 * Implementations handle provider-specific REST API details (GitHub, GitLab, Gitea, Bitbucket).
 * The active implementation is produced at runtime by {@link GitProviderFactory} based on
 * the {@code portal.git.provider} configuration property.
 */
public interface GitProvider {

    /**
     * Validates that the portal can read the given repository.
     *
     * @param repoUrl the HTTPS URL of the repository (e.g., {@code https://github.com/owner/repo})
     * @throws com.portal.integration.PortalIntegrationException if the repository is inaccessible or the token is invalid
     */
    void validateRepoAccess(String repoUrl);

    /**
     * Reads the contents of a file from the repository.
     *
     * @param repoUrl the HTTPS URL of the repository
     * @param branch the branch to read from
     * @param filePath the path to the file within the repository
     * @return the file contents as a string
     * @throws com.portal.integration.PortalIntegrationException if the file cannot be read
     */
    String readFile(String repoUrl, String branch, String filePath);

    /**
     * Lists the names of files and directories at the given path.
     *
     * @param repoUrl the HTTPS URL of the repository
     * @param branch the branch to list from
     * @param dirPath the directory path within the repository
     * @return a list of file/directory names
     * @throws com.portal.integration.PortalIntegrationException if the directory cannot be listed
     */
    List<String> listDirectory(String repoUrl, String branch, String dirPath);

    /**
     * Creates a new branch from an existing branch.
     *
     * @param repoUrl the HTTPS URL of the repository
     * @param branchName the name of the new branch to create
     * @param fromBranch the source branch to branch from
     * @throws com.portal.integration.PortalIntegrationException if the branch cannot be created
     */
    void createBranch(String repoUrl, String branchName, String fromBranch);

    /**
     * Commits multiple files to a branch in a single commit.
     *
     * @param repoUrl the HTTPS URL of the repository
     * @param branch the target branch
     * @param files a map of file paths to file contents
     * @param message the commit message
     * @throws com.portal.integration.PortalIntegrationException if the commit fails
     */
    void commitFiles(String repoUrl, String branch, Map<String, String> files, String message);

    /**
     * Creates a pull request (or merge request) and returns its metadata.
     *
     * @param repoUrl the HTTPS URL of the repository
     * @param branch the source branch containing the changes
     * @param targetBranch the target branch to merge into
     * @param title the pull request title
     * @param description the pull request body/description
     * @return a {@link PullRequest} with the URL, number, and title
     * @throws com.portal.integration.PortalIntegrationException if the PR cannot be created
     */
    PullRequest createPullRequest(String repoUrl, String branch, String targetBranch,
                                  String title, String description);
}
