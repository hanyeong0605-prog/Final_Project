import { describe, expect, it } from "vitest";
import { forecastMetricLabels } from "./CompanyFinanceSection";

describe("forecastMetricLabels", () => {
  it("marks every model-derived value as a possibility rather than a fact", () => {
    expect(forecastMetricLabels("주의")).toEqual({
      growth: "매출 성장 가능성",
      profitability: "수익성 개선 가능성",
      risk: "주의: 재무 위험 가능성 신호",
    });
  });
});
