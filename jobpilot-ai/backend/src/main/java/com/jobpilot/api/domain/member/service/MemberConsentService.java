package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.auth.dto.SignupRequest;
import com.jobpilot.api.domain.member.entity.ConsentType;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberConsent;
import com.jobpilot.api.domain.member.repository.MemberConsentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemberConsentService {
    private static final String SIGNUP_POLICY_VERSION = "2026-08-03";

    private final MemberConsentRepository consents;

    public MemberConsentService(MemberConsentRepository consents) {
        this.consents = consents;
    }

    public void recordSignupConsents(Member member, SignupRequest request) {
        recordOAuthSignupConsents(member, request.termsAgreed(), request.privacyCollectionAgreed(), request.marketingEmailAgreed());
    }

    public void recordOAuthSignupConsents(Member member, boolean termsAgreed, boolean privacyCollectionAgreed,
                                          boolean marketingEmailAgreed) {
        consents.saveAll(List.of(
                new MemberConsent(member, ConsentType.TERMS_OF_SERVICE, SIGNUP_POLICY_VERSION, termsAgreed),
                new MemberConsent(member, ConsentType.PRIVACY_COLLECTION, SIGNUP_POLICY_VERSION,
                        privacyCollectionAgreed),
                new MemberConsent(member, ConsentType.MARKETING_EMAIL, SIGNUP_POLICY_VERSION, marketingEmailAgreed)
        ));
    }
}
