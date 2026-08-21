import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ExternalLink } from "lucide-react";
import { getOpportunity } from "../features/opportunities/api/opportunitiesApi";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";
export function OpportunityDetailPage() { const { id="" }=useParams(); const [item,setItem]=useState<Opportunity>(); const [error,setError]=useState(false); useEffect(()=>{void getOpportunity(id).then(setItem).catch(()=>setError(true));},[id]); if(error)return <DataStatePanel state="error"/>; if(!item)return <DataStatePanel state="loading"/>; return <><PageHeading eyebrow="WORK24 TRAINING" title={item.title} body={`${item.organization} · 훈련 기간 ${item.period}`} /><section className="panel"><span className={`type-badge ${item.status === "EXPIRED" ? "orange" : "blue"}`}>{item.status === "EXPIRED" ? "훈련 종료" : "훈련 진행/예정"}</span><h2>{item.title}</h2><p>{item.reason || "고용24 정보통신·개발 훈련과정입니다."}</p><div className="skills">{item.tags.map(tag=><span key={tag}>{tag}</span>)}</div><div className="form-actions"><Link className="outline-button" to="/opportunities">목록으로</Link><a className="primary-button" href={item.sourceUrl} target="_blank" rel="noreferrer">고용24에서 확인 <ExternalLink size={15}/></a></div></section></>; }
