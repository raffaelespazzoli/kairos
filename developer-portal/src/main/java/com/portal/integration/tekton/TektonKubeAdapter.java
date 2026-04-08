package com.portal.integration.tekton;

import com.portal.build.BuildDetailDto;
import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import com.portal.integration.PortalIntegrationException;
import io.fabric8.knative.pkg.apis.Condition;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.v1.ChildStatusReference;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.PipelineRunBuilder;
import io.fabric8.tekton.v1.TaskRun;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@IfBuildProperty(name = "portal.tekton.provider", stringValue = "tekton", enableIfMissing = true)
public class TektonKubeAdapter implements TektonAdapter {

    @Inject
    DeepLinkService deepLinkService;

    @Override
    public BuildSummaryDto triggerBuild(String appName, String namespace,
                                         String clusterApiUrl, String clusterToken) {
        try (KubernetesClient kubeClient = createClient(clusterApiUrl, clusterToken)) {
            TektonClient tektonClient = kubeClient.adapt(TektonClient.class);

            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                        .withGenerateName(appName + "-")
                        .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                        .withNewPipelineRef()
                            .withName(appName)
                        .endPipelineRef()
                    .endSpec()
                    .build();

            PipelineRun created = tektonClient.v1().pipelineRuns()
                    .inNamespace(namespace)
                    .resource(pipelineRun)
                    .create();

            String runName = created.getMetadata().getName();
            return new BuildSummaryDto(
                    runName,
                    "Building",
                    Instant.now(),
                    null,
                    null,
                    null,
                    appName,
                    deepLinkService.generateTektonLink(runName).orElse(null));

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            String deepLink = deepLinkService.generateTektonLink(appName).orElse(null);
            throw new PortalIntegrationException("tekton", "triggerBuild",
                    "Build could not be started \u2014 the build cluster is unreachable",
                    deepLink, e);
        }
    }

    @Override
    public List<BuildSummaryDto> listBuilds(String appName, String namespace,
                                             String clusterApiUrl, String clusterToken) {
        try (KubernetesClient kubeClient = createClient(clusterApiUrl, clusterToken)) {
            TektonClient tektonClient = kubeClient.adapt(TektonClient.class);

            List<PipelineRun> runs = new ArrayList<>(tektonClient.v1().pipelineRuns()
                    .inNamespace(namespace)
                    .withLabel("tekton.dev/pipeline", appName)
                    .list()
                    .getItems());

            runs.sort((a, b) -> {
                String ta = a.getStatus() != null ? a.getStatus().getStartTime() : null;
                String tb = b.getStatus() != null ? b.getStatus().getStartTime() : null;
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });

            List<BuildSummaryDto> result = new ArrayList<>();
            for (PipelineRun run : runs) {
                Instant startedAt = parseInstant(run.getStatus() != null ? run.getStatus().getStartTime() : null);
                Instant completedAt = parseInstant(run.getStatus() != null ? run.getStatus().getCompletionTime() : null);
                String status = translateStatus(run);

                String imageRef = "Passed".equals(status) ? extractImageReference(run) : null;
                result.add(new BuildSummaryDto(
                        run.getMetadata().getName(),
                        status,
                        startedAt,
                        completedAt,
                        computeDuration(startedAt, completedAt),
                        imageRef,
                        appName,
                        deepLinkService.generateTektonLink(run.getMetadata().getName()).orElse(null)));
            }
            return result;

        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("tekton", "listBuilds",
                    "Build information could not be retrieved \u2014 the build cluster is unreachable",
                    deepLinkService.generateTektonLink(appName).orElse(null), e);
        }
    }

    @Override
    public BuildDetailDto getBuildDetail(String buildId, String namespace,
                                          String clusterApiUrl, String clusterToken) {
        try (KubernetesClient kubeClient = createClient(clusterApiUrl, clusterToken)) {
            TektonClient tektonClient = kubeClient.adapt(TektonClient.class);

            PipelineRun run = tektonClient.v1().pipelineRuns()
                    .inNamespace(namespace).withName(buildId).get();
            if (run == null) {
                throw new jakarta.ws.rs.NotFoundException("Build not found: " + buildId);
            }

            String status = translateStatus(run);
            Instant startedAt = parseInstant(run.getStatus() != null ? run.getStatus().getStartTime() : null);
            Instant completedAt = parseInstant(run.getStatus() != null ? run.getStatus().getCompletionTime() : null);
            String appName = run.getSpec() != null && run.getSpec().getPipelineRef() != null
                    ? run.getSpec().getPipelineRef().getName() : null;

            String imageReference = null;
            String failedStageName = null;
            String errorSummary = null;
            String currentStage = null;

            if ("Passed".equals(status)) {
                imageReference = extractImageReference(run);
            }

            List<ChildStatusReference> childRefs = run.getStatus() != null
                    ? run.getStatus().getChildReferences() : null;

            if ("Failed".equals(status) && childRefs != null) {
                for (ChildStatusReference ref : childRefs) {
                    if (!"TaskRun".equals(ref.getKind())) continue;
                    TaskRun taskRun = tektonClient.v1().taskRuns()
                            .inNamespace(namespace).withName(ref.getName()).get();
                    if (taskRun == null || taskRun.getStatus() == null) continue;
                    Condition taskCond = findSucceededCondition(taskRun.getStatus().getConditions());
                    if (taskCond != null && "False".equals(taskCond.getStatus())) {
                        failedStageName = humanizeTaskName(ref.getPipelineTaskName());
                        errorSummary = taskCond.getMessage();
                        break;
                    }
                }
            }

            if ("Building".equals(status) && childRefs != null) {
                for (ChildStatusReference ref : childRefs) {
                    if (!"TaskRun".equals(ref.getKind())) continue;
                    TaskRun taskRun = tektonClient.v1().taskRuns()
                            .inNamespace(namespace).withName(ref.getName()).get();
                    if (taskRun == null || taskRun.getStatus() == null) continue;
                    Condition taskCond = findSucceededCondition(taskRun.getStatus().getConditions());
                    if (taskCond != null && "Unknown".equals(taskCond.getStatus())) {
                        currentStage = humanizeTaskName(ref.getPipelineTaskName());
                        break;
                    }
                }
            }

            return new BuildDetailDto(
                    buildId,
                    status,
                    startedAt,
                    completedAt,
                    computeDuration(startedAt, completedAt),
                    appName,
                    imageReference,
                    failedStageName,
                    errorSummary,
                    currentStage,
                    deepLinkService.generateTektonLink(buildId).orElse(null));

        } catch (jakarta.ws.rs.NotFoundException e) {
            throw e;
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("tekton", "getBuildDetail",
                    "Build information could not be retrieved \u2014 the build cluster is unreachable",
                    deepLinkService.generateTektonLink(buildId).orElse(null), e);
        }
    }

