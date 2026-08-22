import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useInterests } from "../features/interests/model/InterestContext";
import { getRecommendedOpportunities } from "../features/opportunities/api/opportunitiesApi";
import { getOpportunityInterestIds, toggleOpportunityInterest } from "../features/opportunities/api/opportunityInterestsApi";
import { CertificateOpportunitySection } from "../features/opportunities/components/CertificateOpportunitySection";
import { BookRecommendationSection } from "../features/opportunities/components/BookRecommendationSection";
import { OpportunityCard } from "../features/opportunities/components/OpportunityCard";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

export function OpportunitiesPage() {
  const [searchParams] = useSearchParams();
  const requestedCategory = searchParams.get("category");
  const initialCategory = requestedCategory === "CERTIFICATE" || requestedCategory === "TRAINING" || requestedCategory === "BOOK" || requestedCategory === "OTHER" ? requestedCategory : "ALL";
  const resourceQuery = searchParams.get("resourceQuery") ?? "";
  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [category, setCategory] = useState<"ALL" | "CERTIFICATE" | "TRAINING" | "BOOK" | "OTHER">(initialCategory);
  const [trainingView, setTrainingView] = useState<"OPEN" | "CLOSED">("OPEN");
  const [trainingQuery, setTrainingQuery] = useState(resourceQuery);
  const [trainingCategory, setTrainingCategory] = useState("ALL");
  const [trainingSort, setTrainingSort] = useState<"deadline" | "name">("deadline");
  const [trainingPage, setTrainingPage] = useState(0);
  const { } = useInterests(); const [interestIds, setInterestIds] = useState<number[]>([]);

  useEffect(() => {
    void getRecommendedOpportunities()
      .then((data) => { setOpportunities(data); setStatus("ready"); })
      .catch(() => { setOpportunities([]); setStatus("error"); }); void getOpportunityInterestIds().then(setInterestIds).catch(() => setInterestIds([]));
  }, []);

  return <>
    <PageHeading eyebrow="GROWTH OPPORTUNITIES" title="부족한 근거를 채울 기회" body="매칭 분석의 부족 요건을 기준으로 자격증·고용24 훈련과정·기술 도서를 연결합니다." />
    {searchParams.get("requirementId") && <div className="opportunity-context">선택한 공고의 부족 요건에 맞춘 성장 기회를 보고 있습니다.</div>}
    <nav className="opportunity-category-tabs" aria-label="성장 기회 카테고리"><button className={category === "ALL" ? "active" : ""} onClick={() => setCategory("ALL")}>전체</button><button className={category === "CERTIFICATE" ? "active" : ""} onClick={() => setCategory("CERTIFICATE")}>자격증 정보</button><button className={category === "TRAINING" ? "active" : ""} onClick={() => setCategory("TRAINING")}>고용24 훈련과정</button><button className={category === "BOOK" ? "active" : ""} onClick={() => setCategory("BOOK")}>도서 추천</button><button className={category === "OTHER" ? "active" : ""} onClick={() => setCategory("OTHER")}>기타 기회</button></nav>
    {(category === "ALL" || category === "CERTIFICATE") && <CertificateOpportunitySection initialQuery={resourceQuery} />}
    {(category === "ALL" || category === "BOOK") && <BookRecommendationSection jobPostingId={searchParams.get("jobPostingId")} requirementId={searchParams.get("requirementId")} initialQuery={resourceQuery} />}
    {(category === "ALL" || category === "TRAINING" || category === "OTHER") && <><div className="opportunity-section-heading"><h2 className="opportunity-section-title">{category === "TRAINING" ? "고용24 훈련과정" : "교육·공모전·청년지원"}</h2>{(category === "ALL" || category === "TRAINING") && <div className="training-status-tabs" aria-label="훈련과정 상태"><button className={trainingView === "OPEN" ? "active" : ""} onClick={() => { setTrainingView("OPEN"); setTrainingPage(0); }}>모집 예정</button><button className={trainingView === "CLOSED" ? "active" : ""} onClick={() => { setTrainingView("CLOSED"); setTrainingPage(0); }}>진행·종료 과정</button></div>}</div>
    {(category === "ALL" || category === "TRAINING") && <div className="opportunity-catalog-controls"><input value={trainingQuery} onChange={(event) => { setTrainingQuery(event.target.value); setTrainingPage(0); }} placeholder="과정명·기관·기술 검색" aria-label="훈련과정 검색" /><select value={trainingCategory} onChange={(event) => { setTrainingCategory(event.target.value); setTrainingPage(0); }} aria-label="훈련과정 분야"><option value="ALL">전체 분야</option><option value="IT">IT·개발·데이터</option><option value="LANGUAGE">영어·외국어</option><option value="BUSINESS">OA·회계·마케팅·취업</option><option value="DESIGN">디자인</option></select><select value={trainingSort} onChange={(event) => { setTrainingSort(event.target.value as "deadline" | "name"); setTrainingPage(0); }} aria-label="훈련과정 정렬"><option value="deadline">시작 임박순</option><option value="name">가나다순</option></select></div>}
    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && opportunities.length === 0 && <DataStatePanel state="empty" emptyTitle="추천할 기회 정보가 없습니다" emptyBody="회원의 부족 역량과 연결된 실제 기회 데이터가 생기면 표시됩니다." />}
    {status === "ready" && opportunities.length > 0 && <TrainingOpportunityList opportunities={opportunities} category={category} trainingView={trainingView} trainingQuery={trainingQuery} trainingCategory={trainingCategory} trainingSort={trainingSort} trainingPage={trainingPage} setTrainingPage={setTrainingPage} interestIds={interestIds} setInterestIds={setInterestIds} />}</>}
  </>;
}

