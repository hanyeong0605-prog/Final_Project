import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Award, CalendarDays, ExternalLink, MapPin, Phone, Users } from "lucide-react";
import { getOpportunity } from "../features/opportunities/api/opportunitiesApi";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

export function OpportunityDetailPage() {
  const { id = "" } = useParams();
  const [item, setItem] = useState<Opportunity>();
  const [error, setError] = useState(false);

  useEffect(() => { void getOpportunity(id).then(setItem).catch(() => setError(true)); }, [id]);
  if (error) return <DataStatePanel state="error" />;
  if (!item) return <DataStatePanel state="loading" />;

  const money = (value: number | null) => value === null ? "정보 없음" : `${value.toLocaleString()}원`;
  const courseUrl = item.detailUrl || item.sourceUrl;
  const closed = item.status === "CLOSED" || item.status === "EXPIRED";

  return <>
    <PageHeading eyebrow="WORK24 TRAINING" title={item.title} body={`${item.organization} · 훈련 기간 ${item.period}`} />
    <section className="panel training-detail">
      {item.thumbnailUrl && <img className="training-thumbnail" src={item.thumbnailUrl} alt="고용24 과정 안내 이미지" onError={(event) => { event.currentTarget.style.display = "none"; }} />}
      <span className={`type-badge ${closed ? "closed-badge" : "blue"}`}>{closed ? "신청 마감" : "모집 중"}</span>
      <h2>{item.title}</h2>
      <div className="skills">{item.tags.map(tag => <span key={tag}>{tag}</span>)}</div>
      <div className="spec-summary">
        <div><span>훈련기관</span><strong>{item.organization}</strong></div><div><span><CalendarDays size={13} /> 훈련 기간</span><strong>{item.period}</strong></div>
        <div><span><MapPin size={13} /> 훈련 지역</span><strong>{item.address || "정보 없음"}</strong></div>
        <div><span><Users size={13} /> 훈련 대상</span><strong>{item.trainingTarget || "정보 없음"}</strong></div>
        <div><span><Phone size={13} /> 문의</span><strong>{item.phone || "정보 없음"}</strong></div>
        <div><span>정원 / 신청</span><strong>{item.capacity ?? "-"}명 / {item.enrolledCount ?? "-"}명</strong></div>
        <div><span>총 수강료 / 실제 부담금</span><strong>{money(item.courseFee)} / {money(item.selfPayFee)}</strong></div>
        <div><span><Award size={13} /> 만족도 / 등급</span><strong>{item.satisfactionScore ?? "정보 없음"} {item.grade ? `· ${item.grade}` : ""}</strong></div>
        <div><span>3개월 / 6개월 취업률</span><strong>{item.employmentRate3m || "정보 없음"} / {item.employmentRate6m || "정보 없음"}</strong></div>
        <div><span>관련 자격증</span><strong>{item.certificate || "정보 없음"}</strong></div>
        <div><span>NCS 코드</span><strong>{item.ncsCode || "정보 없음"}</strong></div>
      </div>
      {item.contents && <section className="training-description"><h3>과정 소개</h3><p>{item.contents}</p></section>}
      <div className="form-actions">
        <Link className="outline-button" to="/opportunities">목록으로</Link>
        <a className="primary-button" href={courseUrl} target="_blank" rel="noreferrer">과정 상세 <ExternalLink size={15} /></a>
        {item.institutionUrl && <a className="outline-button" href={item.institutionUrl} target="_blank" rel="noreferrer">기관 상세 <ExternalLink size={15} /></a>}
      </div>
    </section>
  </>;
}
