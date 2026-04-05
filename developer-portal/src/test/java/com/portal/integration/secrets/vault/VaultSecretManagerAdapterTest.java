package com.portal.integration.secrets.vault;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.secrets.ClusterCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VaultSecretManagerAdapterTest {

    private VaultSecretManagerAdapter adapter;
    private HttpClient mockHttpClient;
    private VaultConfig mockVaultConfig;

    @BeforeEach
    void setUp() throws Exception {
        mockHttpClient = mock(HttpClient.class);
        mockVaultConfig = mock(VaultConfig.class);

        adapter = new VaultSecretManagerAdapter(mockHttpClient);

        Field configField = VaultSecretManagerAdapter.class.getDeclaredField("vaultConfig");
        configField.setAccessible(true);
        configField.set(adapter, mockVaultConfig);

        when(mockVaultConfig.url()).thenReturn("http://vault:8200");
        when(mockVaultConfig.credentialPathTemplate())
                .thenReturn("/infra/{cluster}/kubernetes-secret-engine/creds/{role}");
        when(mockVaultConfig.authRole()).thenReturn("portal");
        when(mockVaultConfig.authMountPath()).thenReturn("auth/kubernetes");

        // Pre-set a valid Vault token to avoid auth calls during credential fetch tests
        adapter.setVaultClientToken("hvs.test-token", Instant.now().plusSeconds(3600));
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulCredentialFetchReturnsClusterCredential() throws Exception {
        String vaultResponse = """
                {
                    "lease_id": "infra/dev-cluster/kubernetes-secret-engine/creds/portal-role/abc123",
                    "renewable": false,
                    "lease_duration": 3600,
                    "data": {
                        "service_account_name": "portal-sa",
                        "service_account_namespace": "default",
                        "service_account_token": "eyJhbGciOiJSUzI1NiJ9.test"
                    }
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(vaultResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ClusterCredential result = adapter.getCredentials("dev-cluster", "portal-role");

        assertEquals("eyJhbGciOiJSUzI1NiJ9.test", result.token());
        assertEquals(3600, result.ttlSeconds());
        assertFalse(result.isExpired());
    }

    @Test
    @SuppressWarnings("unchecked")
    void credentialFetchInterpolatesClusterAndRoleInPath() throws Exception {
        String vaultResponse = """
                {
                    "lease_duration": 1800,
                    "data": {
                        "service_account_token": "token-staging"
                    }
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(vaultResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        adapter.getCredentials("staging-cluster", "deploy-role");

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        String uri = requestCaptor.getValue().uri().toString();
        assertEquals("http://vault:8200/v1/infra/staging-cluster/kubernetes-secret-engine/creds/deploy-role", uri);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultAuthTokenRefreshOccursWhenExpired() throws Exception {
        adapter.setVaultClientToken(null, null);

        // First call: auth login
        String authResponse = """
                {
                    "auth": {
                        "client_token": "hvs.new-token",
                        "lease_duration": 7200
                    }
                }
                """;

        // Second call: credential fetch
        String credResponse = """
                {
                    "lease_duration": 3600,
                    "data": {
                        "service_account_token": "cluster-token"
                    }
                }
                """;

        HttpResponse<String> authMock = mock(HttpResponse.class);
        when(authMock.statusCode()).thenReturn(200);
        when(authMock.body()).thenReturn(authResponse);

        HttpResponse<String> credMock = mock(HttpResponse.class);
        when(credMock.statusCode()).thenReturn(200);
        when(credMock.body()).thenReturn(credResponse);

        // Override readServiceAccountToken to avoid filesystem dependency
        VaultSecretManagerAdapter spyAdapter = spy(adapter);
        doReturn("fake-sa-jwt").when(spyAdapter).readServiceAccountToken();

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(authMock)
                .thenReturn(credMock);

        ClusterCredential result = spyAdapter.getCredentials("dev-cluster", "portal-role");

        assertEquals("cluster-token", result.token());
        verify(mockHttpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultUnreachableThrowsPortalIntegrationException() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> adapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("get-credentials", ex.getOperation());
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultNonSuccessStatusThrowsPortalIntegrationException() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(403);
        when(mockResponse.body()).thenReturn("{\"errors\":[\"permission denied\"]}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> adapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("get-credentials", ex.getOperation());
        assertTrue(ex.getMessage().contains("403"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedVaultResponseThrowsPortalIntegrationException() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"lease_duration\": 3600}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> adapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("get-credentials", ex.getOperation());
        assertTrue(ex.getMessage().contains("missing 'data' field"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultAuthLoginFailureThrowsPortalIntegrationException() throws Exception {
        adapter.setVaultClientToken(null, null);

        HttpResponse<String> authMock = mock(HttpResponse.class);
        when(authMock.statusCode()).thenReturn(500);
        when(authMock.body()).thenReturn("{\"errors\":[\"internal error\"]}");

        VaultSecretManagerAdapter spyAdapter = spy(adapter);
        doReturn("fake-sa-jwt").when(spyAdapter).readServiceAccountToken();

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(authMock);

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> spyAdapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("authenticate", ex.getOperation());
    }

    @Test
    void cannotReadServiceAccountTokenThrowsPortalIntegrationException() {
        adapter.setVaultClientToken(null, null);

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> adapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("authenticate", ex.getOperation());
        assertTrue(ex.getMessage().contains("Cannot read service account token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingServiceAccountTokenFieldThrowsPortalIntegrationException() throws Exception {
        String vaultResponse = """
                {
                    "lease_duration": 3600,
                    "data": {
                        "service_account_name": "portal-sa",
                        "service_account_namespace": "default"
                    }
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(vaultResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> adapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("get-credentials", ex.getOperation());
        assertTrue(ex.getMessage().contains("missing 'service_account_token'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingClientTokenInAuthResponseThrowsPortalIntegrationException() throws Exception {
        adapter.setVaultClientToken(null, null);

        String authResponse = """
                {
                    "auth": {
                        "lease_duration": 7200
                    }
                }
                """;

        HttpResponse<String> authMock = mock(HttpResponse.class);
        when(authMock.statusCode()).thenReturn(200);
        when(authMock.body()).thenReturn(authResponse);

        VaultSecretManagerAdapter spyAdapter = spy(adapter);
        doReturn("fake-sa-jwt").when(spyAdapter).readServiceAccountToken();

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(authMock);

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> spyAdapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("authenticate", ex.getOperation());
        assertTrue(ex.getMessage().contains("missing 'client_token'"));
    }

    @Test
    void invalidVaultUrlWrapsAsPortalIntegrationException() {
        when(mockVaultConfig.url()).thenReturn("not a valid url ::: ???");

        PortalIntegrationException ex = assertThrows(
                PortalIntegrationException.class,
                () -> adapter.getCredentials("dev-cluster", "portal-role"));

        assertEquals("vault", ex.getSystem());
        assertEquals("get-credentials", ex.getOperation());
        assertTrue(ex.getMessage().contains("Failed to build Vault request"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cachedVaultTokenIsReusedWhenNotExpired() throws Exception {
        String vaultResponse = """
                {
                    "lease_duration": 3600,
                    "data": {
                        "service_account_token": "token-1"
                    }
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(vaultResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        adapter.getCredentials("dev-cluster", "portal-role");
        adapter.getCredentials("dev-cluster", "portal-role");

        // Only credential fetch calls (no auth calls since token is pre-set and valid)
        verify(mockHttpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
