import { Bookmark, GraduationCap, List, Sparkles } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";
import {
  addCertificateBookmark, getCertificateBookmarks, getMemberCertificates, getQnetFields, getRecommendedCertificates, listQnetQualifications, removeCertificateBookmark,
  type QnetFieldCount, type QnetQualification,
} from "../../profile/api/memberCertificatesApi";
import { getCareerProfile } from "../../profile/api/careerProfileApi";
import { getMemberSkills } from "../../profile/api/memberSkillsApi";
import { CertificateDetailModal } from "../../profile/components/CertificateDetailModal";
import { CertificateSearchModal } from "../../profile/components/CertificateSearchModal";
import { CertificateStudyPlanModal } from "./CertificateStudyPlanModal";
import type { StudyPlanProfileInput } from "../api/certificateStudyPlanApi";
import { DataStatePanel } from "../../../shared/components/DataStatePanel";

// 2026-08-11: "전체 자격증 목록" 분야 필터 버튼 - "it관련이 기본"이라는 요청대로 이
// 값을 기본 선택으로 둔다. CertificateRecommendationService의 JOB_FAMILY_TO_QNET_FIELD가
// "IT 개발·데이터" 직무에 매핑하는 것과 같은 NCS 직무분야명(obligfldnm).
const IT_DEFAULT_FIELD = "정보통신";

