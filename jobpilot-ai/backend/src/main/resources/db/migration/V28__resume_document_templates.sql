ALTER TABLE resume_documents
    ADD COLUMN template_key VARCHAR(40) NULL AFTER original_filename;
