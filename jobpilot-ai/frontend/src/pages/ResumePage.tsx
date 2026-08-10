import { useEffect, useState } from "react";
import { getCareerProfile } from "../features/profile/api/careerProfileApi";
import { ProjectSection } from "../features/resume/components/ProjectSection";
import { SelfIntroductionSection } from "../features/resume/components/SelfIntroductionSection";
import { PageHeading } from "../shared/components/PageHeading";

// 2026-08-10: 이력서 작성 도우미 페이지 - 로그인 회원 전용(router.tsx에서 이 페이지가 속한
// "/" 하위 트리 전체가 RequireAuth로 이미 감싸여 있어서 비회원은 애초에 여기 못 옴).
// 목표직무/기술요약(CareerProfile)을 마운트 시 한 번 읽어와서 두 섹션에 컨텍스트로
// 내려준다("불러오기" 방향 - 태스크 #63) - 질문식 작성/첨삭 요청마다 이미 아는 정보를
// 다시 묻지 않고 그 위에 구체화된 질문/평가를 받을 수 있다.
export function ResumePage() {
  const [tab, setTab] = useState<"self-intro" | "project">("self-intro");
  const [job, setJob] = useState("");
  const [techSummary, setTechSummary] = useState("");

  useEffect(() => {
    void getCareerProfile()
      .then((profile) => {
        if (!profile) return;
        setJob(profile.targetRole ?? "");
        setTechSummary(profile.technicalSummary ?? "");
      })
      .catch(() => {
        // 프로필 조회 실패(미입력 등)해도 이력서 작성 자체는 컨텍스트 없이 그대로 가능하다.
      });
  }, []);

  return (
    <>
      <PageHeading
        eyebrow="RESUME BUILDER"
        title="이력서 작성 도우미"
        body="질문에 답하면 AI가 자기소개서·프로젝트 설명을 다듬어주고, 이미 쓴 글은 첨삭받을 수 있습니다. 저장한 내용은 모의면접 맞춤 질문에도 함께 활용됩니다."
      />

      <div className="form-actions" style={{ marginBottom: 20 }}>
        <button className={tab === "self-intro" ? "primary-button" : "outline-button"} onClick={() => setTab("self-intro")}>
          자기소개서
        </button>
        <button className={tab === "project" ? "primary-button" : "outline-button"} onClick={() => setTab("project")}>
          프로젝트 경험
        </button>
      </div>

      <section className="panel profile-form-panel">
        {tab === "self-intro" ? (
          <SelfIntroductionSection job={job} techSummary={techSummary} />
        ) : (
          <ProjectSection job={job} techSummary={techSummary} />
        )}
      </section>
    </>
  );
}
