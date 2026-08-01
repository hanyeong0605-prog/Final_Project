import { FormEvent, useEffect, useState } from "react";
import { Search } from "lucide-react";
import { getJobPostings } from "../features/job-postings/api/jobPostingsApi";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobPosting } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

export function AllJobPostingsPage() {
  const [query, setQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [postings, setPostings] = useState<JobPosting[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  useEffect(() => {
    setStatus("loading");
    void getJobPostings(submittedQuery)
      .then((data) => { setPostings(data); setStatus("ready"); })
      .catch(() => { setPostings([]); setStatus("error"); });
  }, [submittedQuery]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSubmittedQuery(query);
  };

  return <>
    <PageHeading eyebrow="SARAMIN IT JOBS" title="전체 채용공고" body="사람인에서 수집하고 검증한 IT개발·데이터 채용공고를 모두 확인합니다. 회원별 분석 결과와 관계없이 수집된 공고가 표시됩니다." />
    <form className="posting-search" onSubmit={submit}><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="회사명, 직무, 기술, 지역으로 검색" /><button type="submit">검색</button></form>
    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && postings.length === 0 && <DataStatePanel state="empty" emptyTitle="수집된 채용공고가 없습니다" emptyBody="사람인 공고를 수집하면 이 페이지에 표시됩니다." />}
    {status === "ready" && postings.length > 0 && <><div className="list-heading"><strong>{postings.length}개의 사람인 채용공고</strong><span>최신 등록순</span></div><section className="posting-grid">{postings.map((posting) => <JobPostingCard key={posting.id} posting={posting} />)}</section></>}
  </>;
}
