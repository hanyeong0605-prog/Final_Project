"""Build leakage-safe DART company-year rows for the growth model.

For base year t, features use only t-2..t and labels use t+1. A company must
have all four consecutive annual statements; missing years are never imputed.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Mapping, Any

import pandas as pd
from sqlalchemy import text

from app.core.db import get_engine


FEATURE_COLUMNS = [
    "revenue_growth_1y", "revenue_growth_3y", "operating_margin",
    "operating_margin_change", "debt_ratio", "debt_ratio_change", "profitable",
    "size_bucket",
]
LABEL_COLUMNS = ["next_revenue_growth", "next_revenue_positive", "next_profitability_improved", "next_stability_risk"]


def _ratio(numerator: float | None, denominator: float | None) -> float | None:
    if numerator is None or denominator in (None, 0):
        return None
    return float(numerator) / float(denominator)


def _growth(current: float | None, prior: float | None) -> float | None:
    if current is None or prior in (None, 0):
        return None
    return (float(current) - float(prior)) / abs(float(prior))


def _size_bucket(assets: float | None) -> str:
    if assets is None:
        return "UNKNOWN"
    if assets >= 1_000_000_000_000:
        return "LARGE"
    if assets >= 100_000_000_000:
        return "MEDIUM"
    return "SMALL"


def build_company_year_dataset(financial_rows: Iterable[Mapping[str, Any]]) -> pd.DataFrame:
    grouped: dict[str, dict[int, Mapping[str, Any]]] = defaultdict(dict)
    for row in financial_rows:
        grouped[str(row["corp_code"])][int(row["business_year"])] = row

    records: list[dict[str, Any]] = []
    for corp_code, years in grouped.items():
        for base_year in sorted(years):
            required = [base_year - 2, base_year - 1, base_year, base_year + 1]
            if any(year not in years for year in required):
                continue
            oldest, prior, current, following = (years[year] for year in required)
            current_margin = _ratio(current.get("operating_income"), current.get("revenue"))
            prior_margin = _ratio(prior.get("operating_income"), prior.get("revenue"))
            next_margin = _ratio(following.get("operating_income"), following.get("revenue"))
            current_debt = _ratio(current.get("total_liabilities"), current.get("total_equity"))
            prior_debt = _ratio(prior.get("total_liabilities"), prior.get("total_equity"))
            next_debt = _ratio(following.get("total_liabilities"), following.get("total_equity"))
            next_growth = _growth(following.get("revenue"), current.get("revenue"))
            values = {
                "corp_code": corp_code,
                "base_year": base_year,
                "revenue_growth_1y": _growth(current.get("revenue"), prior.get("revenue")),
                "revenue_growth_3y": _growth(current.get("revenue"), oldest.get("revenue")),
                "operating_margin": current_margin,
                "operating_margin_change": None if current_margin is None or prior_margin is None else current_margin - prior_margin,
                "debt_ratio": current_debt,
                "debt_ratio_change": None if current_debt is None or prior_debt is None else current_debt - prior_debt,
                "profitable": int((current.get("net_income") or 0) > 0),
                "size_bucket": _size_bucket(current.get("total_assets")),
                "next_revenue_growth": next_growth,
                "next_revenue_positive": None if next_growth is None else int(next_growth > 0),
                "next_profitability_improved": None if next_margin is None or current_margin is None else int(next_margin > current_margin),
                # The multi-company major-account API exposes BS/IS, not CF.
                # Stability therefore means material leverage deterioration or a next-year loss.
                "next_stability_risk": None if next_debt is None or current_debt is None else int(
                    next_debt > current_debt * 1.2 or (following.get("net_income") or 0) < 0
                ),
            }
            if all(values[column] is not None for column in FEATURE_COLUMNS + LABEL_COLUMNS):
                records.append(values)
    return pd.DataFrame(records, columns=["corp_code", "base_year", *FEATURE_COLUMNS, *LABEL_COLUMNS])


def load_financial_rows() -> list[dict[str, Any]]:
    query = text("""
        SELECT corp_code, business_year, revenue, operating_income, net_income,
               total_assets, total_liabilities, total_equity, operating_cash_flow
        FROM company_financial_years
        WHERE report_code = '11011'
        ORDER BY corp_code, business_year
    """)
    with get_engine().connect() as connection:
        return [dict(row) for row in connection.execute(query).mappings()]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    dataset = build_company_year_dataset(load_financial_rows())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_csv(args.output, index=False, encoding="utf-8")
    print(f"company-year dataset rows={len(dataset)} output={args.output}")


if __name__ == "__main__":
    main()