    @Override
    public String getBuildLogs(String buildId, String namespace,
                                String clusterApiUrl, String clusterToken) {
        try (KubernetesClient kubeClient = createClient(clusterApiUrl, clusterToken)) {
            List<Pod> pods = kubeClient.pods().inNamespace(namespace)
                    .withLabel("tekton.dev/pipelineRun", buildId)
                    .list().getItems();

            if (pods.isEmpty()) {
                throw new jakarta.ws.rs.NotFoundException("No logs found for build: " + buildId);
            }

            StringBuilder logs = new StringBuilder();
            for (Pod pod : pods) {
                String pipelineTask = pod.getMetadata().getLabels()
                        .getOrDefault("tekton.dev/pipelineTask", "unknown");

                for (Container container : pod.getSpec().getContainers()) {
                    if (!container.getName().startsWith("step-")) continue;
                    String stepName = container.getName().substring(5);
                    logs.append("=== ").append(humanizeTaskName(pipelineTask))
                            .append(" / ").append(stepName).append(" ===\n");
                    try {
                        String podLog = kubeClient.pods().inNamespace(namespace)
                                .withName(pod.getMetadata().getName())
                                .inContainer(container.getName())
                                .getLog();
                        logs.append(podLog).append("\n");
                    } catch (Exception e) {
                        logs.append("[Log unavailable]\n");
                    }
                }
            }
            return logs.toString();

        } catch (jakarta.ws.rs.NotFoundException e) {
            throw e;
        } catch (PortalIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PortalIntegrationException("tekton", "getBuildLogs",
                    "Build information could not be retrieved \u2014 the build cluster is unreachable",
                    deepLinkService.generateTektonLink(buildId).orElse(null), e);
        }
    }

    String translateStatus(PipelineRun run) {
        if (run.getStatus() == null || run.getStatus().getConditions() == null) {
            return "Pending";
        }
        Condition succeeded = findSucceededCondition(run.getStatus().getConditions());
        if (succeeded == null) {
            return "Pending";
        }
        if ("Unknown".equals(succeeded.getStatus())) {
            String reason = succeeded.getReason();
            if ("Running".equals(reason) || "Started".equals(reason)) {
                return "Building";
            }
            return "Pending";
        }
        if ("True".equals(succeeded.getStatus())) {
            return "Passed";
        }
        if ("PipelineRunCancelled".equals(succeeded.getReason())) {
            return "Cancelled";
        }
        return "Failed";
    }

    private Condition findSucceededCondition(List<Condition> conditions) {
        if (conditions == null) return null;
        return conditions.stream()
                .filter(c -> "Succeeded".equals(c.getType()))
                .findFirst()
                .orElse(null);
    }

    private String extractImageReference(PipelineRun run) {
        if (run.getStatus() == null || run.getStatus().getResults() == null) {
            return null;
        }
        return run.getStatus().getResults().stream()
                .filter(r -> "IMAGE_URL".equalsIgnoreCase(r.getName())
                        || "image-url".equalsIgnoreCase(r.getName()))
                .map(r -> r.getValue().getStringVal())
                .findFirst()
                .orElse(null);
    }

    String humanizeTaskName(String taskName) {
        if (taskName == null) return "Unknown";
        String[] words = taskName.split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) result.append(word.substring(1));
        }
        return result.toString();
    }

    String computeDuration(Instant start, Instant end) {
        if (start == null) return null;
        Instant effectiveEnd = (end != null) ? end : Instant.now();
        long seconds = Duration.between(start, effectiveEnd).getSeconds();
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) return minutes + "m " + remainingSeconds + "s";
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return hours + "h " + remainingMinutes + "m";
    }

    private Instant parseInstant(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return null;
        return Instant.parse(timestamp);
    }

    KubernetesClient createClient(String clusterApiUrl, String clusterToken) {
        Config config = new ConfigBuilder()
                .withMasterUrl(clusterApiUrl)
                .withOauthToken(clusterToken)
                .withTrustCerts(false)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
