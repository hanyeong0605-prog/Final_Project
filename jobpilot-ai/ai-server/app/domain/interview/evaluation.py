"""모의면접 종합 평가 리포트 생성 (Gemini).

질문 생성(question_generator.py), 음성 분석(audio_analysis.py), 얼굴 분석(프론트엔드
faceAnalysis.ts, MediaPipe)의 결과를 한데 모아 Gemini에게 종합 평가를 요청한다. 이 모듈
자체는 STT나 신호처리를 하지 않는다 - 이미 계산된 결과(텍스트/숫자)를 모아서 넘기기만 한다.

2026-08-05 설계 메모: audio_analysis.py/faceAnalysis.ts와 같은 원칙을 따른다 - "긴장도 68%"
같은 확신에 찬 심리 판독은 하지 않는다. Gemini 프롬프트에도 명시적으로 "측정된 지표를 근거로만
말하고, 확정적인 심리 상태 판단은 피하라"고 지시한다. 이 기능은 Gemini가 있어야만 성립하는
기능이라(질문 생성처럼 "자체 모델이 메인, Gemini는 보조 검수"인 구조가 아님) 키가 없으면
fail-open으로 대충 채우지 않고 명확한 안내 메시지를 반환한다.

2026-08-05 추가: 원래는 250자 내외 문단 하나만 반환했는데, DeepInterview(오픈소스 AI
면접관)의 rubric 기반 ScoreCard(역량별 점수 + 강점/개선점 + 모범답안 + 다음 학습 방향)를
참고해서 구조화된 형태로 바꿨다. 점수는 "긴장도 68%"류의 심리 상태 추정이 아니라 답변
내용/전달력 자체에 대한 평가라서 수치화해도 앞서 말한 원칙과 충돌하지 않는다.

2026-08-10: 점수 표기를 1~5점에서 100점 만점(0~100 정수)으로 바꿨다 - 원래 1~5는
"과도한 정밀도로 보이지 않게" 일부러 좁힌 범위였는데, 100점 만점 요청에 맞춰 프롬프트에
"5점 단위로" 권장 문구를 넣어서 그 취지(가짜 정밀도 방지)는 유지했다."""

import json
from dataclasses import dataclass, field

from app.core.config import settings

_NO_KEY_MESSAGE = (
    "AI 종합 평가를 사용하려면 GEMINI_API_KEY 설정이 필요합니다. "
    "그동안 개별 지표(발화/얼굴 분석 결과)는 그대로 확인하실 수 있습니다."
)
_PARSE_FAIL_MESSAGE = "AI 종합 평가 응답을 해석하지 못했습니다. 잠시 후 다시 시도해 주세요."

# 리스트형 필드(강점/개선점/다음 학습 방향)가 너무 길게 늘어지지 않도록 방어적으로 상한을 둔다.
_MAX_LIST_ITEMS = 5


@dataclass
class EvaluationReport:
    """종합 평가 결과. ok=False면 message만 채워지고 나머지는 비어있다 - 호출부(router)와
    프론트가 항상 같은 필드 구조를 받도록 해서, "문자열이냐 dict냐"를 매번 구분하지 않아도
    되게 한다."""

    ok: bool
    message: str | None = None  # ok=False일 때 사용자에게 보여줄 안내/에러 메시지
    overall_score: int | None = None  # 총평 (0~100)
    content_score: int | None = None  # 답변 내용(직무 적합성/논리성/구체성) (0~100)
    delivery_score: int | None = None  # 전달력(음성 리듬·침묵, 얼굴 지표 있으면 반영) (0~100)
    strengths: list[str] = field(default_factory=list)
    improvements: list[str] = field(default_factory=list)
    model_answer: str | None = None  # 이 질문에 대한 모범 답안 예시
    next_steps: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "overall_score": self.overall_score,
            "content_score": self.content_score,
            "delivery_score": self.delivery_score,
            "strengths": self.strengths,
            "improvements": self.improvements,
            "model_answer": self.model_answer,
            "next_steps": self.next_steps,
        }


@dataclass
class QuestionFeedback:
    """세션 리포트 안에서 질문 1개에 대한 짧은 피드백 - question은 Gemini가 지어내지
    않도록 프론트/router가 넘겨준 원문을 그대로 echo해서 채운다(순서 매칭용)."""

    question: str
    feedback: str
    model_answer: str | None = None

    def to_dict(self) -> dict:
        return {"question": self.question, "feedback": self.feedback, "model_answer": self.model_answer}


