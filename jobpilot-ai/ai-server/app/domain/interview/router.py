"""모의면접 답변 분석 API.

지금은 프론트에 마이크 녹음 UI가 아직 없어서(캠/마이크 없는 컴퓨터에서도 먼저
로직만 검증하기 위해), 오디오 파일을 업로드하면 STT + 음성 지표 분석 결과를
바로 돌려주는 형태로 만든다. 나중에 프론트에서 MediaRecorder로 녹음한 blob을
그대로 이 엔드포인트에 보내면 된다 - 인터페이스는 지금과 동일(멀티파트 파일 업로드).
"""

import shutil
import tempfile
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, UploadFile
from pydantic import BaseModel

from app.domain.interview.audio_analysis import analyze_voice, transcribe
from app.domain.interview.evaluation import generate_report
from app.domain.interview.question_generator import DEFAULT_JOB, generate_question

router = APIRouter()


class NextQuestionRequest(BaseModel):
    job: str = DEFAULT_JOB
    # 이전 답변 텍스트(선택) - question_generator.py 상단 설계 메모 참고: 지금 학습 데이터엔
    # 진짜 세션 문맥이 없어서 효과는 제한적이지만, 나중에 문맥형 학습으로 갈 걸 대비해 받아둔다.
    context: str = ""


@router.post("/next-question")
def next_question(body: NextQuestionRequest):
    try:
        question = generate_question(job=body.job, context=body.context)
    except RuntimeError as e:
        # 모델 파일이 없는 경우(아직 학습/배포 안 됨) - 500 대신 명확한 메시지로 알려준다.
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
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
    voice_metrics: dict
    # 프론트(faceAnalysis.ts summarizeFaceFrames)가 브라우저에서 계산한 결과. 카메라를 안 썼거나
    # 얼굴 인식에 실패하면 없을 수 있어서 선택값.
    face_metrics: dict | None = None


@router.post("/evaluate")
def evaluate(body: EvaluateRequest):
    """질문 + STT 답변 + 음성/얼굴 지표를 모아 Gemini에게 종합 평가 리포트를 요청한다.
    2026-08-05: /analyze-answer(음성 분석)와 별개 엔드포인트로 분리했다 - 얼굴 지표는
    브라우저에서 답변 종료 후에 계산되므로, 프론트가 (1) /analyze-answer로 STT+음성 지표를
    받고 (2) 그 결과 + 자체 계산한 얼굴 지표를 모아 이 엔드포인트를 호출하는 2단계 흐름이다."""
    report = generate_report(
        question=body.question,
        transcript=body.transcript,
        voice_metrics=body.voice_metrics,
        face_metrics=body.face_metrics,
    )
    return {"report": report}
