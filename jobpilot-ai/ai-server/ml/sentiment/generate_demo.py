"""Reproducible demo corpus, independent of training and production databases.

Seed keys are import identities, not database primary keys. The future importer
must resolve references, assign internal posting URLs after insertion, and bind
only explicitly selected demo employer accounts. No real account is fabricated.
"""
from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

DOMAINS = (
    ("가온", "교육 플랫폼", "학습 진도와 강의 추천"),
    ("누리", "물류 소프트웨어", "배송 일정과 재고 추적"),
    ("다온", "환경 데이터", "에너지 사용량과 절감 현황"),
    ("라온", "협업 도구", "팀 일정과 문서 공유"),
    ("마루", "여행 서비스", "일정 구성과 예약 관리"),
    ("바른", "콘텐츠 플랫폼", "콘텐츠 탐색과 구독 관리"),
    ("새봄", "전자상거래", "주문 처리와 상품 검색"),
    ("여울", "스포츠 데이터", "운동 기록과 활동 통계"),
    ("온빛", "업무 자동화", "반복 작업과 승인 흐름"),
    ("푸른", "농업 소프트웨어", "작물 생육과 센서 데이터"),
)
ROLES = (
    ("백엔드 개발자", ["Java", "Spring Boot", "MySQL"], "API 설계와 트랜잭션 처리"),
    ("프론트엔드 개발자", ["TypeScript", "React", "CSS"], "접근성과 반응형 화면 개선"),
    ("데이터 엔지니어", ["Python", "SQL", "Airflow"], "수집 파이프라인과 데이터 품질 관리"),
    ("머신러닝 엔지니어", ["Python", "PyTorch", "scikit-learn"], "모델 실험과 추론 성능 평가"),
    ("클라우드 엔지니어", ["Linux", "Docker", "AWS"], "배포 자동화와 서비스 모니터링"),
    ("모바일 개발자", ["Kotlin", "Android", "Git"], "모바일 화면과 오프라인 동기화"),
    ("QA 엔지니어", ["Python", "Playwright", "SQL"], "회귀 테스트와 결함 재현"),
    ("데이터 분석가", ["SQL", "Python", "Tableau"], "지표 정의와 사용자 행동 분석"),
    ("서비스 기획자", ["Figma", "SQL", "Jira"], "요구사항 정리와 사용자 흐름 설계"),
    ("보안 엔지니어", ["Linux", "Python", "SQL"], "접근 통제와 취약점 대응"),
)
LOCATIONS = ("서울 강남구", "서울 마포구", "경기 성남시", "부산 해운대구", "대전 유성구")
POSITIVES = (
    "동료들이 질문에 시간을 내어 답해 주어 업무를 익히기 수월했습니다.",
    "업무 우선순위를 함께 정하고 변경 사항을 문서로 공유했습니다.",
    "휴가 사용에 눈치를 주지 않아 개인 일정을 계획하기 좋았습니다.",
    "코드 리뷰와 회고가 정기적으로 열려 다른 접근을 배울 수 있었습니다.",
    "테스트 환경이 분리되어 있어 작은 개선도 안심하고 시도했습니다.",
    "교육비 지원을 이용해 필요한 기술을 학습할 수 있었습니다.",
    "출퇴근 시간을 조정할 수 있어 통근 부담이 줄었습니다.",
    "새로운 의견을 제안하면 근거를 듣고 함께 검토했습니다.",
    "신규 입사자를 위한 문서와 담당 멘토가 마련되어 있었습니다.",
    "담당 범위와 목표가 명확해 무엇에 집중할지 알 수 있었습니다.",
)
NEGATIVES = (
    "마감 직전에 요구사항이 바뀌는 일이 잦아 일정 관리가 어려웠습니다.",
    "연봉 조정 기준이 명확하지 않아 보상에 대한 아쉬움이 남았습니다.",
    "오래된 문서가 많아 처음에는 동료에게 여러 번 확인해야 했습니다.",
    "팀 사이의 의사결정이 늦어져 작업을 다시 하는 경우가 있었습니다.",
    "장애 대응 인원이 적어 특정 담당자에게 부담이 집중됐습니다.",
    "회의가 길어 집중해서 개발할 시간을 확보하기 어려웠습니다.",
    "성장 경로에 대한 설명이 부족해 다음 목표를 잡기 어려웠습니다.",
    "신규 도구 도입 절차가 길어 개선 작업이 지연됐습니다.",
    "출시 기간에는 야근이 늘어 개인 일정과 조율하기 어려웠습니다.",
    "내부 도구의 작은 오류가 오래 남아 반복 작업이 생겼습니다.",
)
MILD_POSITIVES = (
    "뚜렷하게 좋았던 점을 찾기 어려웠습니다.", "기대했던 장점을 경험하지 못했습니다.",
    "업무 환경에서 만족스러운 부분이 거의 없었습니다.", "지원 제도의 장점을 체감하기 어려웠습니다.",
    "긍정적으로 평가할 만한 부분이 부족했습니다.",
)
MILD_NEGATIVES = (
    "일부 문서는 갱신이 필요했지만 업무에 큰 지장은 없었습니다.",
    "바쁜 기간에는 회의가 조금 늘었지만 일정을 조율할 수 있었습니다.",
    "보상 기준을 더 자세히 공유하면 좋겠다는 작은 아쉬움이 있었습니다.",
    "내부 도구의 사용법을 익히는 데 시간이 조금 필요했습니다.",
    "팀별 절차가 조금 달랐지만 문의하면 안내받을 수 있었습니다.",
)


