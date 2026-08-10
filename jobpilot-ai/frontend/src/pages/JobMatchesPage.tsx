import { useEffect, useState } from "react";
import { ChevronRight } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { useInterests } from "../features/interests/model/InterestContext";
import { getJobMatchDetail, getJobMatches } from "../features/jobs/api/jobMatchesApi";
import { JobCard } from "../features/jobs/components/JobCard";
import { JobGradeFilter } from "../features/jobs/components/JobGradeFilter";
import { JobMatchDrawer } from "../features/jobs/components/JobMatchDrawer";
import type { JobMatch, RecommendationLevel } from "../features/jobs/model/job.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

type LoadStatus = "loading" | "ready" | "error";

export function JobMatchesPage() {
  const [searchParams] = useSearchParams();
  const requestedLevel = searchParams.get("level");
  const initialGrade: RecommendationLevel | "ALL" = requestedLevel === "APPLY_NOW" || requestedLevel === "CHALLENGE_AFTER_GAPS" || requestedLevel === "DIFFICULT_NOW" ? requestedLevel : "ALL";
  const [grade, setGrade] = useState<RecommendationLevel | "ALL">(initialGrade);
  const [jobs, setJobs] = useState<JobMatch[]>([]);
  const [status, setStatus] = useState<LoadStatus>("loading");
  const [selectedJob, setSelectedJob] = useState<JobMatch | null>(null);
  const { isInterested, toggleInterest } = useInterests();

  const openJob = (job: JobMatch) => {
    void getJobMatchDetail(job.id).then(setSelectedJob).catch(() => setSelectedJob(job));
  };

  useEffect(() => { setGrade(initialGrade); }, [initialGrade]);

  useEffect(() => {
    setStatus("loading");
    void getJobMatches(grade)
      .then((data) => { setJobs(data); setStatus("ready"); })
      .catch(() => { setJobs([]); setStatus("error"); });
  }, [grade]);

  return <>
    <PageHeading eyebrow="PERSONALIZED JOB MATCH" title="내 근거로 비교한 사람인 IT 채용공고" body="사람인 공고의 상세 자격요건을 회원의 스펙·프로젝트·자격증·자기소개서 근거와 비교합니다." />
    <JobGradeFilter grade={grade} onGradeChange={setGrade} />
    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && jobs.length === 0 && <DataStatePanel state="empty" emptyTitle="분석된 채용공고가 없습니다" emptyBody="사람인 공고 수집과 회원별 매칭 분석이 완료되면 표시됩니다." />}
    {status === "ready" && jobs.length > 0 && <>
      <div className="list-heading"><strong>{jobs.length}개의 공고</strong><span>마감 임박순 <ChevronRight size={14} /></span></div>
      <section className="job-card-grid">{jobs.map((job) => <JobCard key={job.id} job={job} interested={isInterested(job.id)} onOpen={() => openJob(job)} onInterest={() => void toggleInterest(job.id)} />)}</section>
    </>}
    {selectedJob && <JobMatchDrawer job={selectedJob} interested={isInterested(selectedJob.id)} onInterest={() => void toggleInterest(selectedJob.id)} onClose={() => setSelectedJob(null)} />}
  </>;
}
