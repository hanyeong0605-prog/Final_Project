"""generate_personalized_question() 단위 테스트.

generate_personalized_question은 Gemini만 호출하므로 evaluation.py 테스트와 같은 패턴
(google.genai.Client 모킹)으로 검증한다.

2026-08-20: LoRA 관련 코드(generate_question, _generate_raw_candidates 등)를
question_generator.py에서 주석 처리했다 - 실제 배포 환경에서는 어차피 죽어있던 경로였다
(question_generator.py 모듈 docstring 참고). 이 파일에서 그 코드를 테스트하던
TestGenerateRawCandidates 클래스도 같이 주석 처리했고, TestGenerateValidatedQuestion은
generate_question 대신 generate_personalized_question을 monkeypatch하도록 바꿨다.
"""

from unittest.mock import Mock, patch

from app.domain.interview import question_generator
from app.domain.interview.question_generator import generate_personalized_question


def test_no_api_key_returns_none(monkeypatch):
    """GEMINI_API_KEY가 없으면 호출부(router.py)가 코퍼스 폴백으로 이어져야 하므로
    None을 반환해야 한다 - 예외를 던지면 안 된다(fail-open)."""
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


def test_job_interview_category_adds_practical_skill_emphasis(monkeypatch):
    """2026-08-10 추가: '기술_직무역량' 카테고리(=직무면접)는 "이력서 기반 실무 능력/전문성
    검증"에 초점을 맞추라는 뉘앙스가 프롬프트에 명시적으로 들어가야 한다 - 카테고리 라벨만
    으로는 직무면접/역량면접 질문 색깔이 잘 안 갈린다는 피드백으로 추가된 요구사항."""
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
        generate_personalized_question(job="백엔드 개발자", tech_summary="", category="기술_직무역량")

    assert "실무 능력과 전문성" in captured["prompt"]


def test_competency_interview_category_adds_broad_behavior_emphasis(monkeypatch):
    """'문제해결_도전경험'/'강점_약점'(=역량면접) 카테고리는 기술 지식뿐 아니라 문제해결/
    협업/커뮤니케이션 같은 행동 양식·잠재력을 넓게 보라는 뉘앙스가 들어가야 한다."""
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
        generate_personalized_question(job="백엔드 개발자", tech_summary="", category="문제해결_도전경험")

    assert "행동 양식과" in captured["prompt"]
    assert "잠재력" in captured["prompt"]


def test_personality_interview_category_has_no_extra_emphasis(monkeypatch):
    """인성면접 카테고리('가치관_자기관리' 등)는 기존에도 이미 잘 구분되던 편이라 별도
    뉘앙스 규칙(6번)을 추가하지 않는다 - 회귀 확인용."""
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
        generate_personalized_question(job="백엔드 개발자", tech_summary="", category="가치관_자기관리")

    assert "6)" not in captured["prompt"]


def test_non_tech_category_forbids_tech_jargon_in_prompt(monkeypatch):
    """2026-08-10 버그 수정 확인용 - '가치관_자기관리'(인성면접) 같은 비기술 카테고리는
    규칙 1)이 기술 질문을 강제하면 안 되고, 반대로 기술 용어/구현 세부사항을 넣지 말라고
    명시해야 한다. 실제 리포트("인성면접 눌렀는데 왜 기술 스택 질문이 나오냐")로 발견된
    회귀 - 카테고리와 무관하게 항상 '기술 질문으로 만들어라'가 들어가던 버그가 있었다."""
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
        generate_personalized_question(job="백엔드 개발자", tech_summary="", category="가치관_자기관리")

    assert "기술 질문으로 만들어라" not in captured["prompt"]
    assert "기술적 세부사항은 절대 넣지 마라" in captured["prompt"]


