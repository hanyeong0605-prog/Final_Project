import { useEffect, useState } from "react";
import { Bookmark, BriefcaseBusiness, ChevronRight, Target } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { MetricCard } from "../features/dashboard/components/MetricCard";
import { useInterests } from "../features/interests/model/InterestContext";
import { getJobMatches } from "../features/jobs/api/jobMatchesApi";
import { CompactJobCard } from "../features/jobs/components/CompactJobCard";
import { JobMatchDrawer } from "../features/jobs/components/JobMatchDrawer";
import type { JobMatch } from "../features/jobs/model/job.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";

export function DashboardPage() {
  const [jobs, setJobs] = useState<JobMatch[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [selectedJob, setSelectedJob] = useState<JobMatch | null>(null);
  const { interestCount, isInterested, toggleInterest } = useInterests();
  const navigate = useNavigate();

  useEffect(() => {
    void getJobMatches()
      .then((data) => { setJobs(data); setStatus("ready"); })
      .catch(() => { setJobs([]); setStatus("error"); });
  }, []);

  const recommended = jobs.filter((job) => job.recommendationLevel !== "DIFFICULT_NOW");
  const applyNowCount = jobs.filter((job) => job.recommendationLevel === "APPLY_NOW").length;
  const challengeCount = jobs.filter((job) => job.recommendationLevel === "CHALLENGE_AFTER_GAPS").length;

  return <>
    <PageHeading
      eyebrow="TODAY'S CAREER ACTION"
      title="현재 지원할 수 있는 공고를 확인하세요."
      body="회원의 실제 역량 근거와 사람인 채용공고를 비교한 결과만 표시합니다."
      action={
        <div style={{ display: "flex", gap: "10px" }}>
          <button className="outline-button" onClick={() => navigate("/profile")}>
            <Target size={17} />내 역량 근거 관리
          </button>
          <button className="outline-button" onClick={() => navigate("/question")}>
            <Target size={17} />진로 심리 검사
          </button>
          <button className="outline-button" onClick={() => navigate("/check")}>
            <Target size={17} />맞춤법 검사기
          </button>



        </div>
      }
    />

    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && jobs.length === 0 && <DataStatePanel state="empty" emptyTitle="표시할 매칭 결과가 없습니다" emptyBody="사람인 공고 수집과 회원별 AI 분석이 완료되면 대시보드가 구성됩니다." />}

    {status === "ready" && jobs.length > 0 && <>
      <div className="metric-grid">
        <MetricCard icon={<BriefcaseBusiness />} label="분석된 공고" value={String(jobs.length)} hint="실제 DB 분석 결과" tone="blue" />
        <MetricCard icon={<Target />} label="지금 지원 가능" value={String(applyNowCount)} hint="필수요건 충족 공고" tone="green" />
        <MetricCard icon={<BriefcaseBusiness />} label="보완 후 도전" value={String(challengeCount)} hint="부족 요건이 적은 공고" tone="orange" />
        <MetricCard icon={<Bookmark />} label="관심 목록" value={String(interestCount)} hint="사용자가 저장한 항목" tone="purple" />
      </div>
      <section className="panel recommended-panel">
        <PanelTitle title="개인별 추천 공고" subtitle="현재 지원하거나 소수 요건 보완 후 도전할 수 있는 공고입니다." action={<button className="text-button" onClick={() => navigate("/jobs")}>전체 보기 <ChevronRight size={15} /></button>} />
        {recommended.length === 0 ? <DataStatePanel state="empty" emptyTitle="추천 가능한 공고가 없습니다" emptyBody="현재 분석 결과에서는 지원 가능한 공고가 확인되지 않았습니다." /> : <div className="job-list">{recommended.map((job) => <CompactJobCard key={job.id} job={job} interested={isInterested(job.id)} onOpen={() => setSelectedJob(job)} onInterest={() => toggleInterest(job.id)} />)}</div>}
      </section>
    </>}

    {selectedJob && <JobMatchDrawer job={selectedJob} interested={isInterested(selectedJob.id)} onInterest={() => toggleInterest(selectedJob.id)} onClose={() => setSelectedJob(null)} />}
  </>;
}
