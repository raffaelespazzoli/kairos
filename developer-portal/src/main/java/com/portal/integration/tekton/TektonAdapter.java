package com.portal.integration.tekton;

import com.portal.build.BuildDetailDto;
import com.portal.build.BuildSummaryDto;

import java.util.List;

/**
 * Adapter for creating and querying builds on the CI platform.
 * Production implementation uses Fabric8 Kubernetes/Tekton client;
 * dev-mode mock bypasses cluster calls entirely.
 */
public interface TektonAdapter {

    /**
     * Creates a PipelineRun in the specified namespace on the target cluster.
     *
     * @param appName        portal application name (used to derive pipeline name)
     * @param namespace      Kubernetes namespace for the PipelineRun
     * @param clusterApiUrl  target cluster API server URL
     * @param clusterToken   Vault-issued bearer token for the cluster
     * @return build summary with PipelineRun metadata translated to portal domain
     */
    BuildSummaryDto triggerBuild(String appName, String namespace,
                                 String clusterApiUrl, String clusterToken);

    /**
     * Lists all builds for an application, most recent first.
     */
    List<BuildSummaryDto> listBuilds(String appName, String namespace,
                                      String clusterApiUrl, String clusterToken);

    /**
     * Returns detailed build information including failure context or current stage.
     */
    BuildDetailDto getBuildDetail(String buildId, String namespace,
                                   String clusterApiUrl, String clusterToken);

    /**
     * Retrieves concatenated logs from all steps of a build's pipeline run.
     */
    String getBuildLogs(String buildId, String namespace,
                         String clusterApiUrl, String clusterToken);
}
