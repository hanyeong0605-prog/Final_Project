import type { Opportunity } from "../model/opportunity.types";

export const opportunitiesFixture: Opportunity[] = [
  { id: 201, type: "교육", title: "클라우드 기반 Java 풀스택 개발자 양성과정", organization: "고용24 · K-디지털 트레이닝", period: "2026.09.01 — 2027.02.28", deadline: "2026.08.20", reason: "‘브릿지웍스’ 공고에서 보완이 필요한 Docker·AWS 배포 경험을 프로젝트 결과물로 만들 수 있습니다.", tags: ["Spring Boot", "Docker", "AWS", "국비지원"] },
  { id: 202, type: "자격증", title: "SQLD 제58회 원서 접수", organization: "한국데이터산업진흥원", period: "시험일 2026.09.19", deadline: "2026.08.10", reason: "SQL·DB 역량을 요구하거나 우대하는 백엔드 공고가 많아, 학습 계획용으로 관심 등록을 권합니다.", tags: ["SQL", "데이터베이스", "국가공인"] },
  { id: 203, type: "공모전", title: "2026 공공데이터 활용 서비스 개발 공모전", organization: "공공데이터포털", period: "2026.08.01 — 2026.09.30", deadline: "2026.09.30", reason: "공공 API 연동과 팀 협업 경험을 추가해 포트폴리오의 프로젝트 근거를 강화할 수 있습니다.", tags: ["공공 API", "팀 프로젝트", "포트폴리오"] },
];
