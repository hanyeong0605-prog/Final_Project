CREATE TABLE home_promotions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slot_type VARCHAR(20) NOT NULL,
    source_key VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    provider VARCHAR(255) NULL,
    description VARCHAR(1500) NULL,
    image_url VARCHAR(1500) NULL,
    target_url VARCHAR(1500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_home_promotions_source (slot_type, source_key),
    KEY ix_home_promotions_slot_created (slot_type, created_at)
);
