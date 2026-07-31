# GitHub 코드 분석 기능 인수인계

작성일: 2026-07-31  
프로젝트: `Final_Project/jobpilot-ai`

## 목표

JobPilot AI 안에서 GitHub 저장소 URL을 받아, **실제 코드와 설정 파일을 근거로** 발표 전에 확인할 수 있는 한국어 프로젝트 설명을 만든다.

- 대상 결과: 프로젝트가 무엇인지, 어떤 기술을 쓰는지, 어떤 구조인지, 핵심 구현과 코드 흐름이 무엇인지
- 코드 리뷰·버그 지적·개선 제안은 하지 않는다.
- 저장소 코드를 실행하지 않는다.
- README가 없어도 코드·의존성·설정·파일 관계를 기반으로 분석한다.
- 발표자료(PPT) 생성 전 사용자가 핵심 구현을 고르는 미리보기 용도다.

## 사용자가 원하는 분석 품질

단순히 `main()`이 있는 파일을 나열하는 수준이 아니라, Claude/Codex가 저장소를 읽고 설명하는 수준을 목표로 한다.

예시 방향:

> 이 프로젝트는 Java 객체지향 학습용 콘솔 예제 저장소입니다. `ch01`부터 `ch06` 패키지는 변수·제어문·배열·클래스·상속·추상화/인터페이스 순서의 학습 흐름을 보입니다. `PoketMonster01`은 부모 `PoketMon`, 공격형·방어형 하위 클래스, 사용자 입력을 받는 `PoketMonMain`을 조합해 상속·다형성을 실습하는 모듈입니다.

핵심은 파일 하나가 아니라 **서로 연결된 기능/클래스 묶음**을 설명하는 것이다.

## 현재 분석 흐름

```text
GitHub URL
  → GitHub API: 저장소 메타데이터, 언어, 기본 브랜치, 전체 파일 트리 수집
  → 1차 파일 수집: 설정 파일 + 점수 기반 소스 파일
  → Gemini planner: 분석할 핵심 파일 경로 선택
  → 같은 패키지의 관련 소스 파일 확장 수집
  → 정적 분석: 기술/코드 역할/근거/구조 생성
  → Gemini summary: 사실 기반 한국어 설명·흐름·핵심 구현 생성
  → React 화면: 프로젝트 설명, 흐름, 구현 카드, 코드 근거 표시
```

Gemini는 저장소 전체를 한 번에 보내지 않는다. 선별된 소스와 설정만 전달한다.

## Gemini 호출·비용 안전장치

분석 버튼 한 번에 Gemini 호출은 현재 최대 2회다.

1. planner: 어떤 소스 파일을 더 읽을지 선택
2. summary: 프로젝트 설명과 핵심 구현 생성

설정 원칙:

- `store=false`: Gemini 요청 저장 비활성화
- summary `max_output_tokens=1200`
- planner `max_output_tokens=360`
- `thinking_level=minimal`
- 기본 final 분석 모델: `gemini-3.5-flash`
- 기본 planner 모델: `gemini-3.5-flash-lite`
- 코드에는 API 키를 넣지 않는다.

컴파일, 테스트, 서버 재시작에는 Gemini 비용이 들지 않는다. 저장소 분석 버튼을 눌렀을 때만 토큰이 사용된다.

## 필요한 로컬 환경변수

IntelliJ의 Spring Boot Run/Debug Configuration 환경변수에만 설정한다. GitHub에 커밋하지 않는다.

```text
GEMINI_ENABLED=true
GEMINI_API_KEY=본인의_Gemini_API_키
GITHUB_TOKEN=본인의_GitHub_토큰
```

선택 설정이며 생략해도 기본값을 사용한다.

```text
GEMINI_MODEL=gemini-3.5-flash-lite
GEMINI_ANALYSIS_MODEL=gemini-3.5-flash
```

`GEMINI_ENABLED=false`이거나 키가 없으면 Gemini를 호출하지 않고 정적 분석만 표시한다.

## 현재 구현 파일

백엔드 핵심:

- `backend/src/main/java/com/jobpilot/api/domain/projectanalysis/service/GitHubRepositoryClient.java`
  - GitHub REST API로 저장소/언어/트리/파일을 읽는다.
  - planner가 고른 파일과 같은 패키지의 관련 소스 파일도 최대 범위 내에서 확장한다.
- `backend/src/main/java/com/jobpilot/api/domain/projectanalysis/service/StaticProjectAnalyzer.java`
  - 기술 스택, 역할, 정적 근거를 만든다.
  - Java `extends`, `implements`, `new` 객체 생성을 읽어 상속·인터페이스·진입점 관계를 설명한다.
- `backend/src/main/java/com/jobpilot/api/domain/projectanalysis/service/GeminiProjectSummaryClient.java`
  - Gemini planner/summary 요청, 비밀값 마스킹, JSON 응답 처리 담당.
