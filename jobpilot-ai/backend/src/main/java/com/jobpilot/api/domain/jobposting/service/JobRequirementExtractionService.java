package com.jobpilot.api.domain.jobposting.service;

import com.jobpilot.api.domain.jobposting.dto.JobRequirementExtractionRequest;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementExtractionResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementExtractionStatusRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.domain.matching.service.JobMatchGenerationService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobRequirementExtractionService {
    private static final Logger log = LoggerFactory.getLogger(JobRequirementExtractionService.class);
    private static final int MAX_BATCH_SIZE = 20;

    private final JobPostingRepository jobPostingRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final JobRequirementExtractionStatusRepository extractionStatusRepository;
    private final OpenAiJobRequirementClient openAiClient;
    private final JobRequirementExtractionPersistenceService persistenceService;
    private final JobMatchGenerationService matchGenerationService;

    public JobRequirementExtractionService(
            JobPostingRepository jobPostingRepository,
            JobRequirementRepository jobRequirementRepository,
            JobRequirementExtractionStatusRepository extractionStatusRepository,
            OpenAiJobRequirementClient openAiClient,
            JobRequirementExtractionPersistenceService persistenceService,
            JobMatchGenerationService matchGenerationService
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.extractionStatusRepository = extractionStatusRepository;
        this.openAiClient = openAiClient;
        this.persistenceService = persistenceService;
        this.matchGenerationService = matchGenerationService;
    }

    public JobRequirementExtractionResponse extract(JobRequirementExtractionRequest request) {
        JobRequirementExtractionRequest effectiveRequest = request == null
                ? new JobRequirementExtractionRequest(List.of(), 1, false)
                : request;
        List<JobPosting> postings = selectPostings(effectiveRequest);
        List<JobRequirementExtractionResponse.Item> items = new ArrayList<>();
        int extracted = 0;
        int skipped = 0;
        int failed = 0;

        for (JobPosting posting : postings) {
            if (posting.getDescription() == null || posting.getDescription().isBlank()) {
                skipped++;
                items.add(item(posting, "SKIPPED", 0, "공고 원문이 없습니다."));
                continue;
            }
            if (!effectiveRequest.force()
                    && (jobRequirementRepository.existsByJobPostingId(posting.getId())
                    || extractionStatusRepository.isCompleted(posting.getId()))) {
                skipped++;
                items.add(item(posting, "SKIPPED", 0, "이미 추출된 요구사항이 있습니다."));
                continue;
            }

            try {
                List<ExtractedJobRequirement> requirements = openAiClient.extract(posting);
                if (requirements.isEmpty()) {
                    persistenceService.markCompleted(posting.getId());
                    skipped++;
                    items.add(item(posting, "EMPTY", 0, "명시적 요구사항을 찾지 못했습니다."));
                    continue;
                }
                persistenceService.replace(posting.getId(), requirements);
                matchGenerationService.regenerateForPosting(posting.getId());
                extracted++;
                items.add(item(posting, "EXTRACTED", requirements.size(), null));
            } catch (OpenAiQuotaExhaustedException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                failed++;
                log.warn("Job requirement extraction failed for posting {}: {}",
                        posting.getId(), exception.getMessage());
                items.add(item(posting, "FAILED", 0, "AI 추출에 실패했습니다. 다시 시도해 주세요."));
            }
        }

        return new JobRequirementExtractionResponse(postings.size(), extracted, skipped, failed, List.copyOf(items));
    }

    private List<JobPosting> selectPostings(JobRequirementExtractionRequest request) {
        List<Long> requestedIds = request.jobPostingIds() == null ? List.of() : request.jobPostingIds();
        int limit = resolveLimit(request.limit(), requestedIds.size());
        if (!requestedIds.isEmpty()) {
            return new LinkedHashSet<>(requestedIds).stream()
                    .limit(limit)
                    .map(id -> jobPostingRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다: " + id)))
                    .toList();
        }
        return jobPostingRepository.findByStatusOrderByPublishedAtDesc("ACTIVE").stream()
                .filter(posting -> posting.getDescription() != null && !posting.getDescription().isBlank())
                .limit(limit)
                .toList();
    }

    private int resolveLimit(Integer requestedLimit, int idCount) {
        if (requestedLimit != null) return Math.min(requestedLimit, MAX_BATCH_SIZE);
        if (idCount > 0) return Math.min(idCount, MAX_BATCH_SIZE);
        return 1;
    }

    private JobRequirementExtractionResponse.Item item(
            JobPosting posting, String status, int requirementCount, String message
    ) {
        return new JobRequirementExtractionResponse.Item(
                posting.getId(), posting.getTitle(), status, requirementCount, message
        );
    }
}
