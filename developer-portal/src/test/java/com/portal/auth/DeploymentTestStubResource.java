package com.portal.auth;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Test-only stub for the deployment endpoint so PermissionFilterIT can
 * exercise production deployment authorization (AC #6, #7).
 * Remove when Story 5.x implements the real DeploymentResource.
 */
@Path("/api/v1/teams/{teamId}/applications/{appId}/deployments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentTestStubResource {

    @POST
    public Response createDeployment() {
        return Response.status(Response.Status.CREATED).build();
    }
}
