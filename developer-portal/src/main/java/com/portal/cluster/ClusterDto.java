package com.portal.cluster;

import java.time.Instant;

public record ClusterDto(
    Long id,
    String name,
    String apiServerUrl,
    Instant createdAt,
    Instant updatedAt
) {
    public static ClusterDto from(Cluster entity) {
        return new ClusterDto(
            entity.id,
            entity.name,
            entity.apiServerUrl,
            entity.createdAt,
            entity.updatedAt
        );
    }
}
