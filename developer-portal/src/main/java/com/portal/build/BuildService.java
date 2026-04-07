package com.portal.build;

import com.portal.application.Application;
import com.portal.cluster.Cluster;
import com.portal.integration.secrets.ClusterCredential;
import com.portal.integration.secrets.SecretManagerCredentialProvider;
import com.portal.integration.tekton.TektonAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class BuildService {

    @Inject
    TektonAdapter tektonAdapter;

    @Inject
    SecretManagerCredentialProvider credentialProvider;

    public BuildSummaryDto triggerBuild(Long teamId, Long appId) {
        Application app = Application.findById(appId);
        if (app == null || !app.teamId.equals(teamId)) {
            throw new NotFoundException();
        }

        if (app.buildClusterId == null || app.buildNamespace == null) {
            throw new IllegalArgumentException(
                    "Application does not have build configuration \u2014 "
                    + "it may have been onboarded before CI integration was available");
        }

        Cluster buildCluster = Cluster.findById(app.buildClusterId);
        if (buildCluster == null) {
            throw new IllegalArgumentException("Build cluster no longer exists");
        }

        ClusterCredential credential =
                credentialProvider.getCredentials(buildCluster.name, "portal");

        return tektonAdapter.triggerBuild(
                app.name,
                app.buildNamespace,
                buildCluster.apiServerUrl,
                credential.token());
    }
}
