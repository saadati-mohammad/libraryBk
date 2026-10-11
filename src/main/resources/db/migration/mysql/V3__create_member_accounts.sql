CREATE TABLE member_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    person_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_account_username UNIQUE (username),
    CONSTRAINT uk_member_account_person UNIQUE (person_id),
    CONSTRAINT fk_member_account_person FOREIGN KEY (person_id) REFERENCES person (id)
);
