package com.portal.common;

import com.portal.auth.PortalAuthorizationException;
import com.portal.integration.PortalIntegrationException;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.stream.Collectors;

/**
 * Catches all unhandled exceptions and returns standardized error JSON (AR12).
 * Consolidates per-exception mappers into a single handler for consistency.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof PortalIntegrationException pie) {
            return buildResponse(502, ErrorResponse.of(
                    "integration-error",
                    pie.getMessage(),
                    "Operation: " + pie.getOperation(),
                    pie.getSystem(),
                    pie.getDeepLink()));
        }

        if (e instanceof PortalAuthorizationException pae) {
            return buildResponse(403, ErrorResponse.of(
                    "forbidden",
                    "You do not have permission to perform this action",
                    pae.getMessage()));
        }

        if (e instanceof ConstraintViolationException cve) {
            String violations = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            return buildResponse(400, ErrorResponse.of(
                    "validation-error",
                    "Invalid request data",
                    violations));
        }

        if (e instanceof IllegalArgumentException iae) {
            return buildResponse(400, ErrorResponse.of(
                    "validation-error",
                    "Invalid request data",
                    iae.getMessage()));
        }

        if (e instanceof PersistenceException pe
                && pe.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
            return buildResponse(409, ErrorResponse.of(
                    "conflict",
                    "A record with the same unique value already exists",
                    null));
        }

        if (e instanceof NotFoundException) {
            return buildResponse(404, ErrorResponse.of(
                    "not-found",
                    "The requested resource was not found",
                    null));
        }

        return buildResponse(500, ErrorResponse.of(
                "internal-error",
                "An unexpected error occurred",
                e.getMessage()));
    }

    private Response buildResponse(int status, ErrorResponse error) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}
