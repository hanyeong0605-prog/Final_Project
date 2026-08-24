import { ArrowRight, BriefcaseBusiness, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";

export function EmployerHomePage() {
  return (
    <>
      <section className="employer-hero employer-hero-reference">
        <img src="/employer-recruiter-home.png" alt="좋은 인재와 회사를 연결하는 Job-A-Dream 기업회원 채용담당자 홈" />
      </section>
      <section className="employer-home-links">
        <Link to="/employer/dashboard"><UsersRound /><span><strong>공개 인재 대시보드</strong><small>공개에 동의한 회원의 역량과 스펙을 확인합니다.</small></span><ArrowRight /></Link>
        <Link to="/employer/postings"><BriefcaseBusiness /><span><strong>내 공고 관리</strong><small>채용공고를 작성하고 수정·마감·숨김 처리합니다.</small></span><ArrowRight /></Link>
      </section>
    </>
  );
}
