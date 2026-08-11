import { X } from "lucide-react";
import { useEffect, useState } from "react";
import { getQnetQualificationDetail, type QnetQualification, type QnetQualificationDetail } from "../api/memberCertificatesApi";

type Props = { item: QnetQualification; onClose: () => void };

// YYYYMMDD -> "2026.02.09". 빈 값이거나 형식이 다르면 원본 그대로 돌려줘서 데이터 자체가
// 없는 회차(예: 실기시험 없이 필기만 있는 경우)는 "-"로 보이게 한다.
function formatDate(value: string): string {
  if (!/^\d{8}$/.test(value)) return "";
  return `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6, 8)}`;
}

function dateRange(start: string, end: string): string {
  const from = formatDate(start);
  const to = formatDate(end);
  if (!from && !to) return "-";
  if (from && to && from !== to) return `${from} ~ ${to}`;
  return from || to;
}

export function CertificateDetailModal({ item, onClose }: Props) {
  const [detail, setDetail] = useState<QnetQualificationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    setLoading(true); setError(""); setDetail(null);
    void getQnetQualificationDetail(item.code)
      .then(setDetail)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "상세정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [item.code]);

  return <div className="profile-modal-backdrop" role="presentation" onMouseDown={onClose}>
    <section className="profile-select-modal certificate-detail-modal" role="dialog" aria-modal="true" aria-labelledby="certificate-detail-title" onMouseDown={(event) => event.stopPropagation()}>
      <header>
        <div><span className="eyebrow">Q-NET 국가자격 상세정보</span><h3 id="certificate-detail-title">{item.name}</h3>
          <p>{[item.qualificationType, item.field, item.subField].filter(Boolean).join(" · ")}</p>
        </div>
        <button type="button" className="modal-close" onClick={onClose} aria-label="닫기"><X size={18} /></button>
      </header>

      {loading && <p className="education-search-hint">시험일정을 불러오는 중입니다...</p>}
      {error && <p className="education-search-error">{error}</p>}

      {detail && !loading && !error && <div className="certificate-detail-body">
        <div className="certificate-detail-fee"><strong>응시 수수료</strong><span>{detail.fee || "정보 없음"}</span></div>
        {detail.rounds.length === 0
          ? <p className="skill-empty">올해 시행 예정인 회차 정보를 찾지 못했습니다. Q-Net 원서접수 페이지에서 최신 일정을 확인해 주세요.</p>
          : <div className="certificate-round-list">
              {detail.rounds.map((round, index) => <div className="certificate-round-card" key={`${round.roundName}-${index}`}>
                <strong>{round.roundName || `${index + 1}회차`}</strong>
                <dl>
                  <div><dt>필기시험</dt><dd>{dateRange(round.writtenExamStart, round.writtenExamEnd)}</dd></div>
                  <div><dt>필기 합격자 발표</dt><dd>{formatDate(round.writtenResultDate) || "-"}</dd></div>
                  <div><dt>실기시험</dt><dd>{dateRange(round.practicalExamStart, round.practicalExamEnd)}</dd></div>
                  <div><dt>최종 합격자 발표</dt><dd>{dateRange(round.finalResultStart, round.finalResultEnd)}</dd></div>
                </dl>
              </div>)}
            </div>}
        <p className="form-hint">한국산업인력공단(Q-Net) 공공데이터포털 오픈API 기준이며, 실제 접수 가능 여부는 변경될 수 있습니다.</p>
      </div>}
    </section>
  </div>;
}
