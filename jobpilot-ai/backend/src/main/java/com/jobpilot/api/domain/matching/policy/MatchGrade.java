package com.jobpilot.api.domain.matching.policy;

/** 지원 준비도이며 합격 가능성이나 최종 지원 자격 판단이 아니다. */
public enum MatchGrade {
    READY_TO_APPLY,
    NEEDS_IMPROVEMENT,
    INSUFFICIENT_EVIDENCE
}
