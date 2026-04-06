package com.portal.integration.git;

import com.portal.integration.git.model.PullRequest;

import java.util.List;
import java.util.Map;

/**
 * No-op Git provider for development and testing when a real Git server is unavailable.
 * Returns canned responses for all operations. Activated when
 * {@code portal.git.provider=dev} is configured.
 */
public class DevGitProvider implements GitProvider {

    @Override
    public void validateRepoAccess(String repoUrl) {
        // always succeeds
    }

    @Override
    public String readFile(String repoUrl, String branch, String filePath) {
        return "";
    }

    @Override
    public List<String> listDirectory(String repoUrl, String branch, String dirPath) {
        return List.of();
    }

    @Override
    public void createBranch(String repoUrl, String branchName, String fromBranch) {
        // no-op
    }

    @Override
    public void commitFiles(String repoUrl, String branch, Map<String, String> files, String message) {
        // no-op
    }

    @Override
    public PullRequest createPullRequest(String repoUrl, String branch, String targetBranch,
                                         String title, String description) {
        return new PullRequest("https://dev-git/pr/1", 1, title);
    }
}
