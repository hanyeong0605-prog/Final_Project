package com.jobpilot.api.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.jobpilot.api.domain.auth.dto.SignupRequest;
import com.jobpilot.api.domain.member.entity.ConsentType;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberConsent;
import com.jobpilot.api.domain.member.repository.MemberConsentRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberConsentServiceTest {
    @Mock private MemberConsentRepository consents;
    @Captor private ArgumentCaptor<Iterable<MemberConsent>> savedConsents;

    @Test
    void recordsBothRequiredConsentsAndTheOptionalMarketingChoice() {
        MemberConsentService service = new MemberConsentService(consents);
        SignupRequest request = new SignupRequest(
                "member01", "member@example.com", "x".repeat(43), "password123", "회원",
                true, true, false);

        service.recordSignupConsents(new Member("member01", "member@example.com", "hash", "회원"), request);

        verify(consents).saveAll(savedConsents.capture());
        List<MemberConsent> recorded = new ArrayList<>();
        savedConsents.getValue().forEach(recorded::add);
        assertThat(recorded).extracting(MemberConsent::getConsentType)
                .containsExactly(ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_COLLECTION, ConsentType.MARKETING_EMAIL);
        assertThat(recorded).extracting(MemberConsent::isAgreed).containsExactly(true, true, false);
    }
}
