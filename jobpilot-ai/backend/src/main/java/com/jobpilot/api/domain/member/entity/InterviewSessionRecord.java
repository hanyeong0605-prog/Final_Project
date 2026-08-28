package com.jobpilot.api.domain.member.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 2026-08-10: 개인 타임라인 기능(태스크 #66) - 완료된 모의면접 세션(SessionEvaluationReport +
// 세션 메타데이터)을 처음으로 DB에 남긴다. 지금까지는 evaluate-session 결과가 화면에만
// 떴다가 페이지를 나가면 사라졌다(확인됨 - 관련 엔티티가 아예 없었음).
//
// 과거 기록이라 수정 개념이 없다 - 생성만 있고 update()가 없는 게 SelfIntroduction/Project와
// 다른 점이다(그것들은 "지금 쓰는 문서"라 계속 고칠 수 있지만, 이건 "그때 그 결과"라 값이
// 바뀌면 타임라인의 의미가 사라진다).
//
// strengths/improvements/nextSteps/questions는 배열/객체 배열이라 MemberProfile의
// preferredLocations와 같은 JSON 컬럼 패턴(JdbcTypeCode(SqlTypes.JSON) + JsonNode)을
// 그대로 재사용한다 - Service 계층에서 ObjectMapper로 List<String>/List<QuestionFeedback>과
// 서로 변환한다.
@Entity
@Table(name = "interview_session_records")
public class InterviewSessionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    private String role; // 분야 (예: BACKEND) - 선택 안 했으면 null

    @Column(name = "interview_mode", nullable = false)
    private String interviewMode; // "camera" | "chat"

    @Column(name = "interview_type")
    private String interviewType; // 인성면접 | 역량면접 | 직무면접 - null 가능(구버전 세션 등)

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "content_score")
    private Integer contentScore;

    @Column(name = "delivery_score")
    private Integer deliveryScore;

    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode strengths;

    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode improvements;

    @Column(name = "next_steps", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode nextSteps;

    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode questions; // [{question, feedback, modelAnswer}, ...]

    // 2026-08-29: 비언어 행동 리뷰(카메라 정면 응시/고개 방향 안정성/화면 중앙 유지/깜빡임의
    // 관찰 가능한 경향). 카메라를 안 썼거나 분석 신뢰도가 부족하면 ai-server가 null을 주고,
    // 이 기능 이전에 쌓인 기록도 전부 null이라 nullable이다.
    @Column(name = "nonverbal_feedback", columnDefinition = "TEXT")
    private String nonverbalFeedback;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InterviewSessionRecord() {}

    public InterviewSessionRecord(Long memberId, String role, String interviewMode, String interviewType,
            int questionCount, Integer overallScore, Integer contentScore, Integer deliveryScore,
            JsonNode strengths, JsonNode improvements, JsonNode nextSteps, JsonNode questions,
            String nonverbalFeedback) {
        this.memberId = memberId;
        this.role = role;
        this.interviewMode = interviewMode;
        this.interviewType = interviewType;
        this.questionCount = questionCount;
        this.overallScore = overallScore;
        this.contentScore = contentScore;
        this.deliveryScore = deliveryScore;
        this.strengths = strengths;
        this.improvements = improvements;
        this.nextSteps = nextSteps;
        this.questions = questions;
        this.nonverbalFeedback = nonverbalFeedback;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getRole() { return role; }
    public String getInterviewMode() { return interviewMode; }
    public String getInterviewType() { return interviewType; }
    public int getQuestionCount() { return questionCount; }
    public Integer getOverallScore() { return overallScore; }
    public Integer getContentScore() { return contentScore; }
    public Integer getDeliveryScore() { return deliveryScore; }
    public JsonNode getStrengths() { return strengths; }
    public JsonNode getImprovements() { return improvements; }
    public JsonNode getNextSteps() { return nextSteps; }
    public JsonNode getQuestions() { return questions; }
    public String getNonverbalFeedback() { return nonverbalFeedback; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
