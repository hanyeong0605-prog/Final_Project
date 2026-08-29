CREATE TABLE community_posts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, author_member_id BIGINT NOT NULL,
 board_type VARCHAR(20) NOT NULL, title VARCHAR(200) NOT NULL, body VARCHAR(5000) NOT NULL,
 service_feedback BOOLEAN NOT NULL DEFAULT FALSE, private_post BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', view_count BIGINT NOT NULL DEFAULT 0,
 content_hash CHAR(64) NOT NULL, analysis_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 analysis_attempts INT NOT NULL DEFAULT 0, next_analysis_at DATETIME NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_community_list(board_type,status,created_at), KEY ix_community_analysis(analysis_state,next_analysis_at),
 CONSTRAINT fk_community_author FOREIGN KEY(author_member_id) REFERENCES members(id),
 CONSTRAINT ck_community_type CHECK(board_type IN('FREE','QNA')),
 CONSTRAINT ck_community_status CHECK(status IN('PUBLIC','HIDDEN','DELETED')),
 CONSTRAINT ck_community_privacy CHECK(private_post=FALSE OR board_type='QNA'),
 CONSTRAINT ck_community_analysis CHECK(analysis_state IN('SKIPPED','PENDING','PROCESSING','COMPLETED','FAILED'))
);
CREATE TABLE community_comments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,post_id BIGINT NOT NULL,author_member_id BIGINT NOT NULL,
 parent_id BIGINT NULL,body VARCHAR(2000) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_community_comments(post_id,status,created_at),
 CONSTRAINT fk_comment_post FOREIGN KEY(post_id) REFERENCES community_posts(id),
 CONSTRAINT fk_comment_author FOREIGN KEY(author_member_id) REFERENCES members(id),
 CONSTRAINT fk_comment_parent FOREIGN KEY(parent_id) REFERENCES community_comments(id),
 CONSTRAINT ck_comment_status CHECK(status IN('PUBLIC','HIDDEN','DELETED'))
);
CREATE TABLE community_post_likes(post_id BIGINT NOT NULL,member_id BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(post_id,member_id),CONSTRAINT fk_post_like_post FOREIGN KEY(post_id) REFERENCES community_posts(id),CONSTRAINT fk_post_like_member FOREIGN KEY(member_id) REFERENCES members(id));
CREATE TABLE community_comment_likes(comment_id BIGINT NOT NULL,member_id BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(comment_id,member_id),CONSTRAINT fk_comment_like_comment FOREIGN KEY(comment_id) REFERENCES community_comments(id),CONSTRAINT fk_comment_like_member FOREIGN KEY(member_id) REFERENCES members(id));
CREATE TABLE community_post_views(post_id BIGINT NOT NULL,member_id BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(post_id,member_id),CONSTRAINT fk_post_view_post FOREIGN KEY(post_id) REFERENCES community_posts(id),CONSTRAINT fk_post_view_member FOREIGN KEY(member_id) REFERENCES members(id));
CREATE TABLE community_reports(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,target_type VARCHAR(20) NOT NULL,target_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,reason VARCHAR(1000) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'OPEN',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_community_report(target_type,target_id,member_id),CONSTRAINT fk_community_reporter FOREIGN KEY(member_id) REFERENCES members(id),
 CONSTRAINT ck_community_report_target CHECK(target_type IN('POST','COMMENT')),CONSTRAINT ck_community_report_status CHECK(status IN('OPEN','RESOLVED','DISMISSED')));
-- Kept separate from company_review_analyses so community text can never affect company ranking.
CREATE TABLE community_post_analyses(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,post_id BIGINT NOT NULL,content_hash CHAR(64) NOT NULL,
 model_version VARCHAR(150) NOT NULL,policy_version VARCHAR(150) NOT NULL,polarity VARCHAR(20) NOT NULL,
 positive_score DOUBLE NOT NULL,neutral_score DOUBLE NOT NULL,negative_score DOUBLE NOT NULL,emotions JSON NOT NULL,analyzed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_community_analysis(post_id,content_hash,model_version,policy_version),CONSTRAINT fk_community_analysis_post FOREIGN KEY(post_id) REFERENCES community_posts(id),
 CONSTRAINT ck_community_polarity CHECK(polarity IN('POSITIVE','NEUTRAL','NEGATIVE','MIXED')));
CREATE TABLE community_moderation_events(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,target_type VARCHAR(20) NOT NULL,target_id BIGINT NOT NULL,
 admin_member_id BIGINT NOT NULL,action VARCHAR(20) NOT NULL,reason VARCHAR(1000) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_community_moderator FOREIGN KEY(admin_member_id) REFERENCES members(id));