- `backend/src/main/java/com/jobpilot/api/domain/projectanalysis/service/GitHubProjectAnalysisService.java`
  - 전체 분석 파이프라인 조합.
- `backend/src/main/java/com/jobpilot/api/domain/projectanalysis/dto/GitHubProjectAnalysisResponse.java`
  - 프런트엔드 전달 응답 모델.

프런트엔드 핵심:

- `frontend/src/pages/RepositoryAnalysisPage.tsx`
- `frontend/src/features/project-analysis/model/projectAnalysis.types.ts`
- `frontend/src/styles.css`

## 확인한 동작

### 성공 사례: Puppeteer

`https://github.com/puppeteer/puppeteer`

- 화면 상태: `AI 작성 · 코드 근거 기반`
- Puppeteer를 Chrome/Firefox 브라우저 자동화 API 라이브러리로 분류
- `Browser`, `BrowserContext`, `Page` 구조를 인식
- `puppeteer.connect → Puppeteer.connect → Browser` 흐름 제시
- CDP와 WebDriver BiDi 구현을 Browser 추상화로 설명

즉 Gemini 생성 → JSON 파싱 → UI 반영까지 실제 성공했다.

### 학습 저장소 사례: Myjava

`https://github.com/hanyeong0605-prog/Myjava`

기존 결과는 Java 객체지향 학습 프로젝트라는 판단은 맞았지만, 많은 `main()` 파일을 핵심 근거로 보여 주어 부족했다.

이를 개선하기 위해 다음을 추가했다.

- planner가 서로 무관한 `main()` 파일만 선택하지 않고, 하나의 관련 모듈을 선택하도록 지시
- 선택 파일과 동일 패키지의 관련 클래스를 함께 수집
- `extends`, `implements`, `new` 관계를 정적 코드 근거에 반영
- 학습/예제 저장소라면 패키지/챕터 구성 자체를 프로젝트 설명에 포함하도록 지시

새 코드가 실행된 뒤 Myjava를 다시 분석해 품질을 확인해야 한다.

## 실패 상태를 읽는 법

- `AI 작성 · 코드 근거 기반`: Gemini 응답이 정상 파싱되어 화면에 적용된 상태
- `AI 응답 미반영 · 정적 코드 근거`: GitHub/정적 분석은 성공했지만 Gemini 응답이 JSON으로 끝까지 닫히지 않았거나 파싱에 실패한 상태
- 화면 하단 `Gemini 생성 원문`: 실패 시 개발 확인용 원문이다. 발표용 결과로 사용하지 않는다.

과거에 `max_output_tokens=1200`에서 긴 JSON이 중간에 잘려 파싱 실패한 사례가 있었다. 따라서 현재 prompt는 핵심 파일 2개, 코드 흐름 1개, 핵심 구현 1개처럼 출력 크기를 제한한다.

## 알려진 개선 대상

1. Puppeteer에서 `Page.ts`의 일반 `fetch` 신호가 `프론트엔드 HTTP 호출`로 표시된 오탐이 있었다.
   - 정적 규칙이 `fetch(` 문자열만으로 외부 API 연동을 판단한 결과다.
   - 향후 `axios` 실제 import, HTTP client 설정, 백엔드 URL/SDK 사용 같은 더 강한 근거가 있을 때만 표시하도록 개선할 것.

2. 현재 1,200 출력 토큰 상한을 지키기 위해 한 번의 화면 결과는 핵심 구현을 1개로 압축한다.
   - 더 많은 구현을 자세히 보여 주려면 이후 "선택한 기능 심층 분석" 버튼을 만들고, 선택 기능만 별도 호출하는 방식이 적합하다.
   - 이 경우에도 호출 수/토큰/비용을 사용자에게 표시하고 제한해야 한다.

3. private 저장소는 GitHub 토큰에 해당 저장소 읽기 권한이 있어야 한다.

## 검증 기록

마지막 백엔드 검증:

```text
mvn test
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 집 컴퓨터에서 이어서 하는 방법

```bash
git clone https://github.com/hanyeong0605-prog/Final_Project.git
cd Final_Project/jobpilot-ai
```

1. MySQL 등 `application.yml`이 요구하는 로컬 환경을 준비한다.
2. IntelliJ에서 backend를 열고 위 환경변수를 넣는다.
3. 프런트엔드 폴더에서 `npm install` 후 개발 서버를 실행한다.
4. 백엔드 재시작 후 GitHub 코드 분석 화면에서 Myjava 또는 Puppeteer를 다시 분석한다.

## 집에서 새 대화에 붙여 넣을 요약

```text
JobPilot AI의 GitHub 코드 분석 기능을 작업 중이다.
인수인계 문서: jobpilot-ai/docs/codex-handoff-github-analysis.md
이 문서를 먼저 읽고 이어서 작업해줘.
현재 목표는 GitHub 저장소를 코드 근거로 한국어로 설명하는 것이며,
코드 리뷰가 아니라 프로젝트 성격·기술·구조·핵심 구현·코드 흐름을 보여 주는 것이다.
```
