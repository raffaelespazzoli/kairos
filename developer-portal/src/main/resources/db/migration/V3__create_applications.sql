CREATE TABLE applications (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255)  NOT NULL,
    team_id           BIGINT        NOT NULL,
    git_repo_url      VARCHAR(512)  NOT NULL,
    runtime_type      VARCHAR(100)  NOT NULL,
    onboarding_pr_url VARCHAR(512),
    onboarded_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_applications_team_id FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT uq_applications_team_id_name UNIQUE (team_id, name)
);

CREATE INDEX idx_applications_team_id ON applications (team_id);
