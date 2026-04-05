package com.portal.auth;

import com.portal.team.Team;
import com.portal.team.TeamService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamContextFilterTest {

    private TeamContextFilter filter;
    private JsonWebToken jwt;
    private TeamContext teamContext;
    private TeamService teamService;
    private ContainerRequestContext requestContext;

    @BeforeEach
    void setUp() {
        filter = new TeamContextFilter();
        jwt = mock(JsonWebToken.class);
        teamContext = new TeamContext();
        teamService = mock(TeamService.class);
        requestContext = mock(ContainerRequestContext.class);

        filter.jwt = jwt;
        filter.teamContext = teamContext;
        filter.teamService = teamService;
        filter.teamClaim = "team";
        filter.roleClaim = "role";
    }

    @Test
    void skipsFilterWhenNoToken() {
        when(jwt.getRawToken()).thenReturn(null);

        filter.filter(requestContext);

        assertNull(teamContext.getTeamIdentifier());
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void extractsTeamAndRoleFromJwt() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("payments");
        when(jwt.getClaim("role")).thenReturn("lead");

        Team team = stubTeam(42L, "payments");
        when(teamService.findOrCreate("payments")).thenReturn(team);

        filter.filter(requestContext);

        assertEquals("payments", teamContext.getTeamIdentifier());
        assertEquals("lead", teamContext.getRole());
        assertEquals(42L, teamContext.getTeamId());
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void defaultsToMemberWhenRoleMissing() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("platform");
        when(jwt.getClaim("role")).thenReturn(null);

        when(teamService.findOrCreate("platform")).thenReturn(stubTeam(7L, "platform"));

        filter.filter(requestContext);

        assertEquals("platform", teamContext.getTeamIdentifier());
        assertEquals("member", teamContext.getRole());
    }

    @Test
    void defaultsToMemberWhenRoleBlank() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("platform");
        when(jwt.getClaim("role")).thenReturn("   ");

        when(teamService.findOrCreate("platform")).thenReturn(stubTeam(7L, "platform"));

        filter.filter(requestContext);

        assertEquals("member", teamContext.getRole());
    }

    @Test
    void abortsWith403WhenTeamClaimMissing() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn(null);

        filter.filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void errorResponseIncludesSystemField() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn(null);

        filter.filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        String body = (String) captor.getValue().getEntity();
        assertTrue(body.contains("\"system\":\"oidc-provider\""), "403 must include system field");
        assertTrue(body.contains("\"error\":\"missing_team_claim\""));
        assertTrue(body.contains("\"timestamp\":"));
    }

    @Test
    void abortsWith403WhenTeamClaimBlank() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("   ");

        filter.filter(requestContext);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void usesConfigurableClaimNames() {
        filter.teamClaim = "custom_team";
        filter.roleClaim = "custom_role";

        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("custom_team")).thenReturn("checkout");
        when(jwt.getClaim("custom_role")).thenReturn("admin");

        when(teamService.findOrCreate("checkout")).thenReturn(stubTeam(99L, "checkout"));

        filter.filter(requestContext);

        assertEquals("checkout", teamContext.getTeamIdentifier());
        assertEquals("admin", teamContext.getRole());
    }

    @Test
    void triggersTeamAutoProvisioning() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("new-team");
        when(jwt.getClaim("role")).thenReturn("member");

        when(teamService.findOrCreate("new-team")).thenReturn(stubTeam(1L, "new-team"));

        filter.filter(requestContext);

        verify(teamService).findOrCreate("new-team");
    }

    @Test
    void parsesCommaSeparatedTeamClaim() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("payments,checkout,platform");
        when(jwt.getClaim("role")).thenReturn("member");

        when(teamService.findOrCreate("payments")).thenReturn(stubTeam(10L, "payments"));

        filter.filter(requestContext);

        assertEquals("payments", teamContext.getTeamIdentifier());
    }

    @Test
    void parsesArrayTeamClaim() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn(List.of("payments", "checkout"));
        when(jwt.getClaim("role")).thenReturn("lead");

        when(teamService.findOrCreate("payments")).thenReturn(stubTeam(10L, "payments"));

        filter.filter(requestContext);

        assertEquals("payments", teamContext.getTeamIdentifier());
        assertEquals("lead", teamContext.getRole());
    }

    @Test
    void handlesNonStringClaimGracefully() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn(Integer.valueOf(42));
        when(jwt.getClaim("role")).thenReturn("member");

        when(teamService.findOrCreate("42")).thenReturn(stubTeam(5L, "42"));

        filter.filter(requestContext);

        assertEquals("42", teamContext.getTeamIdentifier());
    }

    @Test
    void usesReturnValueOfFindOrCreateDirectly() {
        when(jwt.getRawToken()).thenReturn("some.jwt.token");
        when(jwt.getClaim("team")).thenReturn("direct");
        when(jwt.getClaim("role")).thenReturn("member");

        Team team = stubTeam(77L, "direct");
        when(teamService.findOrCreate("direct")).thenReturn(team);

        filter.filter(requestContext);

        assertEquals(77L, teamContext.getTeamId());
        verify(teamService, never()).findByOidcGroupId(any());
    }

    private Team stubTeam(Long id, String oidcGroupId) {
        Team team = new Team();
        team.id = id;
        team.oidcGroupId = oidcGroupId;
        team.name = oidcGroupId;
        return team;
    }
}
