ALTER TABLE members
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER nickname;

CREATE INDEX ix_members_role ON members (role);

-- Initial service operator. Other administrators can be assigned from the admin console.
UPDATE members SET role = 'ADMIN' WHERE login_id = 'hanyeong';
