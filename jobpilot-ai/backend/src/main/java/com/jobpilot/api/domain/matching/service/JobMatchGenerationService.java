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
import com.jobpilot.api.domain.member.repository.SkillAliasRepository;
import com.jobpilot.api.domain.member.entity.SkillAlias;
import com.jobpilot.api.domain.resume.entity.ResumeEntry;
import com.jobpilot.api.domain.resume.entity.ResumeEntryType;
import com.jobpilot.api.domain.resume.repository.ResumeEntryRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final SkillAliasRepository skillAliases;
    private final JobMatchLearningClient learningClient;
    private final ResumeEntryRepository resumeEntries;

    public JobMatchGenerationService(JobMatchRepository matches, JobMatchEvidenceRepository evidences,
            JobPostingRepository postings, JobRequirementRepository requirements,
            MemberSkillRepository memberSkills, SkillRepository skills,
            MemberProfileRepository profiles, MemberSpecificationRepository specifications,
            CertificateRepository certificates, SkillAliasRepository skillAliases,
            JobMatchLearningClient learningClient, ResumeEntryRepository resumeEntries) {
        this.matches = matches; this.evidences = evidences; this.postings = postings; this.requirements = requirements;
        this.memberSkills = memberSkills; this.skills = skills; this.profiles = profiles; this.specifications = specifications;
        this.certificates = certificates;
        this.skillAliases = skillAliases;
        this.learningClient = learningClient;
        this.resumeEntries = resumeEntries;
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
        List<ResumeEntry> memberResumeEntries = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId);

        List<SavedMatch> generatedMatches = new ArrayList<>();
        for (JobPosting posting : postings.findActiveWithRequirements()) {
            List<JobRequirement> postingRequirements = requirements.findByJobPostingId(posting.getId());
            MatchDraft draft = evaluate(posting, postingRequirements, memberSkillCatalog, memberCertificates, profile, specification, memberResumeEntries);
            JobMatch saved = matches.save(new JobMatch(memberId, posting.getId(), draft.level(), draft.score(),
                    draft.summary(), draft.missingRequired()));
            evidences.saveAll(draft.evidences(saved.getId()));
            generatedMatches.add(new SavedMatch(saved, draft, posting, profile));
        }
        applyLearningScores(generatedMatches);
        return generatedMatches.size();
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
        List<ResumeEntry> memberResumeEntries = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId);
        MatchDraft draft = evaluate(posting, postingRequirements, memberSkillCatalog, certificates.findByMemberId(memberId), profile, specification, memberResumeEntries);
        JobMatch saved = matches.save(new JobMatch(memberId, posting.getId(), draft.level(), draft.score(),
                draft.summary(), draft.missingRequired()));
        evidences.saveAll(draft.evidences(saved.getId()));
        applyLearningScores(List.of(new SavedMatch(saved, draft, posting, profile)));
    }

    private MatchDraft evaluate(JobPosting posting, List<JobRequirement> postingRequirements, List<Skill> memberSkills,
                                List<Certificate> memberCertificates, MemberProfile profile, MemberSpecification specification,
                                List<ResumeEntry> memberResumeEntries) {
        int required = 0, covered = 0, missing = 0, verificationNeeded = 0;
        Map<String, Integer> requiredByType = new java.util.HashMap<>();
        for (JobRequirement requirement : postingRequirements) {
            String type = safe(requirement.getType());
            if ("REQUIRED".equalsIgnoreCase(requirement.getImportance()) && isComparableType(type)) {
                requiredByType.merge(type, 1, Integer::sum);
            }
        }
        double fulfilledWeight = 0;
        double totalWeight = requiredByType.keySet().stream().mapToDouble(this::weightFor).sum();
        boolean criticalGap = false;
        Map<Long, Set<String>> aliasesBySkillId = aliasesBySkillId(memberSkills);
        List<EvidenceDraft> result = new ArrayList<>();
        for (JobRequirement requirement : postingRequirements) {
            boolean requiredItem = "REQUIRED".equalsIgnoreCase(requirement.getImportance());
            String type = safe(requirement.getType());
            String content = safe(requirement.getContent());
            if (!(type.equals("SKILL") || type.equals("EXPERIENCE") || type.equals("EDUCATION") || type.equals("CERTIFICATION"))) {
                verificationNeeded++;
                result.add(new EvidenceDraft(requirement, null, "CHECK_REQUIRED", "공고 원문을 확인해 주세요.", null));
                continue;
            }
            if (requiredItem) required++;
            Skill matchedSkill = type.equals("SKILL") ? memberSkills.stream().filter(skill -> containsSkill(content, skill, aliasesBySkillId)).findFirst().orElse(null) : null;
            Certificate matchedCertificate = type.equals("CERTIFICATION") ? memberCertificates.stream()
                    .filter(certificate -> containsCertificate(content, certificate)).findFirst().orElse(null) : null;
            boolean profileMatch = !type.equals("SKILL") && profileMatches(type, content, profile, specification);
            boolean employmentEvidenceAllowed = !type.equals("EXPERIENCE") || isExperienced(profile);
            ResumeEntry resumeEvidence = employmentEvidenceAllowed ? findResumeEvidence(type, content, matchedSkill, memberResumeEntries) : null;
            // A broad experience requirement (for example, "prompt engineering experience")
            // needs a matching member-written record. Career duration alone must not become
            // false proof for every detailed experience requirement in the posting.
            if (matchedSkill != null || matchedCertificate != null || profileMatch || resumeEvidence != null) {
                if (requiredItem) {
                    covered++;
                    fulfilledWeight += weightFor(type) / requiredByType.get(type);
                }
                String evidenceType = matchedSkill != null ? "MEMBER_SKILL" : matchedCertificate != null ? "CERTIFICATE" : "PROFILE";
                Long evidenceId = matchedSkill != null ? matchedSkill.getId() : matchedCertificate != null ? matchedCertificate.getId() : null;
                if (resumeEvidence != null) {
                    evidenceType = "RESUME_ENTRY";
                    evidenceId = resumeEvidence.getId();
                }
                result.add(new EvidenceDraft(requirement, evidenceId, evidenceType, "DIRECT", "내 스펙에서 확인되었습니다.", null));
            } else {
                if (requiredItem) {
                    missing++;
                    criticalGap |= type.equals("EXPERIENCE") || type.equals("EDUCATION") || type.equals("CERTIFICATION");
                }
                result.add(new EvidenceDraft(requirement, null, "MISSING", "아직 등록된 스펙에서 확인되지 않았습니다.",
                        type.equals("CERTIFICATION") ? "관련 자격증을 등록하거나 취득 계획을 세워 보세요." : "프로젝트·교육·기술 경험으로 보완해 보세요."));
            }
        }
        // Comparable profile evidence accounts for at most 85 points. Conditions which
        // cannot be verified from a member profile reduce the confidence of the result.
        int score = required == 0 || totalWeight == 0 ? 0 : (int) Math.round(85d * fulfilledWeight / totalWeight);
        if (roleMatches(posting, profile)) score += 10;
        score -= Math.min(35, verificationNeeded * 8);
        score = Math.max(0, Math.min(score, 100));
        RecommendationLevel level = required == 0 || criticalGap || score < 50 ? RecommendationLevel.DIFFICULT_NOW
                : missing == 0 && verificationNeeded == 0 && score >= 80
                        ? RecommendationLevel.APPLY_NOW : RecommendationLevel.CHALLENGE_AFTER_GAPS;
        String summary = switch (level) {
            case APPLY_NOW -> "필수 요구사항이 등록한 스펙과 잘 맞습니다. 지원을 우선 검토해 보세요.";
            case CHALLENGE_AFTER_GAPS -> "필수 항목 " + missing + "개를 보완하면 지원 경쟁력을 높일 수 있습니다.";
            case DIFFICULT_NOW -> required == 0 ? "요구사항 분석 정보가 부족합니다. 공고 원문을 먼저 확인해 주세요."
                    : "필수 항목 " + missing + "개가 비어 있습니다. 준비 계획을 세운 뒤 도전해 보세요.";
        };
        if (verificationNeeded > 0) {
            summary += " " + verificationNeeded + "개 항목은 공고 원문 확인이 필요합니다.";
        }
        return new MatchDraft(level, BigDecimal.valueOf(score), summary, missing, result,
                coverage(requiredByType, "SKILL", result),
                coverage(requiredByType, "CERTIFICATION", result),
                hasDirect(result, "EXPERIENCE"), hasDirect(result, "EDUCATION"));
    }

    private boolean containsSkill(String requirement, Skill skill, Map<Long, Set<String>> aliasesBySkillId) {
        String normalizedRequirement = normalize(requirement);
        Set<String> candidates = new HashSet<>(aliasesBySkillId.getOrDefault(skill.getId(), Set.of()));
        candidates.add(normalize(skill.getName()));
        candidates.add(normalize(skill.getNormalizedName()));
        return candidates.stream().anyMatch(candidate -> candidate.length() >= 2 && normalizedRequirement.contains(candidate));
    }

    private boolean containsCertificate(String requirement, Certificate certificate) {
        String expected = normalize(requirement);
        String actual = normalize(certificate.getName());
        return actual.length() >= 2 && (expected.contains(actual) || actual.contains(expected));
    }

    /** Rebuilds evidence only for the posting the member is currently viewing. */
    public void regenerateForMemberAndPosting(Long memberId, Long jobPostingId) {
        JobPosting posting = postings.findById(jobPostingId)
                .orElseThrow(() -> new IllegalArgumentException("채용공고를 찾을 수 없습니다."));
        List<JobRequirement> postingRequirements = requirements.findByJobPostingId(jobPostingId);
        if (postingRequirements.isEmpty()) return;
        MemberProfile profile = profiles.findById(memberId).orElse(null);
        MemberSpecification specification = specifications.findById(memberId).orElse(null);
        generateOne(memberId, posting, postingRequirements, profile, specification);
    }

    /**
     * Employment history and project experience are intentionally different evidence classes.
     * A portfolio may support a skill claim, but must never satisfy a company-career requirement.
     */
    private ResumeEntry findResumeEvidence(String requirementType, String requirement, Skill matchedSkill, List<ResumeEntry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        Set<ResumeEntryType> allowedTypes = switch (requirementType) {
            case "EXPERIENCE" -> Set.of(ResumeEntryType.CAREER);
            case "EDUCATION" -> Set.of(ResumeEntryType.EDUCATION, ResumeEntryType.TRAINING);
            case "SKILL" -> Set.of(ResumeEntryType.CAREER, ResumeEntryType.ACTIVITY, ResumeEntryType.TRAINING, ResumeEntryType.PORTFOLIO, ResumeEntryType.EDUCATION);
            default -> Set.of();
        };
        if (allowedTypes.isEmpty()) return null;
        Set<String> terms = new HashSet<>();
        if (matchedSkill != null) {
            terms.add(normalize(matchedSkill.getName()));
            terms.add(normalize(matchedSkill.getNormalizedName()));
        }
        Matcher english = Pattern.compile("[A-Za-z][A-Za-z0-9+#.\\-]{1,}").matcher(requirement);
        while (english.find()) terms.add(normalize(english.group()));
        Matcher korean = Pattern.compile("[가-힣]{3,}").matcher(requirement);
        while (korean.find()) {
            String term = normalize(korean.group());
            if (!Set.of("경험", "기반", "구현", "설계", "문서", "추출", "검증", "결과", "프로젝트", "요구사항").contains(term)) terms.add(term);
        }
        return entries.stream()
                .filter(entry -> allowedTypes.contains(entry.getEntryType()))
                .map(entry -> new java.util.AbstractMap.SimpleEntry<>(entry, resumeEvidenceScore(entry, terms)))
                .filter(scored -> scored.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private int resumeEvidenceScore(ResumeEntry entry, Set<String> terms) {
        String title = normalize(entry.getTitle());
        String body = normalize(entry.getContent() == null ? "" : entry.getContent().toString());
        return terms.stream().mapToInt(term -> term.length() < 2 ? 0 : (title.contains(term) ? 3 : 0) + (body.contains(term) ? 1 : 0)).sum();
    }

    private boolean profileMatches(String type, String requirement, MemberProfile profile, MemberSpecification specification) {
        if (type.equals("EXPERIENCE")) {
            int requiredMonths = requiredCareerMonths(requirement);
            return isExperienced(profile) && requiredMonths > 0 && specification != null && specification.getTotalCareerMonths() >= requiredMonths;
        }
        if (type.equals("EDUCATION")) {
            return specification != null && educationRank(specification.getEducationLevel()) >= requiredEducationRank(requirement);
        }
        if (type.equals("CERTIFICATION")) return false;
        return false;
    }

    private boolean isExperienced(MemberProfile profile) {
        return profile != null && "EXPERIENCED".equalsIgnoreCase(safe(profile.getExperienceType()));
    }

    private boolean roleMatches(JobPosting posting, MemberProfile profile) {
        if (profile == null) return false;
        String target = normalize(profile.getTargetRole() + " " + profile.getTargetJobFamily());
        String job = normalize(posting.getTitle() + " " + posting.getJobName() + " " + posting.getJobMidName());
        return target.length() > 1 && (job.contains(target) || target.contains(job));
    }

    private String normalize(String value) { return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
    private String safe(String value) { return value == null ? "" : value.trim(); }

    private boolean isComparableType(String type) {
        return Set.of("SKILL", "EXPERIENCE", "EDUCATION", "CERTIFICATION").contains(type);
    }

    private double weightFor(String type) {
        return switch (type) {
            case "SKILL" -> 45d;
            case "CERTIFICATION" -> 20d;
            case "EXPERIENCE" -> 20d;
            case "EDUCATION" -> 15d;
            default -> 0d;
        };
    }

    private Map<Long, Set<String>> aliasesBySkillId(List<Skill> memberSkills) {
        List<Long> ids = memberSkills.stream().map(Skill::getId).toList();
        Map<Long, Set<String>> result = new java.util.HashMap<>();
        for (SkillAlias alias : skillAliases.findBySkillIdIn(ids)) {
            result.computeIfAbsent(alias.getSkillId(), ignored -> new HashSet<>()).add(normalize(alias.getNormalizedAlias()));
        }
        return result;
    }

    private int requiredCareerMonths(String content) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*(년|개월|month|year)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (!matcher.find()) return 0;
        int value = Integer.parseInt(matcher.group(1));
        return matcher.group(2).contains("년") || matcher.group(2).toLowerCase(Locale.ROOT).contains("year") ? value * 12 : value;
    }

    private int educationRank(String value) {
        String normalized = normalize(value);
        if (normalized.contains("박사")) return 4;
        if (normalized.contains("석사")) return 3;
        if (normalized.contains("대학교") || normalized.contains("학사") || normalized.contains("bachelor")) return 2;
        if (normalized.contains("전문") || normalized.contains("associate")) return 1;
        return 0;
    }

    private int requiredEducationRank(String value) {
        String normalized = normalize(value);
        if (normalized.contains("박사")) return 4;
        if (normalized.contains("석사")) return 3;
        if (normalized.contains("학사") || normalized.contains("4년제") || normalized.contains("대학교")) return 2;
        if (normalized.contains("전문학사") || normalized.contains("전문대")) return 1;
        return 0;
    }

    private double coverage(Map<String, Integer> requiredByType, String type, List<EvidenceDraft> evidence) {
        int requiredCount = requiredByType.getOrDefault(type, 0);
        if (requiredCount == 0) return 0d;
        long direct = evidence.stream().filter(item -> type.equals(safe(item.requirement().getType()))
                && "DIRECT".equals(item.status())).count();
        return Math.min(1d, (double) direct / requiredCount);
    }

    private double hasDirect(List<EvidenceDraft> evidence, String type) {
        return evidence.stream().anyMatch(item -> type.equals(safe(item.requirement().getType()))
                && "DIRECT".equals(item.status())) ? 1d : 0d;
    }

    private void applyLearningScores(List<SavedMatch> savedMatches) {
        List<JobMatchLearningClient.LearningCandidate> candidates = savedMatches.stream().map(item ->
                new JobMatchLearningClient.LearningCandidate(
                        item.draft().skillCoverage(), item.draft().certificateCoverage(),
                        item.draft().experienceMatch(), item.draft().educationMatch(),
                        item.draft().score().doubleValue(), item.draft().missingRequired(),
                        targetText(item.profile()), jobText(item.posting())))
                .toList();
        JobMatchLearningClient.LearningScores learned = learningClient.score(candidates);
        if (!learned.ready()) return;
        for (int index = 0; index < savedMatches.size(); index++) {
            SavedMatch item = savedMatches.get(index);
            item.match().applyLearnedScore(learningClient.blendedReadiness(item.draft().score(), learned.scores().get(index)),
                    "HYBRID_RANDOM_FOREST_V1:" + learned.source());
        }
        matches.saveAll(savedMatches.stream().map(SavedMatch::match).toList());
    }

    private String targetText(MemberProfile profile) {
        return profile == null ? "" : safe(profile.getTargetRole()) + " " + safe(profile.getTargetJobFamily());
    }

    private String jobText(JobPosting posting) {
        return safe(posting.getTitle()) + " " + safe(posting.getJobName()) + " " + safe(posting.getJobMidName()) + " " + safe(posting.getKeywords());
    }

    private record MatchDraft(RecommendationLevel level, BigDecimal score, String summary, int missingRequired,
                              List<EvidenceDraft> evidenceDrafts, double skillCoverage,
                              double certificateCoverage, double experienceMatch, double educationMatch) {
        List<JobMatchEvidence> evidences(Long matchId) {
            return evidenceDrafts.stream().map(item -> new JobMatchEvidence(matchId, item.requirement().getId(),
                    null, item.evidenceType(), item.memberEvidenceId(), item.status(), item.comment(), item.action())).toList();
        }
    }
    private record SavedMatch(JobMatch match, MatchDraft draft, JobPosting posting, MemberProfile profile) {}
    private record EvidenceDraft(JobRequirement requirement, Long memberEvidenceId, String evidenceType,
                                 String status, String comment, String action) {
        EvidenceDraft(JobRequirement requirement, Skill skill, String status, String comment, String action) {
            this(requirement, skill == null ? null : skill.getId(), skill == null ? "NONE" : "MEMBER_SKILL",
                    status, comment, action);
        }
    }
}
