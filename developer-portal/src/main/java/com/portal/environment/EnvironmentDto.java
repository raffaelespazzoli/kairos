package com.portal.environment;

import java.time.Instant;

public record EnvironmentDto(
    Long id,
    String name,
    Long applicationId,
    Long clusterId,
    String namespace,
    Integer promotionOrder,
    Instant createdAt,
    Instant updatedAt
) {
    public static EnvironmentDto from(Environment entity) {
        return new EnvironmentDto(
            entity.id,
            entity.name,
            entity.applicationId,
            entity.clusterId,
            entity.namespace,
            entity.promotionOrder,
            entity.createdAt,
            entity.updatedAt
        );
    }
}
