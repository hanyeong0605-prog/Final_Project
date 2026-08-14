"""AI Hub "채용면접 인터뷰 데이터"(TL_05.ICT, 신입) 실제 답변 음성으로 전달력(delivery_score)
예측 모델용 학습 데이터를 만든다.

2026-08-12 배경: interview_session_records(실사용자 모의면접 결과)가 아직 0건이라 실제
라벨(점수)이 없다. 대신 이미 실서비스에서 매 답변마다 Gemini가 delivery_score를 매기고
있으므로(evaluation.py), 그 판단을 "정답"으로 삼아 가벼운 로컬 회귀모델이 흉내 내게
학습시킨다(지식 증류) - real transcript + real audio(AI Hub 실제 면접 답변 음성)로
audio_analysis.analyze_voice()가 뽑는 피처는 100% 진짜고, 점수만 Gemini에게 한 번씩
빌려오는 방식이다.

피처 추출은 우리 서비스가 실제로 쓰는 audio_analysis.analyze_voice()를 그대로 재사용한다 -
학습 데이터와 실서비스 입력이 같은 함수로 만들어져야 모델이 실전에서도 의미가 있다.

사용법(ai-server 폴더에서, venv 활성화한 상태):
    python ml/build_delivery_score_dataset.py --limit 30
    (처음엔 --limit로 소량만 돌려서 파이프라인이 도는지 확인하고, 문제없으면 --limit 없이
    전체(1,753건, Female_New 기준)를 돌린다. 중간에 API 할당량에 걸려도 이미 처리된 건
    OUTPUT_CSV에 저장돼 있어서 다시 실행하면 이어서 진행된다 - 처음부터 다시 안 해도 됨.)

준비물:
    - LABELS_DIR: AI Hub 라벨링데이터(json) 압축 푼 폴더
    - AUDIO_DIR: AI Hub 원천데이터(wav) 압축 푼 걸 모아둔 폴더 (라벨 json의
      rawDataInfo.answer.audioPath 파일명과 같은 이름의 wav가 여기 있어야 함)
"""

import argparse
import csv
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.config import settings  # noqa: E402
from app.domain.interview.audio_analysis import analyze_voice  # noqa: E402

# 팀 로컬 PC 기준 기본 경로 - 다른 PC에서 돌릴 땐 --labels-dir/--audio-dir로 덮어쓰면 된다.
DEFAULT_LABELS_DIR = Path(r"C:\Users\ICT02-011\Downloads\TL_05_extracted")
DEFAULT_AUDIO_DIR = Path(r"C:\Users\ICT02-011\Downloads\TL_05_extracted\audio")
OUTPUT_CSV = Path(__file__).parent / "delivery_score_dataset.csv"

_FEATURE_COLUMNS = [
    "duration_sec",
    "speaking_rate_chars_per_min",
    "pitch_mean_hz",
    "pitch_variation_hz",
    "silence_ratio",
    "long_pause_count",
    "volume_mean_rms",
    "volume_variation_rms",
]
_CSV_COLUMNS = ["sample_id", *_FEATURE_COLUMNS, "delivery_score"]

# 2026-08-12: 실제로 429를 맞아보고 확인한 값 - gemini-3.5-flash-lite 무료 티어
# generateContent 한도가 "분당 15회"였다(에러 메시지의 quotaValue=15 그대로). 60/15=4초가
# 이론상 한계라 살짝 여유를 두고 4.5초로 잡는다.
_SLEEP_BETWEEN_CALLS_SEC = 4.5
# 그래도 순간적으로 다른 프로세스(실서비스 등)와 겹쳐서 429가 나면, 에러 메시지가 알려주는
# 재시도 대기시간(보통 60초 이내)만큼 쉬었다가 자동으로 재시도한다 - 스크립트가 멈추지 않게.
_RATE_LIMIT_RETRY_SEC = 65
_MAX_RETRIES_PER_SAMPLE = 3


