package com.portal.onboarding;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractValidatorTest {

    private ContractValidator validator;
    private GitProvider mockGitProvider;

    private static final String REPO_URL = "https://github.com/team/app";

    @BeforeEach
    void setUp() throws Exception {
        mockGitProvider = mock(GitProvider.class);
        validator = new ContractValidator();

        Field field = ContractValidator.class.getDeclaredField("gitProvider");
        field.setAccessible(true);
        field.set(validator, mockGitProvider);
    }

    @Test
    void allChecksPassed() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenReturn(List.of("Chart.yaml", "templates"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml", "templates"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenReturn("key: value");
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("build", "run", "values-build.yaml", "values-run-dev.yaml", "values-run-qa.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml", "src", ".helm"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertTrue(result.allPassed());
        assertEquals(5, result.checks().size());
        assertTrue(result.checks().stream().allMatch(ContractCheck::passed));
        assertEquals("Quarkus/Java", result.runtimeType());
        assertEquals(List.of("dev", "qa"), result.detectedEnvironments());
    }

    @Test
    void helmBuildChartMissingFails() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenReturn(List.of("templates"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenReturn("");
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("values-run-dev.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertFalse(result.allPassed());
        ContractCheck buildCheck = result.checks().get(0);
        assertFalse(buildCheck.passed());
        assertEquals("Helm Build Chart", buildCheck.name());
        assertNotNull(buildCheck.fixInstruction());
        assertTrue(buildCheck.fixInstruction().contains("Chart.yaml"));
    }

    @Test
    void multipleEnvironmentsDetected() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenReturn("");
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("values-run-dev.yaml", "values-run-qa.yaml", "values-run-prod.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertEquals(List.of("dev", "qa", "prod"), result.detectedEnvironments());
        ContractCheck envCheck = result.checks().get(3);
        assertTrue(envCheck.passed());
        assertTrue(envCheck.detail().contains("3 environment(s) detected"));
    }

    @Test
    void runtimeDetectionPomXml() {
        setupAllChecksPass();
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml", "src"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertEquals("Quarkus/Java", result.runtimeType());
        ContractCheck runtimeCheck = result.checks().get(4);
        assertTrue(runtimeCheck.passed());
        assertTrue(runtimeCheck.detail().contains("Quarkus/Java"));
        assertTrue(runtimeCheck.detail().contains("pom.xml"));
    }

    @Test
    void runtimeDetectionPackageJson() {
        setupAllChecksPass();
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("package.json", "src"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertEquals("Node.js", result.runtimeType());
        ContractCheck runtimeCheck = result.checks().get(4);
        assertTrue(runtimeCheck.passed());
        assertTrue(runtimeCheck.detail().contains("Node.js"));
    }

    @Test
    void runtimeDetectionCsproj() {
        setupAllChecksPass();
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("MyApp.csproj", "src"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertEquals(".NET", result.runtimeType());
        ContractCheck runtimeCheck = result.checks().get(4);
        assertTrue(runtimeCheck.passed());
        assertTrue(runtimeCheck.detail().contains(".NET"));
    }

    @Test
    void singleCheckFailureDoesNotAbortOthers() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenThrow(new PortalIntegrationException("git", "listDirectory", "Not found"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenReturn("");
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("values-run-dev.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertFalse(result.allPassed());
        assertEquals(5, result.checks().size());

        assertFalse(result.checks().get(0).passed());
        assertTrue(result.checks().get(1).passed());
        assertTrue(result.checks().get(2).passed());
        assertTrue(result.checks().get(3).passed());
        assertTrue(result.checks().get(4).passed());
    }

    @Test
    void portalIntegrationExceptionMarksCheckAsFailedContinuesRest() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenThrow(new PortalIntegrationException("git", "readFile", "File not found"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("values-run-dev.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertFalse(result.allPassed());
        assertTrue(result.checks().get(0).passed());
        assertTrue(result.checks().get(1).passed());
        assertFalse(result.checks().get(2).passed());
        assertNotNull(result.checks().get(2).fixInstruction());
        assertTrue(result.checks().get(3).passed());
        assertTrue(result.checks().get(4).passed());
    }

    @Test
    void noRuntimeDetectedFails() {
        setupAllChecksPass();
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("README.md", "Makefile"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertFalse(result.allPassed());
        ContractCheck runtimeCheck = result.checks().get(4);
        assertFalse(runtimeCheck.passed());
        assertNotNull(runtimeCheck.fixInstruction());
        assertNull(result.runtimeType());
    }

    @Test
    void noEnvironmentValuesFails() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenReturn("");
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("values-build.yaml", "Chart.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ""))
                .thenReturn(List.of("pom.xml"));

        ContractValidationResult result = validator.validate(REPO_URL);

        assertFalse(result.allPassed());
        ContractCheck envCheck = result.checks().get(3);
        assertFalse(envCheck.passed());
        assertNotNull(envCheck.fixInstruction());
        assertTrue(result.detectedEnvironments().isEmpty());
    }

    private void setupAllChecksPass() {
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/build"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm/run"))
                .thenReturn(List.of("Chart.yaml"));
        when(mockGitProvider.readFile(REPO_URL, "main", ".helm/values-build.yaml"))
                .thenReturn("");
        when(mockGitProvider.listDirectory(REPO_URL, "main", ".helm"))
                .thenReturn(List.of("values-run-dev.yaml"));
    }
}
