package com.portal.integration.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.model.PullRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GitHub REST API implementation of {@link GitProvider}.
 * Uses the GitHub REST API v3 with Bearer token authentication.
 */
public class GitHubProvider implements GitProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String ACCEPT_HEADER = "application/vnd.github+json";

    private final GitProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiBase;

    public GitHubProvider(GitProviderConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.apiBase = config.apiUrl().orElse("https://api.github.com");
    }

    @Override
    public void validateRepoAccess(String repoUrl) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/repos/" + coords.owner() + "/" + coords.repo();
        sendGet(url, "validateRepoAccess", "validate access to " + repoUrl);
    }

    @Override
    public String readFile(String repoUrl, String branch, String filePath) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/repos/" + coords.owner() + "/" + coords.repo()
                + "/contents/" + encodePath(filePath) + "?ref=" + encodeQuery(branch);
        String body = sendGet(url, "readFile", "read file " + filePath);
        try {
            JsonNode json = objectMapper.readTree(body);
            String content = json.get("content").asText();
            String cleaned = content.replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(cleaned));
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "readFile",
                    "Failed to decode file content for " + filePath + ": " + e.getMessage(), null, e);
        }
    }

    @Override
    public List<String> listDirectory(String repoUrl, String branch, String dirPath) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/repos/" + coords.owner() + "/" + coords.repo()
                + "/contents/" + encodePath(dirPath) + "?ref=" + encodeQuery(branch);
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
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String repoPath = "/repos/" + coords.owner() + "/" + coords.repo();

        String refBody = sendGet(apiBase + repoPath + "/git/ref/heads/" + fromBranch,
                "createBranch", "get SHA for branch " + fromBranch);
        try {
            JsonNode refJson = objectMapper.readTree(refBody);
            String sha = refJson.get("object").get("sha").asText();

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("ref", "refs/heads/" + branchName);
            payload.put("sha", sha);

            sendPost(apiBase + repoPath + "/git/refs", payload.toString(),
                    "createBranch", "create branch " + branchName);
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "createBranch",
                    "Failed to create branch " + branchName + ": " + e.getMessage(), null, e);
        }
    }

    @Override
    public void commitFiles(String repoUrl, String branch, Map<String, String> files, String message) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String repoPath = "/repos/" + coords.owner() + "/" + coords.repo();

        try {
            // 1. Get current commit SHA from the branch ref
            String refBody = sendGet(apiBase + repoPath + "/git/ref/heads/" + branch,
                    "commitFiles", "get current ref for " + branch);
            JsonNode refJson = objectMapper.readTree(refBody);
            String commitSha = refJson.get("object").get("sha").asText();

            // 2. Get the tree SHA from the current commit
            String commitBody = sendGet(apiBase + repoPath + "/git/commits/" + commitSha,
                    "commitFiles", "get current commit " + commitSha);
            JsonNode commitJson = objectMapper.readTree(commitBody);
            String treeSha = commitJson.get("tree").get("sha").asText();

            // 3. Create blobs for each file
            ArrayNode treeItems = objectMapper.createArrayNode();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                ObjectNode blobPayload = objectMapper.createObjectNode();
                blobPayload.put("content", entry.getValue());
                blobPayload.put("encoding", "utf-8");

                String blobBody = sendPost(apiBase + repoPath + "/git/blobs",
                        blobPayload.toString(), "commitFiles", "create blob for " + entry.getKey());
                JsonNode blobJson = objectMapper.readTree(blobBody);
                String blobSha = blobJson.get("sha").asText();

                ObjectNode treeItem = objectMapper.createObjectNode();
                treeItem.put("path", entry.getKey());
                treeItem.put("mode", "100644");
                treeItem.put("type", "blob");
                treeItem.put("sha", blobSha);
                treeItems.add(treeItem);
            }

            // 4. Create tree
            ObjectNode treePayload = objectMapper.createObjectNode();
            treePayload.put("base_tree", treeSha);
            treePayload.set("tree", treeItems);

            String treeBody = sendPost(apiBase + repoPath + "/git/trees",
                    treePayload.toString(), "commitFiles", "create tree");
            JsonNode treeJson = objectMapper.readTree(treeBody);
            String newTreeSha = treeJson.get("sha").asText();

            // 5. Create commit
            ObjectNode commitPayload = objectMapper.createObjectNode();
            commitPayload.put("message", message);
            commitPayload.put("tree", newTreeSha);
            commitPayload.putArray("parents").add(commitSha);

            String newCommitBody = sendPost(apiBase + repoPath + "/git/commits",
                    commitPayload.toString(), "commitFiles", "create commit");
            JsonNode newCommitJson = objectMapper.readTree(newCommitBody);
            String newCommitSha = newCommitJson.get("sha").asText();

            // 6. Update ref to point to new commit
            ObjectNode refPayload = objectMapper.createObjectNode();
            refPayload.put("sha", newCommitSha);

            sendPatch(apiBase + repoPath + "/git/refs/heads/" + branch,
                    refPayload.toString(), "commitFiles", "update ref for " + branch);

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "commitFiles",
                    "Failed to commit files to " + branch + ": " + e.getMessage(), null, e);
        }
    }

    @Override
    public void createTag(String repoUrl, String commitSha, String tagName) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/repos/" + coords.owner() + "/" + coords.repo() + "/git/refs";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ref", "refs/tags/" + tagName);
        payload.put("sha", commitSha);

        try {
            sendPost(url, payload.toString(), "createTag", "create tag " + tagName);
        } catch (PortalIntegrationException e) {
            if (e.getMessage().contains("422")) {
                throw new PortalIntegrationException("git", "createTag",
                        "Release tag already exists \u2014 choose a different version");
            }
            throw e;
        }
    }

    @Override
    public PullRequest createPullRequest(String repoUrl, String branch, String targetBranch,
                                         String title, String description) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/repos/" + coords.owner() + "/" + coords.repo() + "/pulls";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", title);
        payload.put("body", description);
        payload.put("head", branch);
        payload.put("base", targetBranch);

        String body = sendPost(url, payload.toString(), "createPullRequest",
                "create pull request for " + branch + " -> " + targetBranch);
        try {
            JsonNode json = objectMapper.readTree(body);
            return new PullRequest(
                    json.get("html_url").asText(),
                    json.get("number").asInt(),
                    json.get("title").asText());
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "createPullRequest",
                    "Failed to parse PR response: " + e.getMessage(), null, e);
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

    private String sendPatch(String url, String jsonBody, String operation, String description) {
        return sendRequest(buildRequest(url)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build(), operation, description);
    }

    private HttpRequest.Builder buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.token())
                .header("Accept", ACCEPT_HEADER)
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

    static String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static RepoCoordinates parseRepoUrl(String repoUrl) {
        String cleaned = repoUrl.replaceAll("\\.git$", "");
        try {
            URI uri = URI.create(cleaned);
            String path = uri.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            String[] parts = path.split("/");
            if (parts.length < 2) {
                throw new IllegalArgumentException("URL must have at least owner/repo: " + repoUrl);
            }
            return new RepoCoordinates(parts[0], parts[1]);
        } catch (IllegalArgumentException e) {
            throw new PortalIntegrationException("git", "parseUrl",
                    "Invalid repository URL: " + repoUrl + " — " + e.getMessage(), null, e);
        }
    }

    record RepoCoordinates(String owner, String repo) {}
}