def _score_delivery(client, transcript: str, voice_metrics: dict) -> int | None:
    """evaluation.py의 delivery_score 채점 기준을 그대로 가져오되, 이 스크립트는 delivery_score
    하나만 필요하므로(overall/content/strengths 등 불필요) 최소한의 프롬프트로 요청 1건당
    토큰/비용을 줄인다. 실패하면 None(호출부가 그 샘플을 건너뛴다).

    2026-08-12: client를 매번 새로 만들지 않고 호출부(main)에서 한 번만 만든 걸 재사용한다 -
    루프 안에서 genai.Client()를 수백~수천 번 새로 생성/소멸시키면 내부 httpx 연결이 꼬여서
    "Cannot send a request, as the client has been closed" 에러가 났다(실사용 중 발견)."""
    from google.genai import types

    prompt = _build_prompt(transcript, voice_metrics)

    from google.genai import errors as genai_errors

    for attempt in range(1, _MAX_RETRIES_PER_SAMPLE + 1):
        try:
            response = client.models.generate_content(
                model=settings.gemini_model,
                contents=prompt,
                config=types.GenerateContentConfig(response_mime_type="application/json"),
            )
            break
        except genai_errors.ClientError as exc:
            if getattr(exc, "code", None) == 429 and attempt < _MAX_RETRIES_PER_SAMPLE:
                print(f"    (분당 요청 한도 걸림 - {_RATE_LIMIT_RETRY_SEC}초 쉬고 재시도 {attempt}/{_MAX_RETRIES_PER_SAMPLE})")
                time.sleep(_RATE_LIMIT_RETRY_SEC)
                continue
            raise

    try:
        data = json.loads((response.text or "").strip())
        score = int(data["delivery_score"])
        return max(0, min(100, score))
    except (json.JSONDecodeError, KeyError, TypeError, ValueError):
        return None


def _build_prompt(transcript: str, voice_metrics: dict) -> str:
    """Gemini/Ollama 공용 프롬프트 - 같은 문구를 써야 두 백엔드로 채점한 라벨을 나중에
    섞어도(또는 비교해도) 프롬프트 차이로 인한 오차가 안 섞인다."""
    voice_metrics_text = (
        f"- 말속도: 분당 {voice_metrics.get('speaking_rate_chars_per_min')}자\n"
        f"- 평균 음높이(피치): {voice_metrics.get('pitch_mean_hz')}Hz\n"
        f"- 음높이(피치) 변동폭: {voice_metrics.get('pitch_variation_hz')}Hz\n"
        f"- 침묵 비율: {voice_metrics.get('silence_ratio')}\n"
        f"- 긴 침묵(1초 이상) 횟수: {voice_metrics.get('long_pause_count')}회\n"
        f"- 음량 변동폭: {voice_metrics.get('volume_variation_rms')}\n"
    )
    return (
        "당신은 채용면접 코치입니다. 아래 지원자 답변의 '전달력'만 100점 만점(0~100 정수, "
        "5점 단위 권장)으로 평가해서 JSON으로만 응답하세요. 답변 내용(직무 적합성/논리성)은 "
        "평가하지 마세요 - 오직 말하기 방식(속도/억양/침묵)만 근거로 삼으세요. '긴장도 68%' "
        "같은 확정적 심리 판단 대신 측정된 지표가 보여주는 경향만 근거로 삼으세요.\n\n"
        f"[지원자 답변 텍스트]\n{transcript}\n\n"
        "[음성 지표 - 실측값]\n"
        f"{voice_metrics_text}\n"
        '응답 형식: {"delivery_score": <0~100 정수>}'
    )


# 2026-08-13: Gemini 무료 티어 쿼터가 자주 막혀서(분당 15회 한도) 로컬 Ollama로도 채점할 수
# 있게 백엔드를 선택 가능하게 만들었다. 토큰/한도 제약이 없어 대량(1,753건) 처리에 유리하지만,
# 모델 크기가 작아 채점 일관성은 Gemini보다 떨어질 수 있다(check_label_noise.py로 확인 권장).
_OLLAMA_URL = "http://localhost:11434/api/generate"


