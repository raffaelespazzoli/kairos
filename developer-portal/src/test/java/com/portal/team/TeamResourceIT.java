package com.portal.team;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TeamResourceIT {

    @Test
    @TestSecurity(authorizationEnabled = true)
    void unauthenticatedRequestReturns401() {
        given()
                .when().get("/api/v1/teams")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "dev@example.com", roles = "member")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "payments"),
            @Claim(key = "role", value = "member")
    })
    void authenticatedRequestReturnsTeams() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("[0].oidcGroupId", equalTo("payments"));
    }

    @Test
    @TestSecurity(user = "lead@example.com", roles = "lead")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "platform-eng"),
            @Claim(key = "role", value = "lead")
    })
    void teamAutoCreatedOnFirstLogin() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(1))
                .body("[0].name", equalTo("platform-eng"))
                .body("[0].oidcGroupId", equalTo("platform-eng"))
                .body("[0].id", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin@example.com", roles = "admin")
    @OidcSecurity(claims = {
            @Claim(key = "team", value = "security"),
            @Claim(key = "role", value = "admin")
    })
    void returnsOnlyUserTeam() {
        given()
                .when().get("/api/v1/teams")
                .then()
                .statusCode(200)
                .body("$.size()", equalTo(1))
                .body("[0].oidcGroupId", equalTo("security"));
    }
}
