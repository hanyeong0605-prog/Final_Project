"""evaluation.py 단위 테스트.

Gemini API를 실제로 호출하지 않는 부분만 다룬다(키 없을 때의 fail-clear 동작,
JSON 파싱/방어 로직) - 실제 Gemini 응답 파싱까지 검증하려면 API 키가 있는 통합
테스트가 별도로 필요하다(README 참고).
"""

from unittest.mock import patch

from app.domain.interview import evaluation
from app.domain.interview.evaluation import (
    EvaluationReport,
    SessionEvaluationReport,
    _as_str_list,
    _clamp_score,
    _parse_json_response,
    generate_report,
    generate_session_report,
)


def test_no_api_key_returns_guidance_message(monkeypatch):
    """GEMINI_API_KEY가 없으면 Gemini를 호출하지 않고, ok=False + 안내 메시지만
    채워서 즉시 반환해야 한다(fail-clear - 예외를 던지거나 대충 채우지 않음)."""
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "")

    report = generate_report(
        question="자기소개를 해주세요.",
        transcript="안녕하세요, 저는...",
        voice_metrics={},
        face_metrics=None,
    )

    assert isinstance(report, EvaluationReport)
    assert report.ok is False
    assert report.message == evaluation._NO_KEY_MESSAGE
    # ok=False일 땐 다른 필드는 전부 비어있어야 프론트가 message만 보고 그려도 안전하다.
    assert report.overall_score is None
    assert report.strengths == []
    assert report.model_answer is None


def test_parse_json_response_valid():
    raw = '{"overall_score": 4, "strengths": ["논리적으로 설명함"]}'
    data = _parse_json_response(raw)
    assert data == {"overall_score": 4, "strengths": ["논리적으로 설명함"]}


def test_parse_json_response_code_fenced():
    """response_mime_type=json으로 대부분 순수 JSON만 오지만, 혹시 ```json ... ```로
    감싸서 올 경우를 대비한 방어 로직 확인."""
    raw = '```json\n{"overall_score": 3}\n```'
    data = _parse_json_response(raw)
    assert data == {"overall_score": 3}


def test_parse_json_response_broken_returns_none():
    raw = "이건 JSON이 아니라 그냥 문장입니다."
    assert _parse_json_response(raw) is None


def test_clamp_score_out_of_range_and_invalid():
    assert _clamp_score(3) == 3
    assert _clamp_score(0) == 1  # 하한 미만 -> 1로 클램프
    assert _clamp_score(9) == 5  # 상한 초과 -> 5로 클램프
    assert _clamp_score("이상한 값") is None
    assert _clamp_score(None) is None


def test_as_str_list_filters_and_caps_length():
    # 빈 문자열/공백은 걸러내고, 최대 5개까지만 남긴다(_MAX_LIST_ITEMS).
    value = ["좋은 점 1", "  ", "", "좋은 점 2", "3", "4", "5", "6"]
    result = _as_str_list(value)
    assert result == ["좋은 점 1", "좋은 점 2", "3", "4", "5"]
    assert _as_str_list("리스트가 아님") == []


def test_typed_answer_without_voice_metrics_asks_for_null_delivery_score(monkeypatch):
    """마이크 없이 텍스트로 답변한 경우(voice_metrics=None, face_metrics=None) 프롬프트가
    '텍스트로 답변'이라고 명시하고, delivery_score를 null로 달라고 지시해야 한다 - 근거
    없는 전달력 점수를 억지로 만들어내면 안 된다."""
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "fake-key")
    captured = {}

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            raise RuntimeError("stop-here")

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        generate_report(
            question="자기소개를 해주세요.",
            transcript="타이핑으로 작성한 답변입니다.",
            voice_metrics=None,
            face_metrics=None,
        )

    prompt = captured["prompt"]
    assert "텍스트로 답변" in prompt
    assert "delivery_score는 억지로 만들지 말고 반드시 null" in prompt


def test_typed_answer_report_has_null_delivery_score(monkeypatch):
    """Gemini가 delivery_score: null을 반환하면 EvaluationReport.delivery_score도 None이어야
    한다(_clamp_score가 None을 그대로 통과시키는지 generate_report 전체 흐름에서 확인)."""
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = (
            '{"overall_score": 4, "content_score": 4, "delivery_score": null, '
            '"strengths": ["명확함"], "improvements": ["더 구체적으로"], '
            '"model_answer": "예시 답변", "next_steps": ["연습하기"]}'
        )

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        report = generate_report(
            question="자기소개를 해주세요.",
            transcript="타이핑으로 작성한 답변입니다.",
            voice_metrics=None,
            face_metrics=None,
        )

    assert report.ok is True
    assert report.delivery_score is None
    assert report.content_score == 4


