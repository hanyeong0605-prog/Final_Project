CREATE TABLE member_daily_visits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_daily_visits_member_date UNIQUE (member_id, visit_date),
    CONSTRAINT fk_member_daily_visits_member FOREIGN KEY (member_id) REFERENCES members (id),
    KEY ix_member_daily_visits_date (visit_date)
);
