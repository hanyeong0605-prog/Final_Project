import { deleteJsonReturning, getJson, postJson, putJson } from "../../../api/httpClient";
import type { MemberCertificate, MemberCertificateInput } from "../model/memberCertificate.types";

export type QnetQualification = {
  code: string;
  name: string;
  qualificationType: string;
  field: string;
  subField: string;
};

// 2026-08-11: 종목코드(QnetQualification.code = jmcd)로 조회하는 상세정보 - 올해
// 시행 회차별 필기/실기 시험일정 + 응시 수수료. 날짜는 Q-Net 원본 그대로 "20140209"
// 같은 YYYYMMDD 문자열로 온다 (없는 회차면 빈 문자열).
export type QnetExamRound = {
  roundName: string;
  writtenExamStart: string;
  writtenExamEnd: string;
  writtenResultDate: string;
  practicalExamStart: string;
  practicalExamEnd: string;
  finalResultStart: string;
  finalResultEnd: string;
};

export type QnetQualificationDetail = {
  code: string;
  name: string;
  fee: string;
  rounds: QnetExamRound[];
};

export function getMemberCertificates(): Promise<MemberCertificate[]> {
  return getJson<MemberCertificate[]>("/api/v1/members/me/certificates");
}

export function saveMemberCertificates(input: MemberCertificateInput[]): Promise<MemberCertificate[]> {
  return putJson<MemberCertificate[]>("/api/v1/members/me/certificates", input);
}

export function searchQnetQualifications(query: string): Promise<QnetQualification[]> {
  return getJson<QnetQualification[]>(`/api/v1/certifications/catalog?query=${encodeURIComponent(query)}`);
}

export function getQnetQualificationDetail(jmcd: string): Promise<QnetQualificationDetail> {
  return getJson<QnetQualificationDetail>(`/api/v1/certifications/catalog/${encodeURIComponent(jmcd)}/detail`);
}

// 2026-08-11: "성장 기회 추천" 페이지 - 찜한 자격증 목록/추가/삭제, 목표 직무분야 기준
// 자동 추천. 셋 다 로그인 회원 전용(백엔드 SecurityConfig에서 permitAll 안 됨).
export function getCertificateBookmarks(): Promise<QnetQualification[]> {
  return getJson<QnetQualification[]>("/api/v1/certifications/bookmarks");
}

export function addCertificateBookmark(item: QnetQualification): Promise<QnetQualification[]> {
  return postJson<QnetQualification[]>("/api/v1/certifications/bookmarks", {
    jmcd: item.code, name: item.name, qualificationType: item.qualificationType, field: item.field, subField: item.subField,
  });
}

export function removeCertificateBookmark(jmcd: string): Promise<QnetQualification[]> {
  return deleteJsonReturning<QnetQualification[]>(`/api/v1/certifications/bookmarks/${encodeURIComponent(jmcd)}`);
}

export function getRecommendedCertificates(): Promise<QnetQualification[]> {
  return getJson<QnetQualification[]>("/api/v1/certifications/recommended");
}

// 2026-08-11: "성장 기회 추천" 페이지 - 검색 없이 전체 자격증 목록을 이름순으로 훑어보기.
// field를 넘기면 백엔드가 NCS 직무분야(예: "정보통신")가 정확히 일치하는 종목만 걸러서
// 내려준다(비우면 전체).
export type QnetQualificationPage = { items: QnetQualification[]; hasMore: boolean };
export function listQnetQualifications(page: number, size = 24, field = ""): Promise<QnetQualificationPage> {
  return getJson<QnetQualificationPage>(`/api/v1/certifications/catalog/list?page=${page}&size=${size}${field ? `&field=${encodeURIComponent(field)}` : ""}`);
}

// 2026-08-11: "전체 자격증 목록" 위 분야별 필터 버튼용 - 카탈로그에 실제 존재하는
// 분야 목록 + 건수. 하드코딩 없이 이 목록으로 버튼을 만든다.
export type QnetFieldCount = { field: string; count: number };
export function getQnetFields(): Promise<QnetFieldCount[]> {
  return getJson<QnetFieldCount[]>("/api/v1/certifications/catalog/fields");
}
