import { ArrowRight, BriefcaseBusiness, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";

export function EmployerHomePage() {
  const { employer } = useEmployerAuth();
  if (!employer) return null;

  return (
    <>
      <section className="employer-hero employer-hero-reference">
        <div className="employer-hero-copy">
          <span className="eyebrow">JOB-A-DREAM FOR RECRUITERS</span>
          <h1>좋은 인재와 회사를 연결해<br />더 나은 내일을 만듭니다.</h1>
          <p>{employer.managerName} 담당자님, 공개된 역량 정보를 확인하고 우리 회사에 맞는 인재를 만나보세요.</p>
          <div className="employer-hero-actions">
            <Link className="primary-button" to="/employer/dashboard"><UsersRound size={17} />공개 인재 보기</Link>
            <Link className="outline-button" to="/employer/postings"><BriefcaseBusiness size={17} />공고 관리</Link>
          </div>
        </div>
        <div className="employer-reference-visual">
          <img src="/employer-recruiter-home.png" alt="인재 목록을 확인하는 Job-A-Dream 기업 채용담당자" />
        </div>
      </section>
      <section className="employer-home-links">
        <Link to="/employer/dashboard"><UsersRound /><span><strong>공개 인재 대시보드</strong><small>공개에 동의한 회원의 역량과 스펙을 확인합니다.</small></span><ArrowRight /></Link>
        <Link to="/employer/postings"><BriefcaseBusiness /><span><strong>내 공고 관리</strong><small>채용공고를 작성하고 수정·마감·숨김 처리합니다.</small></span><ArrowRight /></Link>
      </section>
    </>
  );
}