@dataclass
class SessionEvaluationReport:
    """2026-08-05: 질문마다 Gemini를 호출하던 걸(질문 1개당 1회) 세션 전체(보통 3개)를
    한 번에 묶어서 호출 1회로 줄인 버전 - 토큰/비용 절감 + 개별 답변이 아니라 면접
    전체를 놓고 보는 종합 평가라는 취지에도 더 맞는다. EvaluationReport와 필드 구성을
    최대한 맞추되, model_answer(질문 1개짜리 필드) 대신 questions(질문별 피드백 배열)를 둔다."""

    ok: bool
    message: str | None = None
    overall_score: int | None = None
    content_score: int | None = None
    delivery_score: int | None = None
    strengths: list[str] = field(default_factory=list)
    improvements: list[str] = field(default_factory=list)
    next_steps: list[str] = field(default_factory=list)
    questions: list[QuestionFeedback] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "overall_score": self.overall_score,
            "content_score": self.content_score,
            "delivery_score": self.delivery_score,
            "strengths": self.strengths,
            "improvements": self.improvements,
            "next_steps": self.next_steps,
            "questions": [q.to_dict() for q in self.questions],
        }


def _clamp_score(value: object) -> int | None:
    """Gemini가 범위를 벗어난 값(-10, 105, "73점" 같은 문자열 등)을 줄 수 있어서 방어적으로
    정수화 + 0~100 범위로 자른다. 변환 자체가 안 되면 점수 없음(None)으로 처리한다."""
    try:
        n = int(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None
    return max(0, min(100, n))


def _as_str_list(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(v).strip() for v in value if str(v).strip()][:_MAX_LIST_ITEMS]


def _parse_json_response(raw: str) -> dict | None:
    """model_config로 response_mime_type="application/json"을 지정해두면 대부분 순수 JSON만
    오지만, 혹시 ```json ... ``` 코드펜스로 감싸서 줄 가능성에 대비해 방어적으로 벗겨낸다."""
    text = raw.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:]
        text = text.strip()
    try:
        data = json.loads(text)
    except (json.JSONDecodeError, ValueError):
        return None
    return data if isinstance(data, dict) else None


# 2026-08-05: generate_report(질문 1개용)와 generate_session_report(질문 3개 묶음용)가
# 음성/얼굴 지표를 프롬프트용 텍스트로 바꾸는 로직을 그대로 공유해서 함수로 뺐다.
def _voice_metrics_text(voice_metrics: dict | None) -> str:
    if not voice_metrics:
        return "- (음성 지표 없음 - 지원자가 마이크 대신 텍스트로 답변을 작성함)\n"
    return (
        f"- 말속도: 분당 {voice_metrics.get('speaking_rate_chars_per_min')}자\n"
        f"- 평균 음높이(피치): {voice_metrics.get('pitch_mean_hz')}Hz\n"
        f"- 음높이(피치) 변동폭: {voice_metrics.get('pitch_variation_hz')}Hz\n"
        f"- 침묵 비율: {voice_metrics.get('silence_ratio')}\n"
        f"- 긴 침묵(1초 이상) 횟수: {voice_metrics.get('long_pause_count')}회\n"
        f"- 음량 변동폭: {voice_metrics.get('volume_variation_rms')}\n"
    )


def _face_metrics_text(face_metrics: dict | None) -> str:
    if not face_metrics:
        return "- (얼굴 분석 데이터 없음 - 카메라를 안 썼거나 인식에 실패함)\n"
    return (
        f"- 실제 깜빡임 횟수: {face_metrics.get('blinkCount')}회\n"
        f"- 분당 깜빡임(환산값, 답변이 짧으면 부풀려질 수 있음): "
        f"{face_metrics.get('blinkRatePerMin')}회/분\n"
        f"- 고개 움직임 정도(0~100, 상대값이지 각도 아님): {face_metrics.get('headMovement')}\n"
    )


