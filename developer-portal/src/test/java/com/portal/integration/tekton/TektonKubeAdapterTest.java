package com.portal.integration.tekton;

import com.portal.build.BuildDetailDto;
import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import com.portal.integration.PortalIntegrationException;
import io.fabric8.knative.pkg.apis.Condition;
import io.fabric8.knative.pkg.apis.ConditionBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.client.dsl.V1APIGroupDSL;
import io.fabric8.tekton.v1.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        assertNull(result.completedAt());
        assertNull(result.duration());
        assertNull(result.imageReference());
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

    @Nested
    class StatusTranslation {

        @Test
        void runningPipelineRunMapsToBuilding() {
            PipelineRun run = buildRunWithCondition("Unknown", "Running");
            assertEquals("Building", adapter.translateStatus(run));
        }

        @Test
        void startedPipelineRunMapsToBuilding() {
            PipelineRun run = buildRunWithCondition("Unknown", "Started");
            assertEquals("Building", adapter.translateStatus(run));
        }

        @Test
        void succeededPipelineRunMapsToPassed() {
            PipelineRun run = buildRunWithCondition("True", "Succeeded");
            assertEquals("Passed", adapter.translateStatus(run));
        }

        @Test
        void failedPipelineRunMapsToFailed() {
            PipelineRun run = buildRunWithCondition("False", "Failed");
            assertEquals("Failed", adapter.translateStatus(run));
        }

        @Test
        void cancelledPipelineRunMapsToCancelled() {
            PipelineRun run = buildRunWithCondition("False", "PipelineRunCancelled");
            assertEquals("Cancelled", adapter.translateStatus(run));
        }

        @Test
        void timeoutPipelineRunMapsToFailed() {
            PipelineRun run = buildRunWithCondition("False", "PipelineRunTimeout");
            assertEquals("Failed", adapter.translateStatus(run));
        }

        @Test
        void noConditionsMappsToPending() {
            PipelineRun run = new PipelineRunBuilder().withNewStatus().endStatus().build();
            assertEquals("Pending", adapter.translateStatus(run));
        }

        @Test
        void nullStatusMapsToPending() {
            PipelineRun run = new PipelineRunBuilder().build();
            assertEquals("Pending", adapter.translateStatus(run));
        }

        @Test
        void unknownWithOtherReasonMapsToPending() {
            PipelineRun run = buildRunWithCondition("Unknown", "PipelineRunPending");
            assertEquals("Pending", adapter.translateStatus(run));
        }
    }

    @Nested
    class DurationComputation {

        @Test
        void lessThan60SecondsShowsSeconds() {
            Instant start = Instant.parse("2026-04-07T12:00:00Z");
            Instant end = Instant.parse("2026-04-07T12:00:42Z");
            assertEquals("42s", adapter.computeDuration(start, end));
        }

        @Test
        void minutesAndSeconds() {
            Instant start = Instant.parse("2026-04-07T12:00:00Z");
            Instant end = Instant.parse("2026-04-07T12:02:34Z");
            assertEquals("2m 34s", adapter.computeDuration(start, end));
        }

        @Test
        void hoursAndMinutes() {
            Instant start = Instant.parse("2026-04-07T12:00:00Z");
            Instant end = Instant.parse("2026-04-07T13:05:00Z");
            assertEquals("1h 5m", adapter.computeDuration(start, end));
        }

        @Test
        void nullStartReturnsNull() {
            assertNull(adapter.computeDuration(null, Instant.now()));
        }

        @Test
        void nullEndUsesNow() {
            Instant start = Instant.now().minusSeconds(30);
            String duration = adapter.computeDuration(start, null);
            assertNotNull(duration);
            assertTrue(duration.endsWith("s"));
        }
    }

    @Nested
    class HumanizeTaskName {

        @Test
        void hyphenatedNameToTitleCase() {
            assertEquals("Run Tests", adapter.humanizeTaskName("run-tests"));
        }

        @Test
        void singleWord() {
            assertEquals("Build", adapter.humanizeTaskName("build"));
        }

        @Test
        void multipleHyphens() {
            assertEquals("Build And Push Image", adapter.humanizeTaskName("build-and-push-image"));
        }

        @Test
        void nullReturnsUnknown() {
            assertEquals("Unknown", adapter.humanizeTaskName(null));
        }
    }

    @SuppressWarnings("unchecked")
    @Nested
    class ListBuilds {

        @Test
        void returnsBuildsSortedByStartTimeDescending() {
            PipelineRun older = buildRunWithTime("app-001", "2026-04-07T10:00:00Z", null,
                    "True", "Succeeded");
            PipelineRun newer = buildRunWithTime("app-002", "2026-04-07T12:00:00Z", null,
                    "Unknown", "Running");

            V1APIGroupDSL v1 = mock(V1APIGroupDSL.class);
            MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> prOps =
                    mock(MixedOperation.class);
            MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> nsScoped =
                    mock(MixedOperation.class);
            FilterWatchListDeletable<PipelineRun, PipelineRunList, Resource<PipelineRun>> labelFiltered =
                    mock(FilterWatchListDeletable.class);
            PipelineRunList list = new PipelineRunList();
            list.setItems(List.of(older, newer));

            when(mockTektonClient.v1()).thenReturn(v1);
            when(v1.pipelineRuns()).thenReturn(prOps);
            when(prOps.inNamespace("ns")).thenReturn(nsScoped);
            when(nsScoped.withLabel("tekton.dev/pipeline", "my-app")).thenReturn(labelFiltered);
            when(labelFiltered.list()).thenReturn(list);

            List<BuildSummaryDto> result = adapter.listBuilds(
                    "my-app", "ns", "https://api:6443", "token");

            assertEquals(2, result.size());
            assertEquals("app-002", result.get(0).buildId());
            assertEquals("app-001", result.get(1).buildId());
            assertEquals("Building", result.get(0).status());
            assertEquals("Passed", result.get(1).status());
        }

        @Test
        void populatesDeepLinks() {
            PipelineRun run = buildRunWithTime("app-xyz", "2026-04-07T12:00:00Z",
                    "2026-04-07T12:05:00Z", "True", "Succeeded");

            V1APIGroupDSL v1 = mock(V1APIGroupDSL.class);
            MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> prOps =
                    mock(MixedOperation.class);
            MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> nsScoped =
                    mock(MixedOperation.class);
            FilterWatchListDeletable<PipelineRun, PipelineRunList, Resource<PipelineRun>> labelFiltered =
                    mock(FilterWatchListDeletable.class);
            PipelineRunList list = new PipelineRunList();
            list.setItems(List.of(run));

            when(mockTektonClient.v1()).thenReturn(v1);
            when(v1.pipelineRuns()).thenReturn(prOps);
            when(prOps.inNamespace("ns")).thenReturn(nsScoped);
            when(nsScoped.withLabel("tekton.dev/pipeline", "my-app")).thenReturn(labelFiltered);
            when(labelFiltered.list()).thenReturn(list);

            List<BuildSummaryDto> result = adapter.listBuilds(
                    "my-app", "ns", "https://api:6443", "token");

            assertNotNull(result.get(0).tektonDeepLink());
            verify(mockDeepLinkService).generateTektonLink("app-xyz");
        }

        @Test
        void wrapsExceptionInPortalIntegrationException() {
            when(mockTektonClient.v1()).thenThrow(new RuntimeException("timeout"));

            PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                    () -> adapter.listBuilds("app", "ns", "https://api:6443", "token"));

            assertEquals("tekton", ex.getSystem());
            assertEquals("listBuilds", ex.getOperation());
        }
    }

    @SuppressWarnings("unchecked")
    @Nested
    class GetBuildDetail {

        @Test
        void passedBuildIncludesImageReference() {
            PipelineRun run = buildRunWithResults("app-pass", "2026-04-07T12:00:00Z",
                    "2026-04-07T12:05:00Z", "True", "Succeeded",
                    "IMAGE_URL", "registry.example.com/team/app:sha123");

            stubGetPipelineRun("app-pass", "ns", run);

            BuildDetailDto result = adapter.getBuildDetail(
                    "app-pass", "ns", "https://api:6443", "token");

            assertEquals("Passed", result.status());
            assertEquals("registry.example.com/team/app:sha123", result.imageReference());
            assertNull(result.failedStageName());
            assertNull(result.currentStage());
        }

        @Test
        void failedBuildIncludesFailureInfo() {
            PipelineRun run = buildFailedRunWithTaskRun("app-fail", "ns",
                    "run-tests", "Test failure in SomeTest.testMethod");

            stubGetPipelineRun("app-fail", "ns", run);
            stubGetTaskRun("app-fail-run-tests-pod", "ns", "False",
                    "Test failure in SomeTest.testMethod");

            BuildDetailDto result = adapter.getBuildDetail(
                    "app-fail", "ns", "https://api:6443", "token");

            assertEquals("Failed", result.status());
            assertEquals("Run Tests", result.failedStageName());
            assertEquals("Test failure in SomeTest.testMethod", result.errorSummary());
            assertNull(result.imageReference());
        }

        @Test
        void buildingBuildIncludesCurrentStage() {
            PipelineRun run = buildInProgressRunWithTaskRun("app-build", "ns", "build-image");

            stubGetPipelineRun("app-build", "ns", run);
            stubGetTaskRun("app-build-build-image-pod", "ns", "Unknown", "Running");

            BuildDetailDto result = adapter.getBuildDetail(
                    "app-build", "ns", "https://api:6443", "token");

            assertEquals("Building", result.status());
            assertEquals("Build Image", result.currentStage());
            assertNull(result.failedStageName());
        }

        @Test
        void nonExistentBuildThrowsNotFoundException() {
            stubGetPipelineRun("no-such-build", "ns", null);

            assertThrows(jakarta.ws.rs.NotFoundException.class,
                    () -> adapter.getBuildDetail("no-such-build", "ns", "https://api:6443", "token"));
        }

        @Test
        void wrapsExceptionInPortalIntegrationException() {
            when(mockTektonClient.v1()).thenThrow(new RuntimeException("timeout"));

            PortalIntegrationException ex = assertThrows(PortalIntegrationException.class,
                    () -> adapter.getBuildDetail("build-x", "ns", "https://api:6443", "token"));

            assertEquals("getBuildDetail", ex.getOperation());
        }
    }

    @SuppressWarnings("unchecked")
    @Nested
    class GetBuildLogs {

        @Test
        void retrievesLogsFromStepContainers() {
            Pod pod = new PodBuilder()
                    .withNewMetadata()
                        .withName("app-pod-1")
                        .withLabels(Map.of(
                                "tekton.dev/pipelineRun", "app-build-1",
                                "tekton.dev/pipelineTask", "run-tests"))
                    .endMetadata()
                    .withNewSpec()
                        .withContainers(
                                new ContainerBuilder().withName("step-run").build(),
                                new ContainerBuilder().withName("step-report").build())
                    .endSpec()
                    .build();

            PodList podList = new PodList();
            podList.setItems(List.of(pod));

            MixedOperation<Pod, PodList, PodResource> podOps = mock(MixedOperation.class);
            MixedOperation<Pod, PodList, PodResource> nsScoped = mock(MixedOperation.class);
            FilterWatchListDeletable<Pod, PodList, PodResource> labelFiltered =
                    mock(FilterWatchListDeletable.class);

            when(mockKubeClient.pods()).thenReturn(podOps);
            when(podOps.inNamespace("ns")).thenReturn(nsScoped);
            when(nsScoped.withLabel("tekton.dev/pipelineRun", "app-build-1")).thenReturn(labelFiltered);
            when(labelFiltered.list()).thenReturn(podList);

            PodResource podResource = mock(PodResource.class);
            PodResource inContainerRun = mock(PodResource.class);
            PodResource inContainerReport = mock(PodResource.class);

            when(nsScoped.withName("app-pod-1")).thenReturn(podResource);
            when(podResource.inContainer("step-run")).thenReturn(inContainerRun);
            when(podResource.inContainer("step-report")).thenReturn(inContainerReport);
            when(inContainerRun.getLog()).thenReturn("test output line 1\ntest output line 2");
            when(inContainerReport.getLog()).thenReturn("report data");

            String logs = adapter.getBuildLogs("app-build-1", "ns", "https://api:6443", "token");

            assertTrue(logs.contains("=== Run Tests / run ==="));
            assertTrue(logs.contains("test output line 1"));
            assertTrue(logs.contains("=== Run Tests / report ==="));
            assertTrue(logs.contains("report data"));
        }

        @Test
        void noPodsThrowsNotFoundException() {
            PodList emptyList = new PodList();
            emptyList.setItems(List.of());

            MixedOperation<Pod, PodList, PodResource> podOps = mock(MixedOperation.class);
            MixedOperation<Pod, PodList, PodResource> nsScoped = mock(MixedOperation.class);
            FilterWatchListDeletable<Pod, PodList, PodResource> labelFiltered =
                    mock(FilterWatchListDeletable.class);

            when(mockKubeClient.pods()).thenReturn(podOps);
            when(podOps.inNamespace("ns")).thenReturn(nsScoped);
            when(nsScoped.withLabel("tekton.dev/pipelineRun", "no-build")).thenReturn(labelFiltered);
            when(labelFiltered.list()).thenReturn(emptyList);

            assertThrows(jakarta.ws.rs.NotFoundException.class,
                    () -> adapter.getBuildLogs("no-build", "ns", "https://api:6443", "token"));
        }

        @Test
        void handlesLogRetrievalFailureGracefully() {
            Pod pod = new PodBuilder()
                    .withNewMetadata()
                        .withName("app-pod-err")
                        .withLabels(Map.of(
                                "tekton.dev/pipelineRun", "app-err",
                                "tekton.dev/pipelineTask", "build-image"))
                    .endMetadata()
                    .withNewSpec()
                        .withContainers(
                                new ContainerBuilder().withName("step-build").build())
                    .endSpec()
                    .build();

            PodList podList = new PodList();
            podList.setItems(List.of(pod));

            MixedOperation<Pod, PodList, PodResource> podOps = mock(MixedOperation.class);
            MixedOperation<Pod, PodList, PodResource> nsScoped = mock(MixedOperation.class);
            FilterWatchListDeletable<Pod, PodList, PodResource> labelFiltered =
                    mock(FilterWatchListDeletable.class);

            when(mockKubeClient.pods()).thenReturn(podOps);
            when(podOps.inNamespace("ns")).thenReturn(nsScoped);
            when(nsScoped.withLabel("tekton.dev/pipelineRun", "app-err")).thenReturn(labelFiltered);
            when(labelFiltered.list()).thenReturn(podList);

            PodResource podResource = mock(PodResource.class);
            PodResource inContainer = mock(PodResource.class);

            when(nsScoped.withName("app-pod-err")).thenReturn(podResource);
            when(podResource.inContainer("step-build")).thenReturn(inContainer);
            when(inContainer.getLog()).thenThrow(new RuntimeException("container not ready"));

            String logs = adapter.getBuildLogs("app-err", "ns", "https://api:6443", "token");

            assertTrue(logs.contains("=== Build Image / build ==="));
            assertTrue(logs.contains("[Log unavailable]"));
        }
    }

    // --- Helper methods ---

    private PipelineRun buildRunWithCondition(String conditionStatus, String reason) {
        Condition condition = new ConditionBuilder()
                .withType("Succeeded")
                .withStatus(conditionStatus)
                .withReason(reason)
                .build();
        return new PipelineRunBuilder()
                .withNewStatus()
                    .withConditions(condition)
                .endStatus()
                .build();
    }

    private PipelineRun buildRunWithTime(String name, String startTime, String completionTime,
                                          String conditionStatus, String reason) {
        Condition condition = new ConditionBuilder()
                .withType("Succeeded")
                .withStatus(conditionStatus)
                .withReason(reason)
                .build();
        PipelineRunBuilder builder = new PipelineRunBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec().withNewPipelineRef().withName("my-app").endPipelineRef().endSpec()
                .withNewStatus()
                    .withStartTime(startTime)
                    .withConditions(condition)
                .endStatus();
        if (completionTime != null) {
            builder.editStatus().withCompletionTime(completionTime).endStatus();
        }
        return builder.build();
    }

    private PipelineRun buildRunWithResults(String name, String startTime, String completionTime,
                                             String conditionStatus, String reason,
                                             String resultName, String resultValue) {
        PipelineRun run = buildRunWithTime(name, startTime, completionTime, conditionStatus, reason);
        PipelineRunResult result = new PipelineRunResultBuilder()
                .withName(resultName)
                .withValue(new ParamValue(resultValue))
                .build();
        run.getStatus().setResults(List.of(result));
        return run;
    }

    @SuppressWarnings("unchecked")
    private void stubGetPipelineRun(String buildId, String namespace, PipelineRun run) {
        V1APIGroupDSL v1 = mock(V1APIGroupDSL.class);
        MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> prOps =
                mock(MixedOperation.class);
        MixedOperation<PipelineRun, PipelineRunList, Resource<PipelineRun>> nsScoped =
                mock(MixedOperation.class);
        Resource<PipelineRun> resource = mock(Resource.class);

        when(mockTektonClient.v1()).thenReturn(v1);
        when(v1.pipelineRuns()).thenReturn(prOps);
        when(prOps.inNamespace(namespace)).thenReturn(nsScoped);
        when(nsScoped.withName(buildId)).thenReturn(resource);
        when(resource.get()).thenReturn(run);

        MixedOperation<TaskRun, TaskRunList, Resource<TaskRun>> trOps = mock(MixedOperation.class);
        MixedOperation<TaskRun, TaskRunList, Resource<TaskRun>> trNsScoped = mock(MixedOperation.class);
        when(v1.taskRuns()).thenReturn(trOps);
        when(trOps.inNamespace(namespace)).thenReturn(trNsScoped);

        if (run != null && run.getStatus() != null && run.getStatus().getChildReferences() != null) {
            for (ChildStatusReference ref : run.getStatus().getChildReferences()) {
                Resource<TaskRun> trResource = mock(Resource.class);
                when(trNsScoped.withName(ref.getName())).thenReturn(trResource);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void stubGetTaskRun(String taskRunName, String namespace,
                                 String conditionStatus, String message) {
        V1APIGroupDSL v1 = mockTektonClient.v1();
        NonNamespaceOperation<TaskRun, TaskRunList, Resource<TaskRun>> trNsScoped =
                v1.taskRuns().inNamespace(namespace);
        Resource<TaskRun> trResource = mock(Resource.class);
        when(trNsScoped.withName(taskRunName)).thenReturn(trResource);

        Condition taskCondition = new ConditionBuilder()
                .withType("Succeeded")
                .withStatus(conditionStatus)
                .withMessage(message)
                .build();
        TaskRun taskRun = new TaskRunBuilder()
                .withNewStatus()
                    .withConditions(taskCondition)
                .endStatus()
                .build();
        when(trResource.get()).thenReturn(taskRun);
    }

    private PipelineRun buildFailedRunWithTaskRun(String name, String namespace,
                                                    String failedTask, String errorMessage) {
        Condition condition = new ConditionBuilder()
                .withType("Succeeded")
                .withStatus("False")
                .withReason("Failed")
                .build();
        String taskRunName = name + "-" + failedTask + "-pod";
        ChildStatusReference childRef = new ChildStatusReferenceBuilder()
                .withName(taskRunName)
                .withPipelineTaskName(failedTask)
                .withKind("TaskRun")
                .build();
        return new PipelineRunBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec().withNewPipelineRef().withName("my-app").endPipelineRef().endSpec()
                .withNewStatus()
                    .withStartTime("2026-04-07T12:00:00Z")
                    .withCompletionTime("2026-04-07T12:05:00Z")
                    .withConditions(condition)
                    .withChildReferences(childRef)
                .endStatus()
                .build();
    }

    private PipelineRun buildInProgressRunWithTaskRun(String name, String namespace, String runningTask) {
        Condition condition = new ConditionBuilder()
                .withType("Succeeded")
                .withStatus("Unknown")
                .withReason("Running")
                .build();
        String taskRunName = name + "-" + runningTask + "-pod";
        ChildStatusReference childRef = new ChildStatusReferenceBuilder()
                .withName(taskRunName)
                .withPipelineTaskName(runningTask)
                .withKind("TaskRun")
                .build();
        return new PipelineRunBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec().withNewPipelineRef().withName("my-app").endPipelineRef().endSpec()
                .withNewStatus()
                    .withStartTime("2026-04-07T12:00:00Z")
                    .withConditions(condition)
                    .withChildReferences(childRef)
                .endStatus()
                .build();
    }
}
