package com.portal.common;

import java.time.Instant;

/**
 * Standardized error response format (AR12) returned by all portal API endpoints.
 * Fields match the frontend PortalError TypeScript interface exactly.
 */
public record ErrorResponse(
    String error,
    String message,
    String detail,
    String system,
    String deepLink,
    Instant timestamp
) {
    public static ErrorResponse of(String error, String message, String detail,
                                   String system, String deepLink) {
        return new ErrorResponse(error, message, detail, system, deepLink, Instant.now());
    }

    public static ErrorResponse of(String error, String message, String detail) {
        return new ErrorResponse(error, message, detail, "portal", null, Instant.now());
    }
}
