CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_email (email)
);

CREATE TABLE member_profiles (
    member_id BIGINT NOT NULL,
    target_role VARCHAR(80) NOT NULL,
    target_job_family VARCHAR(80) NOT NULL,
    preferred_locations JSON NULL,
    available_from DATE NULL,
    experience_type VARCHAR(30) NOT NULL DEFAULT 'ENTRY',
    github_username VARCHAR(100) NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_member_profiles_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE job_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_sources_code (code)
);

CREATE TABLE skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skills_name (name)
);

CREATE TABLE skill_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    skill_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_aliases_alias (alias),
    CONSTRAINT fk_skill_aliases_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE job_postings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    external_job_id VARCHAR(150) NOT NULL,
    title VARCHAR(500) NOT NULL,
    company_name VARCHAR(255) NULL,
    description MEDIUMTEXT NULL,
    source_url VARCHAR(1500) NOT NULL,
    location VARCHAR(255) NULL,
    employment_type VARCHAR(50) NULL,
    experience_type VARCHAR(50) NULL,
    published_at DATETIME NULL,
    deadline_at DATETIME NULL,
    is_rolling_deadline BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_updated_at DATETIME NULL,
    raw_payload JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_postings_source_external (source_id, external_job_id),
    KEY ix_job_postings_status_deadline (status, deadline_at),
    KEY ix_job_postings_company_title (company_name, title),
    CONSTRAINT fk_job_postings_source FOREIGN KEY (source_id) REFERENCES job_sources (id)
);

CREATE TABLE job_requirements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    source_excerpt TEXT NOT NULL,
    importance VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    PRIMARY KEY (id),
    KEY ix_job_requirements_posting_type (job_posting_id, type),
    CONSTRAINT fk_job_requirements_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
);

CREATE TABLE job_skills (
    job_posting_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    requirement_type VARCHAR(20) NOT NULL,
    source_excerpt TEXT NOT NULL,
    PRIMARY KEY (job_posting_id, skill_id, requirement_type),
    CONSTRAINT fk_job_skills_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
    CONSTRAINT fk_job_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    role_description TEXT NULL,
    problem_description TEXT NULL,
    solution_description TEXT NULL,
    result_description TEXT NULL,
    github_url VARCHAR(1000) NULL,
    deployment_url VARCHAR(1000) NULL,
    started_at DATE NULL,
    ended_at DATE NULL,
    PRIMARY KEY (id),
    KEY ix_projects_member (member_id),
    CONSTRAINT fk_projects_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE project_skills (
    project_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, skill_id),
    CONSTRAINT fk_project_skills_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE member_skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    self_reported_level VARCHAR(20) NULL,
    note VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_skills_member_skill (member_id, skill_id),
    CONSTRAINT fk_member_skills_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    issuer VARCHAR(255) NULL,
    acquired_at DATE NULL,
    expires_at DATE NULL,
    official_url VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY ix_certificates_member (member_id),
    CONSTRAINT fk_certificates_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE education_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NULL,
    started_at DATE NULL,
    ended_at DATE NULL,
    result_url VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY ix_education_histories_member (member_id),
    CONSTRAINT fk_education_histories_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE job_matches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    grade VARCHAR(30) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    summary_comment TEXT NULL,
    analyzed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_matches_member_job (member_id, job_posting_id),
    KEY ix_job_matches_member_grade_score (member_id, grade, score),
    CONSTRAINT fk_job_matches_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_job_matches_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
);

CREATE TABLE job_match_evidences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_match_id BIGINT NOT NULL,
    job_requirement_id BIGINT NULL,
    skill_id BIGINT NULL,
    member_evidence_type VARCHAR(30) NOT NULL,
    member_evidence_id BIGINT NULL,
    status VARCHAR(30) NOT NULL,
    comment TEXT NULL,
    PRIMARY KEY (id),
    KEY ix_job_match_evidences_match (job_match_id),
    CONSTRAINT fk_job_match_evidences_match FOREIGN KEY (job_match_id) REFERENCES job_matches (id),
    CONSTRAINT fk_job_match_evidences_requirement FOREIGN KEY (job_requirement_id) REFERENCES job_requirements (id),
    CONSTRAINT fk_job_match_evidences_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE opportunities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(30) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    external_id VARCHAR(150) NULL,
    title VARCHAR(500) NOT NULL,
    organization VARCHAR(255) NULL,
    description TEXT NULL,
    source_url VARCHAR(1500) NOT NULL,
    application_start_at DATETIME NULL,
    deadline_at DATETIME NULL,
    event_start_at DATETIME NULL,
    event_end_at DATETIME NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_opportunities_source_external (source_name, external_id),
    KEY ix_opportunities_type_deadline (type, deadline_at)
);

CREATE TABLE opportunity_skills (
    opportunity_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (opportunity_id, skill_id),
    CONSTRAINT fk_opportunity_skills_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunities (id),
    CONSTRAINT fk_opportunity_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE user_interests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_interests_member_target (member_id, target_type, target_id),
    KEY ix_user_interests_member (member_id),
    CONSTRAINT fk_user_interests_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE planner_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    title VARCHAR(500) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NULL,
    all_day BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_planner_events_member_source_type (member_id, source_type, source_id, event_type),
    KEY ix_planner_events_member_starts (member_id, starts_at),
    CONSTRAINT fk_planner_events_member FOREIGN KEY (member_id) REFERENCES members (id)
);

INSERT INTO job_sources (code, display_name, enabled) VALUES
    ('WORK24', '고용24', TRUE),
    ('JOBKOREA', '잡코리아', FALSE),
    ('MANUAL', '직접 등록', TRUE);
