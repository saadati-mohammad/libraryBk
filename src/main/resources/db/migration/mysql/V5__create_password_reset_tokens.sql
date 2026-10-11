CREATE TABLE password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    username VARCHAR(254) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    INDEX ix_password_reset_username_created (username, created_at)
) ENGINE=InnoDB;
