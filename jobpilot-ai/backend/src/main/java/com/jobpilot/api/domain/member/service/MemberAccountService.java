package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.auth.exception.InvalidCredentialsException;
import com.jobpilot.api.domain.member.dto.NicknameUpdateRequest;
import com.jobpilot.api.domain.member.dto.PasswordUpdateRequest;
import com.jobpilot.api.domain.member.dto.WithdrawalRequest;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MemberAccountService {
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public MemberAccountService(MemberRepository members, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    public MemberResponse changeNickname(Long memberId, NicknameUpdateRequest request) {
        Member member = member(memberId);
        member.changeNickname(request.nickname().trim());
        return MemberResponse.from(member);
    }

    public void changePassword(Long memberId, PasswordUpdateRequest request) {
        Member member = member(memberId);
        if (isOAuthOnly(member)) {
            throw new IllegalArgumentException("소셜 로그인 계정의 비밀번호는 네이버·카카오·구글에서 변경해 주세요.");
        }
        verifyPassword(request.currentPassword(), member);
        if (passwordEncoder.matches(request.newPassword(), member.getPasswordHash())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    public void withdraw(Long memberId, WithdrawalRequest request) {
        Member member = member(memberId);
        if (isOAuthOnly(member)) {
            if (!"회원탈퇴".equals(request.confirmationText())) {
                throw new IllegalArgumentException("소셜 로그인 계정은 확인 문구 ‘회원탈퇴’를 입력해 주세요.");
            }
        } else {
            verifyPassword(request.password(), member);
        }
        // Production databases have evolved over several schema versions.
        // Delete every known child table that exists before removing members,
        // rather than failing withdrawal because an optional legacy table is absent.
        if (tableExists("job_match_evidences") && tableExists("job_matches")) {
            jdbc.update("DELETE evidence FROM job_match_evidences evidence JOIN job_matches matches ON matches.id = evidence.job_match_id WHERE matches.member_id = ?", memberId);
        }
        if (tableExists("project_skills") && tableExists("projects")) {
            jdbc.update("DELETE skills FROM project_skills skills JOIN projects projects ON projects.id = skills.project_id WHERE projects.member_id = ?", memberId);
        }
        String[] memberTables = {
                "notification_logs", "push_subscriptions", "member_daily_visits", "member_job_events",
                "member_oauth_accounts", "member_consents", "portfolio_documents", "resume_documents",
                "resume_entries", "member_resume_save_states", "interview_session_records", "planner_events",
                "user_interests", "certificate_bookmarks", "credit_transactions", "payments",
                "subscription_payments", "subscriptions", "credit_balances", "job_matches", "projects",
                "self_introductions", "member_skills", "certificates", "education_histories",
                "member_specifications", "member_profiles"
        };
        for (String table : memberTables) {
            if (tableExists(table)) jdbc.update("DELETE FROM " + table + " WHERE member_id = ?", memberId);
        }
        if (tableExists("employer_accounts")) jdbc.update("UPDATE employer_accounts SET reviewed_by = NULL WHERE reviewed_by = ?", memberId);
        members.delete(member);
        members.flush();
    }

    private Member member(Long memberId) {
        return members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
    }

    private void verifyPassword(String rawPassword, Member member) {
        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) throw new InvalidCredentialsException();
    }

    private boolean isOAuthOnly(Member member) {
        // OAuth signup stores a random non-login password and an oauth-* login
        // id. It must never be presented as a password users can change.
        return member.getLoginId().startsWith("oauth-");
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
