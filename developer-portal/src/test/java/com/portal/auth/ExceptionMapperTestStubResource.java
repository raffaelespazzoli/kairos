package com.portal.auth;

import com.portal.integration.PortalIntegrationException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only resource that throws specific exceptions so integration tests
 * can verify GlobalExceptionMapper produces the correct HTTP responses.
 * The /test/ path prefix is outside /api/v1/ so no auth is required.
 */
@Path("/api/v1/admin/test-exceptions")
@Produces(MediaType.APPLICATION_JSON)
public class ExceptionMapperTestStubResource {

    @GET
    @Path("/integration")
    public String throwIntegration() {
        throw new PortalIntegrationException(
                "argocd",
                "sync-application",
                "ArgoCD sync failed: connection timeout",
                "https://argocd.internal/applications/my-app-qa");
    }

    @GET
    @Path("/not-found")
    public String throwNotFound() {
        throw new NotFoundException();
    }

    @GET
    @Path("/validation")
    public String throwValidation() {
        throw new IllegalArgumentException("appName must not be blank");
    }

    @GET
    @Path("/unexpected")
    public String throwUnexpected() {
        throw new RuntimeException("Something unexpected happened");
    }
}
