"""모의면접 답변 분석 API.

지금은 프론트에 마이크 녹음 UI가 아직 없어서(캠/마이크 없는 컴퓨터에서도 먼저
로직만 검증하기 위해), 오디오 파일을 업로드하면 STT + 음성 지표 분석 결과를
바로 돌려주는 형태로 만든다. 나중에 프론트에서 MediaRecorder로 녹음한 blob을
그대로 이 엔드포인트에 보내면 된다 - 인터페이스는 지금과 동일(멀티파트 파일 업로드).
"""

import logging
import shutil
import tempfile
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, Response, UploadFile
from pydantic import BaseModel

from app.domain.interview.audio_analysis import analyze_voice, transcribe
from app.domain.interview.evaluation import generate_report, generate_session_report
from app.domain.interview.question_generator import DEFAULT_JOB, generate_personalized_question, generate_question
from app.domain.interview.tts import DEFAULT_VOICE_ID, list_voice_options, synthesize_speech

logger = logging.getLogger(__name__)

router = APIRouter()


class NextQuestionRequest(BaseModel):
    job: str = DEFAULT_JOB
    # 이전 답변 텍스트(선택) - question_generator.py 상단 설계 메모 참고: 지금 학습 데이터엔
    # 진짜 세션 문맥이 없어서 효과는 제한적이지만, 나중에 문맥형 학습으로 갈 걸 대비해 받아둔다.
    context: str = ""
    # 2026-08-06: QUESTION_CATEGORIES 중 하나(선택) - 재학습 데이터가 카테고리별로 태깅돼
    # 있어서 지정하면 그 카테고리 스타일 질문이 나온다. 체험판/결제 등급별로 난이도를 다르게
    # 주는 기능(결제 설계 문서, 태스크 #1)에서 이 필드를 그대로 활용할 예정.
    category: str = ""
    # 2026-08-06: 회원 프로필(MemberCareerProfile)의 기술/프로젝트 요약(선택) - 프론트가
    # /api/v1/members/me/career-profile을 조회해 채워 보낸다. 값이 있으면 LoRA 모델
    # 대신 generate_personalized_question(Gemini)으로 라우팅된다 - question_generator.py의
    # generate_personalized_question docstring 설계 메모 참고. 프로필이 없거나 스킵한
    # 사용자는 빈 문자열이 오고, 기존 LoRA 경로가 그대로 적용된다(동작 변화 없음).
    tech_summary: str = ""

    def wants_personalized(self) -> bool:
        # 2026-08-06: job이 기본값(DEFAULT_JOB)이 아니라는 건 (a) 회원 프로필의 targetRole이
        # 채워졌거나 (b) 모의면접 시작 화면에서 "분야"를 명시적으로 골랐다는 뜻이다 - 둘 중
        # 하나라도, 또는 기술 요약이 있으면 Gemini 맞춤 질문 경로를 탄다. 아무 정보도 없는
        # 사용자(job이 기본값 그대로, tech_summary도 빈 문자열)는 기존 LoRA 경로 그대로.
        return self.tech_summary.strip() != "" or self.job.strip() not in ("", DEFAULT_JOB)


@router.post("/next-question")
def next_question(body: NextQuestionRequest):
    try:
        # The trained LoRA artifact is intentionally not included in the Docker image.
        # Use Gemini for every request when it is configured, including users who have
        # not completed a career profile yet.  Without this fallback the default path
        # tries to load the absent artifact and returns 503 in production.
        question = generate_personalized_question(
            job=body.job, tech_summary=body.tech_summary, category=body.category
        )
        if question is None:
            question = generate_question(job=body.job, context=body.context, category=body.category)
    except RuntimeError as e:
        # 모델 파일이 없는 경우(아직 학습/배포 안 됨) - 500 대신 명확한 메시지로 알려준다.
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        # 2026-08-06: 그냥 raise HTTPException만 하면 예외가 "처리됨" 취급이라 콘솔에 트레이스백이
        # 안 찍혀서, 새 어댑터 교체 후 KeyError('__reduce_cython__') 같은 원인 불명 에러를 디버깅할
        # 방법이 없었다 - logger.exception으로 전체 스택을 콘솔에 남기고 나서 HTTPException을 던진다.
        logger.exception("질문 생성 실패")
        raise HTTPException(status_code=500, detail=f"질문 생성 중 오류: {type(e).__name__}: {e}")

    return {"question": question}

# 브라우저 MediaRecorder 기본 산출물(webm/opus)과 흔한 업로드 포맷을 넉넉히 허용.
# mp4/mkv는 영상 컨테이너지만 ffmpeg가 오디오 트랙만 알아서 뽑아내므로 그대로 처리 가능.
ALLOWED_SUFFIXES = {".wav", ".mp3", ".m4a", ".webm", ".ogg", ".mp4", ".mkv"}


