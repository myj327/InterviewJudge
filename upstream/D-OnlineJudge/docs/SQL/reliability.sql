-- Apply explicitly to the unified acceptance environment; do not modify R1 volumes.
CREATE TABLE IF NOT EXISTS doj_submission.event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route VARCHAR(48) NOT NULL,
    payload LONGTEXT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_outbox_event (event_key),
    KEY ix_outbox_due (state, available_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS doj_problem.stats_inbox (
    submission_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS doj_user.solved_problem (
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, problem_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS doj_submission.review_job (
    submission_id BIGINT NOT NULL PRIMARY KEY,
    state VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) CHARACTER SET ascii DEFAULT NULL,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