def generate_report(
    question: str,
    transcript: str,
    voice_metrics: dict | None,
    face_metrics: dict | None,
) -> EvaluationReport:
    """면접 질문, 답변 텍스트, 음성 지표(dict, 없으면 None), 얼굴 지표(dict, 없으면 None)를
    받아 Gemini로 구조화된 종합 평가를 생성한다. 실패하거나 키가 없으면 ok=False에 안내/에러
    메시지만 채워서 반환한다(예외를 던지지 않음 - 호출부에서 ok만 보고 분기하면 됨).

    2026-08-05: 마이크/카메라를 못 쓰는 사용자를 위한 "타이핑으로 답변" 경로가 생기면서
    voice_metrics가 없을 수 있게 됐다(face_metrics는 원래도 카메라 미사용 시 None이었음).
    이 경우 delivery_score(전달력)는 애초에 평가할 근거가 없으므로 null로 받는다 -
    억지로 숫자를 만들어내지 않는다(감정/긴장도 추정 금지와 같은 원칙)."""
    if not settings.gemini_api_key:
        return EvaluationReport(ok=False, message=_NO_KEY_MESSAGE)

    voice_metrics_text = _voice_metrics_text(voice_metrics)
    face_metrics_text = _face_metrics_text(face_metrics)
    has_delivery_signal = bool(voice_metrics) or bool(face_metrics)

    prompt = (
        "당신은 채용면접 코치입니다. 아래 정보를 바탕으로 지원자의 답변을 평가해서 정해진 "
        "JSON 형식으로만 응답하세요.\n\n"
        f"[면접 질문]\n{question}\n\n"
        f"[지원자 답변 텍스트]\n{transcript}\n\n"
        "[음성 지표 - 실측값. 심리 상태를 단정하는 근거로 쓰지 말고 경향으로만 언급할 것]\n"
        f"{voice_metrics_text}\n"
        "[얼굴/시선 지표 - 실측값, 마찬가지로 경향으로만 언급할 것]\n"
        f"{face_metrics_text}\n"
        "[작성 규칙]\n"
        "1. '긴장도 68%', '자신감이 부족함' 같은 확정적인 심리 판단은 하지 마라 - 음성/얼굴 "
        "지표에 대해 언급할 땐 수치가 보여주는 경향만 서술하고, 지적할 땐 '~한 경향이 있어 "
        "보입니다' 같은 완곡한 표현을 써라\n"
        "2. 평균 음높이(피치) 수치 자체를 근거로 '음이 낮다/높다'처럼 절대적으로 단정하지 "
        "마라 - 사람마다 원래 목소리 톤이 다르므로 그 자체는 문제가 아니다. 대신 변동폭이 "
        "작으면(단조로운 톤) '억양 변화가 적어 다소 단조롭게 들릴 수 있다', 변동폭이 크면 "
        "'억양에 강약이 있어 생동감 있게 들린다'처럼 톤의 '변화 패턴'을 근거로 서술해라\n"
        "3. overall_score/content_score는 100점 만점(0~100 정수, 5점 단위로 주는 것을 "
        "권장 - 과도하게 정밀한 인상을 주지 않기 위함)으로, 답변 내용(직무 적합성/논리성/"
        "구체성)을 가장 비중 있게 반영해라 - 음성/얼굴 지표는 content_score에 영향을 주지 마라\n"
        + (
            "3-1. delivery_score는 100점 만점(0~100 정수, 5점 단위 권장)으로, 음성/얼굴 "
            "지표를 근거로 평가해라\n"
            if has_delivery_signal
            else "3-1. 음성/얼굴 지표가 전혀 없으므로(텍스트로만 답변함) delivery_score는 "
            "억지로 만들지 말고 반드시 null로 출력해라 - 전달력을 평가할 근거 자체가 없다\n"
        )
        + "4. strengths(잘한 점), improvements(개선할 점)는 각각 1~3개, 짧고 구체적인 한국어 "
        "문장으로 작성해라(각 문장 60자 내외) - 음성/얼굴 지표가 없으면 답변 내용만으로 "
        "작성하고 말투/표정 관련 언급은 하지 마라\n"
        "5. model_answer는 이 질문에 실제로 쓸 수 있는 모범 답안 예시를 200자 내외 한국어 "
        "문단으로 작성해라 - 지원자의 답변 내용과 완전히 무관한 소재보다는, 지원자가 이미 "
        "언급한 경험/맥락을 살리되 더 구체적이고 논리적으로 다듬은 버전으로 만들어라\n"
        "6. next_steps(다음에 연습하면 좋을 점)는 1~3개, 실천 가능한 조언으로 작성해라\n"
        "7. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "overall_score": 0~100 정수,\n'
        '  "content_score": 0~100 정수,\n'
        '  "delivery_score": 0~100 정수 또는 null,\n'
        '  "strengths": ["문장", ...],\n'
        '  "improvements": ["문장", ...],\n'
        '  "model_answer": "문단",\n'
        '  "next_steps": ["문장", ...]\n'
        "}"
    )

    try:
        # 2026-08-05: google.generativeai는 2025-11-30부로 지원 종료(더 이상 유지보수 안 됨) -
        # 후속 공식 SDK인 google-genai로 전환. Client 인스턴스 하나에 api_key를 명시적으로
        # 넘기는 방식으로 바뀌었고(옛날처럼 전역 genai.configure() 없음), JSON 강제 출력도
        # GenerativeModel(generation_config=...) 대신 client.models.generate_content(config=...)로
        # 넘긴다. (참고: https://ai.google.dev/gemini-api/docs/migrate)
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=settings.gemini_api_key)
        response = client.models.generate_content(
            model=settings.gemini_model,
            contents=prompt,
            config=types.GenerateContentConfig(response_mime_type="application/json"),
        )
        data = _parse_json_response(response.text or "")
        if data is None:
            return EvaluationReport(ok=False, message=_PARSE_FAIL_MESSAGE)

        return EvaluationReport(
            ok=True,
            overall_score=_clamp_score(data.get("overall_score")),
            content_score=_clamp_score(data.get("content_score")),
            delivery_score=_clamp_score(data.get("delivery_score")),
            strengths=_as_str_list(data.get("strengths")),
            improvements=_as_str_list(data.get("improvements")),
            model_answer=(str(data.get("model_answer") or "").strip() or None),
            next_steps=_as_str_list(data.get("next_steps")),
        )
    except Exception as e:
        return EvaluationReport(
            ok=False,
            message=f"AI 종합 평가 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요.",
        )


