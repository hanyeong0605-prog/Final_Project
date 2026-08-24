package com.jobpilot.api.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.member.dto.NicknameUpdateRequest;
import com.jobpilot.api.domain.member.dto.PasswordUpdateRequest;
import com.jobpilot.api.domain.member.dto.WithdrawalRequest;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {
    @Mock private MemberRepository members;
    @Mock private JdbcTemplate jdbc;
    @Spy private BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    @InjectMocks private MemberAccountService service;

    private Member member(String rawPassword) {
        return new Member("member", "member@example.com", passwords.encode(rawPassword), "기존닉네임");
    }

    @Test
    void changesNicknameAndPasswordAfterCurrentPasswordVerification() {
        Member member = member("before-password");
        when(members.findById(1L)).thenReturn(Optional.of(member));

        service.changeNickname(1L, new NicknameUpdateRequest("새 닉네임"));
        service.changePassword(1L, new PasswordUpdateRequest("before-password", "after-password"));

        assertThat(member.getNickname()).isEqualTo("새 닉네임");
        assertThat(passwords.matches("after-password", member.getPasswordHash())).isTrue();
    }

    @Test
    void withdrawsEvenWhenOptionalLegacyTablesDoNotExist() {
        Member member = member("withdraw-password");
        when(members.findById(1L)).thenReturn(Optional.of(member));

        service.withdraw(1L, new WithdrawalRequest("withdraw-password", null));

        verify(members).delete(member);
        verify(members).flush();
    }

    @Test
    void withdrawsOAuthOnlyMemberWithExplicitConfirmationInsteadOfUnknownPassword() {
        Member member = new Member("oauth-naver-test", "oauth@example.com", passwords.encode("random-password"), "소셜회원");
        when(members.findById(1L)).thenReturn(Optional.of(member));

        service.withdraw(1L, new WithdrawalRequest(null, "회원탈퇴"));

        verify(members).delete(member);
        verify(members).flush();
    }
}
