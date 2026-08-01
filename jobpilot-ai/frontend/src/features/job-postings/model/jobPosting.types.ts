export interface JobPosting {
  id: number;
  externalJobId: string;
  companyName: string | null;
  title: string;
  sourceUrl: string;
  location: string | null;
  employmentType: string | null;
  experienceType: string | null;
  jobName: string | null;
  salary: string | null;
  keywords: string | null;
  publishedAt: string | null;
  deadlineAt: string | null;
  rollingDeadline: boolean;
  status: string;
}
