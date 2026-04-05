package com.portal.cluster;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/v1/admin/clusters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClusterResource {

    @Inject
    ClusterService clusterService;

    @GET
    public List<ClusterDto> list() {
        return clusterService.listAll();
    }

    @POST
    public Response create(@Valid CreateClusterRequest request) {
        ClusterDto created = clusterService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{clusterId}")
    public ClusterDto update(@PathParam("clusterId") Long clusterId, @Valid UpdateClusterRequest request) {
        return clusterService.update(clusterId, request);
    }

    @DELETE
    @Path("/{clusterId}")
    public Response delete(@PathParam("clusterId") Long clusterId) {
        clusterService.delete(clusterId);
        return Response.noContent().build();
    }
}