def _score_delivery_ollama(transcript: str, voice_metrics: dict, model: str) -> int | None:
    import json as _json

    import requests

    prompt = _build_prompt(transcript, voice_metrics)
    try:
        response = requests.post(
            _OLLAMA_URL,
            json={"model": model, "prompt": prompt, "format": "json", "stream": False},
            timeout=120,
        )
        response.raise_for_status()
        data = _json.loads(response.json()["response"])
        score = int(data["delivery_score"])
        return max(0, min(100, score))
    except (requests.RequestException, _json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        print(f"    [Ollama 채점 실패] {type(exc).__name__}: {exc}")
        return None


def _load_existing_ids(csv_path: Path) -> set[str]:
    """이미 처리된 sample_id 목록 - 재실행 시 중복 작업/중복 API 호출을 피한다."""
    if not csv_path.exists():
        return set()
    with csv_path.open(encoding="utf-8") as f:
        return {row["sample_id"] for row in csv.DictReader(f)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--labels-dir", type=Path, default=DEFAULT_LABELS_DIR)
    parser.add_argument("--audio-dir", type=Path, default=DEFAULT_AUDIO_DIR)
    parser.add_argument("--limit", type=int, default=None, help="테스트용 - 앞에서부터 N건만 처리")
    parser.add_argument("--backend", choices=["gemini", "ollama"], default="gemini",
                         help="채점에 쓸 모델 - gemini(기본, 분당 15회 무료 한도) 또는 ollama(로컬, 한도 없음)")
    # 2026-08-13: exaone3.5는 LG AI Research가 만든 한국어 특화 모델(영어/한국어 이중언어) -
    # 우리 데이터가 전부 한국어 면접 답변이라 범용 다국어 모델(qwen2.5 등)보다 채점 품질이
    # 나을 가능성이 높아 기본값으로 선택했다.
    parser.add_argument("--ollama-model", default="exaone3.5:7.8b", help="--backend ollama일 때 쓸 모델명")
    args = parser.parse_args()

    client = None
    if args.backend == "gemini":
        if not settings.gemini_api_key:
            raise SystemExit("GEMINI_API_KEY가 설정돼 있지 않습니다 (.env 확인).")
        from google import genai

        client = genai.Client(api_key=settings.gemini_api_key)

    label_files = sorted(args.labels_dir.glob("*.json"))
    if not label_files:
        raise SystemExit(f"라벨 json을 못 찾았습니다: {args.labels_dir}")

    already_done = _load_existing_ids(OUTPUT_CSV)
    print(f"라벨 파일 {len(label_files)}개 발견, 이미 처리된 것 {len(already_done)}개는 건너뜁니다.")

    write_header = not OUTPUT_CSV.exists()
    processed_this_run = 0
    skipped_no_audio = 0
    skipped_score_fail = 0

    with OUTPUT_CSV.open("a", newline="", encoding="utf-8") as out_f:
        writer = csv.DictWriter(out_f, fieldnames=_CSV_COLUMNS)
        if write_header:
            writer.writeheader()

        for label_path in label_files:
            sample_id = label_path.stem
            if sample_id in already_done:
                continue
            if args.limit is not None and processed_this_run >= args.limit:
                break

            try:
                row = json.loads(label_path.read_text(encoding="utf-8"))
                answer = row["dataSet"]["answer"]
                transcript = answer["raw"]["text"]
                audio_filename = Path(row["rawDataInfo"]["answer"]["audioPath"]).name
            except (KeyError, json.JSONDecodeError) as exc:
                print(f"  [스킵] {sample_id}: 라벨 파싱 실패 ({exc})")
                continue

            audio_path = args.audio_dir / audio_filename
            if not audio_path.exists():
                skipped_no_audio += 1
                if skipped_no_audio <= 5:
                    print(f"  [스킵] {sample_id}: 음성 파일 없음 ({audio_path})")
                continue

            try:
                metrics = analyze_voice(str(audio_path), transcript)
            except Exception as exc:
                print(f"  [스킵] {sample_id}: 음성 분석 실패 ({type(exc).__name__}: {exc})")
                continue

            metrics_dict = metrics.to_dict()
            if args.backend == "ollama":
                score = _score_delivery_ollama(transcript, metrics_dict, args.ollama_model)
            else:
                try:
                    score = _score_delivery(client, transcript, metrics_dict)
                except Exception as exc:
                    # 재시도(_MAX_RETRIES_PER_SAMPLE번)까지 다 실패한 경우 - 할당량이 예상보다
                    # 오래 막혀있다는 뜻이라 계속 시도해봐야 소용없다. 지금까지 처리된 건 이미
                    # CSV에 저장돼 있으니 여기서 깔끔하게 멈추고, 나중에 다시 실행하면 이어서 된다.
                    print(f"\n{sample_id}에서 반복 실패로 중단합니다: {type(exc).__name__}: {exc}")
                    print(f"지금까지 이번 실행에서 {processed_this_run}건 처리됨 - 잠시 후(또는 내일) 같은 명령으로 다시 실행하면 이어서 진행됩니다.")
                    return
            if score is None:
                skipped_score_fail += 1
                print(f"  [스킵] {sample_id}: 채점 실패(응답 파싱 안 됨)")
                if args.backend == "gemini":
                    time.sleep(_SLEEP_BETWEEN_CALLS_SEC)
                continue

            writer.writerow(
                {
                    "sample_id": sample_id,
                    **{col: metrics_dict[col] for col in _FEATURE_COLUMNS},
                    "delivery_score": score,
                }
            )
            out_f.flush()
            processed_this_run += 1
            if processed_this_run % 10 == 0:
                print(f"  {processed_this_run}건 처리 완료 (마지막: {sample_id} -> {score}점)")
            if args.backend == "gemini":
                time.sleep(_SLEEP_BETWEEN_CALLS_SEC)

    print(
        f"\n끝. 이번 실행에서 {processed_this_run}건 추가함 "
        f"(음성없음 {skipped_no_audio}건, 채점실패 {skipped_score_fail}건 스킵). "
        f"결과: {OUTPUT_CSV}"
    )


if __name__ == "__main__":
    main()
