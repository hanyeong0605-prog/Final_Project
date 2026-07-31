import { useEffect, useState } from "react";
import { ChevronRight } from "lucide-react";
import { useInterests } from "../features/interests/model/InterestContext";
import { getJobMatches } from "../features/jobs/api/jobMatchesApi";
import { JobCard } from "../features/jobs/components/JobCard";
import { JobGradeFilter } from "../features/jobs/components/JobGradeFilter";
import { JobMatchDrawer } from "../features/jobs/components/JobMatchDrawer";
import type { JobMatch, MatchGrade } from "../features/jobs/model/job.types";
import { PageHeading } from "../shared/components/PageHeading";

export function JobMatchesPage() {
  const [grade, setGrade] = useState<MatchGrade | "ALL">("ALL");
  const [jobs, setJobs] = useState<JobMatch[]>([]);
  const [selectedJob, setSelectedJob] = useState<JobMatch | null>(null);
  const { isInterested, toggleInterest } = useInterests();

  useEffect(() => { void getJobMatches(grade).then(setJobs); }, [grade]);

  return <><PageHeading eyebrow="PERSONALIZED JOB MATCH" title="내 근거로 비교한 IT 채용공고" body="고용24·잡코리아 등 출처의 공고를 표준화하고, 회원의 프로젝트·기술·자격 근거와 비교합니다." /><JobGradeFilter grade={grade} onGradeChange={setGrade} /><div className="list-heading"><strong>{jobs.length}개의 공고</strong><span>마감 임박순 <ChevronRight size={14} /></span></div><section className="job-card-grid">{jobs.map((job) => <JobCard key={job.id} job={job} interested={isInterested(job.id)} onOpen={() => setSelectedJob(job)} onInterest={() => toggleInterest(job.id)} />)}</section>{selectedJob && <JobMatchDrawer job={selectedJob} interested={isInterested(selectedJob.id)} onInterest={() => toggleInterest(selectedJob.id)} onClose={() => setSelectedJob(null)} />}</>;
}
