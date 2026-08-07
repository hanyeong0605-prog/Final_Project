-- Keep existing raw skill rows for traceability, while identifying the rows
-- that are safe to expose in member skill search and matching.
ALTER TABLE skills
    ADD COLUMN catalog_status VARCHAR(20) NOT NULL DEFAULT 'RAW' AFTER category,
    ADD COLUMN parent_skill_id BIGINT NULL AFTER catalog_status,
    ADD COLUMN display_order INT NOT NULL DEFAULT 9999 AFTER parent_skill_id,
    ADD COLUMN normalized_name VARCHAR(100) NULL AFTER display_order,
    ADD KEY ix_skills_catalog_search (catalog_status, category, display_order, name),
    ADD KEY ix_skills_parent (parent_skill_id),
    ADD CONSTRAINT fk_skills_parent FOREIGN KEY (parent_skill_id) REFERENCES skills (id);

UPDATE skills
SET normalized_name = REPLACE(REPLACE(REPLACE(REPLACE(LOWER(TRIM(name)), ' ', ''), '.', ''), '-', ''), '_', '');

ALTER TABLE skill_aliases
    ADD COLUMN normalized_alias VARCHAR(100) NOT NULL DEFAULT '' AFTER alias,
    ADD KEY ix_skill_aliases_normalized_alias (normalized_alias);

UPDATE skill_aliases
SET normalized_alias = REPLACE(REPLACE(REPLACE(REPLACE(LOWER(TRIM(alias)), ' ', ''), '.', ''), '-', ''), '_', '');

ALTER TABLE job_skills
    ADD COLUMN canonical_skill_id BIGINT NULL AFTER skill_id,
    ADD KEY ix_job_skills_canonical_skill (canonical_skill_id),
    ADD CONSTRAINT fk_job_skills_canonical_skill
        FOREIGN KEY (canonical_skill_id) REFERENCES skills (id);
