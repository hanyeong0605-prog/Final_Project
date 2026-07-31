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

export interface ProjectProfile {
  classification: string;
  summary: string;
  confidence: "HIGH" | "MEDIUM" | "LOW" | string;
  evidence: string[];
  limitations: string[];
}

export interface ArchitectureLayer {
  name: string;
  description: string;
  evidence: string[];
}

export interface IntegrationFact {
  name: string;
  category: string;
  direction: string;
  description: string;
  evidence: string[];
}

export interface CodeFlow {
  title: string;
  description: string;
  evidence: string[];
  confidence: "HIGH" | "MEDIUM" | "LOW" | string;
}

export interface CodeEvidence {
  path: string;
  symbol: string;
  description: string;
}

export interface ImplementationStory {
  id: string;
  title: string;
  description: string;
  mechanism: string;
  technologies: string[];
  evidence: CodeEvidence[];
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
  responsibility: string;
  symbols: string[];
  excerpt: string;
  score: number;
  importance: "CORE" | "STRUCTURAL" | "REFERENCE" | string;
  selectionReason: string;
}

export interface ProjectAnalysis {
  repository: RepositoryInfo;
  overview: string;
  aiNarrative: string;
  summarySource: "GEMINI" | "STATIC" | string;
  projectProfile: ProjectProfile;
  technologyStack: TechnologyFact[];
  architecture: ArchitectureLayer[];
  integrations: IntegrationFact[];
  codeFlows: CodeFlow[];
  implementations: ImplementationStory[];
  featureCandidates: FeatureCandidate[];
  coreFiles: CoreFile[];
  metrics: {
    totalFiles: number;
    sourceFiles: number;
    analyzedFiles: number;
    fileTypes: Record<string, number>;
  };
  notices: string[];
  generatedOutput?: string | null;
}
