package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import com.portal.integration.PortalIntegrationException;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.PipelineRunBuilder;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

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

    KubernetesClient createClient(String clusterApiUrl, String clusterToken) {
        Config config = new ConfigBuilder()
                .withMasterUrl(clusterApiUrl)
                .withOauthToken(clusterToken)
                .withTrustCerts(false)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
