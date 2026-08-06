import { getJson, putJson } from "../../../api/httpClient";
import type { MemberSkill, MemberSkillInput, SkillCatalogItem } from "../model/memberSkill.types";

export function searchSkillCatalog(query: string): Promise<SkillCatalogItem[]> {
  return getJson<SkillCatalogItem[]>(`/api/v1/skills?query=${encodeURIComponent(query)}&limit=12`);
}

export function getMemberSkills(): Promise<MemberSkill[]> {
  return getJson<MemberSkill[]>("/api/v1/members/me/skills");
}

export function saveMemberSkills(input: MemberSkillInput[]): Promise<MemberSkill[]> {
  return putJson<MemberSkill[]>("/api/v1/members/me/skills", input);
}
