package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@IfBuildProperty(name = "portal.tekton.provider", stringValue = "dev")
public class DevTektonAdapter implements TektonAdapter {

    @Inject
    DeepLinkService deepLinkService;

    @Override
    public BuildSummaryDto triggerBuild(String appName, String namespace,
                                         String clusterApiUrl, String clusterToken) {
        String mockRunId = appName + "-" + UUID.randomUUID().toString().substring(0, 5);
        return new BuildSummaryDto(
                mockRunId,
                "Building",
                Instant.now(),
                appName,
                deepLinkService.generateTektonLink(mockRunId).orElse(null));
    }
}
