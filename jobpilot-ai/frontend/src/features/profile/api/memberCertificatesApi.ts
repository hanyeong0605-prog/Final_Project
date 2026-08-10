import { getJson, putJson } from "../../../api/httpClient";
import type { MemberCertificate, MemberCertificateInput } from "../model/memberCertificate.types";

export type QnetQualification = {
  code: string;
  name: string;
  qualificationType: string;
  field: string;
  subField: string;
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