@router.post("/analyze-answer")
async def analyze_answer(audio: UploadFile = File(...)):
    suffix = Path(audio.filename or "").suffix.lower()
    if suffix not in ALLOWED_SUFFIXES:
        raise HTTPException(
            status_code=400,
            detail=f"지원하지 않는 파일 형식입니다 ({suffix or '확장자 없음'}). 허용: {sorted(ALLOWED_SUFFIXES)}",
        )

    # whisper/librosa 둘 다 파일 경로를 받는 API라 업로드 스트림을 임시 파일로 먼저 내린다.
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        shutil.copyfileobj(audio.file, tmp)
        tmp_path = tmp.name

    try:
        transcription = transcribe(tmp_path)
        metrics = analyze_voice(tmp_path, transcription.text)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"분석 중 오류: {type(e).__name__}: {e}")
    finally:
        Path(tmp_path).unlink(missing_ok=True)

    return {
        "transcript": transcription.text,
        # 2026-08-05: whisper 인식 확신도가 낮았던 답변인지 알려주는 참고 신호(audio_analysis.py
        # TranscriptionResult 설명 참고) - 프론트에서 "인식이 불안정했을 수 있어요" 경고용.
        "low_confidence_transcript": transcription.low_confidence,
        "metrics": metrics.to_dict(),
    }


class EvaluateRequest(BaseModel):
    question: str
    transcript: str
    # /analyze-answer 응답의 "metrics"를 그대로 넣으면 되는 자유 형식 dict - VoiceMetrics의
    # 필드가 늘어나도 이 스키마를 안 고쳐도 되게 느슨하게 받는다.
    # 2026-08-05: 마이크 없이 텍스트로 답변하는 경로가 생기면서 음성 지표 자체가 없을 수
    # 있어 선택값으로 바꿨다(face_metrics와 같은 이유).
    voice_metrics: dict | None = None
    # 프론트(faceAnalysis.ts summarizeFaceFrames)가 브라우저에서 계산한 결과. 카메라를 안 썼거나
    # 얼굴 인식에 실패하면 없을 수 있어서 선택값.
    face_metrics: dict | None = None


@router.post("/evaluate")
def evaluate(body: EvaluateRequest):
    """질문 + STT 답변 + 음성/얼굴 지표를 모아 Gemini에게 종합 평가 리포트를 요청한다.
    2026-08-05: /analyze-answer(음성 분석)와 별개 엔드포인트로 분리했다 - 얼굴 지표는
    브라우저에서 답변 종료 후에 계산되므로, 프론트가 (1) /analyze-answer로 STT+음성 지표를
    받고 (2) 그 결과 + 자체 계산한 얼굴 지표를 모아 이 엔드포인트를 호출하는 2단계 흐름이다.

    2026-08-05: 프론트의 모의면접 세션(질문 3개) 흐름은 이제 질문마다 이 엔드포인트를 부르지
    않고, 세션이 끝난 뒤 /evaluate-session을 한 번만 부른다(토큰/비용 절감 + 질문 단위가
    아니라 면접 전체를 놓고 보는 종합 평가). 이 엔드포인트 자체는 없애지 않았다 - 질문 1개짜리
    평가가 필요한 다른 흐름(예: 개발/디버깅, 향후 "질문 하나만 다시 평가받기" 같은 기능)에
    그대로 쓸 수 있다."""
    report = generate_report(
        question=body.question,
        transcript=body.transcript,
        voice_metrics=body.voice_metrics,
        face_metrics=body.face_metrics,
    )
    return {"report": report.to_dict()}


class SessionAnswer(BaseModel):
    question: str
    transcript: str
    voice_metrics: dict | None = None
    face_metrics: dict | None = None


class EvaluateSessionRequest(BaseModel):
    # 보통 3개(자기소개 포함) - 프론트 세션 진행 순서 그대로 담아서 보낸다.
    answers: list[SessionAnswer]


@router.post("/evaluate-session")
def evaluate_session(body: EvaluateSessionRequest):
    """모의면접 세션(질문 여러 개)을 한 번에 종합 평가한다 - Gemini 호출 1회로 세션
    전체(자기소개 포함 보통 3개 질문)를 평가해서 총평/강점/개선점/질문별 피드백+모범답안을
    반환한다."""
    report = generate_session_report([a.model_dump() for a in body.answers])
    return {"report": report.to_dict()}


# 2026-08-06: 질문 낭독용 TTS - 브라우저 기본 TTS(SpeechSynthesisUtterance)가 기계음성처럼
# 들린다는 피드백으로 Google Cloud TTS를 추가했다. 키가 없는 환경(로컬 개발 등)에서도 기능이
# 죽지 않도록 503으로 명확히 알려주고, 프론트는 그 응답을 보고 브라우저 기본 TTS로 자동
# 폴백한다(question_generator.py 모델 없음 처리와 같은 fail-open 패턴).
@router.get("/tts/voices")
def tts_voices():
    return {"voices": [v.to_dict() for v in list_voice_options()], "default": DEFAULT_VOICE_ID}


class TtsRequest(BaseModel):
    text: str
    voice: str = DEFAULT_VOICE_ID


@router.post("/tts")
def tts(body: TtsRequest):
    try:
        audio_bytes = synthesize_speech(body.text, body.voice)
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        logger.exception("TTS 생성 실패")
        raise HTTPException(status_code=500, detail=f"TTS 생성 중 오류: {type(e).__name__}: {e}")

    return Response(content=audio_bytes, media_type="audio/mpeg")
