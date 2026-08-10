import { getJson, putJson } from "../../../api/httpClient";
import type { MemberCertificate, MemberCertificateInput } from "../model/memberCertificate.types";

export function getMemberCertificates(): Promise<MemberCertificate[]> {
  return getJson<MemberCertificate[]>("/api/v1/members/me/certificates");
}

export function saveMemberCertificates(input: MemberCertificateInput[]): Promise<MemberCertificate[]> {
  return putJson<MemberCertificate[]>("/api/v1/members/me/certificates", input);
}