def test_tech_category_still_forces_tech_question(monkeypatch):
    """'기술_직무역량'(직무면접)은 기존처럼 기술 질문을 강제하는 규칙이 그대로 유지돼야
    한다 - 위 회귀 수정이 직무면접 경로까지 건드리면 안 된다."""
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
        generate_personalized_question(job="백엔드 개발자", tech_summary="", category="기술_직무역량")

    assert "기술 질문으로 만들어라" in captured["prompt"]


def test_empty_category_keeps_default_tech_focused_rule(monkeypatch):
    """category를 아예 안 넘기는 기존 호출(하위 호환)은 원래처럼 기술 질문을 강제하는
    규칙을 그대로 써야 한다 - 이번 수정으로 기본 동작이 바뀌면 안 된다."""
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
        generate_personalized_question(job="백엔드 개발자", tech_summary="")

    assert "기술 질문으로 만들어라" in captured["prompt"]


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
    """generate_validated_question() 단위 테스트.

    2026-08-20: 이 함수가 내부적으로 부르는 생성기가 LoRA(generate_question)에서
    Gemini(generate_personalized_question)로 바뀌었다(question_generator.py 모듈 docstring
    참고 - LoRA는 실제 배포 환경에서 어차피 죽어있던 경로였다). 그래서 이 테스트들도
    generate_question 대신 generate_personalized_question을 monkeypatch한다 - 실제 Gemini
    호출은 여기서 하지 않는다. 검증 로직(question_similarity)과 대체용 코퍼스
    (question_corpus)도 각각 격리해서 테스트한다."""

    def test_relevant_candidate_is_returned_as_is(self, monkeypatch):
        from app.domain.interview import question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_personalized_question", lambda **kw: "JPA N+1 문제를 설명해 주세요.")
        monkeypatch.setattr(question_similarity, "is_topically_relevant", lambda *a, **kw: True)

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "JPA N+1 문제를 설명해 주세요."

    def test_irrelevant_candidate_is_replaced_from_corpus(self, monkeypatch):
        """검증에 실패하면(다른 주제로 판정) 생성된 원본 대신 코퍼스에서 뽑은 실제 질문을
        반환해야 한다 - 사용자가 이상한 질문을 보는 일이 없어야 한다."""
        from app.domain.interview import question_corpus, question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(
            qg, "generate_personalized_question", lambda **kw: "GitHub와 Docker의 차이점을 설명해 주세요."
        )
        monkeypatch.setattr(question_similarity, "is_topically_relevant", lambda *a, **kw: False)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["실제 모바일 질문입니다."])

        result = qg.generate_validated_question(job="모바일 (iOS/Android)", category="기술_직무역량")

        assert result == "실제 모바일 질문입니다."

    def test_irrelevant_candidate_falls_back_to_original_when_corpus_empty(self, monkeypatch):
        """대체할 코퍼스 풀도 없으면(극단적 케이스) 원본이라도 반환해야 한다 - 아예 빈
        응답보다는 낫다."""
        from app.domain.interview import question_corpus, question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_personalized_question", lambda **kw: "이상한 질문입니다.")
        monkeypatch.setattr(question_similarity, "is_topically_relevant", lambda *a, **kw: False)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: [])

        result = qg.generate_validated_question(job="모바일 (iOS/Android)", category="기술_직무역량")

        assert result == "이상한 질문입니다."

    def test_generation_failure_falls_back_to_corpus(self, monkeypatch):
        """생성(Gemini 재시도)이 실패하면 503/500으로 죽는 대신 코퍼스에서 실제 질문을
        반환해야 한다 - "질문이 아예 안 나오는" 상황을 막는 안전장치."""
        from app.domain.interview import question_corpus, question_generator as qg

        def _broken(**kw):
            raise RuntimeError("Gemini 호출 실패")

        monkeypatch.setattr(qg, "generate_personalized_question", _broken)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["코퍼스 대체 질문입니다."])

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "코퍼스 대체 질문입니다."

    def test_generation_returns_none_falls_back_to_corpus(self, monkeypatch):
        """generate_personalized_question()이 예외 없이 None만 반환해도(키 없음 등, 실제
        구현의 정상적인 fail-open 방식) 코퍼스 폴백으로 이어져야 한다."""
        from app.domain.interview import question_corpus, question_generator as qg

        monkeypatch.setattr(qg, "generate_personalized_question", lambda **kw: None)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["코퍼스 대체 질문입니다."])

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "코퍼스 대체 질문입니다."

    def test_generation_failure_and_empty_corpus_returns_hardcoded_fallback(self, monkeypatch):
        """생성도 실패하고 대체할 코퍼스 풀도 없는 극단적인 경우에도 빈 문자열이 아니라
        하드코딩된 최후 보루 문장을 반환해야 한다."""
        from app.domain.interview import question_corpus, question_generator as qg

        def _broken(**kw):
            raise RuntimeError("Gemini 호출 실패")

        monkeypatch.setattr(qg, "generate_personalized_question", _broken)
        monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: [])

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "간단하게 자기소개 부탁드립니다."

    def test_corpus_lookup_failure_never_raises(self, monkeypatch):
        """생성도 실패하고, 코퍼스 조회(question_corpus.get_pool)마저 예상 못 한 이유로
        예외를 던지는 극단적인 경우에도 이 함수는 절대 예외를 밖으로 흘려보내면 안 된다 -
        router.py가 503/500을 반환하는 마지막 구멍을 막는 테스트."""
        from app.domain.interview import question_corpus, question_generator as qg

        def _broken_generate(**kw):
            raise RuntimeError("Gemini 호출 실패")

        def _broken_pool(category, job):
            raise OSError("코퍼스 파일을 읽을 수 없습니다")

        monkeypatch.setattr(qg, "generate_personalized_question", _broken_generate)
        monkeypatch.setattr(question_corpus, "get_pool", _broken_pool)

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "간단하게 자기소개 부탁드립니다."

    def test_validation_infra_failure_is_fail_open(self, monkeypatch):
        """검증 모듈 자체가 예외를 던져도(임포트 실패 등) 생성된 원본은 그대로 반환돼야
        한다 - 검증 인프라 장애가 질문 생성 실패로 이어지면 안 된다."""
        from app.domain.interview import question_generator as qg
        from app.domain.interview import question_similarity

        monkeypatch.setattr(qg, "generate_personalized_question", lambda **kw: "Gemini가 만든 질문입니다.")

        def _broken(*a, **kw):
            raise RuntimeError("검증 인프라 다운")

        monkeypatch.setattr(question_similarity, "is_topically_relevant", _broken)

        result = qg.generate_validated_question(job="백엔드", category="기술_직무역량")

        assert result == "Gemini가 만든 질문입니다."


