package com.portal.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {

    @Test
    void onCreateSetsTimestamps() {
        Application app = new Application();
        app.name = "my-app";
        app.teamId = 1L;
        app.gitRepoUrl = "https://github.com/org/my-app.git";
        app.runtimeType = "quarkus";

        assertNull(app.createdAt);
        assertNull(app.updatedAt);

        app.onCreate();

        assertNotNull(app.createdAt);
        assertNotNull(app.updatedAt);
        assertEquals(app.createdAt, app.updatedAt);
    }

    @Test
    void onUpdateRefreshesUpdatedAt() {
        Application app = new Application();
        app.onCreate();
        Instant pastTimestamp = Instant.parse("2020-01-01T00:00:00Z");
        app.updatedAt = pastTimestamp;

        app.onUpdate();

        assertNotNull(app.updatedAt);
        assertTrue(app.updatedAt.isAfter(pastTimestamp));
    }

    @Test
    void dtoMappingIncludesAllFields() {
        Application app = new Application();
        app.id = 42L;
        app.name = "payments-api";
        app.teamId = 7L;
        app.gitRepoUrl = "https://github.com/org/payments-api.git";
        app.runtimeType = "spring-boot";
        app.onboardingPrUrl = "https://github.com/org/infra/pull/123";
        app.onboardedAt = Instant.parse("2026-04-01T10:00:00Z");
        app.onCreate();

        ApplicationSummaryDto dto = ApplicationSummaryDto.from(app, "https://devspaces.example.com/#/https://github.com/org/payments-api.git");

        assertEquals(42L, dto.id());
        assertEquals("payments-api", dto.name());
        assertEquals("spring-boot", dto.runtimeType());
        assertEquals(Instant.parse("2026-04-01T10:00:00Z"), dto.onboardedAt());
        assertEquals("https://github.com/org/infra/pull/123", dto.onboardingPrUrl());
        assertEquals("https://github.com/org/payments-api.git", dto.gitRepoUrl());
        assertEquals("https://devspaces.example.com/#/https://github.com/org/payments-api.git", dto.devSpacesDeepLink());
    }

    @Test
    void dtoMappingHandlesNullableFields() {
        Application app = new Application();
        app.id = 1L;
        app.name = "my-app";
        app.teamId = 1L;
        app.gitRepoUrl = "https://github.com/org/my-app.git";
        app.runtimeType = "quarkus";
        app.onCreate();

        ApplicationSummaryDto dto = ApplicationSummaryDto.from(app, null);

        assertNull(dto.onboardingPrUrl());
        assertNull(dto.onboardedAt());
        assertEquals("https://github.com/org/my-app.git", dto.gitRepoUrl());
        assertNull(dto.devSpacesDeepLink());
    }
}
