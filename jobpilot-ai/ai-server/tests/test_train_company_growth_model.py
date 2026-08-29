import pandas as pd

from ml.company_finance_dataset import FEATURE_COLUMNS
from ml.train_company_growth_model import train_and_evaluate


def dataset():
    rows = []
    for year in (2020, 2021, 2022, 2023):
        for index in range(16):
            positive = index % 2
            row = {"corp_code": f"{index:08d}", "base_year": year,
                   "revenue_growth_1y": 0.12 if positive else -0.08,
                   "revenue_growth_3y": 0.25 if positive else -0.12,
                   "operating_margin": 0.15 if positive else -0.04,
                   "operating_margin_change": 0.03 if positive else -0.02,
                   "debt_ratio": 0.5 if positive else 2.0,
                   "debt_ratio_change": -0.1 if positive else 0.4,
                   "operating_cashflow_ratio": 0.1 if positive else -0.05,
                   "cashflow_ratio_change": 0.02 if positive else -0.03,
                   "profitable": positive, "size_bucket": "MEDIUM",
                   "next_revenue_growth": 0.1 if positive else -0.1,
                   "next_revenue_positive": positive,
                   "next_profitability_improved": positive,
                   "next_stability_risk": 1 - positive}
            rows.append(row)
    return pd.DataFrame(rows)


def test_time_cutoff_is_excluded_from_training_and_metadata_is_saved(tmp_path):
    output = tmp_path / "artifact"
    artifact = train_and_evaluate(dataset(), cutoff_year=2023, output_dir=output)
    metadata = artifact["metadata"]
    assert metadata["train_years"] == [2020, 2021, 2022]
    assert metadata["holdout_years"] == [2023]
    assert metadata["feature_names"] == FEATURE_COLUMNS
    assert metadata["validated"] is True
    assert (output / "model.joblib").is_file()
    assert (output / "metadata.json").is_file()
