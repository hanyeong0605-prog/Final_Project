import { X } from "lucide-react";
import { useEffect, useState } from "react";
import { generateCertificateStudyPlan, type CertificateStudyPlanResult, type StudyPlanProfileInput } from "../api/certificateStudyPlanApi";
import type { QnetQualification } from "../../profile/api/memberCertificatesApi";

type Props = { item: QnetQualification; profile: StudyPlanProfileInput; onClose: () => void };

// 2026-08-11: CertificateDetailModal.tsx와 같은 구조(모달 열리자마자 useEffect로 조회) -
// 다만 이건 Q-Net 시험일정이 아니라 ai-server(Gemini)가 실시간 생성하는 학습 계획이라
// 로딩이 좀 더 걸릴 수 있다는 안내 문구를 넣었다.
export function CertificateStudyPlanModal({ item, profile, onClose }: Props) {
  const [plan, setPlan] = useState<CertificateStudyPlanResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    setLoading(true); setError(""); setPlan(null);
    void generateCertificateStudyPlan(item.name, item.qualificationType, item.field, item.subField, profile)
      .then(setPlan)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "학습 계획을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [item.code, item.name, item.qualificationType, item.field, item.subField, profile]);

  return <div className="profile-modal-backdrop" role="presentation" onMouseDown={onClose}>
    <section className="profile-select-modal certificate-detail-modal" role="dialog" aria-modal="true" aria-labelledby="certificate-study-plan-title" onMouseDown={(event) => event.stopPropagation()}>
      <header>
        <div><span className="eyebrow">AI 맞춤 학습 계획</span><h3 id="certificate-study-plan-title">{item.name}</h3>
          <p>{[item.qualificationType, item.field, item.subField].filter(Boolean).join(" · ")}</p>
        </div>
        <button type="button" className="modal-close" onClick={onClose} aria-label="닫기"><X size={18} /></button>
      </header>

      {loading && <p className="education-search-hint">보유 스펙을 참고해서 학습 계획을 만드는 중입니다...</p>}
      {error && <p className="education-search-error">{error}</p>}

      {plan && !loading && !error && (plan.ok ? <div className="certificate-detail-body study-plan-body">
        {plan.study_weeks !== null && <div className="certificate-detail-fee"><strong>예상 학습 기간</strong><span>약 {plan.study_weeks}주</span></div>}
        {plan.focus_areas.length > 0 && <div className="study-plan-section">
          <strong>우선 학습 영역</strong>
          <ul>{plan.focus_areas.map((text, index) => <li key={index}>{text}</li>)}</ul>
        </div>}
        {plan.weekly_plan.length > 0 && <div className="study-plan-section">
          <strong>주차별 계획</strong>
          <ul>{plan.weekly_plan.map((text, index) => <li key={index}>{text}</li>)}</ul>
        </div>}
        {plan.study_tips.length > 0 && <div className="study-plan-section">
          <strong>학습 팁</strong>
          <ul>{plan.study_tips.map((text, index) => <li key={index}>{text}</li>)}</ul>
        </div>}
        <p className="form-hint">AI가 등록된 스펙을 참고해 생성한 참고용 계획입니다. 실제 출제 범위는 Q-Net 공식 출제기준을 확인해 주세요.</p>
      </div> : <p className="skill-empty">{plan.message ?? "학습 계획을 만들지 못했습니다."}</p>)}
    </section>
  </div>;
}
