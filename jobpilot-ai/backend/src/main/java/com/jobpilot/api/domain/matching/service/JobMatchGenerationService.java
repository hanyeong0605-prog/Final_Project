package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.entity.JobMatchEvidence;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.repository.JobMatchEvidenceRepository;
import com.jobpilot.api.domain.matching.repository.JobMatchRepository;
import com.jobpilot.api.domain.member.entity.MemberProfile;
import com.jobpilot.api.domain.member.entity.MemberSkill;
import com.jobpilot.api.domain.member.entity.MemberSpecification;
import com.jobpilot.api.domain.member.entity.Skill;
import com.jobpilot.api.domain.member.entity.Certificate;
import com.jobpilot.api.domain.member.repository.CertificateRepository;
import com.jobpilot.api.domain.member.repository.MemberProfileRepository;
import com.jobpilot.api.domain.member.repository.MemberSkillRepository;
import com.jobpilot.api.domain.member.repository.MemberSpecificationRepository;
import com.jobpilot.api.domain.member.repository.SkillRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * LLM 호출 없이 이미 추출된 공고 요구사항과 회원 스펙을 비교해 저장한다.
 * 요구사항이 없는 공고는 "지원 어려움"으로 오판하지 않고, 분석 대상에서 제외한다.
 */
@Service
@Transactional
public class JobMatchGenerationService {
    private final JobMatchRepository matches;
    private final JobMatchEvidenceRepository evidences;
    private final JobPostingRepository postings;
    private final JobRequirementRepository requirements;
    private final MemberSkillRepository memberSkills;
    private final SkillRepository skills;
    private final MemberProfileRepository profiles;
    private final MemberSpecificationRepository specifications;
    private final CertificateRepository certificates;

    public JobMatchGenerationService(JobMatchRepository matches, JobMatchEvidenceRepository evidences,
            JobPostingRepository postings, JobRequirementRepository requirements,
            MemberSkillRepository memberSkills, SkillRepository skills,
            MemberProfileRepository profiles, MemberSpecificationRepository specifications,
            CertificateRepository certificates) {
        this.matches = matches; this.evidences = evidences; this.postings = postings; this.requirements = requirements;
        this.memberSkills = memberSkills; this.skills = skills; this.profiles = profiles; this.specifications = specifications;
        this.certificates = certificates;
    }

    public int regenerateForMember(Long memberId) {
        List<JobMatch> old = matches.findByMemberIdOrderByReadinessScoreDesc(memberId);
        if (!old.isEmpty()) {
            evidences.deleteByJobMatchIdIn(old.stream().map(JobMatch::getId).toList());
            matches.deleteByMemberId(memberId);
            matches.flush();
        }

        Map<Long, Skill> catalog = skills.findAllById(memberSkills.findByMemberId(memberId).stream()
                .map(MemberSkill::getSkillId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Skill::getId, Function.identity()));
        List<Skill> memberSkillCatalog = memberSkills.findByMemberId(memberId).stream()
                .map(item -> catalog.get(item.getSkillId())).filter(java.util.Objects::nonNull).toList();
        MemberProfile profile = profiles.findById(memberId).orElse(null);
        MemberSpecification specification = specifications.findById(memberId).orElse(null);
        List<Certificate> memberCertificates = certificates.findByMemberId(memberId);

        int generated = 0;
        for (JobPosting posting : postings.findActiveWithRequirements()) {
            List<JobRequirement> postingRequirements = requirements.findByJobPostingId(posting.getId());
            MatchDraft draft = evaluate(posting, postingRequirements, memberSkillCatalog, memberCertificates, profile, specification);
            JobMatch saved = matches.save(new JobMatch(memberId, posting.getId(), draft.level(), draft.score(),
                    draft.summary(), draft.missingRequired()));
            evidences.saveAll(draft.evidences(saved.getId()));
            generated++;
        }
        return generated;
    }

