import { useEffect, useMemo, useState } from "react";
import { Bookmark, BriefcaseBusiness, ChevronLeft, ChevronRight, Target } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { MetricCard } from "../features/dashboard/components/MetricCard";
import { useInterests } from "../features/interests/model/InterestContext";
import { getJobMatches, recalculateJobMatches, refreshJobMatchEvidence } from "../features/jobs/api/jobMatchesApi";
import { CompactJobCard } from "../features/jobs/components/CompactJobCard";
import { JobMatchDrawer } from "../features/jobs/components/JobMatchDrawer";
import type { JobMatch } from "../features/jobs/model/job.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";

type DashboardTab = "ALL" | "APPLY_NOW" | "CHALLENGE_AFTER_GAPS" | "INTERESTED";

const PAGE_SIZE = 20;

const tabCopy: Record<DashboardTab, { label: string; title: string; subtitle: string; emptyTitle: string; emptyBody: string }> = {
  ALL: {
    label: "분석된 공고",
    title: "분석된 채용공고",
    subtitle: "지원 준비도와 부족 요건을 확인하고, 나에게 맞는 다음 행동을 정해 보세요.",
    emptyTitle: "분석된 채용공고가 없습니다",
    emptyBody: "회원 스펙과 채용공고 요구사항 분석이 완료되면 여기에 표시됩니다.",
  },
  APPLY_NOW: {
    label: "지금 지원 가능",
    title: "지금도 지원해볼 만한 채용공고",
    subtitle: "필수 요건을 대부분 충족해 바로 지원을 검토할 수 있는 공고입니다.",
    emptyTitle: "지금 지원 가능한 공고가 없습니다",
    emptyBody: "역량 프로필을 보완하면 새로운 공고가 추천됩니다.",
  },
  CHALLENGE_AFTER_GAPS: {
    label: "보완 후 도전",
    title: "요건 보완 후 도전 가능한 채용공고",
    subtitle: "한두 가지 부족 요건을 채우면 지원 가능성이 높아지는 공고입니다.",
    emptyTitle: "보완 후 도전 공고가 없습니다",
    emptyBody: "분석된 공고 중 보완이 필요한 항목이 있는 공고가 여기에 표시됩니다.",
  },
  INTERESTED: {
    label: "관심 목록",
    title: "관심 채용공고",
    subtitle: "관심 등록한 공고를 모아 보고 준비 상태를 비교하세요.",
    emptyTitle: "관심 등록한 분석 공고가 없습니다",
    emptyBody: "공고 카드의 하트 버튼을 눌러 관심 목록에 저장해 보세요.",
  },
};

