CREATE TABLE api_rate_limits (
    key_hash VARCHAR(64) NOT NULL,
    endpoint_group VARCHAR(40) NOT NULL,
    window_started_at TIMESTAMP NOT NULL,
    requests INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (key_hash, endpoint_group),
    CONSTRAINT chk_api_rate_limit_requests CHECK (requests > 0)
);

CREATE INDEX idx_api_rate_limits_updated
    ON api_rate_limits(updated_at);
