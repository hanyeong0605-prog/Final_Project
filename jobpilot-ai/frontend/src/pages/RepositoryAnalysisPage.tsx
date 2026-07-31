import { useState } from "react";
import {
  Braces,
  Check,
  ChevronRight,
  Code2,
  ExternalLink,
  FileCode2,
  GitBranch,
  LoaderCircle,
  Sparkles,
} from "lucide-react";
import { analyzeGitHubRepository } from "../features/project-analysis/api/projectAnalysisApi";
import type { FeatureCandidate, ProjectAnalysis } from "../features/project-analysis/model/projectAnalysis.types";
import { PageHeading } from "../shared/components/PageHeading";

function FeatureToggle({
  candidate,
  selected,
  onToggle,
}: {
  candidate: FeatureCandidate;
  selected: boolean;
  onToggle: () => void;
}) {
  return (
    <button className={selected ? "analysis-feature selected" : "analysis-feature"} onClick={onToggle} type="button">
      <span className="feature-check">{selected && <Check size={13} />}</span>
      <span className="analysis-feature-copy">
        <strong>{candidate.title}</strong>
        <small>{candidate.description}</small>
        <em>{candidate.confidence === "HIGH" ? "strong evidence" : "supporting evidence"}</em>
      </span>
      <ChevronRight size={16} />
    </button>
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
      if (!result) {
        throw new Error("The analysis API is not connected. Set VITE_API_BASE_URL and start the Spring server.");
      }
      setAnalysis(result);
      setSelectedIds(result.featureCandidates.slice(0, 3).map((candidate) => candidate.id));
    } catch (requestError) {
      setAnalysis(null);
      setSelectedIds([]);
      setError(requestError instanceof Error ? requestError.message : "Repository analysis could not be completed.");
    } finally {
      setIsLoading(false);
    }
  };

  const toggleFeature = (id: string) => {
    setSelectedIds((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  };

  const selectedFeatures = analysis?.featureCandidates.filter((candidate) => selectedIds.includes(candidate.id)) ?? [];

  return (
    <>
      <PageHeading
        eyebrow="REPOSITORY TO PRESENTATION"
        title="GitHub code analysis preview"
        body="Inspect evidence files first, then choose which implementation features should become the core of a presentation."
      />

      <section className="analysis-intake">
        <div className="analysis-intake-icon"><Code2 size={22} /></div>
        <div>
          <strong>Public GitHub repository</strong>
          <p>GitHub tree, manifest files, and a capped set of high-signal source files are inspected. The repository is never executed.</p>
        </div>
        <form onSubmit={submit}>
          <input
            aria-label="GitHub repository URL"
            onChange={(event) => setRepositoryUrl(event.target.value)}
            placeholder="https://github.com/owner/repository"
            required
            type="url"
            value={repositoryUrl}
          />
          <button className="primary-button" disabled={isLoading} type="submit">
            {isLoading ? <LoaderCircle className="spin" size={16} /> : <Sparkles size={16} />}
            {isLoading ? "Analyzing" : "Analyze repository"}
          </button>
        </form>
      </section>

      {error && <div className="analysis-error">{error}</div>}

      {!analysis && !isLoading && !error && (
        <section className="analysis-empty">
          <GitBranch size={28} />
          <strong>Enter a repository URL to create a presentation-ready preview.</strong>
          <span>No results are fabricated when the backend connection is unavailable.</span>
        </section>
      )}

      {analysis && (
        <>
          <section className="analysis-summary">
            <div>
              <span className="mini-label">{analysis.summarySource === "GEMINI" ? "AI WRITTEN, EVIDENCE BOUNDED" : "STATIC EVIDENCE PREVIEW"}</span>
              <div className="analysis-repo-heading">
                <h2>{analysis.repository.fullName}</h2>
                <a href={analysis.repository.htmlUrl} rel="noreferrer" target="_blank"><ExternalLink size={14} /> Open repository</a>
              </div>
              <p>{analysis.overview}</p>
              <div className="analysis-techs">
                {analysis.technologyStack.slice(0, 7).map((technology) => <span key={technology.name}>{technology.name}</span>)}
              </div>
            </div>
            <div className="analysis-summary-meta">
              <span><GitBranch size={14} /> {analysis.repository.defaultBranch}</span>
              <span><FileCode2 size={14} /> {analysis.metrics.analyzedFiles} evidence files</span>
              <span><Braces size={14} /> {analysis.metrics.sourceFiles} source files</span>
            </div>
          </section>

          <div className="analysis-workspace">
            <section className="panel analysis-features-panel">
              <div className="panel-title">
                <div><h2>Choose presentation features</h2><p>These candidates are derived from high-signal files. You decide the final emphasis.</p></div>
                <span className="analysis-count">{selectedIds.length} selected</span>
              </div>
              <div className="analysis-feature-list">
                {analysis.featureCandidates.map((candidate) => (
                  <FeatureToggle
                    candidate={candidate}
                    key={candidate.id}
                    onToggle={() => toggleFeature(candidate.id)}
                    selected={selectedIds.includes(candidate.id)}
                  />
                ))}
              </div>
              {!analysis.featureCandidates.length && <p className="analysis-muted">No confident feature grouping was found. Review the core files below.</p>}
            </section>

            <aside className="analysis-brief">
              <span className="mini-label">PRESENTATION BRIEF</span>
              <h2>{selectedFeatures.length ? "Selected implementation story" : "Select a feature to focus the story"}</h2>
              <p>{analysis.aiNarrative}</p>
              <div className="analysis-selected-list">
                {selectedFeatures.map((feature) => <span key={feature.id}><Check size={12} /> {feature.title}</span>)}
              </div>
              <small>
                {analysis.summarySource === "GEMINI"
                  ? "The wording was generated by Gemini from the selected evidence files."
                  : "This is a deterministic preview. Set GEMINI_ENABLED=true and GEMINI_API_KEY to add an AI-written Korean brief."}
              </small>
            </aside>
          </div>

          <section className="panel analysis-code-panel">
            <div className="panel-title">
              <div><h2>Core code evidence</h2><p>Files are ranked by architectural responsibility; snippets are shown only as proof for the preview.</p></div>
              <span className="analysis-count">{analysis.coreFiles.length} files</span>
            </div>
            <div className="analysis-core-grid">
              {analysis.coreFiles.map((file) => (
                <article className="analysis-file" key={file.path}>
                  <div><span>{file.role}</span><strong>{file.path}</strong></div>
                  <p>{file.symbols.join(" · ")}</p>
                  <pre>{file.excerpt}</pre>
                </article>
              ))}
            </div>
          </section>

          <section className="analysis-notices">
            {analysis.notices.map((notice) => <span key={notice}>{notice}</span>)}
          </section>
        </>
      )}
    </>
  );
}
