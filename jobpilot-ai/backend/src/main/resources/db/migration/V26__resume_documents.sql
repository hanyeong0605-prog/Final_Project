CREATE TABLE resume_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    document_type VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    original_filename VARCHAR(500),
    extracted_text LONGTEXT,
    generated_content LONGTEXT,
    structured_content JSON,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_resume_documents_member_created (member_id, created_at DESC),
    CONSTRAINT fk_resume_documents_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);
