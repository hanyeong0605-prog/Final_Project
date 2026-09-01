import { useEffect, useState } from "react";
import { EyeOff, ExternalLink, RotateCcw, Trash2 } from "lucide-react";
import { getJson, postJson } from "../../../api/httpClient";
import type { PostPage } from "../../community/api/communityApi";

export function AdminCommunityManager() {
  const [data, setData] = useState<PostPage | null>(null);
  const [status, setStatus] = useState("ALL");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const load = () => void getJson<PostPage>(`/api/v1/admin/community/posts?status=${status}&query=${encodeURIComponent(query)}&size=20`).then(setData).catch((e) => setError(e.message));
  useEffect(load, [status]);
  const moderate = async (targetId: number, action: "HIDE" | "RESTORE" | "DELETE") => { await postJson("/api/v1/admin/community/moderate", { targetType: "POST", targetId, action, reason: "관리자 게시판 관리" }); load(); };
  return <section className="panel admin-community-manager"><div className="admin-panel-heading"><div><span className="eyebrow">COMMUNITY MODERATION</span><h2>게시글 관리</h2><p>게시글을 검색하고 공개 상태를 숨김·복구·삭제로 관리합니다.</p></div><form onSubmit={(e) => { e.preventDefault(); load(); }}><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="제목·내용·작성자 검색" /><button className="outline-button">검색</button></form></div><div className="admin-posting-filters"><label>상태<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="ALL">전체</option><option value="PUBLIC">공개</option><option value="HIDDEN">숨김</option><option value="DELETED">삭제</option></select></label><span>{data?.total.toLocaleString() ?? 0}건</span></div>{error && <p className="form-error">{error}</p>}<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>상태</th><th>게시글</th><th>작성자</th><th>활동</th><th>관리</th></tr></thead><tbody>{data?.items.map((post) => <tr key={post.id}><td><span className={`admin-status ${post.status.toLowerCase()}`}>{post.status === "PUBLIC" ? "공개" : post.status === "HIDDEN" ? "숨김" : "삭제"}</span></td><td><a href={`/community/${post.id}`} target="_blank" rel="noreferrer">{post.title}<ExternalLink size={12} /></a></td><td>{post.author}</td><td>조회 {post.views} · 좋아요 {post.likes} · 댓글 {post.comments}</td><td><div className="admin-row-actions">{post.status !== "HIDDEN" && <button title="숨김" onClick={() => void moderate(post.id, "HIDE")}><EyeOff size={14} /></button>}{post.status !== "PUBLIC" && <button title="복구" onClick={() => void moderate(post.id, "RESTORE")}><RotateCcw size={14} /></button>}{post.status !== "DELETED" && <button title="삭제" className="danger" onClick={() => void moderate(post.id, "DELETE")}><Trash2 size={14} /></button>}</div></td></tr>)}</tbody></table></div></section>;
}
