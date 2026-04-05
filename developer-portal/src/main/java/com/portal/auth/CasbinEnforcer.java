package com.portal.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.casbin.jcasbin.main.Enforcer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application-scoped CDI bean wrapping jCasbin for RBAC enforcement.
 * Loads the static model.conf and policy.csv from the classpath at startup.
 */
@ApplicationScoped
public class CasbinEnforcer {

    Enforcer enforcer;

    @PostConstruct
    void init() {
        Path modelPath = extractToTempFile("casbin/model.conf");
        Path policyPath = extractToTempFile("casbin/policy.csv");
        this.enforcer = new Enforcer(modelPath.toString(), policyPath.toString());
    }

    /**
     * Checks whether the given role is permitted to perform the action on the resource.
     */
    public boolean enforce(String role, String resource, String action) {
        return enforcer.enforce(role, resource, action);
    }

    /**
     * Extracts a classpath resource to a temporary file so jCasbin can read it
     * by file path — works in both quarkus:dev and uber-jar modes.
     */
    private Path extractToTempFile(String resource) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Casbin resource not found on classpath: " + resource);
            }
            String suffix = resource.contains(".") ? resource.substring(resource.lastIndexOf('.')) : ".tmp";
            Path tempFile = Files.createTempFile("casbin-", suffix);
            tempFile.toFile().deleteOnExit();
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Casbin resource: " + resource, e);
        }
    }
}
