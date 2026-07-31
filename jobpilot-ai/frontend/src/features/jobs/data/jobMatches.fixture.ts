import type { JobMatch } from "../model/job.types";

export const jobMatchesFixture: JobMatch[] = [
  {
    id: 101, company: "모노랩", title: "신입 백엔드 개발자 (Java/Spring)", source: "고용24", location: "서울 · 강남구", deadline: "2026.08.12", grade: "READY_TO_APPLY", score: 86,
    comment: "Spring Boot·JPA·MySQL 프로젝트 근거가 필수 요건과 직접 연결됩니다. 배포 과정만 포트폴리오에 더 구체적으로 적어 보세요.",
    skills: ["Java", "Spring Boot", "JPA", "MySQL", "REST API"],
    requirements: [
      { requirement: "Spring Boot 기반 API 개발", requirementType: "필수", evidence: "팀 프로젝트 ‘MealMate’의 예약 API 구현", status: "DIRECT", action: "README에 담당 API와 트러블슈팅 링크" },
      { requirement: "JPA / MySQL 사용 경험", requirementType: "필수", evidence: "ERD 설계 및 JPA 연관관계 매핑", status: "DIRECT", action: "ERD 이미지와 핵심 쿼리 추가" },
      { requirement: "AWS 배포 경험", requirementType: "우대", evidence: "현재 근거 없음", status: "MISSING", action: "EC2 배포 과정을 포트폴리오에 추가" },
    ],
  },
  {
    id: 102, company: "브릿지웍스", title: "주니어 풀스택 개발자", source: "잡코리아", location: "서울 · 마포구", deadline: "2026.08.05", grade: "NEEDS_IMPROVEMENT", score: 69,
    comment: "React와 Spring 프로젝트 경험은 확인되지만, Docker 배포와 TypeScript 활용 근거를 보완하면 지원 준비도가 높아집니다.",
    skills: ["React", "TypeScript", "Spring Boot", "Docker", "AWS"],
    requirements: [
      { requirement: "React 화면 개발", requirementType: "필수", evidence: "React 기반 관리자 대시보드", status: "DIRECT", action: "컴포넌트 분리 기준을 면접 답변으로 정리" },
      { requirement: "TypeScript 사용", requirementType: "필수", evidence: "JavaScript 프로젝트 경험", status: "RELATED", action: "기존 프로젝트 한 화면을 TypeScript로 전환" },
      { requirement: "Docker 컨테이너 배포", requirementType: "우대", evidence: "현재 근거 없음", status: "MISSING", action: "Spring + React Docker Compose 배포 실습" },
    ],
  },
  {
    id: 103, company: "데이터코어", title: "데이터 엔지니어 신입", source: "고용24", location: "경기 · 성남시", deadline: "2026.08.17", grade: "INSUFFICIENT_EVIDENCE", score: 31,
    comment: "Python·Airflow·데이터 파이프라인 필수 요건을 증명하는 경험이 아직 확인되지 않습니다.",
    skills: ["Python", "SQL", "Airflow", "Spark"],
    requirements: [
      { requirement: "Python 데이터 처리", requirementType: "필수", evidence: "현재 근거 없음", status: "MISSING", action: "데이터 수집·정제 미니 프로젝트 수행" },
      { requirement: "Airflow 기반 파이프라인", requirementType: "필수", evidence: "현재 근거 없음", status: "MISSING", action: "장기 학습 로드맵 확인" },
    ],
  },
];
