package com.portal.integration.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bitbucket Cloud REST API 2.0 implementation of {@link GitProvider}.
 * Uses Bearer token authentication. Commit uses multipart form upload.
 */
public class BitbucketProvider implements GitProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final GitProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiBase;

    public BitbucketProvider(GitProviderConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.apiBase = config.apiUrl().orElse("https://api.bitbucket.org");
    }

    @Override
    public void validateRepoAccess(String repoUrl) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/2.0/repositories/" + coords.workspace() + "/" + coords.repoSlug();
        sendGet(url, "validateRepoAccess", "validate access to " + repoUrl);
    }

    @Override
    public String readFile(String repoUrl, String branch, String filePath) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/2.0/repositories/" + coords.workspace() + "/" + coords.repoSlug()
                + "/src/" + encodeQuery(branch) + "/" + encodePath(filePath);
        return sendGet(url, "readFile", "read file " + filePath);
    }

    @Override
    public List<String> listDirectory(String repoUrl, String branch, String dirPath) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/2.0/repositories/" + coords.workspace() + "/" + coords.repoSlug()
                + "/src/" + encodeQuery(branch) + "/" + encodePath(dirPath) + "/";
        String body = sendGet(url, "listDirectory", "list directory " + dirPath);
        try {
            JsonNode json = objectMapper.readTree(body);
            List<String> names = new ArrayList<>();
            JsonNode values = json.get("values");
            if (values != null && values.isArray()) {
                for (JsonNode item : values) {
                    String path = item.get("path").asText();
                    String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                    names.add(name);
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
        String repoBase = "/2.0/repositories/" + coords.workspace() + "/" + coords.repoSlug();

        String branchBody = sendGet(apiBase + repoBase + "/refs/branches/" + encodePath(fromBranch),
                "createBranch", "get commit hash for " + fromBranch);
        try {
            JsonNode branchJson = objectMapper.readTree(branchBody);
            String hash = branchJson.get("target").get("hash").asText();

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("name", branchName);
            payload.putObject("target").put("hash", hash);

            sendPost(apiBase + repoBase + "/refs/branches",
                    payload.toString(), "createBranch", "create branch " + branchName);
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
        String url = apiBase + "/2.0/repositories/" + coords.workspace() + "/" + coords.repoSlug() + "/src";

        String boundary = "----PortalBoundary" + System.currentTimeMillis();
        StringBuilder multipart = new StringBuilder();

        for (Map.Entry<String, String> entry : files.entrySet()) {
            multipart.append("--").append(boundary).append("\r\n");
            multipart.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n\r\n");
            multipart.append(entry.getValue()).append("\r\n");
        }

        multipart.append("--").append(boundary).append("\r\n");
        multipart.append("Content-Disposition: form-data; name=\"message\"\r\n\r\n");
        multipart.append(message).append("\r\n");

        multipart.append("--").append(boundary).append("\r\n");
        multipart.append("Content-Disposition: form-data; name=\"branch\"\r\n\r\n");
        multipart.append(branch).append("\r\n");

        multipart.append("--").append(boundary).append("--\r\n");

        try {
            URI commitUri = URI.create(url);
            requireHttps(commitUri, "commitFiles");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(commitUri)
                    .header("Authorization", "Bearer " + config.token())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(multipart.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortalIntegrationException("git", "commitFiles",
                        "Git server returned HTTP " + response.statusCode() + " for commit files to " + branch);
            }
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PortalIntegrationException("git", "commitFiles",
                    "Failed to commit files to " + branch + ": " + e.getMessage(), null, e);
        } catch (RuntimeException e) {
            throw new PortalIntegrationException("git", "commitFiles",
                    "Failed to commit files to " + branch + ": " + e.getMessage(), null, e);
        }
    }

    @Override
    public void createTag(String repoUrl, String commitSha, String tagName) {
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/2.0/repositories/" + coords.workspace() + "/"
                + coords.repoSlug() + "/refs/tags";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", tagName);
        payload.putObject("target").put("hash", commitSha);

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
        RepoCoordinates coords = parseRepoUrl(repoUrl);
        String url = apiBase + "/2.0/repositories/" + coords.workspace() + "/" + coords.repoSlug()
                + "/pullrequests";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", title);
        payload.put("description", description);
        payload.putObject("source").putObject("branch").put("name", branch);
        payload.putObject("destination").putObject("branch").put("name", targetBranch);

        String body = sendPost(url, payload.toString(), "createPullRequest",
                "create pull request for " + branch + " -> " + targetBranch);
        try {
            JsonNode json = objectMapper.readTree(body);
            String prUrl = json.get("links").get("html").get("href").asText();
            int id = json.get("id").asInt();
            String prTitle = json.get("title").asText();
            return new PullRequest(prUrl, id, prTitle);
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

    private HttpRequest.Builder buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.token())
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
                throw new IllegalArgumentException("URL must have at least workspace/repo: " + repoUrl);
            }
            return new RepoCoordinates(parts[0], parts[1]);
        } catch (IllegalArgumentException e) {
            throw new PortalIntegrationException("git", "parseUrl",
                    "Invalid repository URL: " + repoUrl + " — " + e.getMessage(), null, e);
        }
    }

    record RepoCoordinates(String workspace, String repoSlug) {}
}
