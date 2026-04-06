CREATE TABLE environments (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    application_id  BIGINT       NOT NULL,
    cluster_id      BIGINT       NOT NULL,
    namespace       VARCHAR(255) NOT NULL,
    promotion_order INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_environments_application_id FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_environments_cluster_id FOREIGN KEY (cluster_id) REFERENCES clusters (id),
    CONSTRAINT uq_environments_application_id_name UNIQUE (application_id, name),
    CONSTRAINT uq_environments_application_id_promotion_order UNIQUE (application_id, promotion_order)
);

CREATE INDEX idx_environments_application_id ON environments (application_id);
