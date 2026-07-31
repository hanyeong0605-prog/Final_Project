import { useEffect, useState } from "react";
import { Bookmark, BriefcaseBusiness, CalendarDays, ChevronRight, Target } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { ActionItem } from "../features/dashboard/components/ActionItem";
import { MetricCard } from "../features/dashboard/components/MetricCard";
import { useInterests } from "../features/interests/model/InterestContext";
import { getJobMatches } from "../features/jobs/api/jobMatchesApi";
import { CompactJobCard } from "../features/jobs/components/CompactJobCard";
import { JobMatchDrawer } from "../features/jobs/components/JobMatchDrawer";
import type { JobMatch } from "../features/jobs/model/job.types";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";

export function DashboardPage() {
  const [jobs, setJobs] = useState<JobMatch[]>([]);
  const [selectedJob, setSelectedJob] = useState<JobMatch | null>(null);
  const { interestCount, isInterested, toggleInterest } = useInterests();
  const navigate = useNavigate();

  useEffect(() => { void getJobMatches().then(setJobs); }, []);
  const recommended = jobs.filter((job) => job.grade !== "INSUFFICIENT_EVIDENCE");
  const priorityJob = recommended[0];

  return <>
    <PageHeading eyebrow="TODAY'S CAREER ACTION" title="김개발님, 지금 지원을 검토할 공고가 있어요." body="프로젝트·기술·자격 근거를 기준으로 공고를 비교했어요. 합격 예측이 아닌 지원 준비도를 보여드립니다." action={<button className="outline-button" onClick={() => navigate("/profile")}><Target size={17} />내 역량 근거 관리</button>} />
    <section className="strategy-banner"><div className="strategy-copy"><span className="mini-label">오늘의 우선 행동</span><h2>‘모노랩’ 공고에 맞춰<br />AWS 배포 근거를 한 줄 추가해 보세요.</h2><p>필수 기술은 충분히 연결되었습니다. 배포 URL과 운영 중 해결한 문제를 적으면 포트폴리오 설득력이 더 좋아집니다.</p><button onClick={() => priorityJob && setSelectedJob(priorityJob)}>근거 매트릭스 보기 <ChevronRight size={16} /></button></div><div className="strategy-meter"><div className="meter-title"><span>지원 준비도</span><strong>86<small>/100</small></strong></div><div className="meter-track"><span style={{ width: "86%" }} /></div><div className="meter-legend"><span><i className="dot success" />필수 4/4</span><span><i className="dot warning" />우대 1/2</span></div></div></section>
    <div className="metric-grid"><MetricCard icon={<BriefcaseBusiness />} label="지원 검토 공고" value={String(recommended.length)} hint="이번 주 마감 2건" tone="blue" /><MetricCard icon={<Target />} label="보완할 핵심 역량" value="3" hint="Docker · AWS · TypeScript" tone="orange" /><MetricCard icon={<Bookmark />} label="관심 목록" value={String(interestCount)} hint="일정이 플래너에 반영됨" tone="purple" /><MetricCard icon={<CalendarDays />} label="다가오는 마감" value="5일" hint="SQLD 원서 접수" tone="green" /></div>
    <div className="dashboard-grid"><section className="panel recommended-panel"><PanelTitle title="개별 추천 공고" subtitle="직접 근거가 있거나, 보완 후 지원 가능한 공고만 보여요." action={<button className="text-button" onClick={() => navigate("/jobs")}>전체 보기 <ChevronRight size={15} /></button>} /><div className="job-list">{recommended.map((job) => <CompactJobCard key={job.id} job={job} interested={isInterested(job.id)} onOpen={() => setSelectedJob(job)} onInterest={() => toggleInterest(job.id)} />)}</div></section><section className="panel action-panel"><PanelTitle title="다음 액션" subtitle="공고의 부족 항목을 행동으로 바꿔 보세요." /><div className="action-list"><ActionItem number="01" title="AWS EC2 배포 경험 정리" body="MealMate 프로젝트의 배포 URL과 구조를 포트폴리오에 연결" tag="모노랩 공고" /><ActionItem number="02" title="Docker 실습 프로젝트 만들기" body="Spring + React를 Docker Compose로 실행하고 README 작성" tag="풀스택 역량" /><ActionItem number="03" title="SQLD 접수 일정 확인" body="원서 마감 전 플래너에서 알림 시점을 확인" tag="자격증" /></div><button className="full-outline" onClick={() => navigate("/opportunities")}>보완 기회 모두 보기 <ChevronRight size={16} /></button></section></div>
    {selectedJob && <JobMatchDrawer job={selectedJob} interested={isInterested(selectedJob.id)} onInterest={() => toggleInterest(selectedJob.id)} onClose={() => setSelectedJob(null)} />}
  </>;
}
