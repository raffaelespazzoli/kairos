package com.portal.integration.tekton;

import com.portal.build.BuildSummaryDto;

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
}
