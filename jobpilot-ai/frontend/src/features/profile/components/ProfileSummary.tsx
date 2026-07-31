import { ChevronRight, Code2, Github } from "lucide-react";
import type { MemberProfile } from "../model/profile.types";

export function ProfileSummary({ profile }: { profile: MemberProfile }) {
  return <section className="panel profile-summary"><div className="profile-hero"><div className="large-avatar">김</div><div><span>희망 직무</span><h2>{profile.targetRole}</h2><p>{profile.conditions}</p></div><button className="outline-button">수정</button></div><div className="profile-section"><span className="section-label">직접 연결 가능한 기술</span><div className="skill-evidence">{profile.skills.map((item) => <span key={item.skill} className="skill-evidence-item"><strong>{item.skill}</strong><small>{item.evidence}</small></span>)}</div></div><div className="profile-section"><span className="section-label">연결된 프로젝트</span><article className="project-evidence"><div className="project-icon"><Code2 size={20} /></div><div><h3>{profile.project.title}</h3><p>{profile.project.description}</p><a href="#github"><Github size={15} />{profile.project.githubUrl}</a></div><ChevronRight size={19} /></article></div></section>;
}
