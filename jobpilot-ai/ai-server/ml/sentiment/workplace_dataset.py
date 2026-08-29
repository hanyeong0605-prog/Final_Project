"""Create and validate an original Korean workplace-sentiment candidate set.

The generated rows are synthetic candidates, not human gold labels. They may be
used for UI smoke tests and annotation preparation. A row can enter a final
evaluation split only after a person changes ``review_status`` to ``VERIFIED``.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from dataclasses import dataclass, asdict
from pathlib import Path


LABELS = ("POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED")
LABEL_CODES = {"POSITIVE": "POS", "NEUTRAL": "NEU", "NEGATIVE": "NEG", "MIXED": "MIX"}
ASPECTS = ("WORKLOAD", "COMPENSATION", "CULTURE", "MANAGEMENT", "GROWTH", "BENEFITS", "WORK_POLICY")
CONTEXTS = (
    "개발팀에서 근무하는 동안", "입사 후 두 번째 프로젝트에서", "조직 개편을 겪은 뒤에도",
    "서비스 출시를 준비하면서", "퇴사 전 마지막 분기에",
)

# Each case was written for this project. MIXED always contains independently
# interpretable positive and negative evidence instead of a random conjunction.
CASES = (
    ("WORKLOAD", "릴리스 일정에도 업무량을 조정해 야근 없이 마무리했습니다.", "분기마다 업무량을 다시 산정하고 일정은 팀 상황에 따라 조정했습니다.", "인력보다 프로젝트가 많아 릴리스 주간마다 반복해서 야근했습니다.", "평소 업무량은 적당했지만 장애가 발생한 주에는 며칠씩 야근했습니다."),
    ("WORKLOAD", "온콜 순번과 보상 기준이 명확해 운영 부담을 예측할 수 있었습니다.", "온콜은 월별 순번제로 운영됐고 담당 횟수는 팀마다 달랐습니다.", "온콜 담당과 보상 기준이 없어 장애가 나면 늘 같은 사람이 대응했습니다.", "온콜 수당은 지급됐지만 에스컬레이션 기준이 없어 대응 과정은 혼란스러웠습니다."),
    ("COMPENSATION", "성과 기준과 연봉 범위를 미리 안내해 보상 결과를 납득할 수 있었습니다.", "연봉은 매년 한 차례 조정됐고 인상 폭은 개인별로 달랐습니다.", "평가 결과에 대한 설명 없이 연봉 인상률이 일방적으로 정해졌습니다.", "기본 연봉은 만족스러웠지만 성과급 기준은 매년 바뀌어 예측하기 어려웠습니다."),
    ("COMPENSATION", "추가 근무와 당직에 대한 수당이 빠짐없이 지급됐습니다.", "당직 수당은 근무 시간과 직급에 따라 다르게 계산됐습니다.", "공고에는 수당이 있다고 했지만 실제 추가 근무가 보상되지 않았습니다.", "야간 교통비는 지원됐지만 추가 근무 수당은 별도로 없었습니다."),
    ("CULTURE", "의견이 달라도 근거를 공유하며 직급과 관계없이 토론할 수 있었습니다.", "회의에서 의견을 제안할 수 있었고 최종 결정은 팀장이 내렸습니다.", "반대 의견을 내면 협업적이지 않다는 평가를 받아 자유롭게 말하기 어려웠습니다.", "팀 안에서는 편하게 토론했지만 다른 조직과의 회의에서는 직급에 따라 발언권이 달랐습니다."),
    ("CULTURE", "코드리뷰가 비난 없이 구체적인 개선 제안 중심으로 진행됐습니다.", "코드리뷰는 필수였고 검토 깊이는 담당자에 따라 달랐습니다.", "승인만 누르는 형식적인 코드리뷰라 결함이 반복해서 운영에 반영됐습니다.", "시니어의 리뷰는 도움이 됐지만 바쁜 시기에는 승인 대기가 며칠씩 걸렸습니다."),
    ("MANAGEMENT", "경영진이 결정 배경과 변경 이유를 정기적으로 공유했습니다.", "전사 목표는 분기마다 공유됐지만 세부 우선순위는 각 팀이 정했습니다.", "경영진의 지시가 매주 바뀌어 이미 완성한 기능을 반복해서 폐기했습니다.", "의사결정 속도는 빨랐지만 변경 이유가 늦게 공유돼 재작업이 생겼습니다."),
    ("MANAGEMENT", "문제가 생기면 개인을 탓하지 않고 프로세스 개선부터 논의했습니다.", "장애 회고를 진행했고 후속 조치는 담당 팀이 따로 관리했습니다.", "장애 원인을 특정 직원의 실수로 돌려 회고 시간에 공개적으로 질책했습니다.", "비난 없는 회고를 지향했지만 중요한 장애에서는 책임 소재를 먼저 따지는 분위기가 남아 있었습니다."),
    ("GROWTH", "설계부터 운영까지 맡고 정기적인 피드백을 받아 실력이 빠르게 늘었습니다.", "사내 교육과 기술 공유회가 월 한 차례 운영됐습니다.", "반복적인 단순 업무만 배정돼 새로운 기술이나 역할을 경험하기 어려웠습니다.", "새로운 기술을 많이 경험했지만 멘토가 없어 시행착오를 혼자 감당해야 했습니다."),
    ("GROWTH", "교육비와 학습 시간을 실제로 보장해 자격증 준비를 마칠 수 있었습니다.", "연간 교육비가 제공됐고 사용하려면 팀장 승인이 필요했습니다.", "교육 지원 제도는 있었지만 업무가 많아 한 번도 사용할 수 없었습니다.", "교육비는 충분했지만 근무시간 중 학습은 허용되지 않아 활용하기 어려웠습니다."),
    ("BENEFITS", "공고에 안내된 장비와 건강검진, 휴가 제도를 모두 실제로 이용했습니다.", "복지 항목은 입사 안내 문서에 정리돼 있었고 신청 방식은 제도마다 달랐습니다.", "공고에 적힌 복지 대부분이 조건부라 실제로 사용할 수 있는 항목이 거의 없었습니다.", "장비와 식대 지원은 좋았지만 리프레시 휴가는 승인받기 어려웠습니다."),
    ("WORK_POLICY", "선택 출근과 원격근무를 팀 눈치 없이 일정에 맞춰 사용할 수 있었습니다.", "코어타임 외 출퇴근 시간은 자율이었고 원격근무는 사전 공유가 필요했습니다.", "자율 출퇴근이라고 했지만 매일 아침 고정 회의 때문에 실제 선택권이 없었습니다.", "출근 시간은 자유로웠지만 원격근무 허용 여부는 팀장 성향에 따라 달랐습니다."),
)


@dataclass(frozen=True)
class WorkplaceExample:
    id: str
    text: str
    overall: str
    aspects: dict[str, str]
    source_type: str = "SYNTHETIC_CURATED"
    review_status: str = "UNVERIFIED"
    template_group: str = ""


def build_candidates() -> list[WorkplaceExample]:
    rows: list[WorkplaceExample] = []
    for case_index, case in enumerate(CASES, start=1):
        aspect, *sentences = case
        for context_index, context in enumerate(CONTEXTS, start=1):
            for label, sentence in zip(LABELS, sentences, strict=True):
                rows.append(WorkplaceExample(
                    id=f"WP-{case_index:02d}-{context_index}-{LABEL_CODES[label]}",
                    text=f"{context} {sentence}", overall=label, aspects={aspect: label},
                    template_group=f"CASE-{case_index:02d}",
                ))
    return rows


def validate(rows: list[WorkplaceExample], *, final_evaluation: bool = False) -> dict:
    if len({row.id for row in rows}) != len(rows):
        raise ValueError("duplicate workplace dataset ID")
    if len({row.text for row in rows}) != len(rows):
        raise ValueError("duplicate workplace dataset text")
    for row in rows:
        if row.overall not in LABELS or not row.aspects or set(row.aspects) - set(ASPECTS):
            raise ValueError(f"invalid label or aspect: {row.id}")
        if any(value not in LABELS for value in row.aspects.values()):
            raise ValueError(f"invalid aspect polarity: {row.id}")
        if final_evaluation and row.review_status != "VERIFIED":
            raise ValueError(f"unverified row cannot enter final evaluation: {row.id}")
    return {"count": len(rows), "labels": dict(Counter(row.overall for row in rows)),
            "aspects": dict(Counter(next(iter(row.aspects)) for row in rows)),
            "verified": sum(row.review_status == "VERIFIED" for row in rows)}


def write_jsonl(path: Path, rows: list[WorkplaceExample]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(asdict(row), ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = build_candidates()
    summary = validate(rows)
    write_jsonl(args.output, rows)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
