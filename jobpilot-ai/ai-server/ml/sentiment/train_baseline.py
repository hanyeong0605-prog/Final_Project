"""Train the explainable TF-IDF/Logistic Regression KOTE baseline."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score
from sklearn.multiclass import OneVsRestClassifier
from sklearn.pipeline import Pipeline

from app.domain.sentiment.labels import KOTE_LABELS
from ml.sentiment.artifact import read_manifest, write_json
from ml.sentiment.dataset import KoteExample, load_split


@dataclass(frozen=True)
class TrainingResult:
    output_dir: Path
    model_version: str
    thresholds: tuple[float, ...]


def _binary_labels(rows: list[KoteExample]) -> np.ndarray:
    matrix = np.zeros((len(rows), len(KOTE_LABELS)), dtype=np.int8)
    for row_index, row in enumerate(rows):
        matrix[row_index, list(row.labels)] = 1
    return matrix


def _tune_thresholds(expected: np.ndarray, probabilities: np.ndarray) -> tuple[float, ...]:
    candidates = tuple(round(value, 2) for value in np.arange(0.20, 0.701, 0.05))
    selected: list[float] = []
    for label_index in range(len(KOTE_LABELS)):
        scored = []
        for threshold in candidates:
            predicted = probabilities[:, label_index] >= threshold
            score = f1_score(expected[:, label_index], predicted, zero_division=0)
            scored.append((score, -abs(threshold - 0.40), threshold))
        selected.append(float(max(scored)[2]))
    return tuple(selected)


def train_baseline(
    dataset_root: Path,
    output_dir: Path,
    *,
    min_df: int = 3,
    max_features: int = 120_000,
) -> TrainingResult:
    manifest = read_manifest(dataset_root)
    train_rows = load_split(dataset_root / "train.tsv")
    validation_rows = load_split(dataset_root / "val.tsv")
    pipeline = Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    analyzer="char_wb",
                    ngram_range=(2, 5),
                    min_df=min_df,
                    max_features=max_features,
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                OneVsRestClassifier(
                    LogisticRegression(
                        solver="liblinear",
                        class_weight="balanced",
                        max_iter=500,
                        random_state=42,
                    ),
                    n_jobs=1,
                ),
            ),
        ]
    )
    pipeline.fit([row.text for row in train_rows], _binary_labels(train_rows))
    validation_probabilities = pipeline.predict_proba([row.text for row in validation_rows])
    thresholds = _tune_thresholds(_binary_labels(validation_rows), validation_probabilities)

    trained_at = datetime.now(timezone.utc)
    model_version = f"kote-baseline-{trained_at.strftime('%Y%m%dT%H%M%SZ')}"
    output_dir.mkdir(parents=True, exist_ok=False)
    joblib.dump(pipeline, output_dir / "model.joblib")
    write_json(output_dir / "labels.json", list(KOTE_LABELS))
    write_json(output_dir / "thresholds.json", list(thresholds))
    write_json(
        output_dir / "metadata.json",
        {
            "model_version": model_version,
            "model_type": "tfidf-logistic-ovr",
            "source_commit": manifest["resolved_commit"],
            "trained_at": trained_at.isoformat(),
            "label_count": len(KOTE_LABELS),
            "sklearn_version": sklearn.__version__,
            "split_counts": {
                "train": len(train_rows),
                "validation": len(validation_rows),
            },
            "threshold_selection": "validation-label-f1-v1",
        },
    )
    return TrainingResult(output_dir, model_version, thresholds)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = train_baseline(args.data, args.output)
    print(f"artifact={result.output_dir} model_version={result.model_version}")


if __name__ == "__main__":
    main()