export function DashboardPage() {
  const [jobs, setJobs] = useState<JobMatch[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [selectedJob, setSelectedJob] = useState<JobMatch | null>(null);
  const [evidenceLoading, setEvidenceLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<DashboardTab>("ALL");
  const [page, setPage] = useState(0);
  const [recalculating, setRecalculating] = useState(false);
  const { interestCount, interestIds, isInterested, toggleInterest } = useInterests();
  const navigate = useNavigate();

  const openJob = (job: JobMatch) => {
    setSelectedJob(job);
    setEvidenceLoading(true);
    void refreshJobMatchEvidence(job.id)
      .then((detail) => {
        setSelectedJob(detail);
        setJobs((current) => current.map((item) => item.id === detail.id ? { ...item, score: detail.score, comment: detail.comment, recommendationLevel: detail.recommendationLevel } : item));
      })
      .catch(() => setSelectedJob(job))
      .finally(() => setEvidenceLoading(false));
  };

  const recalculate = async () => {
    setRecalculating(true);
    try {
      await recalculateJobMatches();
      const refreshed = await getJobMatches();
      setJobs(refreshed);
      setPage(0);
      setStatus("ready");
    } catch {
      setStatus("error");
    } finally {
      setRecalculating(false);
    }
  };

  useEffect(() => {
    void getJobMatches()
      .then((data) => { setJobs(data); setStatus("ready"); })
      .catch(() => { setJobs([]); setStatus("error"); });
  }, []);

  const applyNowCount = jobs.filter((job) => job.recommendationLevel === "APPLY_NOW").length;
  const challengeCount = jobs.filter((job) => job.recommendationLevel === "CHALLENGE_AFTER_GAPS").length;
  const filteredJobs = useMemo(() => {
    if (activeTab === "APPLY_NOW" || activeTab === "CHALLENGE_AFTER_GAPS") return jobs.filter((job) => job.recommendationLevel === activeTab);
    if (activeTab === "INTERESTED") return jobs.filter((job) => interestIds.includes(job.id));
    return jobs;
  }, [activeTab, interestIds, jobs]);
  const pageCount = Math.max(1, Math.ceil(filteredJobs.length / PAGE_SIZE));
  const visibleJobs = filteredJobs.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const selectTab = (tab: DashboardTab) => { setActiveTab(tab); setPage(0); };
  const copy = tabCopy[activeTab];

  return <>
    <PageHeading
      eyebrow="TODAY'S CAREER ACTION"
      title="내 준비도에 맞는 채용공고를 확인하세요"
      body="회원 스펙과 공고 요구사항을 비교한 결과를 바탕으로, 지금 할 수 있는 지원과 보완할 항목을 확인합니다."
      action={<div style={{ display: "flex", gap: "10px" }}><button className="outline-button" onClick={() => navigate("/profile")}><Target size={17} />역량 프로필 관리</button></div>}
    />

    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && jobs.length === 0 && <DataStatePanel state="empty" emptyTitle="표시할 매칭 결과가 없습니다" emptyBody="회원 스펙과 채용공고 요구사항 분석이 완료되면 대시보드가 구성됩니다." />}

    {status === "ready" && jobs.length > 0 && <>
      <div className="dashboard-match-refresh">
        <span>스펙을 수정했거나 최신 기준으로 점수를 확인하려면 매칭을 다시 계산하세요.</span>
        <button type="button" className="outline-button" onClick={() => void recalculate()} disabled={recalculating}>
          {recalculating ? "매칭 분석 중..." : "매칭 다시 분석"}
        </button>
      </div>
      <div className="metric-grid dashboard-metric-grid">
        <MetricCard icon={<BriefcaseBusiness />} label="분석된 공고" value={String(jobs.length)} hint="지원 어려움 공고까지 전체 보기" tone="blue" onClick={() => selectTab("ALL")} />
        <MetricCard icon={<Target />} label="지금 지원 가능" value={String(applyNowCount)} hint="필수요건 충족 공고" tone="green" onClick={() => selectTab("APPLY_NOW")} />
        <MetricCard icon={<BriefcaseBusiness />} label="보완 후 도전" value={String(challengeCount)} hint="부족 요건이 적은 공고" tone="orange" onClick={() => selectTab("CHALLENGE_AFTER_GAPS")} />
        <MetricCard icon={<Bookmark />} label="관심 목록" value={String(interestCount)} hint="저장한 분석 공고" tone="purple" onClick={() => selectTab("INTERESTED")} />
      </div>
      <section className="panel recommended-panel dashboard-match-panel">
        <PanelTitle title={copy.title} subtitle={copy.subtitle} action={<button className="text-button" onClick={() => navigate(`/jobs${activeTab === "ALL" || activeTab === "INTERESTED" ? "" : `?level=${activeTab}`}`)}>매칭 공고 전체 보기 <ChevronRight size={15} /></button>} />
        <div className="dashboard-match-tabs" role="tablist" aria-label="매칭 공고 분류">
          {(Object.keys(tabCopy) as DashboardTab[]).map((tab) => <button key={tab} type="button" role="tab" aria-selected={activeTab === tab} className={activeTab === tab ? "active" : ""} onClick={() => selectTab(tab)}>{tabCopy[tab].label}<small>{tab === "ALL" ? jobs.length : tab === "APPLY_NOW" ? applyNowCount : tab === "CHALLENGE_AFTER_GAPS" ? challengeCount : interestCount}</small></button>)}
        </div>
        {filteredJobs.length === 0 ? <DataStatePanel state="empty" emptyTitle={copy.emptyTitle} emptyBody={copy.emptyBody} /> : <>
          <div className="dashboard-match-list job-list">{visibleJobs.map((job) => <CompactJobCard key={job.id} job={job} interested={isInterested(job.id)} onOpen={() => openJob(job)} onInterest={() => void toggleInterest(job.id)} />)}</div>
          {filteredJobs.length > PAGE_SIZE && <nav className="dashboard-pagination" aria-label="매칭 공고 페이지"><button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}><ChevronLeft size={16} />이전</button><span><b>{page + 1}</b> / {pageCount} · {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, filteredJobs.length)} / {filteredJobs.length}</span><button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((current) => current + 1)}>다음<ChevronRight size={16} /></button></nav>}
        </>}
      </section>
    </>}
    {selectedJob && <JobMatchDrawer job={selectedJob} evidenceLoading={evidenceLoading} interested={isInterested(selectedJob.id)} onInterest={() => void toggleInterest(selectedJob.id)} onClose={() => setSelectedJob(null)} />}
  </>;
}