// 2026-08-11: "성장 기회 추천" 페이지의 자격증 섹션 - 목표 직무 맞춤 추천(자동
// gap-analysis-lite) + 찜한 자격증을 상단에, 전체 종목은 CertificateSearchModal("자격증
// 종목 찾아보기")로 검색해서 그 자리에서 바로 찜할 수 있게 한다. 공모전/청년지원 등
// 다른 기회 유형은 아직 크롤링 데이터가 없어 이 섹션과 별도로 기존 OpportunitiesPage
// 하단 목록(빈 상태)에 맡겨 둔다.
export function CertificateOpportunitySection({ initialQuery = "" }: { initialQuery?: string }) {
  const [recommended, setRecommended] = useState<QnetQualification[]>([]);
  const [bookmarks, setBookmarks] = useState<QnetQualification[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [detailItem, setDetailItem] = useState<QnetQualification | null>(null);
  const [allItems, setAllItems] = useState<QnetQualification[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [loadingCatalogue, setLoadingCatalogue] = useState(false);
  const [fields, setFields] = useState<QnetFieldCount[]>([]);
  const [selectedField, setSelectedField] = useState(IT_DEFAULT_FIELD);
  const [queryDraft, setQueryDraft] = useState(initialQuery);
  const [query, setQuery] = useState(initialQuery);
  const [sort, setSort] = useState<"name" | "field">("name");
  const [studyPlanItem, setStudyPlanItem] = useState<QnetQualification | null>(null);
  // 2026-08-11: "AI 학습 계획" 생성용 - 목표 직무/보유 기술/보유 자격증을 미리 모아둔다
  // (버튼 누를 때마다 다시 불러오지 않도록 페이지 진입 시 한 번만 로드).
  const [studyPlanProfile, setStudyPlanProfile] = useState<StudyPlanProfileInput>({
    targetJobFamily: "", targetRole: "", skills: [], ownedCertificates: [],
  });

  useEffect(() => {
    void Promise.all([
      getRecommendedCertificates(), getCertificateBookmarks(), getQnetFields(),
      getCareerProfile(), getMemberSkills(), getMemberCertificates(),
    ])
      .then(([recommendedResult, bookmarkResult, fieldsResult, careerProfile, skills, ownedCertificates]) => {
        setRecommended(recommendedResult); setBookmarks(bookmarkResult);
        setFields(fieldsResult);
        setStudyPlanProfile({
          targetJobFamily: careerProfile?.targetJobFamily ?? "",
          targetRole: careerProfile?.targetRole ?? "",
          skills: skills.map((skill) => skill.skillName),
          ownedCertificates: ownedCertificates.map((certificate) => certificate.name),
        });
        setStatus("ready");
      })
      .catch(() => setStatus("error"));
  }, []);

  useEffect(() => {
    setLoadingCatalogue(true);
    void listQnetQualifications(page, 30, selectedField, query, sort)
      .then((result) => { setAllItems(result.items); setTotal(result.total); })
      .finally(() => setLoadingCatalogue(false));
  }, [page, selectedField, query, sort]);

  // 2026-08-11: "분야별로 버튼 나눠줄 수 있냐"는 요청으로 IT 단일 체크박스에서 임의
  // 분야 버튼 선택으로 바꿨다 - field=""는 "전체"(필터 없음).
  const selectField = (field: string) => {
    if (field === selectedField) return;
    setSelectedField(field);
    setPage(0);
  };

  const pageCount = Math.max(1, Math.ceil(total / 30));
  const pageStart = Math.max(0, Math.min(page - 3, pageCount - 7));
  const pageNumbers = Array.from({ length: Math.min(7, pageCount) }, (_, index) => pageStart + index);
  const searchCatalogue = (event: FormEvent) => { event.preventDefault(); setPage(0); setQuery(queryDraft.trim()); };

  // IT(기본 선택 분야)를 항상 맨 앞에 두고, 나머지는 백엔드가 이미 건수 내림차순으로 준 순서를 유지한다.
  const orderedFields = [...fields].sort((a, b) => {
    if (a.field === IT_DEFAULT_FIELD) return -1;
    if (b.field === IT_DEFAULT_FIELD) return 1;
    return 0;
  });

  const isBookmarked = (jmcd: string) => bookmarks.some((item) => item.code === jmcd);
  const toggleBookmark = (item: QnetQualification) => {
    const request = isBookmarked(item.code) ? removeCertificateBookmark(item.code) : addCertificateBookmark(item);
    void request.then(setBookmarks);
  };

  const renderCard = (item: QnetQualification, showStudyPlan = false) => <div className="certificate-opportunity-card" key={item.code}>
    <div><strong>{item.name}</strong><span>{[item.qualificationType, item.field, item.subField].filter(Boolean).join(" · ")}</span></div>
    <div className="certificate-opportunity-actions">
      {showStudyPlan && <button type="button" className="certificate-study-plan-trigger" onClick={() => setStudyPlanItem(item)}><GraduationCap size={14} /> AI 학습 계획</button>}
      <button type="button" className="certificate-opportunity-detail" onClick={() => setDetailItem(item)}>상세보기</button>
      <button type="button" className={isBookmarked(item.code) ? "bookmark active" : "bookmark"} onClick={() => toggleBookmark(item)} aria-label={isBookmarked(item.code) ? "찜 해제" : "찜하기"}>
        <Bookmark size={17} fill={isBookmarked(item.code) ? "currentColor" : "none"} />
      </button>
    </div>
  </div>;

  return <section className="certificate-opportunity-section">
    <div className="certificate-opportunity-header">
      <h2>자격증</h2>
      <CertificateSearchModal onSelect={toggleBookmark} actionLabel="찜하기" />
    </div>

    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}

    {status === "ready" && <>
      <h3 className="certificate-opportunity-subtitle"><List size={16} /> 자격증 검색·필터</h3>
      <form className="opportunity-catalog-controls" onSubmit={searchCatalogue}>
        <input value={queryDraft} onChange={(event) => setQueryDraft(event.target.value)} placeholder="자격증명 검색" aria-label="자격증명 검색" />
        <button type="submit" className="outline-button">검색</button>
        <select value={sort} onChange={(event) => { setSort(event.target.value as "name" | "field"); setPage(0); }} aria-label="자격증 정렬"><option value="name">가나다순</option><option value="field">분야순</option></select>
      </form>
      <div className="certificate-field-filter" role="tablist" aria-label="분야 필터">
        <button type="button" className={selectedField === "" ? "field-chip active" : "field-chip"} disabled={loadingCatalogue} onClick={() => selectField("")}>전체</button>
        {orderedFields.map((f) => <button type="button" key={f.field} className={selectedField === f.field ? "field-chip active" : "field-chip"} disabled={loadingCatalogue} onClick={() => selectField(f.field)}>
          {f.field} <span>{f.count}</span>
        </button>)}
      </div>
      <h3 className="certificate-opportunity-subtitle"><Sparkles size={16} /> 목표 직무 맞춤 추천</h3>
      {recommended.length === 0
        ? <p className="skill-empty">스펙정보의 목표 직무분야를 설정하면 맞춤 자격증을 추천해 드려요.</p>
        : <div className="certificate-opportunity-grid">{recommended.map((item) => renderCard(item, true))}</div>}

      <h3 className="certificate-opportunity-subtitle"><Bookmark size={16} /> 찜한 자격증</h3>
      {bookmarks.length === 0
        ? <p className="skill-empty">아직 찜한 자격증이 없습니다. 위 "자격증 종목 찾아보기"에서 추가해 보세요.</p>
        : <div className="certificate-opportunity-grid">{bookmarks.map((item) => renderCard(item))}</div>}

      <h3 className="certificate-opportunity-subtitle"><List size={16} /> 전체 자격증 목록</h3>
      {allItems.length === 0 && !loadingCatalogue
        ? <p className="skill-empty">해당 조건의 자격증이 없습니다.</p>
        : <div className="certificate-opportunity-grid">{allItems.map((item) => renderCard(item))}</div>}
      {loadingCatalogue && <p className="skill-empty">자격증 목록을 불러오는 중입니다.</p>}
      {total > 30 && <div className="opportunity-pagination" aria-label="자격증 목록 페이지"><button type="button" aria-label="이전 페이지" disabled={page === 0 || loadingCatalogue} onClick={() => setPage(page - 1)}>‹</button>{pageNumbers.map((number) => <button type="button" key={number} className={number === page ? "active" : ""} onClick={() => setPage(number)}>{number + 1}</button>)}<button type="button" aria-label="다음 페이지" disabled={page + 1 >= pageCount || loadingCatalogue} onClick={() => setPage(page + 1)}>›</button></div>}
    </>}

    {detailItem && <CertificateDetailModal item={detailItem} onClose={() => setDetailItem(null)} />}
    {studyPlanItem && <CertificateStudyPlanModal item={studyPlanItem} profile={studyPlanProfile} onClose={() => setStudyPlanItem(null)} />}
  </section>;
}
