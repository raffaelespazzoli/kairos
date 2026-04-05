package com.portal.auth;

import com.portal.common.ErrorResponse;
import io.quarkus.oidc.OIDCException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps OIDC failures to a standardized error response following the portal error format (AR12).
 * Remains a dedicated mapper so Quarkus picks it over the global catch-all for OIDCExceptions.
 */
@Provider
public class OidcExceptionMapper implements ExceptionMapper<OIDCException> {

    @Override
    public Response toResponse(OIDCException exception) {
        String detail = resolveDetail(exception);
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(
                        "oidc_provider_error",
                        "Authentication service error — " + sanitize(userFacingMessage(exception)),
                        sanitize(detail),
                        "oidc-provider",
                        null))
                .build();
    }

    private String userFacingMessage(OIDCException exception) {
        String msg = exception.getMessage();
        if (msg == null) {
            return "please try again shortly";
        }
        if (msg.toLowerCase().contains("not available") || msg.toLowerCase().contains("connect")) {
            return "the provider is currently unreachable, please try again shortly";
        }
        return "please try again shortly";
    }

    private String resolveDetail(OIDCException exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            return exception.getMessage() + " -> " + cause.getMessage();
        }
        return exception.getMessage() != null ? exception.getMessage() : "OIDC provider error";
    }

    private String sanitize(String msg) {
        if (msg == null) {
            return "OIDC provider error";
        }
        return msg.replace("\"", "'").replace("\\", "").replace("\n", " ");
    }
}
