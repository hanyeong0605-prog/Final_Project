import type { MemberProfile } from "../model/profile.types";

export const memberProfileFixture: MemberProfile = {
  name: "김개발",
  targetRole: "백엔드 개발자",
  conditions: "서울 · 경기 / 신입 / 2026년 하반기 지원 가능",
  skills: [
    { skill: "Spring Boot", evidence: "프로젝트 2개" }, { skill: "JPA", evidence: "프로젝트 1개" }, { skill: "MySQL", evidence: "프로젝트 2개" }, { skill: "React", evidence: "프로젝트 1개" }, { skill: "REST API", evidence: "GitHub 근거" },
  ],
  project: { title: "MealMate · 식단 예약 서비스", description: "백엔드 담당 · Spring Boot, JPA, MySQL · 예약 중복 문제를 트랜잭션과 DB 제약조건으로 해결", githubUrl: "github.com/kimdev/mealmate" },
};
