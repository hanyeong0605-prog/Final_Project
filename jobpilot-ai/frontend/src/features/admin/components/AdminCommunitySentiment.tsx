import { useEffect, useState } from "react";
import { getJson, postJson } from "../../../api/httpClient";

type Summary={feedbackCount:number;positiveCount:number;neutralCount:number;negativeCount:number;mixedCount:number;pendingCount:number};
type Row={id:number;board_type:string;title:string;created_at:string;polarity?:string;positive_score?:number;negative_score?:number;model_version?:string};
type Data={summary:Summary;recent:Row[]};

export function AdminCommunitySentiment(){
 const[data,setData]=useState<Data>();const[error,setError]=useState("");
 const load=()=>getJson<Data>("/api/v1/admin/community/sentiment/summary").then(setData).catch(e=>setError(e.message));
 useEffect(()=>{void load()},[]);
 async function moderate(id:number,action:"HIDE"|"RESTORE"|"DELETE"){
  const reason=prompt("관리 사유를 입력하세요");if(!reason)return;
  try{await postJson("/api/v1/admin/community/moderate",{targetType:"POST",targetId:id,action,reason});await load()}catch(e){setError((e as Error).message)}
 }
 return <section className="panel admin-community-sentiment"><span className="eyebrow">SERVICE FEEDBACK SENTIMENT</span><h2>커뮤니티 서비스 피드백</h2><p>작성자가 홈페이지 기능·사용 경험이라고 표시한 글만 집계합니다. 일반 자유게시판 대화와 회사 순위에는 반영하지 않습니다.</p>{error?<p className="form-error">{error}</p>:data&&<><div className="review-metrics"><div><strong>{data.summary.feedbackCount||0}</strong><span>피드백</span></div><div><strong>{data.summary.positiveCount||0}</strong><span>긍정</span></div><div><strong>{data.summary.negativeCount||0}</strong><span>부정</span></div><div><strong>{data.summary.mixedCount||0}</strong><span>복합</span></div><div><strong>{data.summary.pendingCount||0}</strong><span>분석 대기</span></div></div><div className="admin-feedback-preview">{data.recent.slice(0,10).map(r=><article key={r.id}><b>{r.title}</b><span>{r.polarity||"분석 대기"}</span><small>{r.board_type} · {r.model_version||"-"}</small><div><button onClick={()=>moderate(r.id,"HIDE")}>숨김</button><button onClick={()=>moderate(r.id,"RESTORE")}>복원</button><button onClick={()=>moderate(r.id,"DELETE")}>삭제</button></div></article>)}</div></>}</section>
}