def generate_session_report(qa_pairs: list[dict]) -> SessionEvaluationReport:
    """모의면접 세션(질문 여러 개, 보통 3개)을 한 번의 Gemini 호출로 종합 평가한다.

    qa_pairs 각 원소: {"question": str, "transcript": str, "voice_metrics": dict|None,
    "face_metrics": dict|None} - /analyze-answer + 프론트에서 모은 결과를 그대로 넘기면 된다.

    2026-08-05: 원래 질문마다 generate_report()를 한 번씩(세션당 최대 3회) 호출했는데,
    "질문 다 받고 한 번에 리포트 쓰는 게 낫지 않냐"는 요청으로 세션 전체를 한 번에 넘겨서
    호출 1회로 줄였다 - 토큰/비용도 아끼고, 개별 답변이 아니라 면접 전체를 놓고 보는
    종합 평가라는 취지에도 더 맞는다."""
    if not settings.gemini_api_key:
        return SessionEvaluationReport(ok=False, message=_NO_KEY_MESSAGE)
    if not qa_pairs:
        return SessionEvaluationReport(ok=False, message="평가할 답변이 없습니다.")

    # 세션 안에서 음성/텍스트 답변 모드가 섞이지는 않지만(한 세션은 처음에 한 번 선택),
    # 혹시 모를 경우를 대비해 하나라도 지표가 있으면 delivery_score를 평가하게 한다.
    has_delivery_signal = any(qa.get("voice_metrics") or qa.get("face_metrics") for qa in qa_pairs)

    qa_blocks = []
    for i, qa in enumerate(qa_pairs, start=1):
        qa_blocks.append(
            f"[질문 {i}] {qa.get('question', '')}\n"
            f"[답변 {i}] {qa.get('transcript', '')}\n"
            f"[답변 {i} 음성 지표]\n{_voice_metrics_text(qa.get('voice_metrics'))}"
            f"[답변 {i} 얼굴/시선 지표]\n{_face_metrics_text(qa.get('face_metrics'))}"
        )
    qa_text = "\n".join(qa_blocks)
    n = len(qa_pairs)

    prompt = (
        "당신은 채용면접 코치입니다. 아래는 모의면접에서 지원자가 받은 질문 "
        f"{n}개와 각각의 답변입니다. 질문 하나하나가 아니라 면접 전체를 하나의 세트로 "
        "평가해서 정해진 JSON 형식으로만 응답하세요.\n\n"
        f"{qa_text}\n\n"
        "[작성 규칙]\n"
        "1. '긴장도 68%', '자신감이 부족함' 같은 확정적인 심리 판단은 하지 마라 - 음성/얼굴 "
        "지표에 대해 언급할 땐 수치가 보여주는 경향만 서술하고, 지적할 땐 '~한 경향이 있어 "
        "보입니다' 같은 완곡한 표현을 써라\n"
        "2. 평균 음높이(피치) 수치 자체를 근거로 '음이 낮다/높다'처럼 절대적으로 단정하지 "
        "마라 - 사람마다 원래 목소리 톤이 다르므로 그 자체는 문제가 아니다. 대신 변동폭이 "
        "작으면(단조로운 톤) '억양 변화가 적어 다소 단조롭게 들릴 수 있다', 변동폭이 크면 "
        "'억양에 강약이 있어 생동감 있게 들린다'처럼 톤의 '변화 패턴'을 근거로 서술해라\n"
        "3. overall_score/content_score는 100점 만점(0~100 정수, 5점 단위로 주는 것을 "
        "권장 - 과도하게 정밀한 인상을 주지 않기 위함)으로, 면접 전체의 답변 내용(직무 "
        "적합성/논리성/구체성)을 가장 비중 있게 반영해라 - 음성/얼굴 지표는 content_score에 "
        "영향을 주지 마라\n"
        + (
            "3-1. delivery_score는 100점 만점(0~100 정수, 5점 단위 권장)으로, 음성/얼굴 "
            "지표를 근거로 평가해라\n"
            if has_delivery_signal
            else "3-1. 음성/얼굴 지표가 전혀 없으므로(텍스트로만 답변함) delivery_score는 "
            "억지로 만들지 말고 반드시 null로 출력해라 - 전달력을 평가할 근거 자체가 없다\n"
        )
        + "4. strengths(잘한 점), improvements(개선할 점)는 면접 전체를 놓고 각각 2~5개, "
        "짧고 구체적인 한국어 문장으로 작성해라(각 문장 60자 내외) - 음성/얼굴 지표가 없으면 "
        "답변 내용만으로 작성하고 말투/표정 관련 언급은 하지 마라\n"
        "5. next_steps(다음에 연습하면 좋을 점)는 2~5개, 실천 가능한 조언으로 작성해라\n"
        f"6. questions 배열은 반드시 입력받은 순서 그대로 정확히 {n}개를 채워라 - 각 원소는 "
        "그 질문 하나에 대한 한두 문장짜리 피드백(feedback)과, 그 질문에 실제로 쓸 수 있는 "
        "모범 답안 예시(model_answer, 150자 내외 - 지원자가 이미 언급한 경험/맥락을 살리되 "
        "더 구체적이고 논리적으로 다듬은 버전)로 구성해라\n"
        "7. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "overall_score": 0~100 정수,\n'
        '  "content_score": 0~100 정수,\n'
        '  "delivery_score": 0~100 정수 또는 null,\n'
        '  "strengths": ["문장", ...],\n'
        '  "improvements": ["문장", ...],\n'
        '  "next_steps": ["문장", ...],\n'
        f'  "questions": [{{"feedback": "문장", "model_answer": "문단"}}, ... (정확히 {n}개)]\n'
        "}"
    )

    try:
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=settings.gemini_api_key)
        response = client.models.generate_content(
            model=settings.gemini_model,
            contents=prompt,
            config=types.GenerateContentConfig(response_mime_type="application/json"),
        )
        data = _parse_json_response(response.text or "")
        if data is None:
            return SessionEvaluationReport(ok=False, message=_PARSE_FAIL_MESSAGE)

        raw_questions = data.get("questions")
        questions: list[QuestionFeedback] = []
        if isinstance(raw_questions, list):
            for i, qa in enumerate(qa_pairs):
                item = raw_questions[i] if i < len(raw_questions) and isinstance(raw_questions[i], dict) else {}
                questions.append(
                    QuestionFeedback(
                        question=qa.get("question", ""),
                        feedback=str(item.get("feedback") or "").strip(),
                        model_answer=(str(item.get("model_answer") or "").strip() or None),
                    )
                )

        return SessionEvaluationReport(
            ok=True,
            overall_score=_clamp_score(data.get("overall_score")),
            content_score=_clamp_score(data.get("content_score")),
            delivery_score=_clamp_score(data.get("delivery_score")),
            strengths=_as_str_list(data.get("strengths")),
            improvements=_as_str_list(data.get("improvements")),
            next_steps=_as_str_list(data.get("next_steps")),
            questions=questions,
        )
    except Exception as e:
        return SessionEvaluationReport(
            ok=False,
            message=f"AI 종합 평가 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요.",
        )
