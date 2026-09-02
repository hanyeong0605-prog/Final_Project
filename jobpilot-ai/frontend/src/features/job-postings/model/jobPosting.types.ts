export interface JobPosting {
  id: number;
  externalJobId: string;
  companyName: string | null;
  companyLogoUrl: string | null;
  thumbnailUrl: string | null;
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
  viewCount?: number;
  bookmarkCount?: number;
  hasFinancials: boolean;
}

export type JobPostingSort = "deadline_asc" | "deadline_desc" | "recent" | "popular";
export type JobExperienceFilter = "" | "ENTRY" | "EXPERIENCED";

export interface JobPostingSearchParams {
  query?: string;
  roles?: string[];
  experience?: JobExperienceFilter;
  location?: string;
  employmentType?: string;
  financialsOnly?: boolean;
  sort?: JobPostingSort;
  page?: number;
  size?: number;
}

export interface JobPostingPage {
  content: JobPosting[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: JobPostingSort;
}

export interface JobPostingDetail extends JobPosting {
  sourceProvider: string;
  companyUrl: string | null;
  description: string | null;
  qualifications?: string | null;
  preferredQualifications?: string | null;
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
