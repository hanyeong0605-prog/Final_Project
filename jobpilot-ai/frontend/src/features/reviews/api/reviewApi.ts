import { deleteJson, getJson, postJson, putJson } from "../../../api/httpClient";
export interface ReviewCompany { id:number; name:string; description:string; industry?:string; location?:string; sourceType:string; reviewsEnabled:boolean }
export interface CompanyReview { id:number; companyId:number; jobPostingId?:number; displayAuthor:string; department:string; employmentStatus:"CURRENT"|"FORMER"; tenureMonths:number; sourceType:string; rating:number; title:string; pros:string; cons:string; body:string; managementMessage:string; analysisState:string; mine:boolean; createdAt:string; likeCount?:number }
export interface ReviewPage { content:CompanyReview[]; totalElements:number; totalPages:number; number:number }
export interface ReviewInput { jobPostingId:number; department:string; employmentStatus:"CURRENT"|"FORMER"; tenureMonths:number; rating:number; title:string; pros:string; cons:string; body:string; managementMessage:string }
export interface PostingReviewOverview { companyId:number; companyName:string; sourceType:string; summary:{reviewCount:number;averageRating:number;analyzedCount:number;positive:number;neutral:number;negative:number;dominantPolarity:string;topEmotions:{label:string;score:number}[]};reviews:CompanyReview[] }
const base="/api/v1/review-companies";
export const listCompanies=()=>getJson<ReviewCompany[]>(`${base}?page=0&size=100`);
export const listReviews=(id:number)=>getJson<ReviewPage>(`${base}/${id}/reviews?size=100`);
export const getAnalysis=(id:number)=>getJson<{available:boolean;analysis?:{modelVersion:string;polarity:{label:string;positive:number;neutral:number;negative:number};emotions:{label:string;score:number}[]}}>(`${base}/reviews/${id}/analysis`);
export const createReview=(id:number,input:ReviewInput)=>postJson<CompanyReview>(`${base}/${id}/reviews`,input);
export const updateReview=(id:number,input:ReviewInput)=>putJson<CompanyReview>(`${base}/reviews/${id}`,input);
export const deleteReview=(id:number)=>deleteJson(`${base}/reviews/${id}`);
export const likeReview=(id:number)=>postJson<{liked:boolean;count:number}>(`${base}/reviews/${id}/like`,{});
export const reportReview=(id:number,reason:string)=>postJson<void>(`${base}/reviews/${id}/report`,{reason});
export const postingOverview=(postingId:number)=>getJson<PostingReviewOverview>(`/api/v1/job-postings/${postingId}/reviews`);
