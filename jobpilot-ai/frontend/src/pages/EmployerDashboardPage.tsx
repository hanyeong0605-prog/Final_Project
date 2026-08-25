import { Search, UsersRound } from "lucide-react";
import { PageHeading } from "../shared/components/PageHeading";

export function EmployerDashboardPage() {
  return (
    <>
      <PageHeading eyebrow="TALENT DASHBOARD" title="공개 인재 대시보드" body="역량과 스펙 공개에 동의한 회원만 조회할 수 있습니다." />
      <section className="panel employer-talent-panel">
        <div className="employer-talent-toolbar"><div><UsersRound size={19} /><strong>공개 인재</strong></div><label><Search size={16} /><input placeholder="직무·기술·지역 검색" /></label></div>
        <div className="data-empty"><UsersRound size={28} /><strong>아직 공개된 인재 정보가 없습니다.</strong><p>일반회원 공개 설정 API가 연결되면 이곳에 인재 미리보기가 표시됩니다.</p></div>
      </section>
    </>
  );
}
