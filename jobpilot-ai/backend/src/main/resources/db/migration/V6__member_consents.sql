CREATE TABLE member_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    policy_version VARCHAR(30) NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_consents_member_type (member_id, consent_type),
    KEY ix_member_consents_member (member_id),
    CONSTRAINT fk_member_consents_member FOREIGN KEY (member_id) REFERENCES members (id)
);
