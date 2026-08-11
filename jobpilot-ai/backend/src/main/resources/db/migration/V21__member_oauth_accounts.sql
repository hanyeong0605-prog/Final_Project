CREATE TABLE member_oauth_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_member_oauth_accounts_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX ix_member_oauth_accounts_member ON member_oauth_accounts (member_id);

CREATE TABLE oauth_pending_logins (
    id CHAR(36) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    provider_email VARCHAR(255) NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_pending_provider_subject UNIQUE (provider, provider_subject)
);
