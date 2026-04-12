package com.portal.health;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/teams/{teamId}/applications/{appId}/dora")
@Produces(MediaType.APPLICATION_JSON)
public class DoraResource {

    @Inject
    DoraService doraService;

    @GET
    public DoraMetricsDto getDoraMetrics(@PathParam("teamId") Long teamId,
                                         @PathParam("appId") Long appId,
                                         @QueryParam("timeRange") String timeRange) {
        return doraService.getDoraMetrics(teamId, appId, timeRange);
    }
}
