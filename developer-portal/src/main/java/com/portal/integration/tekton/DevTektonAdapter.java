package com.portal.integration.tekton;

import com.portal.build.BuildDetailDto;
import com.portal.build.BuildSummaryDto;
import com.portal.deeplink.DeepLinkService;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
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
                null,
                null,
                null,
                appName,
                deepLinkService.generateTektonLink(mockRunId).orElse(null));
    }

    @Override
    public List<BuildSummaryDto> listBuilds(String appName, String namespace,
                                             String clusterApiUrl, String clusterToken) {
        Instant now = Instant.now();
        return List.of(
                new BuildSummaryDto(appName + "-abc12", "Building", now, null, "0s",
                        null, appName, deepLinkService.generateTektonLink(appName + "-abc12").orElse(null)),
                new BuildSummaryDto(appName + "-def34", "Passed",
                        now.minusSeconds(600), now.minusSeconds(300), "5m 0s",
                        "registry.example.com/team/" + appName + ":latest",
                        appName, deepLinkService.generateTektonLink(appName + "-def34").orElse(null)),
                new BuildSummaryDto(appName + "-ghi56", "Failed",
                        now.minusSeconds(1800), now.minusSeconds(1500), "5m 0s",
                        null, appName, deepLinkService.generateTektonLink(appName + "-ghi56").orElse(null)),
                new BuildSummaryDto(appName + "-jkl78", "Cancelled",
                        now.minusSeconds(3600), now.minusSeconds(3500), "1m 40s",
                        null, appName, deepLinkService.generateTektonLink(appName + "-jkl78").orElse(null))
        );
    }

    @Override
    public BuildDetailDto getBuildDetail(String buildId, String namespace,
                                          String clusterApiUrl, String clusterToken) {
        Instant now = Instant.now();
        if (buildId.contains("fail")) {
            return new BuildDetailDto(buildId, "Failed",
                    now.minusSeconds(1800), now.minusSeconds(1500), "5m 0s",
                    "mock-app", null, "Run Tests",
                    "Test failure in ProcessorTest.testNullRefHandling",
                    null, deepLinkService.generateTektonLink(buildId).orElse(null));
        }
        if (buildId.contains("cancel")) {
            return new BuildDetailDto(buildId, "Cancelled",
                    now.minusSeconds(3600), now.minusSeconds(3500), "1m 40s",
                    "mock-app", null, null, null, null,
                    deepLinkService.generateTektonLink(buildId).orElse(null));
        }
        if (buildId.contains("build")) {
            return new BuildDetailDto(buildId, "Building",
                    now.minusSeconds(120), null, "2m 0s",
                    "mock-app", null, null, null,
                    "Running Unit Tests",
                    deepLinkService.generateTektonLink(buildId).orElse(null));
        }
        return new BuildDetailDto(buildId, "Passed",
                now.minusSeconds(600), now.minusSeconds(300), "5m 0s",
                "mock-app", "registry.example.com/team/mock-app:abc1234",
                null, null, null,
                deepLinkService.generateTektonLink(buildId).orElse(null));
    }

    @Override
    public String getBuildLogs(String buildId, String namespace,
                                String clusterApiUrl, String clusterToken) {
        return "=== Clone Source / clone ===\n"
                + "Cloning repository https://github.com/org/mock-app.git\n"
                + "Successfully cloned to /workspace/source\n\n"
                + "=== Run Tests / run-tests ===\n"
                + "Running unit tests...\n"
                + "All 42 tests passed.\n\n"
                + "=== Build Image / build ===\n"
                + "Building container image...\n"
                + "Successfully pushed registry.example.com/team/mock-app:abc1234\n";
    }
}
