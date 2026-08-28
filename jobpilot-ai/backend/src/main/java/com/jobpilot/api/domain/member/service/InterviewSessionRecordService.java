package com.jobpilot.api.domain.member.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.member.dto.InterviewQuestionFeedbackDto;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordDetailResponse;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordRequest;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordSummaryResponse;
import com.jobpilot.api.domain.member.entity.InterviewSessionRecord;
import com.jobpilot.api.domain.member.repository.InterviewSessionRecordRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

// 2026-08-10: 개인 타임라인 기능(태스크 #66) - 완료된 모의면접 세션 기록 CRUD. 과거
// 기록이라 update()가 없다(생성/조회/목록만) - InterviewSessionRecord 엔티티 docstring 참고.
@Service
@Transactional
public class InterviewSessionRecordService {
    private final InterviewSessionRecordRepository repository;
    private final ObjectMapper objectMapper;

    public InterviewSessionRecordService(InterviewSessionRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<InterviewSessionRecordSummaryResponse> list(Long memberId) {
        return repository.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(this::summary).toList();
    }

    public InterviewSessionRecordDetailResponse detail(Long memberId, Long id) {
        return detailResponse(owned(memberId, id));
    }

    public InterviewSessionRecordDetailResponse create(Long memberId, InterviewSessionRecordRequest request) {
        InterviewSessionRecord saved = repository.save(new InterviewSessionRecord(
                memberId,
                clean(request.role()),
                request.interviewMode().trim(),
                clean(request.interviewType()),
                request.questionCount(),
                request.overallScore(),
                request.contentScore(),
                request.deliveryScore(),
                toJson(request.strengths()),
                toJson(request.improvements()),
                toJson(request.nextSteps()),
                toJson(request.questions()),
                clean(request.nonverbalFeedback())));
        return detailResponse(saved);
    }

    private InterviewSessionRecord owned(Long memberId, Long id) {
        return repository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("모의면접 기록을 찾을 수 없습니다."));
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private JsonNode toJson(List<?> value) { return value == null ? null : objectMapper.valueToTree(value); }

    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        return objectMapper.convertValue(node, new TypeReference<List<String>>() {});
    }

    private List<InterviewQuestionFeedbackDto> toQuestionList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        return objectMapper.convertValue(node, new TypeReference<List<InterviewQuestionFeedbackDto>>() {});
    }

    private InterviewSessionRecordSummaryResponse summary(InterviewSessionRecord r) {
        return new InterviewSessionRecordSummaryResponse(r.getId(), r.getRole(), r.getInterviewMode(),
                r.getInterviewType(), r.getQuestionCount(), r.getOverallScore(), r.getContentScore(),
                r.getDeliveryScore(), r.getCreatedAt());
    }

    private InterviewSessionRecordDetailResponse detailResponse(InterviewSessionRecord r) {
        return new InterviewSessionRecordDetailResponse(r.getId(), r.getRole(), r.getInterviewMode(),
                r.getInterviewType(), r.getQuestionCount(), r.getOverallScore(), r.getContentScore(),
                r.getDeliveryScore(), toStringList(r.getStrengths()), toStringList(r.getImprovements()),
                toStringList(r.getNextSteps()), toQuestionList(r.getQuestions()), r.getNonverbalFeedback(),
                r.getCreatedAt());
    }
}
