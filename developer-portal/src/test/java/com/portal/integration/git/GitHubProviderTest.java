package com.portal.integration.git;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.model.PullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GitHubProviderTest {

    private GitHubProvider provider;
    private HttpClient mockHttpClient;
    private GitProviderConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockConfig = mock(GitProviderConfig.class);
        when(mockConfig.token()).thenReturn("test-token");
        when(mockConfig.apiUrl()).thenReturn(Optional.empty());
        provider = new GitHubProvider(mockConfig, mockHttpClient);
    }

    // --- validateRepoAccess ---

    @Test
    void validateRepoAccessSucceedsFor200() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertDoesNotThrow(() -> provider.validateRepoAccess("https://github.com/team/app"));
    }

    @Test
    void validateRepoAccessThrowsFor404() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("{\"message\":\"Not Found\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.validateRepoAccess("https://github.com/team/app"));
        assertEquals("git", ex.getSystem());
        assertEquals("validateRepoAccess", ex.getOperation());
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void validateRepoAccessThrowsOnNetworkError() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.validateRepoAccess("https://github.com/team/app"));
        assertEquals("git", ex.getSystem());
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void validateRepoAccessSendsCorrectUrl() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.validateRepoAccess("https://github.com/my-org/my-repo");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://api.github.com/repos/my-org/my-repo", captor.getValue().uri().toString());
    }

    @Test
    void validateRepoAccessUsesAuthorizationHeader() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.validateRepoAccess("https://github.com/team/app");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().headers().firstValue("Authorization").orElse("").contains("Bearer test-token"));
    }

    // --- readFile ---

    @Test
    void readFileDecodesBase64Content() throws Exception {
        String originalContent = "hello world\nline two";
        String encoded = Base64.getEncoder().encodeToString(originalContent.getBytes());
        String response = "{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(response);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = provider.readFile("https://github.com/team/app", "main", "README.md");
        assertEquals(originalContent, result);
    }

    @Test
    void readFileDecodesBase64WithNewlines() throws Exception {
        String originalContent = "some content";
        String encoded = Base64.getEncoder().encodeToString(originalContent.getBytes());
        // GitHub returns Base64 with line breaks — use escaped newline in JSON
        String withNewlines = encoded.substring(0, 4) + "\\n" + encoded.substring(4);
        String response = "{\"content\":\"" + withNewlines + "\",\"encoding\":\"base64\"}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(response);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = provider.readFile("https://github.com/team/app", "main", "file.txt");
        assertEquals(originalContent, result);
    }

    @Test
    void readFileThrowsOnServerError() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.readFile("https://github.com/team/app", "main", "README.md"));
        assertEquals("git", ex.getSystem());
        assertEquals("readFile", ex.getOperation());
    }

    // --- listDirectory ---

    @Test
    void listDirectoryReturnsFileNames() throws Exception {
        String response = "[{\"name\":\"file1.txt\",\"type\":\"file\"},{\"name\":\"dir1\",\"type\":\"dir\"}]";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(response);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        List<String> result = provider.listDirectory("https://github.com/team/app", "main", "src");
        assertEquals(List.of("file1.txt", "dir1"), result);
    }

    @Test
    void listDirectoryReturnsEmptyForEmptyArray() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("[]");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        List<String> result = provider.listDirectory("https://github.com/team/app", "main", "empty-dir");
        assertTrue(result.isEmpty());
    }

    // --- createBranch ---

    @Test
    void createBranchMakesTwoApiCalls() throws Exception {
        String refResponse = "{\"object\":{\"sha\":\"abc123\"}}";
        String createResponse = "{\"ref\":\"refs/heads/feature\"}";

        HttpResponse<String> refMock = mock(HttpResponse.class);
        when(refMock.statusCode()).thenReturn(200);
        when(refMock.body()).thenReturn(refResponse);

        HttpResponse<String> createMock = mock(HttpResponse.class);
        when(createMock.statusCode()).thenReturn(201);
        when(createMock.body()).thenReturn(createResponse);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(refMock)
                .thenReturn(createMock);

        assertDoesNotThrow(() -> provider.createBranch("https://github.com/team/app", "feature", "main"));
        verify(mockHttpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // --- commitFiles ---

    @Test
    void commitFilesFollowsGitTreesApiFlow() throws Exception {
        // Step 1: get ref
        HttpResponse<String> refResponse = mockJsonResponse(200, "{\"object\":{\"sha\":\"commit-sha\"}}");
        // Step 2: get commit
        HttpResponse<String> commitResponse = mockJsonResponse(200, "{\"tree\":{\"sha\":\"tree-sha\"}}");
        // Step 3: create blob
        HttpResponse<String> blobResponse = mockJsonResponse(201, "{\"sha\":\"blob-sha\"}");
        // Step 4: create tree
        HttpResponse<String> treeResponse = mockJsonResponse(201, "{\"sha\":\"new-tree-sha\"}");
        // Step 5: create commit
        HttpResponse<String> newCommitResponse = mockJsonResponse(201, "{\"sha\":\"new-commit-sha\"}");
        // Step 6: update ref
        HttpResponse<String> updateRefResponse = mockJsonResponse(200, "{\"ref\":\"refs/heads/feat\"}");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(refResponse)
                .thenReturn(commitResponse)
                .thenReturn(blobResponse)
                .thenReturn(treeResponse)
                .thenReturn(newCommitResponse)
                .thenReturn(updateRefResponse);

        assertDoesNotThrow(() -> provider.commitFiles(
                "https://github.com/team/app", "feat",
                Map.of("path/file.yaml", "content"), "Add file"));
        verify(mockHttpClient, times(6)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // --- createPullRequest ---

    @Test
    void createPullRequestReturnsPullRequestModel() throws Exception {
        String response = "{\"html_url\":\"https://github.com/team/app/pull/42\",\"number\":42,\"title\":\"Add onboarding\"}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(201);
        when(mockResponse.body()).thenReturn(response);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PullRequest pr = provider.createPullRequest("https://github.com/team/app",
                "feature", "main", "Add onboarding", "Description");
        assertEquals("https://github.com/team/app/pull/42", pr.url());
        assertEquals(42, pr.number());
        assertEquals("Add onboarding", pr.title());
    }

    @Test
    void createPullRequestThrowsOnFailure() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(422);
        when(mockResponse.body()).thenReturn("{\"message\":\"Validation Failed\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.createPullRequest("https://github.com/team/app",
                        "feature", "main", "Title", "Body"));
        assertEquals("git", ex.getSystem());
        assertEquals("createPullRequest", ex.getOperation());
    }

    // --- URL parsing ---

    @Test
    void parseRepoUrlExtractsOwnerAndRepo() {
        GitHubProvider.RepoCoordinates coords = GitHubProvider.parseRepoUrl("https://github.com/owner/repo");
        assertEquals("owner", coords.owner());
        assertEquals("repo", coords.repo());
    }

    @Test
    void parseRepoUrlStripsTrailingDotGit() {
        GitHubProvider.RepoCoordinates coords = GitHubProvider.parseRepoUrl("https://github.com/owner/repo.git");
        assertEquals("owner", coords.owner());
        assertEquals("repo", coords.repo());
    }

    @Test
    void parseRepoUrlHandlesEnterpriseUrls() {
        when(mockConfig.apiUrl()).thenReturn(Optional.of("https://github.mycompany.com/api/v3"));
        provider = new GitHubProvider(mockConfig, mockHttpClient);

        GitHubProvider.RepoCoordinates coords = GitHubProvider.parseRepoUrl("https://github.mycompany.com/team/service");
        assertEquals("team", coords.owner());
        assertEquals("service", coords.repo());
    }

    @Test
    void parseRepoUrlThrowsForInvalidUrl() {
        assertThrows(PortalIntegrationException.class,
                () -> GitHubProvider.parseRepoUrl("https://github.com/only-one-segment"));
    }

    // --- HTTPS enforcement ---

    @Test
    void rejectsHttpApiUrl() {
        when(mockConfig.apiUrl()).thenReturn(Optional.of("http://github.local/api/v3"));
        provider = new GitHubProvider(mockConfig, mockHttpClient);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.validateRepoAccess("http://github.local/team/app"));
        assertEquals("git", ex.getSystem());
        assertTrue(ex.getMessage().contains("HTTPS"));
    }

    // --- URL encoding ---

    @Test
    void readFileEncodesPathAndBranch() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        String encoded = java.util.Base64.getEncoder().encodeToString("data".getBytes());
        when(mockResponse.body()).thenReturn("{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.readFile("https://github.com/team/app", "feat/branch", "dir/my file.txt");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.contains("dir/my%20file.txt"), "filePath segments should be encoded: " + uri);
        assertTrue(uri.contains("ref=feat%2Fbranch"), "branch query param should be encoded: " + uri);
    }

    @Test
    void encodePathPreservesSlashesAndEncodesSpaces() {
        assertEquals("dir/my%20file.txt", GitHubProvider.encodePath("dir/my file.txt"));
        assertEquals("simple", GitHubProvider.encodePath("simple"));
    }

    // --- Custom API base ---

    @Test
    void usesCustomApiBaseWhenConfigured() throws Exception {
        when(mockConfig.apiUrl()).thenReturn(Optional.of("https://github.corp.com/api/v3"));
        provider = new GitHubProvider(mockConfig, mockHttpClient);

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.validateRepoAccess("https://github.corp.com/team/app");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().uri().toString().startsWith("https://github.corp.com/api/v3/"));
    }

    private HttpResponse<String> mockJsonResponse(int statusCode, String body) {
        HttpResponse<String> mock = mock(HttpResponse.class);
        when(mock.statusCode()).thenReturn(statusCode);
        when(mock.body()).thenReturn(body);
        return mock;
    }
}
