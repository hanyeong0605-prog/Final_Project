package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import com.jobpilot.api.domain.jobposting.provider.saramindata.model.NormalizedSaraminPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaraminDataPersister {
    private static final String SOURCE_PROVIDER = "SARAMIN_DATA";
    private final JobPostingRepository postingRepository;
    private final JobRequirementRepository requirementRepository;

    public SaraminDataPersister(JobPostingRepository postingRepository,
                                JobRequirementRepository requirementRepository) {
        this.postingRepository = postingRepository;
        this.requirementRepository = requirementRepository;
    }

    @Transactional
    public SaveResult save(NormalizedSaraminPosting normalized) {
        var existing = postingRepository.findBySourceProviderAndExternalJobId(
                SOURCE_PROVIDER, normalized.externalJobId());
        JobPosting posting = existing.orElseGet(() -> new JobPosting(SOURCE_PROVIDER, normalized.externalJobId()));
        posting.updateFromProvider(
                normalized.title(), normalized.companyName(), normalized.companyUrl(), normalized.description(), normalized.sourceUrl(),
                normalized.location(), normalized.employmentType(), normalized.experienceType(),
                normalized.industryCode(), normalized.industryName(), normalized.jobMidCode(), normalized.jobMidName(),
                normalized.jobCode(), normalized.jobName(), normalized.salary(), normalized.keywords(),
                normalized.publishedAt(), normalized.deadlineAt(), normalized.rollingDeadline(),
                normalized.status(), LocalDateTime.now(), normalized.sourceUpdatedAt(), normalized.crawlStatus(),
                normalized.crawledAt(), normalized.rawPayload());
        postingRepository.saveAndFlush(posting);

        requirementRepository.deleteByJobPostingId(posting.getId());
        requirementRepository.saveAll(normalized.requirements().stream()
                .map(item -> new JobRequirement(posting.getId(), item.type(), item.content(),
                        item.sourceExcerpt(), item.importance(), item.extractionSource(), item.verificationStatus()))
                .toList());
        return existing.isPresent() ? SaveResult.UPDATED : SaveResult.CREATED;
    }

    public enum SaveResult { CREATED, UPDATED }
}
