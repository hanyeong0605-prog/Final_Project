from ml.company_finance_dataset import build_company_year_dataset


def row(year, revenue, operating_income=10, net_income=5, assets=200, liabilities=80, equity=120, cash=12):
    return {"corp_code": "00000001", "business_year": year, "revenue": revenue,
            "operating_income": operating_income, "net_income": net_income,
            "total_assets": assets, "total_liabilities": liabilities,
            "total_equity": equity, "operating_cash_flow": cash}


def test_base_year_uses_three_past_years_and_next_year_only_as_label():
    dataset = build_company_year_dataset([row(2019, 80), row(2020, 90), row(2021, 100), row(2022, 130)])
    assert len(dataset) == 1
    item = dataset.iloc[0]
    assert item.base_year == 2021
    assert round(item.revenue_growth_1y, 4) == 0.1111
    assert item.next_revenue_growth == 0.3
    assert item.next_revenue_positive == 1


def test_missing_consecutive_year_excludes_row():
    dataset = build_company_year_dataset([row(2019, 80), row(2021, 100), row(2022, 130)])
    assert dataset.empty