    /** 새 공고의 요구사항 추출이 끝났을 때, 온보딩을 마친 회원의 해당 공고 결과만 만든다. */
    public void regenerateForPosting(Long jobPostingId) {
        JobPosting posting = postings.findById(jobPostingId).orElse(null);
        if (posting == null || !"ACTIVE".equals(posting.getStatus())) return;
        List<JobRequirement> postingRequirements = requirements.findByJobPostingId(jobPostingId);
        if (postingRequirements.isEmpty()) return;
        for (MemberProfile profile : profiles.findAll()) {
            generateOne(profile.getMemberId(), posting, postingRequirements, profile,
                    specifications.findById(profile.getMemberId()).orElse(null));
        }
    }

    private void generateOne(Long memberId, JobPosting posting, List<JobRequirement> postingRequirements,
                             MemberProfile profile, MemberSpecification specification) {
        matches.findByMemberIdAndJobPostingId(memberId, posting.getId()).ifPresent(old -> {
            evidences.deleteByJobMatchIdIn(List.of(old.getId()));
            matches.deleteByMemberIdAndJobPostingId(memberId, posting.getId());
            matches.flush();
        });
        List<MemberSkill> savedSkills = memberSkills.findByMemberId(memberId);
        Map<Long, Skill> catalog = skills.findAllById(savedSkills.stream().map(MemberSkill::getSkillId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Skill::getId, Function.identity()));
        List<Skill> memberSkillCatalog = savedSkills.stream().map(item -> catalog.get(item.getSkillId()))
                .filter(java.util.Objects::nonNull).toList();
        MatchDraft draft = evaluate(posting, postingRequirements, memberSkillCatalog, certificates.findByMemberId(memberId), profile, specification);
        JobMatch saved = matches.save(new JobMatch(memberId, posting.getId(), draft.level(), draft.score(),
                draft.summary(), draft.missingRequired()));
        evidences.saveAll(draft.evidences(saved.getId()));
    }

    private MatchDraft evaluate(JobPosting posting, List<JobRequirement> postingRequirements, List<Skill> memberSkills,
                                List<Certificate> memberCertificates, MemberProfile profile, MemberSpecification specification) {
        int required = 0, covered = 0, missing = 0;
        List<EvidenceDraft> result = new ArrayList<>();
        for (JobRequirement requirement : postingRequirements) {
            boolean requiredItem = "REQUIRED".equalsIgnoreCase(requirement.getImportance());
            String type = safe(requirement.getType());
            String content = safe(requirement.getContent());
            if (!(type.equals("SKILL") || type.equals("EXPERIENCE") || type.equals("EDUCATION") || type.equals("CERTIFICATION"))) {
                result.add(new EvidenceDraft(requirement, null, "CHECK_REQUIRED", "공고 원문을 확인해 주세요.", null));
                continue;
            }
            if (requiredItem) required++;
            Skill matchedSkill = type.equals("SKILL") ? memberSkills.stream().filter(skill -> containsSkill(content, skill)).findFirst().orElse(null) : null;
            Certificate matchedCertificate = type.equals("CERTIFICATION") ? memberCertificates.stream()
                    .filter(certificate -> containsCertificate(content, certificate)).findFirst().orElse(null) : null;
            boolean profileMatch = !type.equals("SKILL") && profileMatches(type, content, profile, specification);
            if (matchedSkill != null || matchedCertificate != null || profileMatch) {
                if (requiredItem) covered++;
                String evidenceType = matchedSkill != null ? "MEMBER_SKILL" : matchedCertificate != null ? "CERTIFICATE" : "PROFILE";
                Long evidenceId = matchedSkill != null ? matchedSkill.getId() : matchedCertificate != null ? matchedCertificate.getId() : null;
                result.add(new EvidenceDraft(requirement, evidenceId, evidenceType, "DIRECT", "내 스펙에서 확인되었습니다.", null));
            } else {
                if (requiredItem) missing++;
                result.add(new EvidenceDraft(requirement, null, "MISSING", "아직 등록된 스펙에서 확인되지 않았습니다.",
                        type.equals("CERTIFICATION") ? "관련 자격증을 등록하거나 취득 계획을 세워 보세요." : "프로젝트·교육·기술 경험으로 보완해 보세요."));
            }
        }
        int score = required == 0 ? 0 : Math.round(85f * covered / required);
        if (roleMatches(posting, profile)) score += 10;
        if (specification != null && specification.getTechnicalSummary() != null && !specification.getTechnicalSummary().isBlank()) score += 5;
        score = Math.min(score, 100);
        RecommendationLevel level = required == 0 || missing > 2 ? RecommendationLevel.DIFFICULT_NOW
                : missing == 0 ? RecommendationLevel.APPLY_NOW : RecommendationLevel.CHALLENGE_AFTER_GAPS;
        String summary = switch (level) {
            case APPLY_NOW -> "필수 요구사항이 등록한 스펙과 잘 맞습니다. 지원을 우선 검토해 보세요.";
            case CHALLENGE_AFTER_GAPS -> "필수 항목 " + missing + "개를 보완하면 지원 경쟁력을 높일 수 있습니다.";
            case DIFFICULT_NOW -> required == 0 ? "요구사항 분석 정보가 부족합니다. 공고 원문을 먼저 확인해 주세요."
                    : "필수 항목 " + missing + "개가 비어 있습니다. 준비 계획을 세운 뒤 도전해 보세요.";
        };
        return new MatchDraft(level, BigDecimal.valueOf(score), summary, missing, result);
    }

