package com.jobpilot.api.domain.jobposting.service;

import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementExtractionStatusRepository;
import com.jobpilot.api.domain.jobposting.repository.JobSkillRepository;
import com.jobpilot.api.domain.member.entity.Skill;
import com.jobpilot.api.domain.member.repository.SkillRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JobRequirementExtractionPersistenceService {
    private static final String EXTRACTION_SOURCE = "OPENAI_LUNA";
    private static final String VERIFICATION_STATUS = "UNVERIFIED";
    private static final String SKILL_CATEGORY = "TECHNICAL";

    private final JobRequirementRepository jobRequirementRepository;
    private final JobSkillRepository jobSkillRepository;
    private final JobRequirementExtractionStatusRepository extractionStatusRepository;
    private final SkillRepository skillRepository;
    private final SkillNameNormalizer skillNameNormalizer;

    JobRequirementExtractionPersistenceService(
            JobRequirementRepository jobRequirementRepository,
            JobSkillRepository jobSkillRepository,
            JobRequirementExtractionStatusRepository extractionStatusRepository,
            SkillRepository skillRepository,
            SkillNameNormalizer skillNameNormalizer
    ) {
        this.jobRequirementRepository = jobRequirementRepository;
        this.jobSkillRepository = jobSkillRepository;
        this.extractionStatusRepository = extractionStatusRepository;
        this.skillRepository = skillRepository;
        this.skillNameNormalizer = skillNameNormalizer;
    }

    @Transactional
    void replace(Long jobPostingId, List<ExtractedJobRequirement> requirements) {
        jobSkillRepository.deleteByJobPostingId(jobPostingId);
        jobRequirementRepository.deleteByJobPostingId(jobPostingId);

        for (ExtractedJobRequirement requirement : requirements) {
            jobRequirementRepository.save(new JobRequirement(
                    jobPostingId,
                    requirement.type(),
                    requirement.content(),
                    requirement.sourceExcerpt(),
                    requirement.importance(),
                    EXTRACTION_SOURCE,
                    VERIFICATION_STATUS
            ));

            if (requirement.isSkill()) {
                saveJobSkill(jobPostingId, requirement);
            }
        }
        extractionStatusRepository.markCompleted(jobPostingId);
    }

    @Transactional
    void markCompleted(Long jobPostingId) {
        extractionStatusRepository.markCompleted(jobPostingId);
    }

    private void saveJobSkill(Long jobPostingId, ExtractedJobRequirement requirement) {
        String normalizedName = skillNameNormalizer.normalize(requirement.content());
        if (normalizedName.isBlank()) return;
        Skill skill = skillRepository.findByName(normalizedName)
                .orElseGet(() -> skillRepository.save(new Skill(normalizedName, SKILL_CATEGORY)));
        jobSkillRepository.save(jobPostingId, skill.getId(), requirement.importance(), requirement.sourceExcerpt());
    }
}
