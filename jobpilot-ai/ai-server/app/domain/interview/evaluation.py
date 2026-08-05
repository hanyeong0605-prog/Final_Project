"""모의면접 종합 평가 리포트 생성 (Gemini).

질문 생성(question_generator.py), 음성 분석(audio_analysis.py), 얼굴 분석(프론트엔드
faceAnalysis.ts, MediaPipe)의 결과를 한데 모아 Gemini에게 종합 평가를 요청한다. 이 모듈
자체는 STT나 신호처리를 하지 않는다 - 이미 계산된 결과(텍스트/숫자)를 모아서 넘기기만 한다.

2026-08-05 설계 메모: audio_analysis.py/faceAnalysis.ts와 같은 원칙을 따른다 - "긴장도 68%"
같은 확신에 찬 심리 판독은 하지 않는다. Gemini 프롬프트에도 명시적으로 "측정된 지표를 근거로만
말하고, 확정적인 심리 상태 판단은 피하라"고 지시한다. 이 기능은 Gemini가 있어야만 성립하는
기능이라(질문 생성처럼 "자체 모델이 메인, Gemini는 보조 검수"인 구조가 아님) 키가 없으면
fail-open으로 대충 채우지 않고 명확한 안내 메시지를 반환한다.
"""

from app.core.config import settings

_NO_KEY_MESSAGE = (
    "AI 종합 평가를 사용하려면 GEMINI_API_KEY 설정이 필요합니다. "
    "그동안 개별 지표(발화/얼굴 분석 결과)는 그대로 확인하실 수 있습니다."
)


def generate_report(
    question: str,
    transcript: str,
    voice_metrics: dict,
    face_metrics: dict | None,
) -> str:
    """면접 질문, STT 답변 텍스트, 음성 지표(dict), 얼굴 지표(dict, 없으면 None)를 받아
    Gemini로 종합 평가 리포트 문단을 생성한다. 실패하거나 키가 없으면 안내/에러 메시지를
    그대로 반환한다(예외를 던지지 않음 - 호출부에서 그대로 화면에 보여주면 됨)."""
    if not settings.gemini_api_key:
        return _NO_KEY_MESSAGE

    if face_metrics:
        face_metrics_text = (
            f"- 실제 깜빡임 횟수: {face_metrics.get('blinkCount')}회\n"
            f"- 분당 깜빡임(환산값, 답변이 짧으면 부풀려질 수 있음): "
            f"{face_metrics.get('blinkRatePerMin')}회/분\n"
            f"- 고개 움직임 정도(0~100, 상대값이지 각도 아님): {face_metrics.get('headMovement')}\n"
        )
    else:
        face_metrics_text = "- (얼굴 분석 데이터 없음 - 카메라를 안 썼거나 인식에 실패함)\n"

    prompt = (
        "당신은 채용면접 코치입니다. 아래 정보를 바탕으로 지원자의 답변에 대한 종합 평가 "
        "리포트를 작성하세요.\n\n"
        f"[면접 질문]\n{question}\n\n"
        f"[지원자 답변(음성을 텍스트로 변환)]\n{transcript}\n\n"
        "[음성 지표 - 실측값. 심리 상태를 단정하는 근거로 쓰지 말고 경향으로만 언급할 것]\n"
        f"- 말속도: 분당 {voice_metrics.get('speaking_rate_chars_per_min')}자\n"
        f"- 평균 음높이(피치): {voice_metrics.get('pitch_mean_hz')}Hz\n"
        f"- 음높이(피치) 변동폭: {voice_metrics.get('pitch_variation_hz')}Hz\n"
        f"- 침묵 비율: {voice_metrics.get('silence_ratio')}\n"
        f"- 긴 침묵(1초 이상) 횟수: {voice_metrics.get('long_pause_count')}회\n"
        f"- 음량 변동폭: {voice_metrics.get('volume_variation_rms')}\n\n"
        "[얼굴/시선 지표 - 실측값, 마찬가지로 경향으로만 언급할 것]\n"
        f"{face_metrics_text}\n"
        "[작성 규칙]\n"
        "1. '긴장도 68%', '자신감이 부족함' 같은 확정적인 심리 판단은 하지 마라 - 수치가 "
        "보여주는 경향만 서술하고, 지적할 땐 '~한 경향이 있어 보입니다' 같은 완곡한 표현을 "
        "써라\n"
        "2. 평균 음높이(피치) 수치 자체를 근거로 '음이 낮다/높다'처럼 절대적으로 단정하지 "
        "마라 - 사람마다 원래 목소리 톤이 다르므로 그 자체는 문제가 아니다. 대신 변동폭이 "
        "작으면(단조로운 톤) '억양 변화가 적어 다소 단조롭게 들릴 수 있다', 변동폭이 크면 "
        "'억양에 강약이 있어 생동감 있게 들린다'처럼 톤의 '변화 패턴'을 근거로 서술해라\n"
        "3. 답변 내용(직무 적합성, 논리성, 구체성)에 대한 평가를 가장 비중 있게 다뤄라 - "
        "음성/얼굴 지표는 어디까지나 보조 지표다\n"
        "4. 잘한 점과 개선할 점을 각각 최소 1개씩 포함해라\n"
        "5. 250자 내외의 자연스러운 한국어 문단으로 작성해라 - 항목별 나열(불릿) 대신 문단 "
        "형태로 써라\n"
        "6. 리포트 본문만 출력해라 - 제목이나 다른 부연 설명은 붙이지 마라"
    )

    try:
        import google.generativeai as genai

        genai.configure(api_key=settings.gemini_api_key)
        model = genai.GenerativeModel(settings.gemini_model)
        response = model.generate_content(prompt)
        report = (response.text or "").strip()
        return report or _NO_KEY_MESSAGE
    except Exception as e:
        return f"AI 종합 평가 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
