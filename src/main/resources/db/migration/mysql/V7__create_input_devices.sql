CREATE TABLE input_device (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type_key VARCHAR(64) NOT NULL,
    owner_username VARCHAR(80),
    station VARCHAR(120),
    config_json LONGTEXT NOT NULL,
    capabilities_json LONGTEXT NOT NULL,
    token_hash VARCHAR(64) UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_input_device_type_enabled(type_key, enabled),
    INDEX idx_input_device_owner(owner_username)
);

CREATE TABLE input_device_audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    actor_username VARCHAR(80) NOT NULL,
    action VARCHAR(32) NOT NULL,
    details_json LONGTEXT NOT NULL,
    remote_address VARCHAR(64),
    occurred_at TIMESTAMP NOT NULL,
    INDEX idx_input_device_audit_device_time(device_id, occurred_at),
    CONSTRAINT fk_input_device_audit_device FOREIGN KEY (device_id) REFERENCES input_device(id)
);
