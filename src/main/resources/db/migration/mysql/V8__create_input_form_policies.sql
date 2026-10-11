CREATE TABLE input_form_policy (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    form_key VARCHAR(64) NOT NULL,
    owner_username VARCHAR(80) NOT NULL,
    station VARCHAR(120) NOT NULL DEFAULT '',
    allowed_sources_json LONGTEXT NOT NULL,
    default_source VARCHAR(80) NOT NULL,
    updated_by VARCHAR(80) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_input_form_policy_scope UNIQUE (form_key, owner_username, station),
    INDEX idx_input_form_policy_form(form_key)
);
