package com.portal.health;

import java.util.List;

/**
 * Response payload for the application health endpoint.
 *
 * @param environments health data per environment, ordered by promotion_order
 */
public record HealthResponse(List<EnvironmentHealthDto> environments) {
}
