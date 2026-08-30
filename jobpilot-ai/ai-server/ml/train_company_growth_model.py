"""Train and evaluate the DART growth model with a strict year cutoff."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import sklearn
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.impute import SimpleImputer
from sklearn.metrics import brier_score_loss, f1_score, mean_absolute_error
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from ml.company_finance_dataset import FEATURE_COLUMNS


NUMERIC_FEATURES = [column for column in FEATURE_COLUMNS if column != "size_bucket"]
# The DART sample is sufficient for revenue and profitability validation.
# Stability events are too sparse for a held-out ML classifier, so they remain
# an explainable financial signal in Spring rather than a misleading ML output.
TARGETS = ["next_revenue_positive", "next_profitability_improved"]


def _preprocessor() -> ColumnTransformer:
    return ColumnTransformer([
        ("numeric", Pipeline([("impute", SimpleImputer(strategy="median")), ("scale", StandardScaler())]), NUMERIC_FEATURES),
        ("category", OneHotEncoder(handle_unknown="ignore"), ["size_bucket"]),
    ])


def _probability(model: Pipeline, frame: pd.DataFrame) -> list[float]:
    classifier = model.named_steps["model"]
    probabilities = model.predict_proba(frame)
    classes = list(classifier.classes_)
    if 1 not in classes:
        return [0.0] * len(frame)
    return probabilities[:, classes.index(1)].tolist()


def _classifier() -> Pipeline:
    return Pipeline([("features", _preprocessor()), ("model", RandomForestClassifier(
        n_estimators=250, min_samples_leaf=2, class_weight="balanced", random_state=42, n_jobs=-1))])


def _temporal_decision_threshold(train: pd.DataFrame, target: str) -> float:
    """Choose a cutoff on the latest training year, never on the final holdout."""
    years = sorted(int(value) for value in train.base_year.unique())
    if len(years) < 2:
        return 0.5
    tuning_train = train[train.base_year < years[-1]]
    tuning_validation = train[train.base_year == years[-1]]
    if tuning_train.empty or tuning_validation.empty or tuning_train[target].nunique() < 2:
        return 0.5
    model = _classifier()
    model.fit(tuning_train[FEATURE_COLUMNS], tuning_train[target].astype(int))
    probabilities = np.asarray(_probability(model, tuning_validation[FEATURE_COLUMNS]))
    truth = tuning_validation[target].astype(int)
    candidates = np.linspace(0.10, 0.90, 33)
    scored = [(float(f1_score(truth, probabilities >= threshold, zero_division=0)), float(threshold))
              for threshold in candidates]
    return max(scored, key=lambda item: (item[0], -abs(item[1] - 0.5)))[1]


def train_and_evaluate(dataset: pd.DataFrame, cutoff_year: int, output_dir: Path | None = None) -> dict[str, Any]:
    required = {"corp_code", "base_year", *FEATURE_COLUMNS, "next_revenue_growth", *TARGETS}
    missing = required.difference(dataset.columns)
    if missing:
        raise ValueError(f"dataset columns missing: {sorted(missing)}")
    train = dataset[dataset.base_year < cutoff_year].copy()
    holdout = dataset[dataset.base_year >= cutoff_year].copy()
    if train.empty or holdout.empty:
        raise ValueError("both historical training rows and cutoff-year holdout rows are required")

    regression = Pipeline([("features", _preprocessor()), ("model", RandomForestRegressor(
        n_estimators=200, min_samples_leaf=2, random_state=42, n_jobs=-1))])
    regression.fit(train[FEATURE_COLUMNS], train["next_revenue_growth"])
    growth_prediction = regression.predict(holdout[FEATURE_COLUMNS])
    growth_mae = float(mean_absolute_error(holdout["next_revenue_growth"], growth_prediction))

    classifiers: dict[str, Pipeline] = {}
    classification_metrics: dict[str, dict[str, float]] = {}
    for target in TARGETS:
        decision_threshold = _temporal_decision_threshold(train, target)
        model = _classifier()
        model.fit(train[FEATURE_COLUMNS], train[target].astype(int))
        probability = _probability(model, holdout[FEATURE_COLUMNS])
        predicted = np.asarray(probability) >= decision_threshold
        truth = holdout[target].astype(int)
        classification_metrics[target] = {
            "f1": float(f1_score(truth, predicted, zero_division=0)),
            "brier": float(brier_score_loss(truth, probability)),
            "decision_threshold": decision_threshold,
        }
        classifiers[target] = model

    minimum_f1 = min(metric["f1"] for metric in classification_metrics.values())
    maximum_brier = max(metric["brier"] for metric in classification_metrics.values())
    validation_passed = growth_mae <= 0.35 and minimum_f1 >= 0.50 and maximum_brier <= 0.30
    model_version = f"company-growth-rf-v1-cutoff-{cutoff_year}"
    metadata = {
        "model_version": model_version,
        "validated": validation_passed,
        "cutoff_year": cutoff_year,
        "train_years": sorted(int(value) for value in train.base_year.unique()),
        "holdout_years": sorted(int(value) for value in holdout.base_year.unique()),
        "train_rows": len(train),
        "holdout_rows": len(holdout),
        "feature_names": FEATURE_COLUMNS,
        "growth_mae": growth_mae,
        "classification": classification_metrics,
        "thresholds": {"growth_mae_max": 0.35, "minimum_f1": 0.50, "maximum_brier": 0.30},
        "sklearn_version": sklearn.__version__,
    }
    artifact = {"metadata": metadata, "regression": regression, "classifiers": classifiers}
    if output_dir is not None:
        output_dir.mkdir(parents=True, exist_ok=False)
        joblib.dump(artifact, output_dir / "model.joblib")
        (output_dir / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return artifact


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--cutoff-year", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    artifact = train_and_evaluate(pd.read_csv(args.dataset), args.cutoff_year, args.output)
    print(json.dumps(artifact["metadata"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
