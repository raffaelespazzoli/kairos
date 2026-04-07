package com.portal.application;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "applications")
public class Application extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(name = "team_id", nullable = false)
    public Long teamId;

    @Column(name = "git_repo_url", nullable = false)
    public String gitRepoUrl;

    @Column(name = "runtime_type", nullable = false)
    public String runtimeType;

    @Column(name = "onboarding_pr_url")
    public String onboardingPrUrl;

    @Column(name = "onboarded_at")
    public Instant onboardedAt;

    @Column(name = "build_cluster_id")
    public Long buildClusterId;

    @Column(name = "build_namespace")
    public String buildNamespace;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static List<Application> findByTeam(Long teamId) {
        return list("teamId", Sort.by("name"), teamId);
    }
}
