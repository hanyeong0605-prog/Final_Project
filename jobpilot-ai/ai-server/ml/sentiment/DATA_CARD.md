# KOTE 데이터 카드

## 출처

- 데이터셋: KOTE (Korean Online That-gul Emotions)
- 공식 저장소: <https://github.com/searle-j/KOTE>
- 논문: <https://aclanthology.org/2024.lrec-main.1499/>
- 연구자: Duyoung Jeon, Junho Lee, Cheongtag Kim
- 논문 기재 소속: 서울대학교 심리학과, 서울대학교 인지과학 협동과정
- 저장소 라이선스: MIT (`https://github.com/searle-j/KOTE/blob/main/LICENSE`)

KOTE는 여러 온라인 플랫폼에서 수집한 한국어 댓글 50,000건을 43개 감정과
`없음`으로 분류한 44라벨 다중 라벨 데이터셋이다. 댓글마다 5명이 크라우드소싱으로
라벨링했으며 공식 분할은 학습 40,000건, 검증 5,000건, 테스트 5,000건이다.

## 프로젝트 사용 범위

KOTE는 JobPilot 모델의 일반 한국어 감정 학습과 평가에 사용하는
`PUBLIC_RESEARCH` 데이터다. 회사 리뷰 데이터나 긍정·중립·부정 3분류 데이터라고
표현하지 않는다. 서비스의 극성 표시는 별도의 버전 관리된 표시 정책이며 KOTE 원본
정답과 구분한다.

KOTE는 일반 온라인 댓글 도메인이므로 직장·채용 표현에 그대로 일반화된다고 가정하지
않는다. 직장 도메인의 최종 성능은 별도의 사람 검수 평가 세트로 확인한다.

## 재현 가능한 다운로드

원문은 Git과 Docker 이미지에 포함하지 않는다. 다음 명령이 공식 저장소의 현재 ref를
40자리 커밋 SHA로 고정한 뒤 세 분할과 `manifest.json`을 생성한다.

```powershell
cd jobpilot-ai/ai-server
.\.venv\Scripts\python.exe -m ml.sentiment.download_kote `
  --destination ml/sentiment/data/raw/kote
```

manifest에는 저장소, 확정 커밋, UTC 다운로드 시각, 파일별 SHA-256·바이트·행 수를
기록한다. 학습 보고서에는 manifest의 커밋과 체크섬을 함께 남긴다.
