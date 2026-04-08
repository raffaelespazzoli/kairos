package com.portal.integration.git;

import com.portal.integration.PortalIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GitHubProviderCreateTagTest {

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

    @Test
    void createTagCallsCorrectEndpoint() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(201);
        when(mockResponse.body()).thenReturn("{\"ref\":\"refs/tags/v1.4.2\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertDoesNotThrow(() ->
                provider.createTag("https://github.com/team/app", "abc123def456", "v1.4.2"));

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://api.github.com/repos/team/app/git/refs",
                captor.getValue().uri().toString());
        assertEquals("POST", captor.getValue().method());
    }

    @Test
    void createTagThrowsWhenTagAlreadyExists() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(422);
        when(mockResponse.body()).thenReturn("{\"message\":\"Reference already exists\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.createTag("https://github.com/team/app", "abc123", "v1.0.0"));
        assertEquals("git", ex.getSystem());
        assertEquals("createTag", ex.getOperation());
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void createTagThrowsOnServerError() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("{\"message\":\"Internal Server Error\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> provider.createTag("https://github.com/team/app", "abc123", "v1.0.0"));
        assertEquals("git", ex.getSystem());
    }

    @Test
    void createTagUsesAuthorizationHeader() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(201);
        when(mockResponse.body()).thenReturn("{\"ref\":\"refs/tags/v1.0.0\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.createTag("https://github.com/team/app", "abc123", "v1.0.0");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().headers().firstValue("Authorization")
                .orElse("").contains("Bearer test-token"));
    }
}
