"""member_spec_retrieval 단위 테스트.

실제 MySQL에 붙지 않는다 - 조회 부분은 get_engine을 monkeypatch로 바꿔치기해서 "언제 DB를
건드리는가"만 검증하고, 문맥 조립과 스펙·공고 대조는 MemberSpec을 직접 만들어서 순수 함수로
검증한다(job_requirement_retrieval도 같은 이유로 DB 접근과 조립이 분리돼 있다).

fail-open 검증이 특히 중요하다 - 이 모듈이 예외를 던지면 질문 생성 자체가 막히는데, 스펙
RAG는 있으면 좋은 부가 근거지 필수 단계가 아니다.
"""

import pytest

from app.domain.interview import member_spec_retrieval
from app.domain.interview.job_requirement_retrieval import JobRequirementRow
from app.domain.interview.member_spec_retrieval import (
    MemberCertificateRow,
    MemberProjectRow,
    MemberSkillRow,
    MemberSpec,
    build_gap_context,
    build_member_spec_context,
    fetch_member_spec,
)


@pytest.fixture
def java_spec() -> MemberSpec:
    return MemberSpec(
        target_role="백엔드",
        skills=[MemberSkillRow("Java", "기술"), MemberSkillRow("Spring Boot", "기술")],
        projects=[
            MemberProjectRow(
                title="채용 공고 매칭 서비스",
                role_description="백엔드 API 설계와 구현",
                result_description="응답 시간 40% 단축",
            )
        ],
        certificates=[MemberCertificateRow("정보처리기사", "한국산업인력공단")],
    )


def test_none_member_skips_database(monkeypatch):
    """member_id가 없으면 커넥션 자체를 만들면 안 된다 - 무료 요청이 실수로 흘러들어왔을 때
    쿼리 비용을 물지 않기 위함. 조회 실패도 None을 반환하므로 반환값만으로는 구분이 안 돼서
    get_engine 호출 여부를 직접 센다."""
    calls: list[int] = []
    monkeypatch.setattr(member_spec_retrieval, "get_engine", lambda: calls.append(1))

    assert fetch_member_spec(None) is None
    assert calls == []


def test_database_failure_is_fail_open(monkeypatch):
    """DB가 죽어 있어도 예외를 밖으로 내보내지 않는다 - 스펙 RAG는 선택 사항이라 질문 생성
    전체를 막으면 안 된다."""

    def broken_engine():
        raise RuntimeError("connection refused")

    monkeypatch.setattr(member_spec_retrieval, "get_engine", broken_engine)

    assert fetch_member_spec(7) is None


def test_gap_context_marks_only_verified_matches():
    spec = MemberSpec(
        target_role="백엔드",
        skills=[MemberSkillRow("Java", "기술")],
        projects=[],
        certificates=[],
    )
    requirements = [
        JobRequirementRow("SKILL", "Java 경험", "REQUIRED", "Java 경험 필수", "VERIFIED"),
        JobRequirementRow("SKILL", "Kubernetes 운영", "PREFERRED", "Kubernetes 우대", "VERIFIED"),
    ]

    context = build_gap_context(spec, requirements)

    assert "보유 근거 확인: Java 경험" in context
    assert "스펙에서 확인되지 않음: Kubernetes 운영" in context


def test_gap_context_uses_certificates_as_evidence():
    spec = MemberSpec(certificates=[MemberCertificateRow("정보처리기사", "한국산업인력공단")])
    requirements = [
        JobRequirementRow("CERTIFICATION", "정보처리기사 소지", "PREFERRED", "정보처리기사 우대", "VERIFIED"),
    ]

    context = build_gap_context(spec, requirements)

    assert "보유 근거 확인: 정보처리기사 소지" in context


def test_gap_context_ignores_project_prose(java_spec):
    """프로젝트 설명에만 나오는 단어는 `확인`으로 치지 않는다 - 산문까지 대조 근거로 쓰면
    흔한 단어가 요구사항에 걸려서 확인이 남발된다."""
    requirements = [
        JobRequirementRow("EXPERIENCE", "응답 시간 개선 경험", "PREFERRED", "성능 개선 우대", "VERIFIED"),
    ]

    context = build_gap_context(java_spec, requirements)

    assert "스펙에서 확인되지 않음: 응답 시간 개선 경험" in context


def test_gap_context_returns_none_without_requirements(java_spec):
    assert build_gap_context(java_spec, []) is None


def test_gap_context_returns_none_without_spec():
    requirements = [JobRequirementRow("SKILL", "Java 경험", "REQUIRED", "Java 경험 필수", "VERIFIED")]

    assert build_gap_context(None, requirements) is None


def test_member_spec_context_includes_saved_evidence(java_spec, monkeypatch):
    """이미 조회해둔 spec을 넘기면 DB를 다시 읽지 않는다(한 요청에서 gap 대조와 두 번 읽지
    않기 위한 경로)."""
    calls: list[int] = []
    monkeypatch.setattr(member_spec_retrieval, "get_engine", lambda: calls.append(1))

    context = build_member_spec_context(member_id=7, category="기술_직무역량", spec=java_spec)

    assert calls == []
    assert "목표 직무: 백엔드" in context
    assert "보유 기술: Java, Spring Boot" in context
    assert "자격증: 정보처리기사 (한국산업인력공단)" in context
    assert "프로젝트: 채용 공고 매칭 서비스" in context
    assert "성과: 응답 시간 40% 단축" in context


def test_member_spec_context_returns_none_when_only_target_role():
    """목표 직무만 있는 상태는 맞춤 질문의 근거로 치지 않는다 - 그건 무료 모의면접도 쓰는
    값이라, 이걸 근거로 인정하면 직무 이름만 아는 채로 맞춤 질문을 지어내게 된다."""
    spec = MemberSpec(target_role="백엔드")

    assert spec.has_question_evidence() is False
    assert build_member_spec_context(member_id=7, spec=spec) is None


def test_self_introduction_is_clipped_before_entering_prompt():
    """자기소개서 원문을 그대로 프롬프트에 싣지 않는다 - 상한까지만 잘라서 넣고, 잘렸다는
    사실을 말줄임표로 남긴다."""
    limit = member_spec_retrieval._MAX_SELF_INTRODUCTION_CHARS
    spec = MemberSpec(self_introduction="가" * (limit + 50))

    context = build_member_spec_context(member_id=7, spec=spec)

    assert "..." in context
    assert "가" * (limit + 1) not in context


def test_single_character_skill_does_not_create_false_match():
    """한 글자 토큰은 대조에서 제외한다 - 확인되지 않은 것을 확인됐다고 말하는 쪽이 반대보다
    위험하다."""
    spec = MemberSpec(skills=[MemberSkillRow("C", "기술"), MemberSkillRow("Java", "기술")])
    requirements = [
        JobRequirementRow("SKILL", "C 언어 기반 임베디드 개발", "REQUIRED", "C 필수", "VERIFIED"),
    ]

    context = build_gap_context(spec, requirements)

    assert "스펙에서 확인되지 않음: C 언어 기반 임베디드 개발" in context
