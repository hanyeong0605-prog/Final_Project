import { getEmployerJson } from "./employerHttpClient";import type { EmployerJobPostingPage } from "./employerJobPostingApi";
export type EmployerReview={id:number;jobPostingId:number;displayAuthor:string;rating:number;title:string;pros:string;cons:string;body:string;analysisState:string;createdAt:string};
export const postings=()=>getEmployerJson<EmployerJobPostingPage>("/api/v1/employer/job-postings?page=0&size=100");
export const reviews=(id:number)=>getEmployerJson<{content:EmployerReview[]}>(`/api/v1/employer/job-postings/${id}/reviews?size=100`);
export const summary=(id:number)=>getEmployerJson<{summary:Record<string,number>;topEmotions:{label:string;averageScore:number}[];sampleWarning:boolean}>(`/api/v1/employer/job-postings/${id}/reviews/summary`);
