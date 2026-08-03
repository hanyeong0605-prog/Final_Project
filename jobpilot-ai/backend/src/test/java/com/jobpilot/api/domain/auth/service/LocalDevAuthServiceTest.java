package com.jobpilot.api.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalDevAuthServiceTest {
    @Mock private MemberRepository members;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService tokenService;

    @Test
    void createsTheLocalDevelopmentMemberAndIssuesARegularJwt() throws Exception {
        Member developmentMember = new Member("local-dev", "local-dev@jobpilot.test", "hash", "로컬 개발자");
        setId(developmentMember, 7L);
        when(members.findByLoginId("local-dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(String.class))).thenReturn("hash");
        when(members.save(any(Member.class))).thenReturn(developmentMember);
        when(tokenService.issue(developmentMember)).thenReturn(new JwtTokenService.Token("local-token", 7200));

        AuthResponse response = new LocalDevAuthService(members, passwordEncoder, tokenService).issueDevelopmentToken();

        assertThat(response.accessToken()).isEqualTo("local-token");
        assertThat(response.member().loginId()).isEqualTo("local-dev");
        verify(members).save(any(Member.class));
    }

    private static void setId(Member member, Long id) throws Exception {
        Field field = Member.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(member, id);
    }
}
