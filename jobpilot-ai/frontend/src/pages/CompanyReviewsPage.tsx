import { FormEvent, useEffect, useState } from "react";
import { Building2, Star } from "lucide-react";
import { PageHeading } from "../shared/components/PageHeading";
import * as api from "../features/reviews/api/reviewApi";

const empty:api.ReviewInput={jobPostingId:null,rating:5,title:"",pros:"",cons:"",body:""};
export function CompanyReviewsPage(){
 const [companies,setCompanies]=useState<api.ReviewCompany[]>([]),[selected,setSelected]=useState<number>();
 const [reviews,setReviews]=useState<api.CompanyReview[]>([]),[form,setForm]=useState(empty),[editing,setEditing]=useState<number>();
 const [analyses,setAnalyses]=useState<Record<number,Awaited<ReturnType<typeof api.getAnalysis>>>>({}),[likeCounts,setLikeCounts]=useState<Record<number,number>>({}),[error,setError]=useState("");
 const loadCompanies=()=>api.listCompanies().then(v=>{setCompanies(v);if(!selected&&v[0])setSelected(v[0].id)}).catch(e=>setError(e.message));
 const loadReviews=()=>selected&&api.listReviews(selected).then(v=>setReviews(v.content)).catch(e=>setError(e.message));
 useEffect(()=>{void loadCompanies()},[]); useEffect(()=>{void loadReviews();setAnalyses({});},[selected]);
 async function submit(e:FormEvent){e.preventDefault();setError("");try{editing?await api.updateReview(editing,form):await api.createReview(selected!,form);setForm(empty);setEditing(undefined);await loadReviews()}catch(x){setError((x as Error).message)}}
 function edit(r:api.CompanyReview){setEditing(r.id);setForm({jobPostingId:null,rating:r.rating,title:r.title,pros:r.pros,cons:r.cons,body:r.body});window.scrollTo({top:0,behavior:"smooth"})}
 async function remove(id:number){if(!confirm("내 리뷰를 삭제할까요?"))return;await api.deleteReview(id);await loadReviews()}
 async function analysis(id:number){setAnalyses(v=>({...v,[id]:{available:false}}));try{const result=await api.getAnalysis(id);setAnalyses(v=>({...v,[id]:result}))}catch(e){setError((e as Error).message)}}
 async function like(id:number){try{const result=await api.likeReview(id);setLikeCounts(v=>({...v,[id]:result.count}))}catch(e){setError((e as Error).message)}}
 const company=companies.find(c=>c.id===selected);
 return <div className="review-page"><PageHeading eyebrow="FICTIONAL COMPANY REVIEWS" title="가상기업 근무 리뷰" body="포트폴리오 시연용 가상기업과 합성 초기 리뷰입니다. 실제 기업·채용과 무관합니다."/>
  {error&&<p className="form-error">{error}</p>}<div className="review-layout"><aside className="panel review-company-list"><strong>가상기업 100곳</strong>{companies.map(c=><button className={c.id===selected?"active":""} onClick={()=>setSelected(c.id)} key={c.id}><Building2 size={16}/><span>{c.name}<small>{c.industry} · {c.location}</small></span></button>)}</aside>
  <main>{company&&<section className="panel review-company-head"><span className="eyebrow">가상기업 · 시연용</span><h2>{company.name}</h2><p>{company.description}</p></section>}
  <form className="panel review-form" onSubmit={submit}><h3>{editing?"내 리뷰 수정":"새 리뷰 작성"}</h3><label>별점<select value={form.rating} onChange={e=>setForm({...form,rating:+e.target.value})}>{[5,4,3,2,1].map(n=><option key={n} value={n}>{n}점</option>)}</select></label>{(["title","pros","cons","body"] as const).map(k=><label key={k}>{({title:"한줄평",pros:"장점",cons:"아쉬운 점",body:"상세 후기"})[k]}<textarea required maxLength={k==="title"?200:k==="body"?5000:1500} value={form[k]} onChange={e=>setForm({...form,[k]:e.target.value})}/></label>)}<div><button className="primary-button">{editing?"수정 저장":"리뷰 등록"}</button>{editing&&<button type="button" onClick={()=>{setEditing(undefined);setForm(empty)}}>취소</button>}</div></form>
  <section className="review-cards">{reviews.map(r=><article className="panel" key={r.id}><header><b>{r.title}</b><span>{Array.from({length:r.rating},(_,i)=><Star key={i} size={15} fill="currentColor"/>)}</span></header><small>{r.displayAuthor} · {r.sourceType==="SYNTHETIC_DEMO"?"합성 시연 리뷰":"사용자 리뷰"}</small><p><strong>장점</strong> {r.pros}</p><p><strong>아쉬운 점</strong> {r.cons}</p><p>{r.body}</p><footer><button onClick={()=>analysis(r.id)}>감정분석 보기</button><button onClick={()=>like(r.id)}>좋아요{likeCounts[r.id]===undefined?"":` ${likeCounts[r.id]}`}</button>{!r.mine&&<button onClick={()=>{const reason=prompt("신고 사유를 입력하세요");if(reason)api.reportReview(r.id,reason).catch(e=>setError(e.message))}}>신고</button>}{r.mine&&<><button onClick={()=>edit(r)}>수정</button><button onClick={()=>remove(r.id)}>삭제</button></>}</footer>{analyses[r.id]&&<div className="review-analysis">{analyses[r.id].available?<>분석: <b>{analyses[r.id].analysis!.polarity.label}</b> · {analyses[r.id].analysis!.emotions.slice(0,3).map(e=>e.label).join(", ")}</>:"분석 대기 중입니다."}</div>}</article>)}</section></main></div></div>
}
