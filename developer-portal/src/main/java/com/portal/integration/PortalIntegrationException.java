package com.portal.integration;

/**
 * Thrown by integration adapters when a call to an external platform system fails.
 * Caught by the global ExceptionMapper to produce a 502 Bad Gateway response
 * with the portal standardized error JSON format (AR12).
 */
public class PortalIntegrationException extends RuntimeException {

    private final String system;
    private final String operation;
    private final String deepLink;

    public PortalIntegrationException(String system, String operation, String message) {
        this(system, operation, message, (String) null);
    }

    public PortalIntegrationException(String system, String operation, String message, String deepLink) {
        super(message);
        this.system = system;
        this.operation = operation;
        this.deepLink = deepLink;
    }

    public PortalIntegrationException(String system, String operation, String message,
                                      String deepLink, Throwable cause) {
        super(message, cause);
        this.system = system;
        this.operation = operation;
        this.deepLink = deepLink;
    }

    public String getSystem() {
        return system;
    }

    public String getOperation() {
        return operation;
    }

    public String getDeepLink() {
        return deepLink;
    }
}
