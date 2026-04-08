package com.portal.build;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerCredentialProvider;
import com.portal.integration.tekton.TektonAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class BuildService {

    @Inject
    TektonAdapter tektonAdapter;

    @Inject
    SecretManagerCredentialProvider credentialProvider;

    public BuildSummaryDto triggerBuild(Long teamId, Long appId) {
        Application app = resolveTeamApplication(teamId, appId);
        Cluster buildCluster = resolveBuildCluster(app);
        ClusterCredential credential = credentialProvider.getCredentials(buildCluster.name, "portal");

        return tektonAdapter.triggerBuild(
                app.name, app.buildNamespace, buildCluster.apiServerUrl, credential.token());
    }

    public List<BuildSummaryDto> listBuilds(Long teamId, Long appId) {
        Application app = resolveTeamApplication(teamId, appId);
        Cluster buildCluster = resolveBuildCluster(app);
        ClusterCredential credential = credentialProvider.getCredentials(buildCluster.name, "portal");

        return tektonAdapter.listBuilds(
                app.name, app.buildNamespace, buildCluster.apiServerUrl, credential.token());
    }

    public BuildDetailDto getBuildDetail(Long teamId, Long appId, String buildId) {
        Application app = resolveTeamApplication(teamId, appId);
        Cluster buildCluster = resolveBuildCluster(app);
        ClusterCredential credential = credentialProvider.getCredentials(buildCluster.name, "portal");

        BuildDetailDto detail = tektonAdapter.getBuildDetail(
                buildId, app.buildNamespace, buildCluster.apiServerUrl, credential.token());
        if (detail.applicationName() != null && !app.name.equals(detail.applicationName())) {
            throw new NotFoundException();
        }
        return detail;
    }

    public String getBuildLogs(Long teamId, Long appId, String buildId) {
        Application app = resolveTeamApplication(teamId, appId);
        Cluster buildCluster = resolveBuildCluster(app);
        ClusterCredential credential = credentialProvider.getCredentials(buildCluster.name, "portal");

        BuildDetailDto detail = tektonAdapter.getBuildDetail(
                buildId, app.buildNamespace, buildCluster.apiServerUrl, credential.token());
        if (detail.applicationName() != null && !app.name.equals(detail.applicationName())) {
            throw new NotFoundException();
        }

        return tektonAdapter.getBuildLogs(
                buildId, app.buildNamespace, buildCluster.apiServerUrl, credential.token());
    }

    private Application resolveTeamApplication(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }
        if (app.buildClusterId == null || app.buildNamespace == null) {
            throw new IllegalStateException(
                    "Application does not have build configuration \u2014 "
                    + "it may have been onboarded before CI integration was available");
        }
        return app;
    }

    private Cluster resolveBuildCluster(Application app) {
        Cluster buildCluster = Cluster.findById(app.buildClusterId);
        if (buildCluster == null) {
            throw new IllegalStateException("Build cluster no longer exists");
        }
        return buildCluster;
    }
}
