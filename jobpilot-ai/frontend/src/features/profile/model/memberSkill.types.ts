export interface SkillCatalogItem {
  id: number;
  name: string;
  category: string;
  parentSkillId: number | null;
}

export type SkillLevel = "LEARNING" | "PROJECT" | "INTERNSHIP" | "PROFESSIONAL";

export interface MemberSkill {
  skillId: number;
  skillName: string;
  category: string;
  selfReportedLevel: SkillLevel;
  note: string | null;
}

export interface MemberSkillInput {
  skillId: number;
  selfReportedLevel: SkillLevel;
  note: string | null;
}
