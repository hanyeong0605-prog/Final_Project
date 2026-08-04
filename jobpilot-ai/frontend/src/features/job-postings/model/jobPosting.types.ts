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

export interface JobPostingDetail extends JobPosting {
  sourceProvider: string;
  companyUrl: string | null;
  companyLogoUrl: string | null;
  description: string | null;
  entryLevel: boolean | null;
  industryName: string | null;
  jobMidName: string | null;
  locations?: JobPostingLocation[];
  imageUrls: string[];
}

export interface JobPostingLocation {
  locationText: string | null;
  sido: string | null;
  sigungu: string | null;
  detailedAddress: string | null;
  latitude: number | null;
  longitude: number | null;
  primaryLocation: boolean;
}
