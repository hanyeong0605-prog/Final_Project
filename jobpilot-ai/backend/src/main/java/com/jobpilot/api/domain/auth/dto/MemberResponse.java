package com.jobpilot.api.domain.auth.dto;

import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberRole;

public record MemberResponse(Long id, String loginId, String email, String nickname, boolean onboardingCompleted, MemberRole role,
                             boolean passwordLoginEnabled) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getLoginId(), member.getEmail(), member.getNickname(), member.isOnboardingCompleted(), member.getRole(),
                !member.getLoginId().startsWith("oauth-"));
    }
}
