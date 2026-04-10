package com.portal.integration.git;

import com.portal.integration.git.model.GitCommit;
import com.portal.integration.git.model.GitTag;
import com.portal.integration.git.model.PullRequest;

import java.time.Instant;
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
    public List<GitCommit> listCommits(String repoUrl, String filePath, int maxResults) {
        String envName = filePath.replaceAll(".*values-run-([\\w-]+)\\.yaml", "$1");
        List<GitCommit> all = List.of(
            new GitCommit("abc123def456abc123def456abc123def456abc1",
                    "dev-user", Instant.parse("2026-04-09T15:00:00Z"),
                    "deploy: v1.2.0 to " + envName + "\n\nDeployed-By: marco"),
            new GitCommit("bcd234efg567bcd234efg567bcd234efg567bcd2",
                    "dev-user", Instant.parse("2026-04-08T12:00:00Z"),
                    "deploy: v1.1.0 to " + envName + "\n\nDeployed-By: anna"),
            new GitCommit("cde345fgh678cde345fgh678cde345fgh678cde3",
                    "dev-user", Instant.parse("2026-04-07T09:00:00Z"),
                    "deploy: v1.0.0 to " + envName + "\n\nDeployed-By: marco")
        );
        return all.subList(0, Math.min(all.size(), maxResults));
    }

    @Override
    public List<GitTag> listTags(String repoUrl, int maxResults) {
        List<GitTag> all = List.of(
            new GitTag("v1.2.0", "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", Instant.parse("2026-04-07T10:00:00Z")),
            new GitTag("v1.1.0", "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3", Instant.parse("2026-04-05T14:30:00Z")),
            new GitTag("v1.0.0", "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", Instant.parse("2026-04-01T09:00:00Z"))
        );
        return all.subList(0, Math.min(all.size(), maxResults));
    }

    @Override
    public void createTag(String repoUrl, String commitSha, String tagName) {
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