# class TestGenerateRawCandidates:
#     """2026-08-10 추가: EC2(모델 파일 없음)가 Tailscale로 연결된 로컬/학원 PC의 LoRA
#     추론을 원격 호출하는 디스패치 로직(_generate_raw_candidates) 테스트. 실제 torch 추론과
#     실제 네트워크 호출은 각각 _generate_raw_candidates_locally / requests.post를 모킹해서
#     격리한다."""
# 
#     def test_no_server_url_goes_straight_to_local(self, monkeypatch):
#         """lora_server_url이 비어 있으면(기존 로컬 개발 환경) 원격 호출을 아예 시도하지
#         않고 바로 로컬 추론으로 가야 한다 - 동작 변화가 없어야 한다는 게 핵심."""
#         from app.domain.interview import question_generator as qg
# 
#         monkeypatch.setattr(qg.settings, "lora_server_url", "")
#         local = Mock(return_value=["로컬에서 만든 질문입니다."])
#         monkeypatch.setattr(qg, "_generate_raw_candidates_locally", local)
# 
#         with patch("requests.post") as mock_post:
#             result = qg._generate_raw_candidates(job="백엔드", context="", category="")
# 
#         assert result == ["로컬에서 만든 질문입니다."]
#         mock_post.assert_not_called()
#         local.assert_called_once()
# 
#     def test_server_url_set_and_remote_succeeds_skips_local(self, monkeypatch):
#         """원격 서버가 후보를 정상적으로 돌려주면 로컬 추론(_generate_raw_candidates_locally,
#         즉 무거운 torch 로딩)은 아예 호출하지 않아야 한다."""
#         from app.domain.interview import question_generator as qg
# 
#         monkeypatch.setattr(qg.settings, "lora_server_url", "http://100.1.2.3:8000")
#         monkeypatch.setattr(qg.settings, "lora_server_key", "fake-key")
#         local = Mock(side_effect=AssertionError("로컬 추론을 호출하면 안 된다"))
#         monkeypatch.setattr(qg, "_generate_raw_candidates_locally", local)
# 
#         fake_response = Mock()
#         fake_response.raise_for_status = Mock()
#         fake_response.json.return_value = {"candidates": ["원격에서 만든 질문입니다."]}
# 
#         with patch("requests.post", return_value=fake_response) as mock_post:
#             result = qg._generate_raw_candidates(job="백엔드", context="", category="기술_직무역량")
# 
#         assert result == ["원격에서 만든 질문입니다."]
#         _, kwargs = mock_post.call_args
#         assert kwargs["headers"] == {"X-Internal-Key": "fake-key"}
#         assert kwargs["json"] == {"job": "백엔드", "context": "", "category": "기술_직무역량"}
#         assert "/internal/lora/generate-candidates" in mock_post.call_args[0][0]
# 
#     def test_remote_failure_falls_back_to_local(self, monkeypatch):
#         """원격 서버가 꺼져 있거나(연결 오류) 응답이 비어 있으면, 예외를 던지지 않고
#         로컬 추론으로 자동 복구돼야 한다(로컬에도 모델 파일이 있는 개발 PC 기준)."""
#         from app.domain.interview import question_generator as qg
# 
#         monkeypatch.setattr(qg.settings, "lora_server_url", "http://100.1.2.3:8000")
#         local = Mock(return_value=["로컬 폴백 질문입니다."])
#         monkeypatch.setattr(qg, "_generate_raw_candidates_locally", local)
# 
#         with patch("requests.post", side_effect=RuntimeError("connection refused")):
#             result = qg._generate_raw_candidates(job="백엔드", context="", category="")
# 
#         assert result == ["로컬 폴백 질문입니다."]
#         local.assert_called_once()
# 
#     def test_remote_empty_results_falls_back_to_local(self, monkeypatch):
#         """원격 서버는 응답했지만 candidates가 빈 리스트면(그 PC도 모델 파일이 없는 등)
#         마찬가지로 로컬 추론으로 넘어가야 한다."""
#         from app.domain.interview import question_generator as qg
# 
#         monkeypatch.setattr(qg.settings, "lora_server_url", "http://100.1.2.3:8000")
#         local = Mock(return_value=["로컬 폴백 질문입니다."])
#         monkeypatch.setattr(qg, "_generate_raw_candidates_locally", local)
# 
#         fake_response = Mock()
#         fake_response.raise_for_status = Mock()
#         fake_response.json.return_value = {"candidates": []}
# 
#         with patch("requests.post", return_value=fake_response):
#             result = qg._generate_raw_candidates(job="백엔드", context="", category="")
# 
#         assert result == ["로컬 폴백 질문입니다."]
#         local.assert_called_once()


