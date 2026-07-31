export interface SkillEvidence { skill: string; evidence: string; }

export interface ProjectEvidence {
  title: string;
  description: string;
  githubUrl: string;
}

export interface MemberProfile {
  name: string;
  targetRole: string;
  conditions: string;
  skills: SkillEvidence[];
  project: ProjectEvidence;
}
