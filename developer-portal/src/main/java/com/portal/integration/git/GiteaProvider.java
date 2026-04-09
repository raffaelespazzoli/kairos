package com.portal.integration.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.integration.PortalIntegrationException;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gitea REST API v1 implementation of {@link GitProvider}.
 * API base is always derived from the repo URL host since Gitea is self-hosted.
 * Uses {@code Authorization: token {token}} header.
 */
public class GiteaProvider implements GitProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final GitProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GiteaProvider(GitProviderConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public void validateRepoAccess(String repoUrl) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String url = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo();
        sendGet(url, "validateRepoAccess", "validate access to " + repoUrl);
    }

    @Override
    public String readFile(String repoUrl, String branch, String filePath) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String url = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo()
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
        RepoInfo info = parseRepoUrl(repoUrl);
        String url = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo()
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
        RepoInfo info = parseRepoUrl(repoUrl);
        String url = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo() + "/branches";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("new_branch_name", branchName);
        payload.put("old_branch_name", fromBranch);

        sendPost(url, payload.toString(), "createBranch", "create branch " + branchName);
    }

    @Override
    public void commitFiles(String repoUrl, String branch, Map<String, String> files, String message) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String repoPath = "/repos/" + info.owner() + "/" + info.repo();
        String base = info.apiBase();

        try {
            // Git Trees API approach (same as GitHub)
            String refBody = sendGet(base + repoPath + "/git/refs/heads/" + branch,
                    "commitFiles", "get current ref for " + branch);
            JsonNode refJson = objectMapper.readTree(refBody);
            // Gitea returns an array for refs
            JsonNode refNode = refJson.isArray() ? refJson.get(0) : refJson;
            String commitSha = refNode.get("object").get("sha").asText();

            String commitBody = sendGet(base + repoPath + "/git/commits/" + commitSha,
                    "commitFiles", "get current commit " + commitSha);
            JsonNode commitJson = objectMapper.readTree(commitBody);
            String treeSha = commitJson.get("tree").get("sha").asText();

            ArrayNode treeItems = objectMapper.createArrayNode();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                ObjectNode blobPayload = objectMapper.createObjectNode();
                blobPayload.put("content", entry.getValue());
                blobPayload.put("encoding", "utf-8");

                String blobBody = sendPost(base + repoPath + "/git/blobs",
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

            ObjectNode treePayload = objectMapper.createObjectNode();
            treePayload.put("base_tree", treeSha);
            treePayload.set("tree", treeItems);

            String treeBody = sendPost(base + repoPath + "/git/trees",
                    treePayload.toString(), "commitFiles", "create tree");
            JsonNode treeJson = objectMapper.readTree(treeBody);
            String newTreeSha = treeJson.get("sha").asText();

            ObjectNode newCommitPayload = objectMapper.createObjectNode();
            newCommitPayload.put("message", message);
            newCommitPayload.put("tree", newTreeSha);
            newCommitPayload.putArray("parents").add(commitSha);

            String newCommitBody = sendPost(base + repoPath + "/git/commits",
                    newCommitPayload.toString(), "commitFiles", "create commit");
            JsonNode newCommitJson = objectMapper.readTree(newCommitBody);
            String newCommitSha = newCommitJson.get("sha").asText();

            ObjectNode refPayload = objectMapper.createObjectNode();
            refPayload.put("sha", newCommitSha);

            sendPatch(base + repoPath + "/git/refs/heads/" + branch,
                    refPayload.toString(), "commitFiles", "update ref for " + branch);

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("git", "commitFiles",
                    "Failed to commit files to " + branch + ": " + e.getMessage(), null, e);
        }
    }

    @Override
    public List<GitTag> listTags(String repoUrl, int maxResults) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String baseUrl = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo() + "/tags";
        int limit = Math.min(maxResults, 50);
        List<GitTag> allTags = new ArrayList<>();
        int page = 1;

        while (allTags.size() < maxResults) {
            String url = baseUrl + "?limit=" + limit + "&page=" + page;
            String body = sendGet(url, "list-tags", "list tags for " + repoUrl);
            try {
                JsonNode json = objectMapper.readTree(body);
                if (!json.isArray() || json.isEmpty()) break;

                int pageCount = 0;
                for (JsonNode item : json) {
                    if (allTags.size() >= maxResults) break;
                    String name = item.get("name").asText();
                    String sha = item.get("commit").get("sha").asText();
                    String dateStr = item.get("commit").get("created").asText();
                    allTags.add(new GitTag(name, sha, Instant.parse(dateStr)));
                    pageCount++;
                }

                if (pageCount < limit) break;
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
    public void createTag(String repoUrl, String commitSha, String tagName) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String url = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo() + "/tags";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("tag_name", tagName);
        payload.put("target", commitSha);

        try {
            sendPost(url, payload.toString(), "createTag", "create tag " + tagName);
        } catch (PortalIntegrationException e) {
            if (e.getMessage().contains("422") || e.getMessage().contains("409")) {
                throw new PortalIntegrationException("git", "createTag",
                        "Release tag already exists \u2014 choose a different version");
            }
            throw e;
        }
    }

    @Override
    public PullRequest createPullRequest(String repoUrl, String branch, String targetBranch,
                                         String title, String description) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String url = info.apiBase() + "/repos/" + info.owner() + "/" + info.repo() + "/pulls";

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
                .header("Authorization", "token " + config.token())
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

    /**
     * Parses a Gitea repo URL to extract the API base, owner, and repo.
     * Gitea is self-hosted, so the API base is derived from the URL host.
     * {@code apiUrl} config overrides the derived base if present.
     * Enforces HTTPS when deriving the API base from the repo URL.
     */
    RepoInfo parseRepoUrl(String repoUrl) {
        String cleaned = repoUrl.replaceAll("\\.git$", "");
        try {
            URI uri = URI.create(cleaned);
            String path = uri.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            String[] parts = path.split("/");
            if (parts.length < 2) {
                throw new IllegalArgumentException("URL must have at least owner/repo: " + repoUrl);
            }
            String apiBase = config.apiUrl().orElseGet(() -> {
                if (!"https".equals(uri.getScheme())) {
                    throw new PortalIntegrationException("git", "parseUrl",
                            "Git provider requires HTTPS but repo URL uses " + uri.getScheme());
                }
                return "https://" + uri.getHost()
                        + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "/api/v1";
            });
            return new RepoInfo(apiBase, parts[0], parts[1]);
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new PortalIntegrationException("git", "parseUrl",
                    "Invalid repository URL: " + repoUrl + " — " + e.getMessage(), null, e);
        }
    }

    record RepoInfo(String apiBase, String owner, String repo) {}
}
