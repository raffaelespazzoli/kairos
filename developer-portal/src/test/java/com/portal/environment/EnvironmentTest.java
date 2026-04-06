package com.portal.environment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {

    @Test
    void onCreateSetsTimestamps() {
        Environment env = new Environment();
        env.name = "dev";
        env.applicationId = 1L;
        env.clusterId = 1L;
        env.namespace = "payments-dev";
        env.promotionOrder = 0;

        assertNull(env.createdAt);
        assertNull(env.updatedAt);

        env.onCreate();

        assertNotNull(env.createdAt);
        assertNotNull(env.updatedAt);
        assertEquals(env.createdAt, env.updatedAt);
    }

    @Test
    void onUpdateRefreshesUpdatedAt() {
        Environment env = new Environment();
        env.onCreate();
        Instant pastTimestamp = Instant.parse("2020-01-01T00:00:00Z");
        env.updatedAt = pastTimestamp;

        env.onUpdate();

        assertNotNull(env.updatedAt);
        assertTrue(env.updatedAt.isAfter(pastTimestamp));
    }

    @Test
    void dtoMappingIncludesAllFields() {
        Environment env = new Environment();
        env.id = 10L;
        env.name = "staging";
        env.applicationId = 5L;
        env.clusterId = 2L;
        env.namespace = "payments-staging";
        env.promotionOrder = 1;
        env.onCreate();

        EnvironmentDto dto = EnvironmentDto.from(env);

        assertEquals(10L, dto.id());
        assertEquals("staging", dto.name());
        assertEquals(5L, dto.applicationId());
        assertEquals(2L, dto.clusterId());
        assertEquals("payments-staging", dto.namespace());
        assertEquals(1, dto.promotionOrder());
        assertEquals(env.createdAt, dto.createdAt());
        assertEquals(env.updatedAt, dto.updatedAt());
    }
}