def test_evaluation_report_to_dict_roundtrip():
    report = EvaluationReport(
        ok=True,
        overall_score=4,
        content_score=4,
        delivery_score=3,
        strengths=["강점1"],
        improvements=["개선1"],
        model_answer="모범답안 예시",
        next_steps=["다음 단계1"],
    )
    d = report.to_dict()
    assert d["ok"] is True
    assert d["overall_score"] == 4
    assert d["strengths"] == ["강점1"]
    assert d["model_answer"] == "모범답안 예시"


# 2026-08-05: 질문 3개를 한 번에 평가하는 세션 리포트 - "질문마다 부르지 말고 다 받은 뒤
# 한 번에 부르자"는 요청으로 추가됐다.


def test_session_no_api_key_returns_guidance_message(monkeypatch):
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "")
    report = generate_session_report([{"question": "Q1", "transcript": "A1", "voice_metrics": None, "face_metrics": None}])
    assert isinstance(report, SessionEvaluationReport)
    assert report.ok is False
    assert report.message == evaluation._NO_KEY_MESSAGE
    assert report.questions == []


def test_session_empty_answers_returns_guidance_message(monkeypatch):
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "fake-key")
    report = generate_session_report([])
    assert report.ok is False
    assert report.questions == []


def test_session_calls_gemini_exactly_once_for_three_questions(monkeypatch):
    """세션 안에 질문이 3개여도 Gemini 호출은 정확히 1번만 나가야 한다 - 이게 이 기능의
    핵심 목적(질문마다 호출하던 걸 세션당 1회로 줄임)이다."""
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "fake-key")
    call_count = {"n": 0}

    class FakeResponse:
        text = (
            '{"overall_score": 4, "content_score": 4, "delivery_score": null, '
            '"strengths": ["논리적임"], "improvements": ["더 구체적으로"], '
            '"next_steps": ["연습하기"], '
            '"questions": ['
            '{"feedback": "좋았어요", "model_answer": "모범답안1"}, '
            '{"feedback": "괜찮아요", "model_answer": "모범답안2"}, '
            '{"feedback": "무난해요", "model_answer": "모범답안3"}'
            "]}"
        )

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            call_count["n"] += 1
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    qa_pairs = [
        {"question": "자기소개를 해주세요.", "transcript": "답변1", "voice_metrics": None, "face_metrics": None},
        {"question": "강점은?", "transcript": "답변2", "voice_metrics": None, "face_metrics": None},
        {"question": "약점은?", "transcript": "답변3", "voice_metrics": None, "face_metrics": None},
    ]

    with patch("google.genai.Client", FakeClient):
        report = generate_session_report(qa_pairs)

    assert call_count["n"] == 1
    assert report.ok is True
    assert len(report.questions) == 3
    assert report.questions[0].question == "자기소개를 해주세요."
    assert report.questions[0].feedback == "좋았어요"
    assert report.questions[2].model_answer == "모범답안3"


def test_session_prompt_includes_all_questions_and_asks_for_null_delivery(monkeypatch):
    monkeypatch.setattr(evaluation.settings, "gemini_api_key", "fake-key")
    captured = {}

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            raise RuntimeError("stop-here")

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    qa_pairs = [
        {"question": "자기소개를 해주세요.", "transcript": "답변1", "voice_metrics": None, "face_metrics": None},
        {"question": "강점은?", "transcript": "답변2", "voice_metrics": None, "face_metrics": None},
    ]

    with patch("google.genai.Client", FakeClient):
        generate_session_report(qa_pairs)

    prompt = captured["prompt"]
    assert "[질문 1] 자기소개를 해주세요." in prompt
    assert "[질문 2] 강점은?" in prompt
    assert "delivery_score는 억지로 만들지 말고 반드시 null" in prompt
    assert "정확히 2개" in prompt


def test_session_report_to_dict_roundtrip():
    report = SessionEvaluationReport(
        ok=True,
        overall_score=4,
        content_score=4,
        delivery_score=None,
        strengths=["강점1"],
        improvements=["개선1"],
        next_steps=["다음 단계1"],
        questions=[evaluation.QuestionFeedback(question="Q1", feedback="피드백1", model_answer="모범답안1")],
    )
    d = report.to_dict()
    assert d["ok"] is True
    assert d["questions"] == [{"question": "Q1", "feedback": "피드백1", "model_answer": "모범답안1"}]
