ALTER TABLE applications
    ADD COLUMN build_cluster_id BIGINT REFERENCES clusters(id),
    ADD COLUMN build_namespace  VARCHAR(255);
