"""Validated company-growth artifact loading and prediction."""
from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping

import joblib
import pandas as pd

from ml.company_finance_dataset import FEATURE_COLUMNS
from ml.train_company_growth_model import _probability


class CompanyGrowthModel:
    def __init__(self, artifact_dir: Path):
        artifact = joblib.load(artifact_dir / "model.joblib")
        metadata = artifact.get("metadata", {})
        if not metadata.get("validated"):
            raise ValueError("company growth artifact did not pass held-out evaluation")
        self.metadata = metadata
        self.regression = artifact["regression"]
        self.classifiers = artifact["classifiers"]

    def predict(self, features: Mapping[str, Any]) -> dict[str, Any]:
        missing = [name for name in FEATURE_COLUMNS if features.get(name) is None]
        if missing:
            raise ValueError(f"feature snapshot missing: {missing}")
        frame = pd.DataFrame([{name: features[name] for name in FEATURE_COLUMNS}])
        growth_probability = _probability(self.classifiers["next_revenue_positive"], frame)[0]
        profitability = _probability(self.classifiers["next_profitability_improved"], frame)[0]
        expected_growth = float(self.regression.predict(frame)[0])
        score = growth_probability * 0.6 + profitability * 0.4
        outlook = "POSITIVE" if score >= 0.65 else "NEGATIVE" if score < 0.4 else "CAUTION"
        confidence = "HIGH" if abs(score - 0.5) >= 0.25 else "MEDIUM" if abs(score - 0.5) >= 0.12 else "LOW"
        return {"model_version": self.metadata["model_version"], "validated": True,
                "growth_probability": growth_probability,
                "profitability_improvement_probability": profitability,
                "stability_risk_probability": 0.0, "expected_revenue_growth": expected_growth,
                "outlook": outlook, "confidence": confidence}
