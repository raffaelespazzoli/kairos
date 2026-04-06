package com.portal.onboarding;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.cluster.ClusterDto;
import com.portal.cluster.ClusterService;
import com.portal.deeplink.DeepLinkService;
import com.portal.environment.Environment;
import com.portal.gitops.ManifestGenerator;
import com.portal.gitops.OnboardingPrBuilder;
import com.portal.integration.git.model.PullRequest;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {

    private OnboardingService service;
    private ManifestGenerator mockManifestGenerator;
    private ClusterService mockClusterService;
    private TeamContext mockTeamContext;
    private OnboardingPrBuilder mockPrBuilder;
    private DeepLinkService mockDeepLinkService;

    @BeforeEach
    void setUp() throws Exception {
        mockManifestGenerator = mock(ManifestGenerator.class);
        mockClusterService = mock(ClusterService.class);
        mockTeamContext = mock(TeamContext.class);
        mockPrBuilder = mock(OnboardingPrBuilder.class);
        mockDeepLinkService = mock(DeepLinkService.class);

        when(mockTeamContext.getTeamIdentifier()).thenReturn("payments");
        when(mockTeamContext.getTeamId()).thenReturn(1L);
        when(mockManifestGenerator.generateAllManifests(any(), anyString(), any()))
                .thenReturn(Map.of("path/file.yaml", "content"));
        when(mockDeepLinkService.generateDevSpacesLink(anyString()))
                .thenReturn(java.util.Optional.empty());

        service = new OnboardingService();
        setField("manifestGenerator", mockManifestGenerator);
        setField("clusterService", mockClusterService);
        setField("teamContext", mockTeamContext);
        setField("onboardingPrBuilder", mockPrBuilder);
        setField("deepLinkService", mockDeepLinkService);
    }

    @Test
    void buildPlanOrdersEnvironmentsByConvention() {
        registerCluster(1L, "ocp-dev-01", "https://api.ocp-dev-01:6443");
        registerCluster(2L, "ocp-qa-01", "https://api.ocp-qa-01:6443");
        registerCluster(3L, "ocp-prod-01", "https://api.ocp-prod-01:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "my-app", "Quarkus/Java",
                List.of("prod", "dev", "qa"),
                Map.of("dev", 1L, "qa", 2L, "prod", 3L), 1L);

        var result = service.buildPlan("my-app", request);

        assertEquals(List.of("dev", "qa", "prod"), result.promotionChain());
    }

    @Test
    void buildPlanCreatesCorrectNamespaces() {
        registerCluster(1L, "ocp-dev-01", "https://api.ocp-dev-01:6443");
        registerCluster(2L, "ocp-qa-01", "https://api.ocp-qa-01:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "payment-svc", "Quarkus/Java",
                List.of("dev", "qa"),
                Map.of("dev", 1L, "qa", 2L), 1L);

        var result = service.buildPlan("payment-svc", request);

        assertEquals(3, result.namespaces().size());

        var buildNs = result.namespaces().get(0);
        assertEquals("payments-payment-svc-build", buildNs.name());
        assertEquals("ocp-dev-01", buildNs.clusterName());
        assertTrue(buildNs.isBuild());

        var devNs = result.namespaces().get(1);
        assertEquals("payments-payment-svc-dev", devNs.name());
        assertEquals("ocp-dev-01", devNs.clusterName());
        assertFalse(devNs.isBuild());

        var qaNs = result.namespaces().get(2);
        assertEquals("payments-payment-svc-qa", qaNs.name());
        assertEquals("ocp-qa-01", qaNs.clusterName());
    }

    @Test
    void buildPlanCreatesCorrectArgoCdApps() {
        registerCluster(1L, "ocp-dev-01", "https://api.ocp-dev-01:6443");
        registerCluster(2L, "ocp-qa-01", "https://api.ocp-qa-01:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "payment-svc", "Quarkus/Java",
                List.of("dev", "qa"),
                Map.of("dev", 1L, "qa", 2L), 1L);

        var result = service.buildPlan("payment-svc", request);

        assertEquals(3, result.argoCdApps().size());

        var buildApp = result.argoCdApps().get(0);
        assertEquals("payment-svc-build", buildApp.name());
        assertTrue(buildApp.isBuild());
        assertEquals(".helm/build", buildApp.chartPath());
        assertEquals("values-build.yaml", buildApp.valuesFile());

        var devApp = result.argoCdApps().get(1);
        assertEquals("payment-svc-run-dev", devApp.name());
        assertFalse(devApp.isBuild());
        assertEquals(".helm/run", devApp.chartPath());
        assertEquals("values-run-dev.yaml", devApp.valuesFile());
    }

    @Test
    void buildPlanSetsTeamNameFromContext() {
        when(mockTeamContext.getTeamIdentifier()).thenReturn("checkout");
        registerCluster(1L, "ocp-dev-01", "https://api:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "cart-svc", "Node.js",
                List.of("dev"),
                Map.of("dev", 1L), 1L);

        var result = service.buildPlan("cart-svc", request);

        assertEquals("checkout", result.teamName());
        assertEquals("checkout-cart-svc-build", result.namespaces().get(0).name());
    }

    @Test
    void buildPlanOrdersUnknownEnvironmentsAlphabetically() {
        registerCluster(1L, "cluster", "https://api:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "app", "Java",
                List.of("custom-b", "dev", "custom-a", "prod"),
                Map.of("dev", 1L, "prod", 1L, "custom-a", 1L, "custom-b", 1L), 1L);

        var result = service.buildPlan("app", request);

        assertEquals(List.of("dev", "prod", "custom-a", "custom-b"), result.promotionChain());
    }

    @Test
    void buildPlanThrowsForInvalidClusterId() {
        when(mockClusterService.findById(999L))
                .thenThrow(new NotFoundException("Cluster with ID 999 not found"));

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "app", "Java",
                List.of("dev"),
                Map.of("dev", 999L), 999L);

        assertThrows(NotFoundException.class, () -> service.buildPlan("app", request));
    }

    @Test
    void buildPlanCallsManifestGenerator() {
        registerCluster(1L, "ocp-dev-01", "https://api:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "app", "Java",
                List.of("dev"),
                Map.of("dev", 1L), 1L);

        service.buildPlan("app", request);

        verify(mockManifestGenerator).generateAllManifests(any(), eq("https://github.com/team/app"), any());
    }

    @Test
    void buildPlanBuildNamespaceIncluded() {
        registerCluster(1L, "ocp-dev-01", "https://api:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "my-app", "Java",
                List.of("dev"),
                Map.of("dev", 1L), 1L);

        var result = service.buildPlan("my-app", request);

        boolean hasBuildNs = result.namespaces().stream()
                .anyMatch(ns -> ns.isBuild() && ns.name().equals("payments-my-app-build"));
        assertTrue(hasBuildNs);
    }

    @Test
    void buildPlanSlugifiesAppAndEnvNames() {
        registerCluster(1L, "ocp-dev-01", "https://api.ocp-dev-01:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "Payment_Service", "Quarkus/Java",
                List.of("My Dev"),
                Map.of("My Dev", 1L), 1L);

        var result = service.buildPlan("Payment_Service", request);

        assertEquals("payments-payment-service-build", result.namespaces().get(0).name());
        assertEquals("payments-payment-service-my-dev", result.namespaces().get(1).name());
        assertEquals("payment-service-build", result.argoCdApps().get(0).name());
        assertEquals("payment-service-run-my-dev", result.argoCdApps().get(1).name());
    }

    @Test
    void buildPlanArgoCdAppCountMatchesEnvironmentsPlusBuild() {
        registerCluster(1L, "c1", "https://api:6443");
        registerCluster(2L, "c2", "https://api:6443");
        registerCluster(3L, "c3", "https://api:6443");

        var request = new OnboardingPlanRequest(
                "https://github.com/team/app", "app", "Java",
                List.of("dev", "qa", "prod"),
                Map.of("dev", 1L, "qa", 2L, "prod", 3L), 1L);

        var result = service.buildPlan("app", request);

        assertEquals(4, result.argoCdApps().size());
        assertEquals(1, result.argoCdApps().stream().filter(PlannedArgoCdApp::isBuild).count());
        assertEquals(3, result.argoCdApps().stream().filter(a -> !a.isBuild()).count());
    }

    @Test
    void confirmOnboardingCallsPrBuilderWithManifests() {
        registerCluster(1L, "ocp-dev-01", "https://api:6443");
        registerCluster(2L, "ocp-qa-01", "https://api:6443");
        when(mockPrBuilder.createOnboardingPr(anyString(), anyString(), any()))
                .thenReturn(new PullRequest("https://git.example.com/pr/1", 1, "Onboard"));

        var request = new OnboardingConfirmRequest(
                "my-app", "https://github.com/team/app", "Quarkus/Java",
                List.of("dev", "qa"), Map.of("dev", 1L, "qa", 2L), 1L);

        try (var appMock = mockConstruction(Application.class);
             var envMock = mockConstruction(Environment.class)) {
            service.confirmOnboarding(request);

            verify(mockPrBuilder).createOnboardingPr(
                    eq("payments"), eq("my-app"), eq(Map.of("path/file.yaml", "content")));
        }
    }

    @Test
    void confirmOnboardingPersistsApplicationWithCorrectFields() {
        registerCluster(1L, "ocp-dev-01", "https://api:6443");
        when(mockPrBuilder.createOnboardingPr(anyString(), anyString(), any()))
                .thenReturn(new PullRequest("https://git.example.com/pr/1", 1, "Onboard"));

        var request = new OnboardingConfirmRequest(
                "my-app", "https://github.com/team/app", "Quarkus/Java",
                List.of("dev"), Map.of("dev", 1L), 1L);

        try (var appMock = mockConstruction(Application.class);
             var envMock = mockConstruction(Environment.class)) {
            service.confirmOnboarding(request);

            assertEquals(1, appMock.constructed().size());
            Application app = appMock.constructed().get(0);
            assertEquals("my-app", app.name);
            assertEquals(1L, app.teamId);
            assertEquals("https://github.com/team/app", app.gitRepoUrl);
            assertEquals("Quarkus/Java", app.runtimeType);
            assertEquals("https://git.example.com/pr/1", app.onboardingPrUrl);
            assertNotNull(app.onboardedAt);
            verify(app).persistAndFlush();
        }
    }

    @Test
    void confirmOnboardingPersistsEnvironmentsWithCorrectFields() {
        registerCluster(1L, "ocp-dev-01", "https://api:6443");
        registerCluster(2L, "ocp-qa-01", "https://api:6443");
        registerCluster(3L, "ocp-prod-01", "https://api:6443");
        when(mockPrBuilder.createOnboardingPr(anyString(), anyString(), any()))
                .thenReturn(new PullRequest("https://git.example.com/pr/1", 1, "Onboard"));

        var request = new OnboardingConfirmRequest(
                "my-app", "https://github.com/team/app", "Quarkus/Java",
                List.of("prod", "dev", "qa"),
                Map.of("dev", 1L, "qa", 2L, "prod", 3L), 1L);

        try (var appMock = mockConstruction(Application.class);
             var envMock = mockConstruction(Environment.class)) {
            service.confirmOnboarding(request);

            List<Environment> envs = envMock.constructed();
            assertEquals(3, envs.size());

            Environment devEnv = envs.get(0);
            assertEquals("dev", devEnv.name);
            assertEquals(1L, devEnv.clusterId);
            assertEquals("payments-my-app-dev", devEnv.namespace);
            assertEquals(0, devEnv.promotionOrder);
            verify(devEnv).persist();

            Environment qaEnv = envs.get(1);
            assertEquals("qa", qaEnv.name);
            assertEquals(2L, qaEnv.clusterId);
            assertEquals(1, qaEnv.promotionOrder);

            Environment prodEnv = envs.get(2);
            assertEquals("prod", prodEnv.name);
            assertEquals(3L, prodEnv.clusterId);
            assertEquals(2, prodEnv.promotionOrder);
        }
    }

    @Test
    void confirmOnboardingReturnsCorrectDto() {
        registerCluster(1L, "ocp-dev-01", "https://api:6443");
        registerCluster(2L, "ocp-qa-01", "https://api:6443");
        when(mockPrBuilder.createOnboardingPr(anyString(), anyString(), any()))
                .thenReturn(new PullRequest("https://git.example.com/pr/99", 99, "Onboard"));

        var request = new OnboardingConfirmRequest(
                "my-app", "https://github.com/team/app", "Quarkus/Java",
                List.of("dev", "qa"), Map.of("dev", 1L, "qa", 2L), 1L);

        try (var appMock = mockConstruction(Application.class);
             var envMock = mockConstruction(Environment.class)) {
            OnboardingResultDto result = service.confirmOnboarding(request);

            assertEquals("my-app", result.applicationName());
            assertEquals("https://git.example.com/pr/99", result.onboardingPrUrl());
            assertEquals(List.of("dev", "qa"), result.promotionChain());
        }
    }

    @Test
    void confirmOnboardingPrUrlFromBuilderFlowsToApplication() {
        registerCluster(1L, "cluster", "https://api:6443");
        when(mockPrBuilder.createOnboardingPr(anyString(), anyString(), any()))
                .thenReturn(new PullRequest("https://custom-git.com/pr/55", 55, "PR Title"));

        var request = new OnboardingConfirmRequest(
                "svc", "https://github.com/team/app", "Java",
                List.of("dev"), Map.of("dev", 1L), 1L);

        try (var appMock = mockConstruction(Application.class);
             var envMock = mockConstruction(Environment.class)) {
            service.confirmOnboarding(request);

            Application app = appMock.constructed().get(0);
            assertEquals("https://custom-git.com/pr/55", app.onboardingPrUrl);
        }
    }

    private void registerCluster(Long id, String name, String apiUrl) {
        Instant now = Instant.now();
        when(mockClusterService.findById(id)).thenReturn(
                new ClusterDto(id, name, apiUrl, now, now));
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = OnboardingService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
