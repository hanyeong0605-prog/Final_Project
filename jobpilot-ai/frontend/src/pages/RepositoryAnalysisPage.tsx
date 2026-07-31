import { useState } from "react";
import {
  Braces,
  Check,
  ChevronRight,
  Code2,
  ExternalLink,
  FileCode2,
  GitBranch,
  Link2,
  LoaderCircle,
  Network,
  Sparkles,
} from "lucide-react";
import { analyzeGitHubRepository } from "../features/project-analysis/api/projectAnalysisApi";
import type { CoreFile, ImplementationStory, ProjectAnalysis } from "../features/project-analysis/model/projectAnalysis.types";
import { PageHeading } from "../shared/components/PageHeading";

const roleLabels: Record<string, string> = {
  "API Controller": "API 컨트롤러",
  "Business Service": "비즈니스 서비스",
  "Data Access": "데이터 접근",
  "Domain Model": "도메인 모델",
  "Application Entry Point": "애플리케이션 진입점",
  "External API Client": "외부 API 클라이언트",
  "Authentication / Security": "인증·권한 처리",
  Screen: "화면",
  "UI Component": "UI 컴포넌트",
  "Application Code": "애플리케이션 코드",
};

function roleLabel(role: string) {
  return roleLabels[role] ?? role;
}

function ImplementationToggle({
  implementation,
  coreFiles,
  selected,
  onToggle,
}: {
  implementation: ImplementationStory;
  coreFiles: CoreFile[];
  selected: boolean;
  onToggle: () => void;
}) {
  return (
    <article className={selected ? "analysis-implementation selected" : "analysis-implementation"}>
      <button onClick={onToggle} type="button">
        <span className="feature-check">{selected && <Check size={13} />}</span>
        <span className="analysis-feature-copy">
          <strong>{implementation.title}</strong>
          <small>{implementation.description}</small>
        </span>
        <ChevronRight size={16} />
      </button>
      <p className="analysis-implementation-mechanism"><b>구현 방식</b>{implementation.mechanism}</p>
      {implementation.technologies.length > 0 && (
        <div className="analysis-implementation-techs">
          {implementation.technologies.map((technology) => <span key={technology}>{technology}</span>)}
        </div>
      )}
      <div className="analysis-implementation-evidence">
        {implementation.evidence.map((evidence) => {
          const file = coreFiles.find((coreFile) => coreFile.path === evidence.path);
          return (
            <article key={evidence.path + evidence.symbol}>
              <strong><FileCode2 size={12} /> {evidence.path} <em>{evidence.symbol}</em></strong>
              <p>{evidence.description}</p>
              {file && <pre>{file.excerpt}</pre>}
            </article>
          );
        })}
      </div>
    </article>
  );
}

