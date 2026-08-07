"""generate_personalized_question() 단위 테스트.

LoRA 모델(generate_question)은 GPU/모델 파일이 필요해 여기서 다루지 않는다(기존 동작
그대로 유지, 이 파일에서 건드리지 않았음). generate_personalized_question은 Gemini만
호출하므로 evaluation.py 테스트와 같은 패턴(google.genai.Client 모킹)으로 검증한다.
"""

from unittest.mock import patch

from app.domain.interview import question_generator
from app.domain.interview.question_generator import generate_personalized_question


def test_no_api_key_returns_none(monkeypatch):
    """GEMINI_API_KEY가 없으면 호출부(router.py)가 LoRA 경로로 폴백해야 하므로 None을
    반환해야 한다 - 예외를 던지면 안 된다(fail-open)."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")
    assert generate_personalized_question(job="백엔드 개발자", tech_summary="   ") is None


def test_empty_tech_summary_returns_none(monkeypatch):
    """스펙(기술 요약)이 없는 사용자는 애초에 이 함수를 부를 이유가 없지만, 방어적으로
    빈 값이 오면 Gemini를 호출하지 않고 바로 None을 반환해야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "")
    assert generate_personalized_question(job="백엔드 개발자", tech_summary="Spring, JPA") is None


def test_success_returns_question_reflecting_tech_summary(monkeypatch):
    """정상 응답이면 Gemini가 만든 질문 문자열을 그대로 반환해야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(question_generator.settings, "gemini_model", "gemini-test")

    captured = {}

    class FakeResponse:
        text = "Spring Boot에서 JPA N+1 문제를 어떻게 해결해보셨나요?"

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["model"] = model
            captured["prompt"] = contents
            captured["config"] = config
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        question = generate_personalized_question(
            job="백엔드 개발자", tech_summary="Spring Boot, JPA로 커머스 프로젝트 진행", category="기술_직무역량"
        )

    assert question == "Spring Boot에서 JPA N+1 문제를 어떻게 해결해보셨나요?"
    assert captured["model"] == "gemini-test"
    assert "백엔드 개발자" in captured["prompt"]
    assert "Spring Boot, JPA로 커머스 프로젝트 진행" in captured["prompt"]
    assert "카테고리: 기술_직무역량" in captured["prompt"]


def test_angle_hint_included_in_prompt(monkeypatch):
    """angle_hint를 넘기면 프롬프트에 그 관점이 명시적으로 포함돼야 한다 - tech_summary가
    짧고 구체적일 때(예: "VSCode 확장 프로그램 개발 경험") 세션 안 여러 질문이 같은
    소재로 수렴하지 않도록 호출부가 각도를 강제하는 기능이다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = "VSCode 확장 프로그램을 만들 때 겪은 트러블슈팅 경험을 말씀해 주시겠습니까?"

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        question = generate_personalized_question(
            job="백엔드 개발자",
            tech_summary="VSCode 확장 프로그램 개발 경험",
            category="기술_직무역량",
            angle_hint="트러블슈팅/문제 해결 경험",
        )

    assert question is not None
    assert "트러블슈팅/문제 해결 경험' 관점에서 만들어라" in captured["prompt"]


def test_no_angle_hint_uses_generic_diversity_rule(monkeypatch):
    """angle_hint를 안 넘기면 기존처럼 느슨한 다양성 지시(규칙 5)가 그대로 들어가야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = "질문"

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        generate_personalized_question(job="백엔드 개발자", tech_summary="Spring")

    assert "매번 다른 각도" in captured["prompt"]


def test_gemini_failure_returns_none(monkeypatch):
    """Gemini 호출 자체가 예외를 던져도(네트워크 오류 등) 호출부가 LoRA로 폴백할 수
    있도록 예외를 삼키고 None을 반환해야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")

    class FakeModels:
        def generate_content(self, model, contents):
            raise RuntimeError("network down")

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        assert generate_personalized_question(job="백엔드 개발자", tech_summary="Spring") is None


