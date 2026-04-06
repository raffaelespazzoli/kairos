package com.portal.integration.git;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.model.PullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GitLabProviderTest {

    private GitLabProvider provider;
    private HttpClient mockHttpClient;
    private GitProviderConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockConfig = mock(GitProviderConfig.class);
        when(mockConfig.token()).thenReturn("test-token");
        when(mockConfig.apiUrl()).thenReturn(Optional.empty());
        provider = new GitLabProvider(mockConfig, mockHttpClient);
    }

    // --- validateRepoAccess ---

    @Test
    void validateRepoAccessSucceedsFor200() throws Exception {
        HttpResponse<String> mockResponse = mockJsonResponse(200, "{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertDoesNotThrow(() -> provider.validateRepoAccess("https://gitlab.com/group/repo"));
    }

    @Test
    void validateRepoAccessThrowsFor404() throws Exception {
        HttpResponse<String> mockResponse = mockJsonResponse(404, "{\"message\":\"Not Found\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.validateRepoAccess("https://gitlab.com/group/repo"));
        assertEquals("git", ex.getSystem());
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void validateRepoAccessThrowsOnNetworkError() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.validateRepoAccess("https://gitlab.com/group/repo"));
        assertEquals("git", ex.getSystem());
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void validateRepoAccessUsesPrivateTokenHeader() throws Exception {
        HttpResponse<String> mockResponse = mockJsonResponse(200, "{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.validateRepoAccess("https://gitlab.com/group/repo");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("test-token", captor.getValue().headers().firstValue("PRIVATE-TOKEN").orElse(""));
    }

    // --- readFile ---

    @Test
    void readFileReturnsRawContent() throws Exception {
        String content = "apiVersion: apps/v1\nkind: Deployment";
        HttpResponse<String> mockResponse = mockJsonResponse(200, content);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = provider.readFile("https://gitlab.com/group/repo", "main", "deploy.yaml");
        assertEquals(content, result);
    }

    // --- listDirectory ---

    @Test
    void listDirectoryReturnsNames() throws Exception {
        String response = "[{\"name\":\"file1.txt\",\"type\":\"blob\",\"path\":\"src/file1.txt\"},"
                + "{\"name\":\"dir1\",\"type\":\"tree\",\"path\":\"src/dir1\"}]";
        HttpResponse<String> mockResponse = mockJsonResponse(200, response);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        List<String> result = provider.listDirectory("https://gitlab.com/group/repo", "main", "src");
        assertEquals(List.of("file1.txt", "dir1"), result);
    }

    // --- createBranch ---

    @Test
    void createBranchMakesSinglePost() throws Exception {
        HttpResponse<String> mockResponse = mockJsonResponse(201, "{\"name\":\"feature\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertDoesNotThrow(() -> provider.createBranch("https://gitlab.com/group/repo", "feature", "main"));
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // --- commitFiles ---

    @Test
    void commitFilesUsesActionsArray() throws Exception {
        HttpResponse<String> mockResponse = mockJsonResponse(201, "{\"id\":\"abc123\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertDoesNotThrow(() -> provider.commitFiles("https://gitlab.com/group/repo", "feature",
                Map.of("path/file.yaml", "content"), "Add file"));

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.contains("/repository/commits"));
    }

    // --- createPullRequest ---

    @Test
    void createPullRequestReturnsMergeRequestModel() throws Exception {
        String response = "{\"web_url\":\"https://gitlab.com/group/repo/-/merge_requests/7\",\"iid\":7,\"title\":\"Onboard app\"}";
        HttpResponse<String> mockResponse = mockJsonResponse(201, response);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PullRequest pr = provider.createPullRequest("https://gitlab.com/group/repo",
                "feature", "main", "Onboard app", "Description");
        assertEquals("https://gitlab.com/group/repo/-/merge_requests/7", pr.url());
        assertEquals(7, pr.number());
        assertEquals("Onboard app", pr.title());
    }

    @Test
    void createPullRequestThrowsOnFailure() throws Exception {
        HttpResponse<String> mockResponse = mockJsonResponse(422, "{\"message\":\"Validation failed\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.createPullRequest("https://gitlab.com/group/repo",
                        "feature", "main", "Title", "Body"));
        assertEquals("git", ex.getSystem());
        assertEquals("createPullRequest", ex.getOperation());
    }

    // --- URL parsing ---

    @Test
    void parseProjectPathEncodesSimplePath() {
        String encoded = GitLabProvider.parseProjectPath("https://gitlab.com/group/repo");
        assertEquals("group%2Frepo", encoded);
    }

    @Test
    void parseProjectPathHandlesSubgroups() {
        String encoded = GitLabProvider.parseProjectPath("https://gitlab.com/group/sub/repo");
        assertEquals("group%2Fsub%2Frepo", encoded);
    }

    @Test
    void parseProjectPathStripsTrailingDotGit() {
        String encoded = GitLabProvider.parseProjectPath("https://gitlab.com/group/repo.git");
        assertEquals("group%2Frepo", encoded);
    }

    @Test
    void parseProjectPathThrowsForEmptyPath() {
        assertThrows(PortalIntegrationException.class,
                () -> GitLabProvider.parseProjectPath("https://gitlab.com/"));
    }

    // --- HTTPS enforcement ---

    @Test
    void rejectsHttpApiUrl() {
        when(mockConfig.apiUrl()).thenReturn(Optional.of("http://gitlab.local/api/v4"));
        provider = new GitLabProvider(mockConfig, mockHttpClient);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.validateRepoAccess("http://gitlab.local/group/repo"));
        assertEquals("git", ex.getSystem());
        assertTrue(ex.getMessage().contains("HTTPS"));
    }

    // --- Custom API base ---

    @Test
    void usesCustomApiBaseWhenConfigured() throws Exception {
        when(mockConfig.apiUrl()).thenReturn(Optional.of("https://gitlab.corp.com/api/v4"));
        provider = new GitLabProvider(mockConfig, mockHttpClient);

        HttpResponse<String> mockResponse = mockJsonResponse(200, "{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.validateRepoAccess("https://gitlab.corp.com/group/repo");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().uri().toString().startsWith("https://gitlab.corp.com/api/v4/"));
    }

    private HttpResponse<String> mockJsonResponse(int statusCode, String body) {
        HttpResponse<String> mock = mock(HttpResponse.class);
        when(mock.statusCode()).thenReturn(statusCode);
        when(mock.body()).thenReturn(body);
        return mock;
    }
}
