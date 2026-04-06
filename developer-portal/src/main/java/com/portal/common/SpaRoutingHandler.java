package com.portal.common;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Catches non-API, non-static-file GET requests and reroutes them to {@code /}
 * so Quinoa serves {@code index.html} and the SPA's client-side router takes over.
 *
 * Required because the GlobalExceptionMapper intercepts NotFoundException before
 * Quinoa's built-in SPA routing can act (known Quinoa #666 / Quarkus 3.9+ behaviour).
 *
 * @see <a href="https://docs.quarkiverse.io/quarkus-quinoa/dev/advanced-guides.html#spa-routing">Quinoa SPA Routing</a>
 */
@ApplicationScoped
public class SpaRoutingHandler {

    private static final String[] BACKEND_PREFIXES = {"/api/", "/q/", "/@"};
    private static final Predicate<String> HAS_EXTENSION =
            Pattern.compile(".+\\.[a-zA-Z0-9]+$").asMatchPredicate();

    public void init(@Observes Router router) {
        router.get("/*").handler(rc -> {
            final String path = rc.normalizedPath();
            if (!path.equals("/")
                    && Stream.of(BACKEND_PREFIXES).noneMatch(path::startsWith)
                    && !HAS_EXTENSION.test(path)) {
                rc.reroute("/");
            } else {
                rc.next();
            }
        });
    }
}