def generate_bundle(seed: int = 42) -> dict:
    rng = random.Random(seed)
    companies, postings, reviews = [], [], []
    for domain_index, (prefix, industry, product) in enumerate(DOMAINS):
        for role_index, (role, skills, responsibility) in enumerate(ROLES):
            index = domain_index * len(ROLES) + role_index + 1
            key = f"DEMO-COMPANY-{index:03d}"
            posting_key = f"DEMO-JOB-{index:03d}"
            name = f"{prefix}시연랩{role_index + 1:02d} (가상기업)"
            location = LOCATIONS[(domain_index + role_index) % len(LOCATIONS)]
            salary = 3200 + rng.randrange(8) * 300
            company = dict(seed_key=key, name=name, source_type="FICTIONAL_DEMO",
                           industry=industry, location=location, employee_count=20 + rng.randrange(15) * 10,
                           founded_year=2014 + rng.randrange(10), skills=skills,
                           description=f"{product}을 다루는 포트폴리오 시연용 가상기업입니다.")
            description = (
                f"[가상기업 · 시연용 공고]\n{name}은 {industry} 분야에서 {product} 기능을 만드는 설정의 가상기업입니다.\n\n"
                f"주요 업무\n- {responsibility}\n- {product} 서비스 개선\n- 운영 지표를 바탕으로 품질 개선\n\n"
                f"자격 요건\n- {', '.join(skills)} 활용 경험\n- 협업 과정과 문제 해결을 설명할 수 있는 분\n\n"
                "우대 사항\n- 테스트 자동화 또는 서비스 운영 경험\n- 개선 결과를 문서로 정리한 경험\n\n"
                "복리후생\n- 유연근무제, 교육비 지원, 업무 장비 지원\n\n"
                "본 회사·공고·리뷰는 기능 시연을 위한 합성 데이터이며 실제 채용을 진행하지 않습니다."
            )
            posting = dict(seed_key=posting_key, external_job_id=posting_key, company_seed_key=key,
                           source_provider="FICTIONAL_DEMO", company_name=name, title=f"[시연] {role} 채용",
                           description=description, company_url=None, source_url=None,
                           employer_account_id=None, location=location, employment_type="정규직",
                           experience_type="신입·경력" if role_index % 2 == 0 else "경력 2년 이상",
                           salary=f"연 {salary:,}~{salary + 1200:,}만원 (가상 조건)",
                           industry_name=industry, job_name=role, keywords=", ".join(skills),
                           is_rolling_deadline=True, status="ACTIVE")
            companies.append(company)
            postings.append(posting)
            # Ratings are synthetic user inputs, never ground-truth emotion labels.
            base = 1 + index % 5
            for offset in range(5):
                rating = max(1, min(5, base + rng.choice((-1, 0, 0, 1))))
                # Rating influences what the fictional reviewer chose to emphasize, but is never
                # copied into a sentiment label or used as supervised training ground truth.
                good = (POSITIVES if rating >= 3 else MILD_POSITIVES)[(role_index + offset) % 5]
                bad = (NEGATIVES if rating <= 3 else MILD_NEGATIVES)[(domain_index + offset) % 5]
                verdict = ("전반적으로 만족하며 계속 함께 일하고 싶습니다." if rating >= 4 else
                           "좋은 점도 있지만 업무 환경의 개선이 필요합니다." if rating == 3 else
                           "현재 방식이 유지된다면 다시 근무하기는 망설여집니다.")
                reviews.append(dict(
                    seed_key=f"DEMO-REVIEW-{index:03d}-{offset + 1}",
                    company_seed_key=key, posting_seed_key=posting_key,
                    display_author=f"시연 리뷰어 {index:03d}-{offset + 1}",
                    source_type="SYNTHETIC_DEMO", rating=rating,
                    title=f"{industry} 팀에서의 가상 근무 후기 {offset + 1}",
                    pros=good, cons=bad,
                    body=f"{role}로 {responsibility}를 담당했다는 시연 설정입니다. {verdict}",
                    analysis_state="PENDING", author_member_id=None,
                ))
    return dict(schema_version="jobpilot-demo-v1", seed=seed, source_type="SYNTHETIC_DEMO",
                companies=companies, postings=postings, reviews=reviews)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Refuse overwriting a previously reviewed corpus.
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(generate_bundle(args.seed), handle, ensure_ascii=False, indent=2)
    print("Generated 100 companies, 100 postings and 500 reviews; no database was modified.")


if __name__ == "__main__":
    main()
