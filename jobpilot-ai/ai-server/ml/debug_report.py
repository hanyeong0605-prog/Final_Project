"""STT + 음성 지표 분석 + Gemini 종합 리포트까지, 브라우저 없이 오디오/영상 파일 하나로
전체 파이프라인을 확인하는 스크립트.

주의 - 표정(얼굴) 분석은 여기서 안 나온다: 얼굴 지표는 프론트엔드가 브라우저에서 실시간
카메라 프레임으로 MediaPipe FaceLandmarker를 돌려서 계산하는 거라(faceAnalysis.ts),
서버 쪽에는 "저장된 영상 파일에서 얼굴을 분석하는" 기능 자체가 없다(의도적으로 그렇게
설계함 - audio_analysis.py 모듈 docstring 참고: 영상을 서버로 보내는 걸 피하려고 함).
그래서 이 스크립트는 face_metrics=None으로 리포트를 생성한다 - evaluation.py가 얼굴
데이터 없을 때를 이미 처리하도록 되어 있어서(카메라 안 쓴 경우와 동일하게 취급) 리포트
자체는 정상적으로 나온다. 얼굴 지표까지 실제로 보고 싶으면 브라우저에서 실제로 카메라 켜고
녹음해야 한다.

사용법:
  python ml/debug_report.py <오디오/영상파일경로> ["면접 질문(선택, 기본값 있음)"]
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.interview.audio_analysis import _install_numba_stub_if_needed  # noqa: E402

_install_numba_stub_if_needed()

from app.domain.interview.audio_analysis import analyze_voice, transcribe  # noqa: E402
from app.domain.interview.evaluation import generate_report  # noqa: E402

DEFAULT_QUESTION = "간단하게 자기소개 부탁드립니다."


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    audio_path = sys.argv[1]
    question = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_QUESTION

    print("1) STT 인식 중...")
    transcription = transcribe(audio_path)
    print(f"   인식 결과 (low_confidence={transcription.low_confidence}):\n   {transcription.text}\n")

    print("2) 음성 지표 분석 중...")
    metrics = analyze_voice(audio_path, transcription.text)
    for key, value in metrics.to_dict().items():
        print(f"   {key}: {value}")
    print()

    print("3) Gemini 종합 평가 생성 중... (얼굴 지표는 없음 - 위 설명 참고)")
    report = generate_report(
        question=question,
        transcript=transcription.text,
        voice_metrics=metrics.to_dict(),
        face_metrics=None,
    )
    print(f"\n=== AI 종합 평가 ===\n{report}")


if __name__ == "__main__":
    main()
