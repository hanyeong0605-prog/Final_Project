package com.jobpilot.api.domain.admin;

import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Deletes a member and every member-owned record before removing the parent.
 * Most production tables use restrictive foreign keys, so a plain repository
 * delete consistently fails once the member has used any feature. */
@Service
public class AdminMemberDeletionService {
    private final JdbcTemplate jdbc;
    private final MemberRepository members;

    public AdminMemberDeletionService(JdbcTemplate jdbc, MemberRepository members) {
        this.jdbc = jdbc;
        this.members = members;
    }

    @Transactional
    public Member delete(Long memberId) {
        Member target = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));

        // Child of a member-owned child must go first.
        if (tableExists("job_match_evidences") && tableExists("job_matches")) {
            jdbc.update("DELETE evidence FROM job_match_evidences evidence JOIN job_matches matches ON matches.id = evidence.job_match_id WHERE matches.member_id = ?", memberId);
        }
        String[] memberTables = {
                "notification_logs", "push_subscriptions", "member_daily_visits", "member_job_events",
                "member_oauth_accounts", "member_consents", "resume_documents",
                "resume_entries", "member_resume_save_states", "interview_session_records", "planner_events",
                "user_interests", "certificate_bookmarks", "credit_transactions", "payments",
                "subscription_payments", "subscriptions", "credit_balances", "job_matches", "projects",
                "self_introductions", "member_skills", "certificates", "education_histories",
                "member_specifications", "member_profiles"
        };
        for (String table : memberTables) {
            if (tableExists(table)) jdbc.update("DELETE FROM " + table + " WHERE member_id = ?", memberId);
        }

        // An employer approval is historical data; keep the employer row but
        // detach the administrator who performed the review.
        if (tableExists("employer_accounts")) jdbc.update("UPDATE employer_accounts SET reviewed_by = NULL WHERE reviewed_by = ?", memberId);
        members.delete(target);
        members.flush();
        return target;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
