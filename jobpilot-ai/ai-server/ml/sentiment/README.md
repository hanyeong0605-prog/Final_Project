# KOTE 학습·평가·추론 실행 안내

작업 디렉터리: jobpilot-ai/ai-server. 데이터 출처는 DATA_CARD.md,
실측 성능과 한계는 MODEL_CARD.md를 읽는다.

## 현재 구현된 범위

공식 데이터 다운로드, 헤더 없는 TSV 검증, TF-IDF 다중 라벨 학습,
검증 기반 임계값 선택, 테스트 평가, 인증된 FastAPI 추론 API.
Transformer, 리뷰 DB/화면, 기업 대시보드, 관리자 게시판 분석은 후속 작업이다.

## PowerShell

```powershell
.\.venv\Scripts\python.exe -m ml.sentiment.download_kote --destination ml/sentiment/data/raw/kote
.\.venv\Scripts\python.exe -m ml.sentiment.dataset --root ml/sentiment/data/raw/kote
.\.venv\Scripts\python.exe -m ml.sentiment.train_baseline --data ml/sentiment/data/raw/kote --output ml/sentiment/artifacts/kote-baseline-v1
.\.venv\Scripts\python.exe -m ml.sentiment.evaluate --artifact ml/sentiment/artifacts/kote-baseline-v1 --data ml/sentiment/data/raw/kote --split test --reports ml/sentiment/reports
```

학습 output은 새 디렉터리여야 한다. 기존 모델을 덮어쓰지 않는다.
Linux에서는 위 실행기의 경로를 .venv/bin/python으로 바꾼다.
다운로드만 네트워크가 필요하며 기준 모델 학습·평가는 CPU에서 실행 가능하다.
개별 테스트용 합성 fixture 성능은 모델 품질 지표가 아니다.

## API 연결

서버 실행 환경의 SENTIMENT_MODEL_DIR을 학습된 artifact 디렉터리 절대경로로 지정한다.
INTERNAL_API_KEY는 기존 Spring과 AI 서버의 공유 키를 사용하며 로그나 문서에 실제 키를 남기지 않는다.
모델 경로 기본값은 /models/sentiment다. Docker의 볼륨 연결은 아직 설정하지 않았다.

- GET /sentiment/health: READY, UNAVAILABLE, FAILED 중 현재 상태. 모델 없어도 HTTP 200.
- GET /sentiment/model: 내부 인증키 필요, 모델 버전 제공.
- POST /sentiment/analyze: X-Internal-API-Key 헤더, body는 {"text":"좋아요", "topK":5}.
- POST /sentiment/analyze/batch: 같은 인증 헤더, body는 {"texts":["좋아요","불편해요"],"topK":5}.
- 빈 문장, 5,000자 초과, 32건 초과 batch는 422. 키 누락/불일치는 401.
- 모델 부재·손상은 분석 요청에 503. 서버의 기존 루트 health와 다른 기능을 막지 않는다.
- 모델 변경 후 worker를 재시작한다. 자동 다운로드, HTTP 재학습, 원문 DB 저장은 하지 않는다.

회사가 아닌 게시판 글도 같은 추론 엔진을 이용할 수 있지만, 이후 Backend에서
COMPANY_REVIEW / COMMUNITY_POST / COMMUNITY_COMMENT 집계와 권한을 분리해야 한다.
응답 polarity 값은 서로 독립적이며 합계 100% 차트용 비율이 아니다.

## 검증

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_sentiment_dataset.py tests/test_sentiment_labels.py tests/test_sentiment_training.py tests/test_router_sentiment.py -q -p no:cacheprovider
```

Windows 임시 폴더 권한 충돌 시 새로운 작업공간 임시 경로를 --basetemp로 지정한다.
원본 데이터와 모델은 .gitignore와 기존 .dockerignore(ml 제외)로 배포에서 제외한다.
기존 전체 수집에는 폐지된 generate_question 참조가 남아 있으며, 오디오 테스트는
ffmpeg 실행 환경이 필요하다. 이것을 감정분석 테스트 통과와 혼동하지 않는다.

## 직장 도메인 후보 데이터

KOTE의 일반 댓글 도메인 한계를 점검하기 위한 자체 창작 문장 후보는 다음 명령으로
재생성한다. 생성 파일은 원본 학습 데이터와 마찬가지로 Git에 포함하지 않는다.

```powershell
.\.venv\Scripts\python.exe -m ml.sentiment.workplace_dataset `
  --output ml/sentiment/data/workplace/workplace-candidates-v1.jsonl
```

현재 v1은 긍정·중립·부정·혼합 각 60건, 총 240건이다. 모든 초기 행은
`UNVERIFIED`이며 사람 검수 전에는 최종 평가나 정확도 주장에 사용할 수 없다.
검수 기준과 항목 정의는 `WORKPLACE_DATA_CARD.md`를 따른다.
