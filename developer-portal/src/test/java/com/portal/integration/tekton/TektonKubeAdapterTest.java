package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import com.portal.integration.PortalIntegrationException;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.PipelineRunBuilder;
import io.fabric8.tekton.v1.PipelineRunList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.tekton.client.dsl.V1APIGroupDSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TektonKubeAdapterTest {

    private TektonKubeAdapter adapter;
    private KubernetesClient mockKubeClient;
    private TektonClient mockTektonClient;
    private DeepLinkService mockDeepLinkService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        mockKubeClient = mock(KubernetesClient.class);
        mockTektonClient = mock(TektonClient.class);
        mockDeepLinkService = mock(DeepLinkService.class);

        when(mockKubeClient.adapt(TektonClient.class)).thenReturn(mockTektonClient);
        when(mockDeepLinkService.generateTektonLink(anyString()))
                .thenReturn(Optional.of("https://tekton.example.com/#/pipelineruns/test-run"));

        adapter = new TektonKubeAdapter() {
            @Override
            KubernetesClient createClient(String clusterApiUrl, String clusterToken) {
                return mockKubeClient;
            }
        };

        Field deepLinkField = TektonKubeAdapter.class.getDeclaredField("deepLinkService");
        deepLinkField.setAccessible(true);
        deepLinkField.set(adapter, mockDeepLinkService);
    }

    @SuppressWarnings("unchecked")
    @Test
    void triggerBuildCreatesPipelineRunAndReturnsSummary() {
        PipelineRun createdRun = new PipelineRunBuilder()
                .withNewMetadata()
                    .withName("my-app-xk7f2")
                    .withNamespace("team-myapp-build")
                .endMetadata()
                .build();

        V1APIGroupDSL v1 = mock(V1APIGroupDSL.class);
        MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> pipelineRuns =
                mock(MixedOperation.class);
        MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> nsScoped =
                mock(MixedOperation.class);
        NamespaceableResource<PipelineRun> resource = mock(NamespaceableResource.class);

        when(mockTektonClient.v1()).thenReturn(v1);
        when(v1.pipelineRuns()).thenReturn(pipelineRuns);
        when(pipelineRuns.inNamespace("team-myapp-build")).thenReturn(nsScoped);
        when(nsScoped.resource(any(PipelineRun.class))).thenReturn(resource);
        when(resource.create()).thenReturn(createdRun);

        BuildSummaryDto result = adapter.triggerBuild(
                "my-app", "team-myapp-build",
                "https://api.cluster:6443", "vault-token");

        assertEquals("my-app-xk7f2", result.buildId());
        assertEquals("Building", result.status());
        assertNotNull(result.startedAt());
        assertEquals("my-app", result.applicationName());
        assertNotNull(result.tektonDeepLink());
    }

    @SuppressWarnings("unchecked")
    @Test
    void triggerBuildWrapsExceptionInPortalIntegrationException() {
        when(mockTektonClient.v1()).thenThrow(new RuntimeException("Connection refused"));

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> adapter.triggerBuild("my-app", "ns", "https://api:6443", "token"));

        assertEquals("tekton", ex.getSystem());
        assertEquals("triggerBuild", ex.getOperation());
        assertTrue(ex.getMessage().contains("build cluster is unreachable"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void triggerBuildRethrowsPortalIntegrationException() {
        PortalIntegrationException original = new PortalIntegrationException(
                "tekton", "triggerBuild", "Already wrapped");
        when(mockTektonClient.v1()).thenThrow(original);

        PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                () -> adapter.triggerBuild("my-app", "ns", "https://api:6443", "token"));

        assertSame(original, ex);
    }
}
