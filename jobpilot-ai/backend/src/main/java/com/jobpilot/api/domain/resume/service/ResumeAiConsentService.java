package com.jobpilot.api.domain.resume.service;

import com.jobpilot.api.domain.member.entity.ConsentType;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberConsent;
import com.jobpilot.api.domain.member.repository.MemberConsentRepository;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.resume.dto.ResumeAiConsentResponse;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class ResumeAiConsentService {
    private static final String POLICY_VERSION = "2026-08-19";
    private final MemberConsentRepository consents; private final MemberRepository members;
    public ResumeAiConsentService(MemberConsentRepository consents, MemberRepository members) { this.consents = consents; this.members = members; }
    public ResumeAiConsentResponse get(Long memberId) { return new ResumeAiConsentResponse(consents.findByMemberIdAndConsentType(memberId, ConsentType.RESUME_AI_PROCESSING).map(MemberConsent::isAgreed).orElse(false)); }
    public boolean hasAgreed(Long memberId) { return get(memberId).agreed(); }
    public ResumeAiConsentResponse save(Long memberId, boolean agreed) {
        MemberConsent consent = consents.findByMemberIdAndConsentType(memberId, ConsentType.RESUME_AI_PROCESSING).orElseGet(() -> {
            Member member = members.findById(memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
            return new MemberConsent(member, ConsentType.RESUME_AI_PROCESSING, POLICY_VERSION, agreed);
        });
        consent.updateAgreement(agreed); consents.save(consent); return new ResumeAiConsentResponse(agreed);
    }
    public void requireAgreed(Long memberId) { if (!get(memberId).agreed()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI 초안 생성 전 이력서 정보의 AI 처리 동의가 필요합니다."); }
}