function TrainingOpportunityList({ opportunities, category, trainingView, trainingQuery, trainingCategory, trainingSort, trainingPage, setTrainingPage, interestIds, setInterestIds }: { opportunities: Opportunity[]; category: "ALL" | "CERTIFICATE" | "TRAINING" | "BOOK" | "OTHER"; trainingView: "OPEN" | "CLOSED"; trainingQuery: string; trainingCategory: string; trainingSort: "deadline" | "name"; trainingPage: number; setTrainingPage: (page: number) => void; interestIds: number[]; setInterestIds: (ids: number[]) => void; }) {
  const groupMatches = (item: Opportunity) => { const text = `${item.title} ${item.organization} ${item.tags.join(" ")} ${item.contents || ""}`.toLowerCase(); if (trainingCategory === "IT") return /개발|프로그래밍|코딩|데이터|ai|인공지능|보안|클라우드|네트워크|java|python|react/.test(text); if (trainingCategory === "LANGUAGE") return /영어|english|toeic|토익|외국어/.test(text); if (trainingCategory === "BUSINESS") return /엑셀|office|oa|회계|마케팅|취업/.test(text); if (trainingCategory === "DESIGN") return /디자인|ui|ux|영상|그래픽/.test(text); return true; };
  const query = trainingQuery.trim().toLowerCase();
  const items = opportunities.filter((item) => category !== "TRAINING" || item.type === "교육").filter((item) => category !== "OTHER" || item.type !== "자격증").filter((item) => item.type !== "교육" || (trainingView === "CLOSED" ? item.status === "IN_PROGRESS" || item.status === "EXPIRED" : item.status === "ACTIVE")).filter((item) => item.type !== "교육" || (!query || `${item.title} ${item.organization} ${item.tags.join(" ")} ${item.contents || ""}`.toLowerCase().includes(query))).filter((item) => item.type !== "교육" || groupMatches(item)).sort((a, b) => trainingSort === "name" ? a.title.localeCompare(b.title, "ko") : (a.startAt || "9999-12-31").localeCompare(b.startAt || "9999-12-31"));
  const pageCount = Math.max(1, Math.ceil(items.length / 30));
  const safePage = Math.min(trainingPage, pageCount - 1);
  const pageStart = Math.max(0, Math.min(safePage - 3, pageCount - 7));
  const pageNumbers = Array.from({ length: Math.min(7, pageCount) }, (_, index) => pageStart + index);
  const visible = items.slice(safePage * 30, safePage * 30 + 30);
  return <><section className="opportunity-grid">{visible.map((item) => <OpportunityCard key={item.id} item={item} interested={interestIds.includes(item.id)} onInterest={() => { const next=!interestIds.includes(item.id); setInterestIds(next ? [...interestIds,item.id] : interestIds.filter((id) => id !== item.id)); void toggleOpportunityInterest(item.id,next).catch(() => setInterestIds(interestIds)); }} />)}</section>{items.length === 0 && <DataStatePanel state="empty" emptyTitle="해당 조건의 과정이 없습니다" emptyBody="검색어 또는 분야를 바꿔 다시 찾아보세요." />}{items.length > 30 && <div className="opportunity-pagination" aria-label="훈련과정 목록 페이지"><button type="button" aria-label="이전 페이지" disabled={safePage === 0} onClick={() => setTrainingPage(safePage - 1)}>‹</button>{pageNumbers.map((number) => <button type="button" key={number} className={number === safePage ? "active" : ""} onClick={() => setTrainingPage(number)}>{number + 1}</button>)}<button type="button" aria-label="다음 페이지" disabled={safePage + 1 >= pageCount} onClick={() => setTrainingPage(safePage + 1)}>›</button></div>}</>;
}
