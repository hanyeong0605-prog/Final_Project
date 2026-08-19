CREATE TABLE member_resume_save_states (
    member_id BIGINT NOT NULL PRIMARY KEY,
    save_status VARCHAR(20) NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_member_resume_save_states_member FOREIGN KEY (member_id) REFERENCES members(id)
);
