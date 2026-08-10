package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.client.ResumeSummaryAiClient;
import com.jobpilot.api.domain.member.client.ResumeSummaryAiClient.ProjectContent;
import com.jobpilot.api.domain.member.entity.MemberSpecification;
import com.jobpilot.api.domain.member.entity.Project;
import com.jobpilot.api.domain.member.entity.SelfIntroduction;
import com.jobpilot.api.domain.member.repository.MemberProfileRepository;
import com.jobpilot.api.domain.member.repository.MemberSpecificationRepository;
import com.jobpilot.api.domain.member.repository.ProjectRepository;
import com.jobpilot.api.domain.member.repository.SelfIntroductionRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 2026-08-10: 태스크 #63 "반영" 방향 - CareerProfileForm에서 "불러오기"는 이미 프론트가
 * GET /career-profile로 직접 하고 있어서(ResumePage.tsx) 여기 새로 만들 게 없었다. 반대
 * 방향(자기소개서/프로젝트 저장 -> CareerProfile.technicalSummary에 반영)만 이 서비스가
 * 새로 담당한다.
 *
 * SelfIntroductionService/ProjectService의 create/update 성공 직후 호출된다. 그 회원의
 * 자기소개서 전문 + 프로젝트 STAR 필드를 전부 모아서 ai-server에 넘기고, 새로 합성된 기술
 * 요약을 받아 MemberSpecification에 저장한다.
 *
 * 실패해도 예외를 던지지 않는다(fail-open) - ai-server가 잠깐 죽어있거나 Gemini 키가
 * 없어도 "자기소개서 저장" 자체는 무조건 성공해야 한다. 실패는 로그로만 남긴다.
 */
@Service
@Transactional
public class ResumeCareerSyncService {
    private static final Logger log = LoggerFactory.getLogger(ResumeCareerSyncService.class);

    private final MemberProfileRepository profiles;
    private final MemberSpecificationRepository specifications;
    private final SelfIntroductionRepository selfIntroductions;
    private final ProjectRepository projects;
    private final ResumeSummaryAiClient aiClient;

    public ResumeCareerSyncService(MemberProfileRepository profiles, MemberSpecificationRepository specifications,
            SelfIntroductionRepository selfIntroductions, ProjectRepository projects, ResumeSummaryAiClient aiClient) {
        this.profiles = profiles; this.specifications = specifications;
        this.selfIntroductions = selfIntroductions; this.projects = projects; this.aiClient = aiClient;
    }

    public void reflectFromResume(Long memberId) {
        try {
            String job = profiles.findById(memberId).map(p -> p.getTargetRole()).orElse("");
            MemberSpecification spec = specifications.findById(memberId).orElseGet(() -> new MemberSpecification(memberId));
            String existingSummary = spec.getTechnicalSummary();

            List<String> intros = selfIntroductions.findByMemberIdOrderByUpdatedAtDesc(memberId).stream()
                    .map(SelfIntroduction::getContent).toList();
            List<ProjectContent> projectContents = projects.findByMemberId(memberId).stream()
                    .map(p -> new ProjectContent(p.getTitle(), p.getRoleDescription(), p.getProblemDescription(),
                            p.getSolutionDescription(), p.getResultDescription()))
                    .toList();

            if (intros.isEmpty() && projectContents.isEmpty()) return; // 반영할 내용이 없음

            Map<String, Object> result = aiClient.synthesizeTechnicalSummary(job, existingSummary, intros, projectContents);
            if (result == null || !Boolean.TRUE.equals(result.get("ok"))) {
                log.warn("기술 요약 반영 실패 (memberId={}): {}", memberId, result == null ? null : result.get("message"));
                return;
            }
            String summary = (String) result.get("summary");
            if (summary == null || summary.isBlank()) return;

            spec.updateTechnicalSummary(summary);
            specifications.save(spec);
        } catch (Exception e) {
            // ai-server가 죽어있거나 네트워크 오류여도 이력서 저장 자체는 성공해야 한다.
            log.warn("기술 요약 반영 중 오류 (memberId={})", memberId, e);
        }
    }
}
