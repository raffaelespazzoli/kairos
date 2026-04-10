package com.portal.integration.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.model.GitCommit;
import com.portal.integration.git.model.GitTag;
import com.portal.integration.git.model.PullRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GitLab REST API v4 implementation of {@link GitProvider}.
 * Uses PRIVATE-TOKEN header authentication.
 */
public class GitLabProvider implements GitProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final GitProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiBase;

    public GitLabProvider(GitProviderConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.apiBase = config.apiUrl().orElse("https://gitlab.com/api/v4");
    }

    @Override
    public void validateRepoAccess(String repoUrl) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath;
        sendGet(url, "validateRepoAccess", "validate access to " + repoUrl);
    }

    @Override
    public String readFile(String repoUrl, String branch, String filePath) {
        String encodedPath = parseProjectPath(repoUrl);
        String encodedFilePath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        String url = apiBase + "/projects/" + encodedPath
                + "/repository/files/" + encodedFilePath + "/raw?ref=" + urlEncode(branch);
        return sendGet(url, "readFile", "read file " + filePath);
    }

    @Override
    public List<String> listDirectory(String repoUrl, String branch, String dirPath) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath
                + "/repository/tree?path=" + urlEncode(dirPath) + "&ref=" + urlEncode(branch);
        String body = sendGet(url, "listDirectory", "list directory " + dirPath);
        try {
            JsonNode json = objectMapper.readTree(body);
            List<String> names = new ArrayList<>();
            if (json.isArray()) {
                for (JsonNode item : json) {
                    names.add(item.get("name").asText());
                }
            }
            return names;
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "listDirectory",
                    "Failed to parse directory listing for " + dirPath + ": " + e.getMessage(), null, e);
        }
    }

    @Override
    public void createBranch(String repoUrl, String branchName, String fromBranch) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath
                + "/repository/branches?branch=" + urlEncode(branchName) + "&ref=" + urlEncode(fromBranch);
        sendPost(url, "", "createBranch", "create branch " + branchName);
    }

    @Override
    public void commitFiles(String repoUrl, String branch, Map<String, String> files, String message) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath + "/repository/commits";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("branch", branch);
        payload.put("commit_message", message);

        // "create" action per story spec — onboarding always writes new files to a fresh branch.
        // If future stories need idempotent upsert, change action to "update" for existing files.
        ArrayNode actions = payload.putArray("actions");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            ObjectNode action = objectMapper.createObjectNode();
            action.put("action", "create");
            action.put("file_path", entry.getKey());
            action.put("content", entry.getValue());
            actions.add(action);
        }

        sendPost(url, payload.toString(), "commitFiles", "commit files to " + branch);
    }

    @Override
    public List<GitTag> listTags(String repoUrl, int maxResults) {
        String encodedPath = parseProjectPath(repoUrl);
        String baseUrl = apiBase + "/projects/" + encodedPath + "/repository/tags";
        int perPage = Math.min(maxResults, 100);
        List<GitTag> allTags = new ArrayList<>();
        int page = 1;

        while (allTags.size() < maxResults) {
            String url = baseUrl + "?per_page=" + perPage + "&page=" + page;
            String body = sendGet(url, "list-tags", "list tags for " + repoUrl);
            try {
                JsonNode json = objectMapper.readTree(body);
                if (!json.isArray() || json.isEmpty()) break;

                int pageCount = 0;
                for (JsonNode item : json) {
                    if (allTags.size() >= maxResults) break;
                    String name = item.get("name").asText();
                    String sha = item.get("commit").get("id").asText();
                    String dateStr = item.get("commit").get("committed_date").asText();
                    allTags.add(new GitTag(name, sha, Instant.parse(dateStr)));
                    pageCount++;
                }

                if (pageCount < perPage) break;
                page++;
            } catch (PortalIntegrationException e) {
                throw e;
            } catch (Exception e) {
                throw new PortalIntegrationException("git", "list-tags",
                        "Failed to parse tags response: " + e.getMessage(), null, e);
            }
        }
        return allTags;
    }

    @Override
    public List<GitCommit> listCommits(String repoUrl, String filePath, int maxResults) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath
                + "/repository/commits?path=" + urlEncode(filePath) + "&per_page=" + Math.min(maxResults, 100);
        String body = sendGet(url, "listCommits", "list commits for " + filePath);
        try {
            JsonNode json = objectMapper.readTree(body);
            List<GitCommit> commits = new ArrayList<>();
            if (json.isArray()) {
                for (JsonNode item : json) {
                    if (commits.size() >= maxResults) break;
                    String sha = item.get("id").asText();
                    String author = item.get("author_name").asText();
                    Instant timestamp = Instant.parse(item.get("committed_date").asText());
                    String message = item.get("message").asText();
                    commits.add(new GitCommit(sha, author, timestamp, message));
                }
            }
            return commits;
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "listCommits",
                    "Failed to parse commits response: " + e.getMessage(), null, e);
        }
    }

    @Override
    public void createTag(String repoUrl, String commitSha, String tagName) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath + "/repository/tags";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("tag_name", tagName);
        payload.put("ref", commitSha);

        try {
            sendPost(url, payload.toString(), "createTag", "create tag " + tagName);
        } catch (PortalIntegrationException e) {
            if (e.getMessage().contains("400")) {
                throw new PortalIntegrationException("git", "createTag",
                        "Release tag already exists \u2014 choose a different version");
            }
            throw e;
        }
    }

    @Override
    public PullRequest createPullRequest(String repoUrl, String branch, String targetBranch,
                                         String title, String description) {
        String encodedPath = parseProjectPath(repoUrl);
        String url = apiBase + "/projects/" + encodedPath + "/merge_requests";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source_branch", branch);
        payload.put("target_branch", targetBranch);
        payload.put("title", title);
        payload.put("description", description);

        String body = sendPost(url, payload.toString(), "createPullRequest",
                "create merge request for " + branch + " -> " + targetBranch);
        try {
            JsonNode json = objectMapper.readTree(body);
            return new PullRequest(
                    json.get("web_url").asText(),
                    json.get("iid").asInt(),
                    json.get("title").asText());
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "createPullRequest",
                    "Failed to parse MR response: " + e.getMessage(), null, e);
        }
    }

    private String sendGet(String url, String operation, String description) {
        return sendRequest(buildRequest(url).GET().build(), operation, description);
    }

    private String sendPost(String url, String jsonBody, String operation, String description) {
        return sendRequest(buildRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build(), operation, description);
    }

    private HttpRequest.Builder buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("PRIVATE-TOKEN", config.token())
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT);
    }

    private String sendRequest(HttpRequest request, String operation, String description) {
        requireHttps(request.uri(), operation);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortalIntegrationException("git", operation,
                        "Git server returned HTTP " + response.statusCode() + " for " + description);
            }
            return response.body();
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PortalIntegrationException("git", operation,
                    "Failed to " + description + ": " + e.getMessage(), null, e);
        } catch (RuntimeException e) {
            throw new PortalIntegrationException("git", operation,
                    "Failed to " + description + ": " + e.getMessage(), null, e);
        }
    }

    private static void requireHttps(URI uri, String operation) {
        if (!"https".equals(uri.getScheme())) {
            throw new PortalIntegrationException("git", operation,
                    "Git provider requires HTTPS but URL uses " + uri.getScheme());
        }
    }

    /**
     * Extracts the project path from a GitLab URL and URL-encodes it.
     * Handles subgroups (e.g., {@code gitlab.com/group/sub/repo} -> {@code group%2Fsub%2Frepo}).
     */
    static String parseProjectPath(String repoUrl) {
        String cleaned = repoUrl.replaceAll("\\.git$", "");
        try {
            URI uri = URI.create(cleaned);
            String path = uri.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            if (path.isEmpty()) {
                throw new IllegalArgumentException("URL has no project path: " + repoUrl);
            }
            return URLEncoder.encode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new PortalIntegrationException("git", "parseUrl",
                    "Invalid repository URL: " + repoUrl + " — " + e.getMessage(), null, e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
