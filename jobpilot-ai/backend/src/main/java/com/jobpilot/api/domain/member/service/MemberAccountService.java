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
        verifyPassword(request.currentPassword(), member);
        if (passwordEncoder.matches(request.newPassword(), member.getPasswordHash())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    public void withdraw(Long memberId, WithdrawalRequest request) {
        Member member = member(memberId);
        verifyPassword(request.password(), member);
        jdbc.update("DELETE FROM job_match_evidences WHERE job_match_id IN (SELECT id FROM job_matches WHERE member_id = ?)", memberId);
        jdbc.update("DELETE FROM job_matches WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM project_skills WHERE project_id IN (SELECT id FROM projects WHERE member_id = ?)", memberId);
        jdbc.update("DELETE FROM projects WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_skills WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM certificates WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM education_histories WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM self_introductions WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM planner_events WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM user_interests WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_specifications WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_profiles WHERE member_id = ?", memberId);
        members.delete(member);
    }

    private Member member(Long memberId) {
        return members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
    }

    private void verifyPassword(String rawPassword, Member member) {
        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) throw new InvalidCredentialsException();
    }
}
