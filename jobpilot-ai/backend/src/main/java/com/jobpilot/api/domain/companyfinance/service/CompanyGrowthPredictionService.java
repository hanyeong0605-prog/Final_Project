package com.jobpilot.api.domain.companyfinance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.companyfinance.client.CompanyGrowthModelClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CompanyGrowthPredictionService {
    private final JdbcTemplate jdbc;
    private final CompanyGrowthModelClient model;
    private final ObjectMapper json;

    public CompanyGrowthPredictionService(JdbcTemplate jdbc, CompanyGrowthModelClient model, ObjectMapper json) {
        this.jdbc = jdbc;
        this.model = model;
        this.json = json;
    }

    public int refreshConfirmedCompanies() {
        List<String> corpCodes = jdbc.queryForList("""
                SELECT DISTINCT corp_code FROM company_dart_matches
                WHERE match_status='CONFIRMED' AND corp_code IS NOT NULL
                """, String.class);
        int stored = 0;
        for (String corpCode : corpCodes) if (refresh(corpCode)) stored++;
        return stored;
    }

    boolean refresh(String corpCode) {
        List<YearRow> rows = jdbc.query("""
                SELECT business_year,revenue,operating_income,net_income,total_assets,total_liabilities,
                       total_equity,operating_cash_flow FROM company_financial_years
                WHERE corp_code=? AND report_code='11011' ORDER BY business_year DESC LIMIT 3
                """, (rs, n) -> new YearRow(rs.getInt(1), number(rs.getObject(2)), number(rs.getObject(3)),
                number(rs.getObject(4)), number(rs.getObject(5)), number(rs.getObject(6)), number(rs.getObject(7)),
                number(rs.getObject(8))), corpCode);
        if (rows.size() != 3) return false;
        rows = new ArrayList<>(rows);
        java.util.Collections.reverse(rows);
        if (rows.get(1).year != rows.get(0).year + 1 || rows.get(2).year != rows.get(1).year + 1) return false;
        Map<String, Object> features = features(rows);
        if (features == null) return false;
        var prediction = model.predict(features);
        if (prediction.isEmpty()) return false;
        var value = prediction.get();
        try {
            List<String> evidence = evidence(features);
            jdbc.update("""
                    INSERT INTO company_growth_predictions(corp_code,base_year,model_version,growth_probability,
                      profitability_improvement_probability,stability_risk_probability,outlook,confidence,evidence,feature_snapshot)
                    VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE growth_probability=VALUES(growth_probability),
                      profitability_improvement_probability=VALUES(profitability_improvement_probability),
                      stability_risk_probability=VALUES(stability_risk_probability),outlook=VALUES(outlook),
                      confidence=VALUES(confidence),evidence=VALUES(evidence),feature_snapshot=VALUES(feature_snapshot),
                      generated_at=CURRENT_TIMESTAMP
                    """, corpCode, rows.get(2).year, value.modelVersion(), value.growthProbability(),
                    value.profitabilityImprovementProbability(), value.stabilityRiskProbability(), value.outlook(),
                    value.confidence(), json.writeValueAsString(evidence), json.writeValueAsString(features));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> features(List<YearRow> rows) {
        YearRow oldest=rows.get(0), prior=rows.get(1), current=rows.get(2);
        Double margin=ratio(current.operatingIncome,current.revenue), priorMargin=ratio(prior.operatingIncome,prior.revenue);
        Double debt=ratio(current.liabilities,current.equity), priorDebt=ratio(prior.liabilities,prior.equity);
        Double cash=ratio(current.cash,current.revenue), priorCash=ratio(prior.cash,prior.revenue);
        Double growth1=growth(current.revenue,prior.revenue), growth3=growth(current.revenue,oldest.revenue);
        if (java.util.Arrays.asList(margin,priorMargin,debt,priorDebt,cash,priorCash,growth1,growth3).contains(null)) return null;
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("revenueGrowth1Y",growth1);result.put("revenueGrowth3Y",growth3);
        result.put("operatingMargin",margin);result.put("operatingMarginChange",margin-priorMargin);
        result.put("debtRatio",debt);result.put("debtRatioChange",debt-priorDebt);
        result.put("operatingCashflowRatio",cash);result.put("cashflowRatioChange",cash-priorCash);
        result.put("profitable",current.netIncome!=null&&current.netIncome>0?1:0);
        result.put("sizeBucket",current.assets==null?"UNKNOWN":current.assets>=1_000_000_000_000L?"LARGE":current.assets>=100_000_000_000L?"MEDIUM":"SMALL");
        return result;
    }

    private List<String> evidence(Map<String,Object> feature) {
        List<String> result=new ArrayList<>();
        double growth=(double)feature.get("revenueGrowth1Y"), margin=(double)feature.get("operatingMargin"), cash=(double)feature.get("operatingCashflowRatio");
        result.add(growth>=0?"최근 매출이 전년보다 증가했습니다.":"최근 매출이 전년보다 감소했습니다.");
        result.add(margin>=0?"최근 영업이익률이 흑자입니다.":"최근 영업이익률이 적자입니다.");
        result.add(cash>=0?"영업활동 현금흐름이 양수입니다.":"영업활동 현금흐름이 음수입니다.");
        return result;
    }

    private static Double number(Object value){return value==null?null:((Number)value).doubleValue();}
    private static Double ratio(Double a,Double b){return a==null||b==null||b==0?null:a/b;}
    private static Double growth(Double a,Double b){return a==null||b==null||b==0?null:(a-b)/Math.abs(b);}
    private record YearRow(int year,Double revenue,Double operatingIncome,Double netIncome,Double assets,Double liabilities,Double equity,Double cash){}
}
