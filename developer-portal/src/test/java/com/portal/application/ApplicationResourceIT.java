package com.portal.application;

import com.portal.team.Team;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationResourceIT {

    private Team testTeam;
    private Team emptyTeam;

    @BeforeAll
    void setUpData() {
        testTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "appres-test-team";
            t.oidcGroupId = "appres-test-team";
            t.persist();
            t.flush();
            return t;
        });

        emptyTeam = QuarkusTransaction.requiringNew().call(() -> {
            Team t = new Team();
            t.name = "appres-empty-team";
            t.oidcGroupId = "appres-empty-team";
            t.persist();
            t.flush();
            return t;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Application a1 = new Application();
            a1.name = "res-beta-service";
            a1.teamId = testTeam.id;
            a1.gitRepoUrl = "https://github.com/org/beta.git";
            a1.runtimeType = "quarkus";
            a1.onboardingPrUrl = "https://github.com/org/infra/pull/1";
            a1.onboardedAt = Instant.parse("2026-04-01T10:00:00Z");
            a1.persist();

            Application a2 = new Application();
            a2.name = "res-alpha-api";
            a2.teamId = testTeam.id;
            a2.gitRepoUrl = "https://github.com/org/alpha.git";
            a2.runtimeType = "spring-boot";
            a2.persist();
        });
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "appres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void listApplicationsReturnsTeamApps() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications", testTeam.id)
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(2));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "appres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void listApplicationsReturnedInAlphabeticalOrder() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications", testTeam.id)
                .then()
                .statusCode(200)
                .body("[0].name", equalTo("res-alpha-api"))
                .body("[1].name", equalTo("res-beta-service"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "appres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void listApplicationsDtoContainsExpectedFields() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications", testTeam.id)
                .then()
                .statusCode(200)
                .body("[1].id", notNullValue())
                .body("[1].name", equalTo("res-beta-service"))
                .body("[1].runtimeType", equalTo("quarkus"))
                .body("[1].onboardedAt", notNullValue())
                .body("[1].onboardingPrUrl", equalTo("https://github.com/org/infra/pull/1"))
                .body("[1].gitRepoUrl", equalTo("https://github.com/org/beta.git"))
                .body("[1].devSpacesDeepLink",
                        equalTo("https://devspaces.test.example.com/#/https://github.com/org/beta.git"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "appres-test-team"),
            @Claim(key = "role", value = "member")
    })
    void listApplicationsIncludesDevSpacesDeepLink() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications", testTeam.id)
                .then()
                .statusCode(200)
                .body("[0].devSpacesDeepLink",
                        equalTo("https://devspaces.test.example.com/#/https://github.com/org/alpha.git"))
                .body("[0].gitRepoUrl", equalTo("https://github.com/org/alpha.git"));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "appres-empty-team"),
            @Claim(key = "role", value = "member")
    })
    void listApplicationsReturnsEmptyForTeamWithNoApps() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications", emptyTeam.id)
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "appres-empty-team"),
            @Claim(key = "role", value = "member")
    })
    void listApplicationsCrossTeamReturns404() {
        given()
                .when()
                .get("/api/v1/teams/{teamId}/applications", testTeam.id)
                .then()
                .statusCode(404);
    }
}
