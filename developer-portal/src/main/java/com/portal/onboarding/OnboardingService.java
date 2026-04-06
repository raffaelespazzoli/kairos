package com.portal.onboarding;

import com.portal.application.Application;
import com.portal.auth.TeamContext;
import com.portal.cluster.ClusterDto;
import com.portal.cluster.ClusterService;
import com.portal.deeplink.DeepLinkService;
import com.portal.environment.Environment;
import com.portal.gitops.ManifestGenerator;
import com.portal.gitops.OnboardingPrBuilder;
import com.portal.integration.git.model.PullRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Assembles the onboarding provisioning plan: namespaces, ArgoCD applications,
 * promotion chain ordering, and generated manifests.
 */
@ApplicationScoped
public class OnboardingService {

    private static final Map<String, Integer> ENV_ORDER = Map.ofEntries(
        Map.entry("dev", 0),
        Map.entry("development", 0),
        Map.entry("qa", 1),
        Map.entry("test", 1),
        Map.entry("staging", 2),
        Map.entry("stage", 2),
        Map.entry("uat", 3),
        Map.entry("preprod", 4),
        Map.entry("prod", 5),
        Map.entry("production", 5)
    );

    @Inject
    ManifestGenerator manifestGenerator;

    @Inject
    ClusterService clusterService;

    @Inject
    TeamContext teamContext;

    @Inject
    OnboardingPrBuilder onboardingPrBuilder;

    @Inject
    DeepLinkService deepLinkService;

    @Transactional
    public OnboardingResultDto confirmOnboarding(OnboardingConfirmRequest request) {
        String teamName = teamContext.getTeamIdentifier();
        Long teamId = teamContext.getTeamId();

        OnboardingPlanRequest planRequest = new OnboardingPlanRequest(
                request.gitRepoUrl(), request.appName(), request.runtimeType(),
                request.detectedEnvironments(), request.environmentClusterMap(),
                request.buildClusterId());
        OnboardingPlanResult plan = buildPlan(request.appName(), planRequest);

        Map<String, String> manifests = plan.generatedManifests();

        PullRequest pr = onboardingPrBuilder.createOnboardingPr(teamName, request.appName(), manifests);

        Application app = new Application();
        app.name = request.appName();
        app.teamId = teamId;
        app.gitRepoUrl = request.gitRepoUrl();
        app.runtimeType = request.runtimeType();
        app.onboardingPrUrl = pr.url();
        app.onboardedAt = Instant.now();
        app.persistAndFlush();

        List<String> orderedEnvs = plan.promotionChain();
        for (int i = 0; i < orderedEnvs.size(); i++) {
            String envName = orderedEnvs.get(i);
            Environment env = new Environment();
            env.name = envName;
            env.applicationId = app.id;
            env.clusterId = request.environmentClusterMap().get(envName);
            env.namespace = teamName.toLowerCase(Locale.ROOT) + "-"
                    + request.appName().toLowerCase(Locale.ROOT) + "-"
                    + envName.toLowerCase(Locale.ROOT);
            env.promotionOrder = i;
            env.persist();
        }

        return new OnboardingResultDto(
                app.id, app.name, pr.url(),
                plan.namespaces().size(), plan.argoCdApps().size(),
                orderedEnvs,
                deepLinkService.generateDevSpacesLink(request.gitRepoUrl()).orElse(null));
    }

    public OnboardingPlanResult buildPlan(String appName, OnboardingPlanRequest request) {
        String teamName = teamContext.getTeamIdentifier();
        String safeApp = slugify(appName);

        List<String> orderedEnvs = orderEnvironments(request.detectedEnvironments());

        ClusterDto buildCluster = clusterService.findById(request.buildClusterId());
        Map<String, String> clusterApiUrls = new HashMap<>();
        clusterApiUrls.put(buildCluster.name(), buildCluster.apiServerUrl());

        List<PlannedNamespace> namespaces = new ArrayList<>();
        List<PlannedArgoCdApp> argoCdApps = new ArrayList<>();

        String buildNs = buildNamespaceName(teamName, safeApp, "build");
        namespaces.add(new PlannedNamespace(buildNs, buildCluster.name(), "build", true));
        argoCdApps.add(new PlannedArgoCdApp(
                safeApp + "-build", buildCluster.name(), buildNs,
                ".helm/build", "values-build.yaml", true));

        for (String env : orderedEnvs) {
            Long clusterId = request.environmentClusterMap().get(env);
            if (clusterId == null) {
                throw new IllegalArgumentException("No cluster mapped for environment: " + env);
            }
            ClusterDto cluster = clusterService.findById(clusterId);
            clusterApiUrls.put(cluster.name(), cluster.apiServerUrl());

            String safeEnv = slugify(env);
            String nsName = buildNamespaceName(teamName, safeApp, safeEnv);
            namespaces.add(new PlannedNamespace(nsName, cluster.name(), env, false));
            argoCdApps.add(new PlannedArgoCdApp(
                    safeApp + "-run-" + safeEnv, cluster.name(), nsName,
                    ".helm/run", "values-run-" + safeEnv + ".yaml", false));
        }

        OnboardingPlanResult plan = new OnboardingPlanResult(
                appName, teamName, namespaces, argoCdApps, orderedEnvs, Map.of());

        Map<String, String> manifests = manifestGenerator.generateAllManifests(
                plan, request.gitRepoUrl(), clusterApiUrls);

        return new OnboardingPlanResult(
                appName, teamName, namespaces, argoCdApps, orderedEnvs, manifests);
    }

    private List<String> orderEnvironments(List<String> envNames) {
        return envNames.stream()
                .sorted(Comparator.comparingInt(
                        (String env) -> ENV_ORDER.getOrDefault(env.toLowerCase(), 100))
                        .thenComparing(String::compareToIgnoreCase))
                .toList();
    }

    private String buildNamespaceName(String teamName, String appName, String envOrBuild) {
        return String.join("-",
                slugify(teamName),
                appName,
                envOrBuild);
    }

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_HYPHEN = Pattern.compile("^-+|-+$");

    /**
     * Converts an arbitrary string to a DNS-1123 compliant label segment:
     * lowercase, alphanumeric and hyphens only, no leading/trailing hyphens.
     */
    static String slugify(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("-");
        s = LEADING_TRAILING_HYPHEN.matcher(s).replaceAll("");
        return s;
    }
}
