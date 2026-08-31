import { useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { getJson } from "../../../api/httpClient";

type Row = { id: number; title: string; body: string; polarity?: string; positiveTerms: number; negativeTerms: number };
type Board = { boardType: "FREE" | "QNA"; postCount: number; positivePostCount: number; negativePostCount: number; positiveTerms: number; negativeTerms: number; pendingCount: number; recent: Row[] };
type Data = { boards: Board[] };

const positiveSignals = ["좋", "만족", "추천", "감사", "최고", "편리", "도움", "훌륭", "반갑", "빠르", "개선", "유용", "안정", "다양", "공정", "효율"];
const negativeSignals = ["불공정", "불안정", "불균형", "불편", "오류", "실망", "느리", "최악", "짜증", "문제", "불만", "답답", "고장", "편향", "부족", "한정", "제한", "아쉽", "차별", "미흡", "복잡", "힘들"];
const label = (board: string) => board === "FREE" ? "자유게시판" : "Q&A 게시판";
const polarity = (value?: string) => value === "POSITIVE" ? "긍정" : value === "NEGATIVE" ? "부정" : value === "MIXED" ? "복합" : value === "NEUTRAL" ? "중립" : "분석 대기";

function HighlightedText({ text }: { text: string }) {
  const signals = useMemo(() => new Map([...positiveSignals.map((signal) => [signal, "positive"] as const), ...negativeSignals.map((signal) => [signal, "negative"] as const)]), []);
  const matcher = useMemo(() => new RegExp(`(${[...signals.keys()].sort((a, b) => b.length - a.length).join("|")})`, "g"), [signals]);
  return <>{text.split(matcher).map((part, index) => {
    const kind = signals.get(part);
    return kind ? <mark key={`${part}-${index}`} className={`community-signal-mark ${kind}`}>{part}</mark> : part;
  })}</>;
}

export function AdminCommunitySentiment() {
  const [data, setData] = useState<Data | null>(null);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState<number | null>(null);
  useEffect(() => { void getJson<Data>("/api/v1/admin/community/sentiment/summary").then(setData).catch((e) => setError(e.message)); }, []);

  return <section className="panel admin-community-sentiment"><span className="eyebrow">COMMUNITY MOOD SIGNALS</span><h2>커뮤니티 요약</h2><p>공개 글의 감정분석 결과를 자유게시판과 Q&A로 나눠 보여줍니다. 비공개 문의는 분석에서 제외합니다.</p>{error ? <p className="form-error">{error}</p> : data && <div className="admin-community-board-grid">{data.boards.map((board) => <article key={board.boardType} className="admin-community-board"><header><strong>{label(board.boardType)}</strong><small>공개 글 {board.postCount}개 · 분석 대기 {board.pendingCount}개</small></header><div className="admin-community-signal-grid"><span><b>{board.positivePostCount}</b>긍정 글</span><span><b>{board.negativePostCount}</b>부정 글</span><span><b>{board.positiveTerms}</b>긍정 표현</span><span><b>{board.negativeTerms}</b>부정 표현</span></div><div className="admin-community-preview">{board.recent.map((row) => <div key={row.id} className="admin-community-preview-row"><b>{row.title}</b><small><em className={row.polarity?.toLowerCase()}>{polarity(row.polarity)}</em> · 긍정 표현 {row.positiveTerms} · 부정 표현 {row.negativeTerms}</small><button type="button" className="admin-community-detail-button" onClick={() => setExpandedId((current) => current === row.id ? null : row.id)}>자세히 보기 {expandedId === row.id ? <ChevronUp size={13} /> : <ChevronDown size={13} />}</button>{expandedId === row.id && <div className="admin-community-detail-content"><p><HighlightedText text={row.body} /></p><small><mark className="community-signal-mark positive">긍정 표현</mark><mark className="community-signal-mark negative">부정 표현</mark></small></div>}</div>)}</div></article>)}</div>}</section>;
}
