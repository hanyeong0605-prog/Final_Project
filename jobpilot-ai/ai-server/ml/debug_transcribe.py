"""STT(whisper) 품질을 지금 당장 마이크로 말하지 않고도 테스트하기 위한 스크립트.

사용법:
  python ml/debug_transcribe.py <오디오파일경로> [모델이름 모델이름 ...]

  예) python ml/debug_transcribe.py sample.m4a base small
  -> 같은 오디오를 base/small 두 모델로 각각 돌려서 결과를 나란히 비교해준다
     (지금 서비스에서 쓰는 것과 동일한 무음 트리밍 + initial_prompt를 그대로 적용함 -
     audio_analysis.py의 실제 transcribe() 흐름을 그대로 재사용한다).

지금 말하면서 테스트할 수 없을 때 - 오디오 파일이 꼭 "방금 내가 녹음한 답변"일 필요는
없다. 아래 중 아무거나로 대체 가능:
  - 휴대폰 음성 메모 앱으로 예전에 녹음해둔 아무 한국어 음성 파일
  - 유튜브 등에서 받은 한국어 음성 클립
  - Windows 내레이터나 온라인 TTS로 한국어 문장을 읽게 해서 만든 음성 파일
    (실제 목소리는 아니지만 STT 파이프라인 자체 비교엔 문제없음)
  - AI Hub 채용면접 데이터셋에 원본 음성 파일이 같이 들어있다면 그걸 써도 됨

wav/mp3/m4a/webm 등 ffmpeg가 읽을 수 있는 포맷이면 다 된다(ffmpeg가 PATH에 있어야 함 -
서버 쪽 요구사항과 동일).
"""

import sys
import time
from pathlib import Path

# cmd에서 "python ml\debug_transcribe.py"처럼 직접 실행하면 sys.path에 ai-server
# 루트가 안 잡혀서 "app" 패키지를 못 찾는다(ModuleNotFoundError) - PyCharm에서 돌릴 땐
# 프로젝트 루트를 자동으로 잡아줘서 문제가 없었을 뿐. 실행 방식과 무관하게 항상 되도록
# 이 파일 기준 한 단계 위(ai-server 루트)를 sys.path에 직접 추가한다.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.interview.audio_analysis import (
    WHISPER_SAMPLE_RATE,
    _TRANSCRIBE_INITIAL_PROMPT,
    _install_numba_stub_if_needed,
    _load_audio_mono16k,
    _trim_silence,
)

# 2026-08-05: audio_analysis.py의 실제 서비스 코드에서는 whisper를 항상 함수 안에서
# "나중에" import해서(_get_whisper_model 등) 이 스텁 설치가 먼저 실행되도록 순서를
# 지켰는데, 이 스크립트를 처음 짤 때 실수로 whisper를 파일 맨 위에서 바로 import해서
# 스텁이 심어지기도 전에 whisper가 numba를 로드하려다 그대로 죽는 문제가 있었다
# (numba DLL 차단 재현). 반드시 스텁 설치 -> whisper import 순서를 지켜야 한다.
_install_numba_stub_if_needed()
import whisper  # noqa: E402


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    audio_path = sys.argv[1]
    model_names = sys.argv[2:] or ["base"]

    y = _load_audio_mono16k(audio_path)
    trimmed = _trim_silence(y, WHISPER_SAMPLE_RATE)
    print(f"원본 길이: {len(y) / WHISPER_SAMPLE_RATE:.1f}초 -> 무음 트리밍 후: {len(trimmed) / WHISPER_SAMPLE_RATE:.1f}초\n")

    for name in model_names:
        print(f"=== 모델: {name} ===")
        start = time.time()
        model = whisper.load_model(name)
        load_elapsed = time.time() - start

        start = time.time()
        result = model.transcribe(trimmed, language="ko", initial_prompt=_TRANSCRIBE_INITIAL_PROMPT)
        infer_elapsed = time.time() - start

        segments = result.get("segments") or []
        avg_logprobs = [s["avg_logprob"] for s in segments if "avg_logprob" in s]
        mean_logprob = sum(avg_logprobs) / len(avg_logprobs) if avg_logprobs else None

        print(f"로드 {load_elapsed:.1f}초 / 인식 {infer_elapsed:.1f}초")
        print(f"평균 확신도(avg_logprob, 0에 가까울수록 확신 높음): {mean_logprob}")
        print(f"결과: {result['text'].strip()}\n")


if __name__ == "__main__":
    main()