    private boolean containsSkill(String requirement, Skill skill) {
        String name = normalize(skill.getName());
        return name.length() >= 2 && normalize(requirement).contains(name);
    }

    private boolean containsCertificate(String requirement, Certificate certificate) {
        String expected = normalize(requirement);
        String actual = normalize(certificate.getName());
        return actual.length() >= 2 && (expected.contains(actual) || actual.contains(expected));
    }

    private boolean profileMatches(String type, String requirement, MemberProfile profile, MemberSpecification specification) {
        String text = normalize(requirement);
        if (type.equals("EXPERIENCE")) {
            return specification != null && specification.getTotalCareerMonths() > 0;
        }
        if (type.equals("EDUCATION")) {
            return specification != null && specification.getEducationLevel() != null && !specification.getEducationLevel().isBlank();
        }
        if (type.equals("CERTIFICATION")) return false;
        return false;
    }

    private boolean roleMatches(JobPosting posting, MemberProfile profile) {
        if (profile == null) return false;
        String target = normalize(profile.getTargetRole() + " " + profile.getTargetJobFamily());
        String job = normalize(posting.getTitle() + " " + posting.getJobName() + " " + posting.getJobMidName());
        return target.length() > 1 && (job.contains(target) || target.contains(job));
    }

    private String normalize(String value) { return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
    private String safe(String value) { return value == null ? "" : value.trim(); }

    private record MatchDraft(RecommendationLevel level, BigDecimal score, String summary, int missingRequired,
                              List<EvidenceDraft> evidenceDrafts) {
        List<JobMatchEvidence> evidences(Long matchId) {
            return evidenceDrafts.stream().map(item -> new JobMatchEvidence(matchId, item.requirement().getId(),
                    null, item.evidenceType(), item.memberEvidenceId(), item.status(), item.comment(), item.action())).toList();
        }
    }
    private record EvidenceDraft(JobRequirement requirement, Long memberEvidenceId, String evidenceType,
                                 String status, String comment, String action) {
        EvidenceDraft(JobRequirement requirement, Skill skill, String status, String comment, String action) {
            this(requirement, skill == null ? null : skill.getId(), skill == null ? "NONE" : "MEMBER_SKILL",
                    status, comment, action);
        }
    }
}
