package com.portal.gitops;

import com.portal.integration.PortalIntegrationException;
import com.portal.integration.git.GitProvider;
import com.portal.integration.git.GitProviderConfig;
import com.portal.integration.git.model.PullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnboardingPrBuilderTest {

    private OnboardingPrBuilder builder;
    private GitProvider mockGitProvider;
    private GitProviderConfig mockConfig;

    @BeforeEach
    void setUp() throws Exception {
        mockGitProvider = mock(GitProvider.class);
        mockConfig = mock(GitProviderConfig.class);
        when(mockConfig.infraRepoUrl()).thenReturn("https://github.com/org/infra-repo");
        when(mockGitProvider.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PullRequest("https://github.com/org/infra-repo/pull/42", 42, "Onboard team/app"));

        builder = new OnboardingPrBuilder();
        setField("gitProvider", mockGitProvider);
        setField("gitConfig", mockConfig);
    }

    @Test
    void createOnboardingPrCallsThreeStepsInOrder() {
        Map<String, String> manifests = Map.of(
                "ocp-dev-01/payments-app-dev/namespace.yaml", "ns-yaml",
                "ocp-dev-01/payments-app-dev/argocd-app-run-dev.yaml", "argo-yaml"
        );

        PullRequest result = builder.createOnboardingPr("payments", "app", manifests);

        var inOrder = inOrder(mockGitProvider);
        inOrder.verify(mockGitProvider).createBranch(
                "https://github.com/org/infra-repo", "onboard/payments-app", "main");
        inOrder.verify(mockGitProvider).commitFiles(
                eq("https://github.com/org/infra-repo"), eq("onboard/payments-app"),
                eq(manifests), anyString());
        inOrder.verify(mockGitProvider).createPullRequest(
                eq("https://github.com/org/infra-repo"), eq("onboard/payments-app"),
                eq("main"), anyString(), anyString());

        assertEquals("https://github.com/org/infra-repo/pull/42", result.url());
    }

    @Test
    void branchNameFollowsFormat() {
        Map<String, String> manifests = Map.of(
                "cluster/ns/namespace.yaml", "yaml"
        );

        builder.createOnboardingPr("team-alpha", "my-svc", manifests);

        verify(mockGitProvider).createBranch(anyString(), eq("onboard/team-alpha-my-svc"), eq("main"));
    }

    @Test
    void prTitleContainsCorrectCounts() {
        Map<String, String> manifests = Map.of(
                "c1/ns1/namespace.yaml", "ns1",
                "c1/ns2/namespace.yaml", "ns2",
                "c1/ns1/argocd-app-build.yaml", "argo1",
                "c1/ns2/argocd-app-run-dev.yaml", "argo2",
                "c2/ns3/argocd-app-run-qa.yaml", "argo3"
        );

        builder.createOnboardingPr("team", "app", manifests);

        verify(mockGitProvider).createPullRequest(
                anyString(), anyString(), anyString(),
                eq("Onboard team/app — 2 namespaces, 3 ArgoCD applications"),
                anyString());
    }

    @Test
    void prDescriptionListsAllFilePaths() {
        Map<String, String> manifests = Map.of(
                "c1/ns1/namespace.yaml", "ns1",
                "c1/ns1/argocd-app-build.yaml", "argo1"
        );

        builder.createOnboardingPr("team", "app", manifests);

        verify(mockGitProvider).createPullRequest(
                anyString(), anyString(), anyString(), anyString(),
                argThat(desc -> desc.contains("c1/ns1/argocd-app-build.yaml")
                        && desc.contains("c1/ns1/namespace.yaml")));
    }

    @Test
    void createBranchFailurePropagatesAsPortalIntegrationException() {
        doThrow(new PortalIntegrationException("git", "createBranch", "branch error"))
                .when(mockGitProvider).createBranch(anyString(), anyString(), anyString());

        Map<String, String> manifests = Map.of("path/file.yaml", "content");

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> builder.createOnboardingPr("team", "app", manifests));
        assertTrue(ex.getMessage().contains("Could not create branch in infrastructure repository"));
    }

    @Test
    void commitFilesFailurePropagatesAsPortalIntegrationException() {
        doThrow(new PortalIntegrationException("git", "commitFiles", "commit error"))
                .when(mockGitProvider).commitFiles(anyString(), anyString(), any(), anyString());

        Map<String, String> manifests = Map.of("path/file.yaml", "content");

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> builder.createOnboardingPr("team", "app", manifests));
        assertTrue(ex.getMessage().contains("Failed to commit manifest files"));
    }

    @Test
    void createPullRequestFailurePropagatesAsPortalIntegrationException() {
        when(mockGitProvider.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new PortalIntegrationException("git", "createPullRequest", "pr error"));

        Map<String, String> manifests = Map.of("path/file.yaml", "content");

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> builder.createOnboardingPr("team", "app", manifests));
        assertTrue(ex.getMessage().contains("Pull request creation failed"));
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = OnboardingPrBuilder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(builder, value);
    }
}