export function RepositoryAnalysisPage() {
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [analysis, setAnalysis] = useState<ProjectAnalysis | null>(null);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      const result = await analyzeGitHubRepository(repositoryUrl.trim());
      if (!result) throw new Error("분석 API에 연결할 수 없습니다. VITE_API_BASE_URL을 설정하고 Spring 서버를 실행해 주세요.");
      setAnalysis(result);
      setSelectedIds((result.implementations ?? []).slice(0, 3).map((implementation) => implementation.id));
    } catch (requestError) {
      setAnalysis(null);
      setSelectedIds([]);
      setError(requestError instanceof Error ? requestError.message : "저장소 분석을 완료하지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  const toggleImplementation = (id: string) => {
    setSelectedIds((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  };

  const implementations = analysis?.implementations ?? [];
  const selectedImplementations = implementations.filter((implementation) => selectedIds.includes(implementation.id));
  const profile = analysis?.projectProfile;

  return (
    <>
      <PageHeading
        eyebrow="저장소에서 발표까지"
        title="GitHub 코드 분석 미리보기"
        body="저장소의 실제 코드와 설정을 바탕으로 프로젝트가 무엇을 구현했는지 설명합니다. 코드는 실행하지 않습니다."
      />

      <section className="analysis-intake">
        <div className="analysis-intake-icon"><Code2 size={22} /></div>
        <div>
          <strong>GitHub 저장소 분석</strong>
          <p>README가 없어도 코드, 의존성 설정, API 호출, 데이터 접근 구조를 읽어 프로젝트와 구현 내용을 정리합니다.</p>
        </div>
        <form onSubmit={submit}>
          <input
            aria-label="GitHub 저장소 URL"
            onChange={(event) => setRepositoryUrl(event.target.value)}
            placeholder="https://github.com/owner/repository"
            required
            type="url"
            value={repositoryUrl}
          />
          <button className="primary-button" disabled={isLoading} type="submit">
            {isLoading ? <LoaderCircle className="spin" size={16} /> : <Sparkles size={16} />}
            {isLoading ? "분석 중" : "저장소 분석"}
          </button>
        </form>
      </section>

      {error && <div className="analysis-error">{error}</div>}

      {!analysis && !isLoading && !error && (
        <section className="analysis-empty">
          <GitBranch size={28} />
          <strong>저장소 URL을 입력하면 코드 기반 프로젝트 설명이 생성됩니다.</strong>
          <span>프로젝트의 목적, 사용 기술, 확인된 구현, 구현 근거 코드를 함께 보여줍니다.</span>
        </section>
      )}

      {analysis && (
        <>
          <section className="analysis-summary">
            <div>
              <span className="mini-label">
                {analysis.summarySource === "GEMINI"
                  ? "AI 작성 · 코드 근거 기반"
                  : analysis.summarySource === "GEMINI_FALLBACK"
                    ? "AI 응답 미반영 · 정적 코드 근거"
                    : "정적 코드 근거 미리보기"}
              </span>
              <div className="analysis-repo-heading">
                <h2>{analysis.repository.fullName}</h2>
                <a href={analysis.repository.htmlUrl} rel="noreferrer" target="_blank"><ExternalLink size={14} /> 저장소 열기</a>
              </div>
              <p>{analysis.overview}</p>
              <div className="analysis-techs">
                {analysis.technologyStack.slice(0, 7).map((technology) => <span key={technology.name}>{technology.name}</span>)}
              </div>
            </div>
            <div className="analysis-summary-meta">
              <span><GitBranch size={14} /> {analysis.repository.defaultBranch}</span>
              <span><FileCode2 size={14} /> 분석 파일 {analysis.metrics.analyzedFiles}개</span>
              <span><Braces size={14} /> 소스 파일 {analysis.metrics.sourceFiles}개</span>
            </div>
          </section>

          {profile && (
            <section className="analysis-understanding analysis-understanding-single">
              <article className="analysis-project-profile">
                <div className="analysis-section-label"><Network size={16} /> 이 프로젝트는 무엇인가</div>
                <h2>{profile.classification}</h2>
                <p>{profile.summary}</p>
                <div className="analysis-evidence-list">
                  {profile.evidence.map((path) => <span key={path}><FileCode2 size={12} /> {path}</span>)}
                </div>
              </article>
            </section>
          )}

          <section className="panel analysis-integrations">
            <div className="panel-title">
              <div><h2>사용 기술과 연결 환경</h2><p>의존성 설정과 실제 HTTP·SDK·인증·메시지 코드 신호가 있을 때만 표시합니다.</p></div>
              <span className="analysis-count">{analysis.integrations?.length ?? 0}개 연결</span>
            </div>
            {analysis.integrations?.length ? (
              <div className="analysis-integration-list">
                {analysis.integrations.map((integration) => (
                  <article className="analysis-integration" key={integration.name + "-" + integration.direction}>
                    <Link2 size={17} />
                    <div><strong>{integration.name}</strong><p>{integration.description}</p></div>
                    <span>{integration.evidence.join(", ")}</span>
                  </article>
                ))}
              </div>
            ) : (
              <p className="analysis-muted">분석한 코드와 설정 파일에서는 별도 HTTP API, AI SDK, 인증, 메시지 큐, 예약 작업 연결 신호가 확인되지 않았습니다.</p>
            )}
          </section>

          {analysis.codeFlows?.length > 0 && (
            <section className="panel analysis-flows">
              <div className="panel-title">
                <div><h2>코드에서 확인된 주요 흐름</h2><p>파일 간 연결이 실제 코드에서 확인된 경우에만 표시합니다.</p></div>
                <span className="analysis-count">{analysis.codeFlows.length}개 흐름</span>
              </div>
              <div className="analysis-flow-list">
                {analysis.codeFlows.map((flow) => (
                  <article className="analysis-flow" key={flow.title}>
                    <Network size={17} />
                    <div><strong>{flow.title}</strong><p>{flow.description}</p></div>
                    <span>{flow.evidence.join(" → ")}</span>
                  </article>
                ))}
              </div>
            </section>
          )}

          <div className="analysis-workspace">
            <section className="panel analysis-features-panel">
              <div className="panel-title">
                <div><h2>코드에서 확인된 구현</h2><p>무엇을 구현했는지와 구현 방식을 설명하고, 바로 아래에 근거 코드 일부를 표시합니다.</p></div>
                <span className="analysis-count">{selectedIds.length}개 선택</span>
              </div>
              <div className="analysis-feature-list">
                {implementations.map((implementation) => (
                  <ImplementationToggle
                    coreFiles={analysis.coreFiles}
                    implementation={implementation}
                    key={implementation.id}
                    onToggle={() => toggleImplementation(implementation.id)}
                    selected={selectedIds.includes(implementation.id)}
                  />
                ))}
              </div>
            </section>

            <aside className="analysis-brief">
              <span className="mini-label">발표에 포함할 구현</span>
              <h2>{selectedImplementations.length ? "선택한 구현의 설명을 발표 흐름으로 사용합니다." : "발표에 포함할 구현을 선택해 주세요."}</h2>
              <p>{analysis.aiNarrative}</p>
              <div className="analysis-selected-list">
                {selectedImplementations.map((implementation) => <span key={implementation.id}><Check size={12} /> {implementation.title}</span>)}
              </div>
              <small>
                {analysis.summarySource === "GEMINI"
                  ? "설명은 제공된 코드와 설정 근거에서만 작성되었습니다."
                  : analysis.summarySource === "GEMINI_FALLBACK"
                    ? "Gemini 호출은 완료되었지만 설명을 적용하지 못했습니다. 서버 로그에서 Gemini project-summary 메시지를 확인하세요."
                    : "정적 분석 결과입니다. Gemini를 켜고 다시 분석하면 코드 관계를 읽은 설명이 추가됩니다."}
              </small>
            </aside>
          </div>

          <section className="panel analysis-code-panel">
            <div className="panel-title">
              <div><h2>추가 코드 근거</h2><p>구현 카드에 포함된 코드 외에, 프로젝트 구조를 이해하는 데 사용된 소스 파일입니다.</p></div>
              <span className="analysis-count">{analysis.coreFiles.length}개 파일</span>
            </div>
            <div className="analysis-core-grid">
              {analysis.coreFiles.map((file) => (
                <article className="analysis-file" key={file.path}>
                  <div className="analysis-file-heading">
                    <span>{roleLabel(file.role)}</span>
                    <strong>{file.path}</strong>
                  </div>
                  <p className="analysis-file-symbols">{file.symbols.join(" · ")}</p>
                  <div className="analysis-file-reason"><b>코드에서 확인된 역할</b><span>{file.responsibility ?? "정적 코드 근거를 바탕으로 분류된 파일입니다."}</span></div>
                  <pre>{file.excerpt}</pre>
                </article>
              ))}
            </div>
          </section>

          {analysis.summarySource === "GEMINI_FALLBACK" && analysis.generatedOutput && (
            <section className="panel analysis-generated-output">
              <div className="panel-title">
                <div><h2>Gemini 생성 원문</h2><p>화면 적용에 실패한 AI 응답을 개발 확인용으로 표시합니다. 발표 자료에는 사용하지 마세요.</p></div>
              </div>
              <pre>{analysis.generatedOutput}</pre>
            </section>
          )}
        </>
      )}
    </>
  );
}
