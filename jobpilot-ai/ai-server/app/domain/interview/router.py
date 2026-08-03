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

from app.domain.interview.audio_analysis import analyze_voice, transcribe

router = APIRouter()

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
        transcript = transcribe(tmp_path)
        metrics = analyze_voice(tmp_path, transcript)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"분석 중 오류: {type(e).__name__}: {e}")
    finally:
        Path(tmp_path).unlink(missing_ok=True)

    return {
        "transcript": transcript,
        "metrics": metrics.to_dict(),
    }
