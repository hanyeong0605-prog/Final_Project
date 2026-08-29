export type CompanyFinanceStatus =
  | "READY"
  | "FINANCIALS_ONLY"
  | "UNMATCHED"
  | "FINANCIALS_NOT_FOUND"
  | "DATA_INSUFFICIENT"
  | "TEMPORARILY_UNAVAILABLE";

export interface CompanyFinancialYear {
  businessYear: number;
  revenue: number | null;
  operatingIncome: number | null;
  netIncome: number | null;
  totalAssets: number | null;
  totalLiabilities: number | null;
  totalEquity: number | null;
  operatingCashFlow: number | null;
  fsDiv: string | null;
  receiptNumber: string | null;
}

export interface CompanyGrowthForecast {
  baseYear: number;
  outlook: string;
  confidence: string;
  growthProbability: number;
  profitabilityImprovementProbability: number;
  stabilityRiskProbability: number;
  modelVersion: string;
  evidence: string[];
  generatedAt: string | null;
}

export interface CompanyFinanceAnalysis {
  status: CompanyFinanceStatus;
  message: string;
  corpCode: string | null;
  financials: CompanyFinancialYear[];
  forecast: CompanyGrowthForecast | null;
}
