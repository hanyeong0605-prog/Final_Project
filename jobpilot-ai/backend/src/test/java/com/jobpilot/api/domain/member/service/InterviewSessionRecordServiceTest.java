package com.jobpilot.api.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.member.dto.InterviewQuestionFeedbackDto;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordDetailResponse;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordRequest;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordSummaryResponse;
import com.jobpilot.api.domain.member.entity.InterviewSessionRecord;
import com.jobpilot.api.domain.member.repository.InterviewSessionRecordRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2026-08-10: 개인 타임라인 기능(태스크 #66) 서비스 테스트. ObjectMapper는 진짜 인스턴스를
// 써서 List<String>/List<QuestionFeedback> <-> JSON 변환이 실제로 왕복되는지 검증한다
// (mock으로 대체하면 이 변환 로직 자체를 테스트할 수 없다) - repository만 Mockito로 대체.
@ExtendWith(MockitoExtension.class)
class InterviewSessionRecordServiceTest {
    @Mock private InterviewSessionRecordRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private InterviewSessionRecordService service() {
        return new InterviewSessionRecordService(repository, objectMapper);
    }

    private InterviewSessionRecordRequest request() {
        return new InterviewSessionRecordRequest(
                "BACKEND", "chat", "역량면접", 3, 4, 4, null,
                List.of("논리적으로 설명함"), List.of("두괄식으로 답하면 좋겠음"), List.of("STAR 기법 연습"),
                List.of(new InterviewQuestionFeedbackDto("협업 경험은?", "구체적이었음", "모범답안 예시")));
    }

    @Test
    void createConvertsListsToJsonAndBackOnResponse() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewSessionRecordDetailResponse result = service().create(1L, request());

        assertThat(result.role()).isEqualTo("BACKEND");
        assertThat(result.interviewMode()).isEqualTo("chat");
        assertThat(result.interviewType()).isEqualTo("역량면접");
        assertThat(result.overallScore()).isEqualTo(4);
        assertThat(result.deliveryScore()).isNull(); // 채팅 모드라 음성 지표가 없어서 null일 수 있음
        assertThat(result.strengths()).containsExactly("논리적으로 설명함");
        assertThat(result.improvements()).containsExactly("두괄식으로 답하면 좋겠음");
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).question()).isEqualTo("협업 경험은?");
        assertThat(result.questions().get(0).modelAnswer()).isEqualTo("모범답안 예시");
    }

    @Test
    void listReturnsSummariesOrderedByRepository() {
        InterviewSessionRecord saved = new InterviewSessionRecord(1L, "BACKEND", "camera", "직무면접", 5,
                4, 4, 3, objectMapper.valueToTree(List.of("강점1")), objectMapper.valueToTree(List.of()),
                objectMapper.valueToTree(List.of()), objectMapper.valueToTree(List.of()));
        when(repository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(saved));

        List<InterviewSessionRecordSummaryResponse> result = service().list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("BACKEND");
        assertThat(result.get(0).interviewMode()).isEqualTo("camera");
        assertThat(result.get(0).overallScore()).isEqualTo(4);
    }

    @Test
    void detailThrowsWhenNotOwnedByRequestingMember() {
        when(repository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(1L, 10L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createHandlesNullOptionalListsAsEmpty() {
        // 세션 평가가 강점/개선점을 하나도 못 뽑은 극단적인 경우(ai-server fail-open 등)에도
        // 저장/조회가 깨지면 안 된다.
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        InterviewSessionRecordRequest request = new InterviewSessionRecordRequest(
                null, "camera", null, 1, null, null, null, null, null, null, null);

        InterviewSessionRecordDetailResponse result = service().create(1L, request);

        assertThat(result.role()).isNull();
        assertThat(result.strengths()).isEmpty();
        assertThat(result.questions()).isEmpty();
    }
}
