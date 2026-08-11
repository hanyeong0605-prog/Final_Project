import { getJson, putJson } from "../../../api/httpClient";
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

// 2026-08-11: KCA 국가기술자격증(개인) 진위여부 확인. 무선설비/통신설비/전파전자/
// 정보통신 분야만 커버하므로 다른 분야 자격증은 항상 genuine:false에 가까운 결과가
// 나올 수 있다 - CertificateAuthenticityCheck.tsx의 안내 문구 참고.
export function checkCertificateAuthenticity(certificateNumber: string): Promise<boolean> {
  return getJson<{ genuine: boolean }>(`/api/v1/certifications/authenticity?no=${encodeURIComponent(certificateNumber)}`)
    .then((result) => result.genuine);
}
