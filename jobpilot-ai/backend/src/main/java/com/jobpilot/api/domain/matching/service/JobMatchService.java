package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import com.jobpilot.api.domain.matching.dto.JobMatchDetailResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchEvidenceResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchSummaryResponse;
import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.entity.JobMatchEvidence;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.repository.JobMatchEvidenceRepository;
import com.jobpilot.api.domain.matching.repository.JobMatchRepository;
import com.jobpilot.api.domain.member.entity.Certificate;
import com.jobpilot.api.domain.member.entity.MemberSkill;
import com.jobpilot.api.domain.member.entity.Skill;
import com.jobpilot.api.domain.member.repository.CertificateRepository;
import com.jobpilot.api.domain.member.repository.MemberSkillRepository;
import com.jobpilot.api.domain.member.repository.SkillRepository;
import com.jobpilot.api.domain.resume.entity.ResumeEntry;
import com.jobpilot.api.domain.resume.repository.ResumeEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class JobMatchService {
    private final JobMatchRepository jobMatchRepository;
    private final JobMatchEvidenceRepository jobMatchEvidenceRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final ResumeEntryRepository resumeEntries;
    private final MemberSkillRepository memberSkills;
    private final SkillRepository skills;
    private final CertificateRepository certificates;

    public JobMatchService(
            JobMatchRepository jobMatchRepository,
            JobMatchEvidenceRepository jobMatchEvidenceRepository,
            JobPostingRepository jobPostingRepository,
            JobRequirementRepository jobRequirementRepository,
            ResumeEntryRepository resumeEntries, MemberSkillRepository memberSkills,
            SkillRepository skills, CertificateRepository certificates
    ) {
        this.jobMatchRepository = jobMatchRepository;
        this.jobMatchEvidenceRepository = jobMatchEvidenceRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.resumeEntries = resumeEntries;
        this.memberSkills = memberSkills;
        this.skills = skills;
        this.certificates = certificates;
    }

    public List<JobMatchSummaryResponse> findMatches(Long memberId, RecommendationLevel level) {
        List<JobMatch> matches = level == null
                ? jobMatchRepository.findByMemberIdOrderByReadinessScoreDesc(memberId)
                : jobMatchRepository.findByMemberIdAndRecommendationLevelOrderByReadinessScoreDesc(memberId, level);
        Map<Long, JobPosting> postings = mapById(jobPostingRepository.findAllById(matches.stream().map(JobMatch::getJobPostingId).toList()));

        return matches.stream()
                .map(match -> toSummary(match, requiredPosting(postings, match.getJobPostingId())))
                // Dashboard recommendations prioritize applications that close soon.
                // Rolling / unknown deadlines remain available, but follow dated postings.
                .sorted(Comparator
                        .comparing(JobMatchSummaryResponse::deadlineAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(JobMatchSummaryResponse::readinessScore, Comparator.reverseOrder()))
                .toList();
    }

    public JobMatchDetailResponse findDetail(Long memberId, Long jobPostingId) {
        JobMatch match = jobMatchRepository.findByMemberIdAndJobPostingId(memberId, jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 공고의 매칭 결과를 찾을 수 없습니다."));
        JobPosting posting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        List<JobMatchEvidence> evidences = jobMatchEvidenceRepository.findByJobMatchId(match.getId());
        Map<Long, JobRequirement> requirements = mapById(jobRequirementRepository.findAllById(
                evidences.stream().map(JobMatchEvidence::getJobRequirementId).filter(id -> id != null).toList()
        ));

        return new JobMatchDetailResponse(
                toSummary(match, posting),
                evidences.stream().map(evidence -> toEvidence(memberId, evidence, requirements.get(evidence.getJobRequirementId()))).toList(),
                posting.getDescription()
        );
    }

    private JobMatchSummaryResponse toSummary(JobMatch match, JobPosting posting) {
        return new JobMatchSummaryResponse(
                posting.getId(), posting.getCompanyName(), posting.getTitle(), posting.getSourceUrl(), posting.getLocation(), posting.getDeadlineAt(),
                match.getRecommendationLevel(), match.getReadinessScore(), match.getSummaryComment()
        );
    }

    private JobMatchEvidenceResponse toEvidence(Long memberId, JobMatchEvidence evidence, JobRequirement requirement) {
        return new JobMatchEvidenceResponse(
                evidence.getJobRequirementId(),
                requirement == null ? null : requirement.getContent(),
                requirement == null ? null : requirement.getType(),
                requirement == null ? null : requirement.getSourceExcerpt(),
                evidence.getMemberEvidenceType(), evidence.getMemberEvidenceId(), memberEvidence(memberId, evidence),
                evidence.getStatus(), evidence.getComment(), evidence.getGapAction()
        );
    }

    private String memberEvidence(Long memberId, JobMatchEvidence evidence) {
        Long evidenceId = evidence.getMemberEvidenceId();
        if (evidenceId == null) return null;
        return switch (evidence.getMemberEvidenceType()) {
            case "RESUME_ENTRY" -> resumeEntries.findByIdAndMemberId(evidenceId, memberId).map(this::resumeExcerpt).orElse(null);
            case "MEMBER_SKILL" -> memberSkills.findById(evidenceId)
                    .filter(skill -> memberId.equals(skill.getMemberId()))
                    .flatMap(skill -> skills.findById(skill.getSkillId()).map(catalog -> skillEvidence(catalog, skill)))
                    .orElse(null);
            case "CERTIFICATE" -> certificates.findById(evidenceId)
                    .filter(certificate -> memberId.equals(certificate.getMemberId()))
                    .map(certificate -> "자격증 · " + certificate.getName()).orElse(null);
            default -> null;
        };
    }

    private String skillEvidence(Skill skill, MemberSkill memberSkill) {
        String level = memberSkill.getSelfReportedLevel();
        return "보유 기술 · " + skill.getName() + (level == null || level.isBlank() ? "" : " (" + level + ")");
    }

    private String resumeExcerpt(ResumeEntry entry) {
        List<String> values = new ArrayList<>();
        collectText(entry.getContent(), values);
        String text = String.join(" · ", values).replaceAll("\\s+", " ").trim();
        if (text.length() > 120) text = text.substring(0, 117).trim() + "…";
        return "이력 · " + entry.getTitle() + (text.isBlank() ? "" : " — " + text);
    }

    private void collectText(JsonNode node, List<String> values) {
        if (node == null || node.isNull()) return;
        if (node.isValueNode()) { if (!node.asText().isBlank()) values.add(node.asText()); return; }
        node.elements().forEachRemaining(child -> collectText(child, values));
    }

    private JobPosting requiredPosting(Map<Long, JobPosting> postings, Long jobPostingId) {
        JobPosting posting = postings.get(jobPostingId);
        if (posting == null) throw new ResourceNotFoundException("채용공고를 찾을 수 없습니다.");
        return posting;
    }

    private <T> Map<Long, T> mapById(Collection<T> entities) {
        return entities.stream().collect(java.util.stream.Collectors.toMap(this::extractId, Function.identity()));
    }

    private Long extractId(Object entity) {
        if (entity instanceof JobPosting posting) return posting.getId();
        if (entity instanceof JobRequirement requirement) return requirement.getId();
        throw new IllegalArgumentException("지원하지 않는 엔터티 타입입니다.");
    }
}
