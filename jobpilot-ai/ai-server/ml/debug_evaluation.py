from app.domain.interview.evaluation import generate_report
import json

report = generate_report(
    question="최근에 협업하면서 갈등을 해결했던 경험이 있다면 말씀해 주세요.",
    transcript="이전 프로젝트에서 팀원과 API 설계 방향이 달라서 의견 차이가 있었습니다. 저는 회의를 잡아서 각자 장단점을 정리해서 공유했고, 결국 두 방식을 절충한 안으로 합의했습니다.",
    voice_metrics={
        "speaking_rate_chars_per_min": 280,
        "pitch_mean_hz": 165.2,
        "pitch_variation_hz": 18.4,
        "silence_ratio": 0.12,
        "long_pause_count": 1,
        "volume_variation_rms": 0.03,
    },
    face_metrics={"blinkCount": 14, "blinkRatePerMin": 22, "headMovement": 12},
)
print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))