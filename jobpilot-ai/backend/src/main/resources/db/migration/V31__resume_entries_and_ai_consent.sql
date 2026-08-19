-- Detailed resume facts are kept independently from the one-row matching summary.
-- `entry_type` is a controlled application value (EDUCATION, CAREER, ACTIVITY,
-- AWARD, LANGUAGE, PORTFOLIO). `content` preserves the fields that differ per type.
CREATE TABLE resume_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content JSON NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_resume_entries_member_type_order (member_id, entry_type, display_order, id),
    CONSTRAINT fk_resume_entries_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

-- Existing member_consents already stores a versioned agreement record. The application
-- adds RESUME_AI_PROCESSING as a new string enum value; the DB column is VARCHAR.
