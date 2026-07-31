export interface RepositoryInfo {
  owner: string;
  name: string;
  fullName: string;
  htmlUrl: string;
  defaultBranch: string;
  description?: string | null;
  analyzedAt: string;
}

export interface TechnologyFact {
  name: string;
  category: string;
  evidence: string[];
}

export interface ArchitectureLayer {
  name: string;
  description: string;
  evidence: string[];
}

export interface FeatureCandidate {
  id: string;
  title: string;
  description: string;
  confidence: "HIGH" | "MEDIUM" | "LOW" | string;
  evidence: string[];
  score: number;
}

export interface CoreFile {
  path: string;
  role: string;
  symbols: string[];
  excerpt: string;
  score: number;
}

export interface ProjectAnalysis {
  repository: RepositoryInfo;
  overview: string;
  aiNarrative: string;
  summarySource: "GEMINI" | "STATIC" | string;
  technologyStack: TechnologyFact[];
  architecture: ArchitectureLayer[];
  featureCandidates: FeatureCandidate[];
  coreFiles: CoreFile[];
  metrics: {
    totalFiles: number;
    sourceFiles: number;
    analyzedFiles: number;
    fileTypes: Record<string, number>;
  };
  notices: string[];
}
