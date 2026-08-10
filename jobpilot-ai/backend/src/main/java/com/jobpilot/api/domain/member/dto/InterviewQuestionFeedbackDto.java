package com.jobpilot.api.domain.member.dto;

// SessionEvaluationReport.questions(ai-server)의 항목 하나 - question/feedback/model_answer.
// 요청/응답 양쪽에서 같은 모양이라 하나로 공유한다.
public record InterviewQuestionFeedbackDto(String question, String feedback, String modelAnswer) {}
