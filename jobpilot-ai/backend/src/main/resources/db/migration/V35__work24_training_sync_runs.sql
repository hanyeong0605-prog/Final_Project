CREATE TABLE work24_training_sync_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    imported_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY ix_work24_training_sync_runs_started (started_at)
);
