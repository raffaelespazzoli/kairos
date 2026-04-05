package com.portal.auth;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionFilterTest {

    private PermissionFilter filter;
    private CasbinEnforcer casbinEnforcer;
    private TeamContext teamContext;
    private ContainerRequestContext requestContext;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() {
        filter = new PermissionFilter();
        casbinEnforcer = new CasbinEnforcer();
        casbinEnforcer.init();
        teamContext = new TeamContext();
        requestContext = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);

        filter.casbinEnforcer = casbinEnforcer;
        filter.teamContext = teamContext;
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    }

    // --- Path skipping ---

    @Test
    void skipsNonApiPaths() {
        when(uriInfo.getPath()).thenReturn("q/health/ready");
        when(requestContext.getMethod()).thenReturn("GET");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void skipsStaticAssetPaths() {
        when(uriInfo.getPath()).thenReturn("assets/main.js");
        when(requestContext.getMethod()).thenReturn("GET");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    // --- Null role → PortalAuthorizationException (standardized 403 JSON) ---

    @Test
    void throwsAuthorizationExceptionWhenRoleIsNull() {
        when(uriInfo.getPath()).thenReturn("api/v1/teams");
        when(requestContext.getMethod()).thenReturn("GET");
        teamContext.setRole(null);

        PortalAuthorizationException ex = assertThrows(
                PortalAuthorizationException.class,
                () -> filter.filter(requestContext));
        assertEquals("none", ex.getRole());
    }

    // --- Member allowed/denied ---

    @Test
    void memberCanReadTeams() {
        setupRequest("api/v1/teams", "GET", "member");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void memberCanReadApplications() {
        setupRequest("api/v1/teams/1/applications", "GET", "member");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void memberDeniedAdminClusters() {
        setupRequest("api/v1/admin/clusters", "GET", "member");
        assertThrows(PortalAuthorizationException.class, () -> filter.filter(requestContext));
    }

    @Test
    void memberDeniedProdDeployment() {
        setupRequest("api/v1/teams/1/applications/2/deployments", "POST", "member",
                "env", "prod");
        assertThrows(PortalAuthorizationException.class, () -> filter.filter(requestContext));
    }

    @Test
    void memberAllowedNonProdDeployment() {
        setupRequest("api/v1/teams/1/applications/2/deployments", "POST", "member");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void memberCanTriggerBuilds() {
        setupRequest("api/v1/teams/1/applications/2/builds", "POST", "member");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void memberCanCreateReleases() {
        setupRequest("api/v1/teams/1/applications/2/releases", "POST", "member");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void memberCanOnboardApplications() {
        setupRequest("api/v1/teams/1/applications/2/onboard", "POST", "member");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    // --- Lead permissions ---

    @Test
    void leadCanDeployToProd() {
        setupRequest("api/v1/teams/1/applications/2/deployments", "POST", "lead",
                "env", "prod");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void leadCanReadTeams() {
        setupRequest("api/v1/teams", "GET", "lead");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void leadDeniedClusterCreate() {
        setupRequest("api/v1/admin/clusters", "POST", "lead");
        assertThrows(PortalAuthorizationException.class, () -> filter.filter(requestContext));
    }

    // --- Admin permissions ---

    @Test
    void adminCanAccessClusters() {
        setupRequest("api/v1/admin/clusters", "GET", "admin");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void adminCanCreateClusters() {
        setupRequest("api/v1/admin/clusters", "POST", "admin");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void adminCanUpdateClusters() {
        setupRequest("api/v1/admin/clusters/5", "PUT", "admin");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void adminCanDeleteClusters() {
        setupRequest("api/v1/admin/clusters/5", "DELETE", "admin");
        filter.filter(requestContext);
        verify(requestContext, never()).abortWith(any());
    }

    // --- extractResource tests ---

    @Test
    void extractsResourceFromAdminPath() {
        assertEquals("clusters", filter.extractResource("api/v1/admin/clusters"));
        assertEquals("clusters", filter.extractResource("api/v1/admin/clusters/5"));
    }

    @Test
    void extractsResourceFromTeamPath() {
        assertEquals("teams", filter.extractResource("api/v1/teams"));
        assertEquals("teams", filter.extractResource("api/v1/teams/1"));
    }

    @Test
    void extractsNestedResource() {
        assertEquals("applications", filter.extractResource("api/v1/teams/1/applications"));
        assertEquals("applications", filter.extractResource("api/v1/teams/1/applications/2"));
        assertEquals("builds", filter.extractResource("api/v1/teams/1/applications/2/builds"));
        assertEquals("deployments", filter.extractResource("api/v1/teams/1/applications/2/deployments"));
    }

    // --- mapAction tests ---

    @Test
    void mapsGetToRead() {
        assertEquals("read", filter.mapAction("GET", "api/v1/teams", "teams"));
    }

    @Test
    void mapsDeleteToDelete() {
        assertEquals("delete", filter.mapAction("DELETE", "api/v1/admin/clusters/5", "clusters"));
    }

    @Test
    void mapsPutToUpdate() {
        assertEquals("update", filter.mapAction("PUT", "api/v1/admin/clusters/5", "clusters"));
    }

    @Test
    void mapsPostOnboardToOnboard() {
        assertEquals("onboard", filter.mapAction("POST", "api/v1/teams/1/applications/2/onboard", "onboard"));
    }

    @Test
    void mapsPostBuildsToTrigger() {
        assertEquals("trigger", filter.mapAction("POST", "api/v1/teams/1/applications/2/builds", "builds"));
    }

    @Test
    void mapsPostDeploymentsToDeployForNonProd() {
        assertEquals("deploy", filter.mapAction("POST", "api/v1/teams/1/applications/2/deployments", "deployments"));
    }

    @Test
    void mapsPostDeploymentsToDeployAlways() {
        // mapAction always returns "deploy" for POST on deployments;
        // production detection is done in filter() via query parameter
        assertEquals("deploy", filter.mapAction("POST", "api/v1/teams/1/applications/2/deployments", "deployments"));
    }

    @Test
    void mapsPostReleasesToCreate() {
        assertEquals("create", filter.mapAction("POST", "api/v1/teams/1/applications/2/releases", "releases"));
    }

    @Test
    void mapsPostClustersToCreate() {
        assertEquals("create", filter.mapAction("POST", "api/v1/admin/clusters", "clusters"));
    }

    // --- Exception content ---

    @Test
    void authorizationExceptionContainsRoleResourceAction() {
        setupRequest("api/v1/admin/clusters", "POST", "member");
        PortalAuthorizationException ex = assertThrows(
                PortalAuthorizationException.class,
                () -> filter.filter(requestContext));
        assertEquals("member", ex.getRole());
        assertEquals("clusters", ex.getResource());
        assertEquals("create", ex.getAction());
    }

    private void setupRequest(String path, String method, String role) {
        setupRequest(path, method, role, null, null);
    }

    private void setupRequest(String path, String method, String role,
                              String queryKey, String queryValue) {
        when(uriInfo.getPath()).thenReturn(path);
        when(requestContext.getMethod()).thenReturn(method);

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        if (queryKey != null) {
            queryParams.putSingle(queryKey, queryValue);
        }
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        teamContext.setRole(role);
        teamContext.setTeamIdentifier("test-team");
        teamContext.setTeamId(1L);
    }
}