# ======================================================================================
# 2026-08-29 실전면접 개편 - 회원 스펙/대조 문맥, 실전 모드 프롬프트
# ======================================================================================


def _capture_prompt(monkeypatch, **kwargs) -> str:
    """generate_personalized_question을 한 번 호출하고 Gemini에 실제로 넘어간 프롬프트를
    돌려준다 - 위 테스트들이 반복하던 FakeClient 4중첩을 새 테스트에서는 한 곳에 모았다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")
    captured = {}

    class FakeResponse:
        text = "실제로 그 경험에서 어떤 역할을 맡으셨나요?"

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        generate_personalized_question(**kwargs)
    return captured["prompt"]


def test_real_mode_uses_professional_interviewer_persona(monkeypatch):
    """실전면접은 "이력과 공고를 미리 검토하고 들어온 면접관" 역할을 준다 - 압박/공격
    면접관 설정은 설계 문서의 범위 제외 항목이라 쓰지 않는다."""
    prompt = _capture_prompt(
        monkeypatch, job="백엔드 개발자", tech_summary="", interview_mode="real"
    )

    assert "전문 채용 면접관" in prompt
    assert "압박하거나 몰아세우는 말투" in prompt
    assert "음성으로 낭독된다" in prompt


def test_practice_mode_keeps_original_persona_and_no_real_rules(monkeypatch):
    """무료 경로는 원래 프롬프트 그대로여야 한다 - 실전 전용 규칙이 새어 들어가면 안 된다."""
    prompt = _capture_prompt(monkeypatch, job="백엔드 개발자", tech_summary="")

    assert "너는 IT 채용 면접관이다." in prompt
    assert "음성으로 낭독된다" not in prompt


def test_member_spec_context_added_with_no_invention_rule(monkeypatch):
    """회원 스펙이 주어지면 그 블록과 함께 "여기 없는 경험을 전제하지 마라" 규칙이 붙어야
    한다 - 행동 질문 슬롯도 Gemini가 만들기 때문에 이 가드가 없으면 지원자가 답할 수 없는
    질문(있지도 않은 프로젝트를 전제)이 나온다."""
    prompt = _capture_prompt(
        monkeypatch,
        job="백엔드 개발자",
        tech_summary="",
        member_spec_context="[회원이 저장한 스펙]\n- 보유 기술: Java",
        interview_mode="real",
    )

    assert "- 보유 기술: Java" in prompt
    assert "거기 없는 " in prompt and "전제하는 질문은 절대 만들지 마라" in prompt


def test_gap_context_forbids_treating_unverified_as_missing(monkeypatch):
    """`스펙에서 확인되지 않음`을 "없다"로 단정하지 말라는 규칙이 함께 가야 한다 - 스펙에
    입력만 안 했을 뿐 실제로는 보유했을 수 있다."""
    prompt = _capture_prompt(
        monkeypatch,
        job="백엔드 개발자",
        tech_summary="",
        gap_context="[대조]\n- 스펙에서 확인되지 않음: Kubernetes 운영",
        interview_mode="real",
    )

    assert "스펙에서 확인되지 않음: Kubernetes 운영" in prompt
    assert "없다고 단정하거나 추궁하지 말고" in prompt


def test_asked_questions_added_with_no_repeat_rule(monkeypatch):
    """이미 나온 질문 목록이 있으면 프롬프트에 들어가고 중복 금지 규칙이 붙어야 한다."""
    prompt = _capture_prompt(
        monkeypatch,
        job="백엔드 개발자",
        tech_summary="",
        interview_mode="real",
        asked_questions=["자기소개를 해주세요.", "   "],
    )

    assert "이번 세션에서 이미 나온 질문" in prompt
    assert "- 자기소개를 해주세요." in prompt
    assert "소재·관점이 겹치는 질문은 만들지 마라" in prompt


def test_no_asked_questions_omits_the_block(monkeypatch):
    """빈 목록이면 그 블록 자체가 안 붙어야 한다 - 무료 경로 프롬프트가 그대로 유지된다."""
    prompt = _capture_prompt(
        monkeypatch, job="백엔드 개발자", tech_summary="", asked_questions=[]
    )

    assert "이번 세션에서 이미 나온 질문" not in prompt
