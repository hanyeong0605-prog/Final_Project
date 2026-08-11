package com.jobpilot.api.domain.admin;

import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberRole;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {
    private final MemberRepository members;

    public AdminAccessService(MemberRepository members) {
        this.members = members;
    }

    public Member requireAdmin(Long memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
        if (member.getRole() != MemberRole.ADMIN) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }
        return member;
    }

    public boolean isAdmin(Long memberId) {
        return members.findById(memberId).map(Member::isAdmin).orElse(false);
    }
}