def test_discardable_empty_response_returns_none(monkeypatch):
    """Gemini가 빈 문자열을 반환하면(드묾) None을 반환해서 LoRA 폴백을 타게 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "   "

    class FakeModels:
        def generate_content(self, model, contents):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        assert generate_personalized_question(job="백엔드 개발자", tech_summary="Spring") is None


class TestGenerateValidatedQuestion:
    """generate_validated_question() 단위 테스트 - LoRA 모델 로딩(generate_question 내부)은
    GPU/모델 파일이 필요해 여기서 실제로 호출하지 않고 monkeypatch로 대체한다. 검증 로직
    (question_similarity)과 대체용 코퍼스(question_corpus)도 각각 격리해서 테스트한다."""

    def test_relevant_candidate_is_returned_as_is(self, monkeypatch):
        from app.domain.interview import question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_question", lambda **kw: "JPA N+1 문제를 설명해 주세요.")
        monkeypatch.setattr(question_similarity, "is_topically_relevant", lambda *a, **kw: True)

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "JPA N+1 문제를 설명해 주세요."

    def test_irrelevant_candidate_is_replaced_from_corpus(self, monkeypatch):
        """검증에 실패하면(다른 주제로 판정) LoRA 원본 대신 코퍼스에서 뽑은 실제 질문을
        반환해야 한다 - 사용자가 이상한 질문을 보는 일이 없어야 한다."""
        from app.domain.interview import question_corpus, question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_question", lambda **kw: "GitHub와 Docker의 차이점을 설명해 주세요.")
        monkeypatch.setattr(question_similarity, "is_topically_relevant", lambda *a, **kw: False)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["실제 모바일 질문입니다."])

        result = qg.generate_validated_question(job="모바일 (iOS/Android)", category="기술_직무역량")

        assert result == "실제 모바일 질문입니다."

    def test_irrelevant_candidate_falls_back_to_original_when_corpus_empty(self, monkeypatch):
        """대체할 코퍼스 풀도 없으면(극단적 케이스) 원본이라도 반환해야 한다 - 아예 빈
        응답보다는 낫다."""
        from app.domain.interview import question_corpus, question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_question", lambda **kw: "이상한 질문입니다.")
        monkeypatch.setattr(question_similarity, "is_topically_relevant", lambda *a, **kw: False)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: [])

        result = qg.generate_validated_question(job="모바일 (iOS/Android)", category="기술_직무역량")

        assert result == "이상한 질문입니다."

    def test_generation_failure_falls_back_to_corpus(self, monkeypatch):
        """generate_question() 자체가 예외를 던지면(모델 파일 없음/원격 컴퓨터 꺼짐 등)
        503/500으로 죽는 대신 코퍼스에서 실제 질문을 반환해야 한다 - "질문이 아예 안
        나오는" 상황을 막는 안전장치."""
        from app.domain.interview import question_corpus, question_generator as qg

        def _broken(**kw):
            raise RuntimeError("모델을 찾을 수 없습니다")

        monkeypatch.setattr(qg, "generate_question", _broken)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["코퍼스 대체 질문입니다."])

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "코퍼스 대체 질문입니다."

    def test_generation_failure_and_empty_corpus_returns_hardcoded_fallback(self, monkeypatch):
        """생성도 실패하고 대체할 코퍼스 풀도 없는 극단적인 경우에도 빈 문자열이 아니라
        하드코딩된 최후 보루 문장을 반환해야 한다."""
        from app.domain.interview import question_corpus, question_generator as qg

        def _broken(**kw):
            raise RuntimeError("모델을 찾을 수 없습니다")

        monkeypatch.setattr(qg, "generate_question", _broken)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: [])

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "간단하게 자기소개 부탁드립니다."

    def test_corpus_lookup_failure_never_raises(self, monkeypatch):
        """generate_question()도 실패하고, 코퍼스 조회(question_corpus.get_pool)마저
        예상 못 한 이유로 예외를 던지는 극단적인 경우에도 이 함수는 절대 예외를 밖으로
        흘려보내면 안 된다 - router.py가 503/500을 반환하는 마지막 구멍을 막는 테스트."""
        from app.domain.interview import question_corpus, question_generator as qg

        def _broken_generate(**kw):
            raise RuntimeError("모델을 찾을 수 없습니다")

        def _broken_pool(category, job):
            raise OSError("코퍼스 파일을 읽을 수 없습니다")

        monkeypatch.setattr(qg, "generate_question", _broken_generate)
        monkeypatch.setattr(question_corpus, "get_pool", _broken_pool)

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "간단하게 자기소개 부탁드립니다."

    def test_validation_infra_failure_is_fail_open(self, monkeypatch):
        """검증 모듈 자체가 예외를 던져도(임포트 실패 등) LoRA 원본은 그대로 반환돼야 한다 -
        검증 인프라 장애가 질문 생성 실패로 이어지면 안 된다."""
        from app.domain.interview import question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_question", lambda **kw: "LoRA가 만든 질문입니다.")

        def _broken(*a, **kw):
            raise RuntimeError("검증 인프라 다운")

        monkeypatch.setattr(question_similarity, "is_topically_relevant", _broken)

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "LoRA가 만든 질문입니다."
