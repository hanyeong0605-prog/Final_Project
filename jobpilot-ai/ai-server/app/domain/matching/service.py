"""Small, explainable RandomForest recommendation model.

The Java backend remains the source of truth for *readiness*: it decides whether
mandatory experience, education or certification conditions are missing.  This
model learns a separate "interest likelihood" from bookmarks and blends it
only when enough real behaviour exists.  That prevents a popular job from being
presented as qualified when a user lacks a mandatory requirement.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sqlalchemy import text
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_extraction.text import TfidfVectorizer

from app.core.db import get_engine


MODEL_PATH = Path("/tmp/jobpilot_job_match_model.joblib")
MIN_REAL_POSITIVES = 5
MAX_NEGATIVES_PER_POSITIVE = 3
NUMERIC_FIELDS = (
    "skill_coverage",
    "certificate_coverage",
    "experience_match",
    "education_match",
    "rule_readiness",
    "missing_required_count",
)


@dataclass
class TrainedModel:
    classifier: RandomForestClassifier
    vectorizer: TfidfVectorizer
    source: str
    sample_count: int
    positive_count: int


_model: TrainedModel | None = None


def _load_rows() -> list[dict[str, Any]]:
    engine = get_engine()
    # One row per existing member/job match. Evidence rows calculate the same
    # requirement-coverage features that are shown in the UI's evidence matrix.
    statement = text("""
        SELECT
          jm.member_id,
          jm.job_posting_id,
          COALESCE(jm.readiness_score, 0) AS rule_readiness,
          COALESCE(jm.missing_required_count, 0) AS missing_required_count,
          CONCAT_WS(' ', mp.target_role, mp.target_job_family) AS target_text,
          CONCAT_WS(' ', jp.title, jp.job_name, jp.job_mid_name, jp.keywords) AS job_text,
          MAX(CASE WHEN ui.id IS NOT NULL THEN 1 ELSE 0 END) AS bookmarked,
          MAX(CASE WHEN mje.event_type = 'VIEW_DETAIL' THEN 1 ELSE 0 END) AS viewed_detail,
          AVG(CASE WHEN jr.type = 'SKILL' AND jme.status = 'DIRECT' THEN 1
                   WHEN jr.type = 'SKILL' THEN 0 ELSE NULL END) AS skill_coverage,
          AVG(CASE WHEN jr.type = 'CERTIFICATION' AND jme.status = 'DIRECT' THEN 1
                   WHEN jr.type = 'CERTIFICATION' THEN 0 ELSE NULL END) AS certificate_coverage,
          MAX(CASE WHEN jr.type = 'EXPERIENCE' AND jme.status = 'DIRECT' THEN 1 ELSE 0 END) AS experience_match,
          MAX(CASE WHEN jr.type = 'EDUCATION' AND jme.status = 'DIRECT' THEN 1 ELSE 0 END) AS education_match
        FROM job_matches jm
        JOIN job_postings jp ON jp.id = jm.job_posting_id
        LEFT JOIN member_profiles mp ON mp.member_id = jm.member_id
        LEFT JOIN job_match_evidences jme ON jme.job_match_id = jm.id
        LEFT JOIN job_requirements jr ON jr.id = jme.job_requirement_id
        LEFT JOIN user_interests ui
          ON ui.member_id = jm.member_id
         AND ui.target_type = 'JOB_POSTING'
         AND ui.target_id = jm.job_posting_id
        LEFT JOIN member_job_events mje
          ON mje.member_id = jm.member_id
         AND mje.job_posting_id = jm.job_posting_id
        WHERE jp.status = 'ACTIVE'
        GROUP BY jm.id, jm.member_id, jm.job_posting_id, jm.readiness_score,
                 jm.missing_required_count, mp.target_role, mp.target_job_family,
                 jp.title, jp.job_name, jp.job_mid_name, jp.keywords
    """)
    with engine.connect() as connection:
        return [dict(row) for row in connection.execute(statement).mappings()]


def _feature_matrix(rows: list[dict[str, Any]], vectorizer: TfidfVectorizer | None = None, fit: bool = False) -> tuple[np.ndarray, TfidfVectorizer]:
    role_texts = [str(row.get("target_text") or "") for row in rows]
    job_texts = [str(row.get("job_text") or "") for row in rows]
    if vectorizer is None:
        vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), min_df=1, max_features=3000)
    if fit:
        vectorizer.fit(role_texts + job_texts)
    role_vectors = vectorizer.transform(role_texts)
    job_vectors = vectorizer.transform(job_texts)
    # TF-IDF cosine similarity: target role/job family versus job title/category.
    role_norm = np.sqrt(role_vectors.multiply(role_vectors).sum(axis=1)).A1
    job_norm = np.sqrt(job_vectors.multiply(job_vectors).sum(axis=1)).A1
    dot = role_vectors.multiply(job_vectors).sum(axis=1).A1
    cosine = np.divide(dot, role_norm * job_norm, out=np.zeros_like(dot), where=(role_norm * job_norm) > 0)
    numeric = np.array([
        [float(row.get(field) or 0) for field in NUMERIC_FIELDS] + [float(value)]
        for row, value in zip(rows, cosine)
    ], dtype=float)
    return numeric, vectorizer


def _balanced_rows(rows: list[dict[str, Any]], source: str) -> list[dict[str, Any]]:
    positives = [row for row in rows if int(row["label"]) == 1]
    # A non-bookmark is only a meaningful negative after the member actually
    # opened the detail page.  Unseen postings are unlabeled, not disliked.
    negatives = [row for row in rows if int(row["label"]) == 0 and (
        source == "RULE_BASED_WEAK_LABEL_V1" or int(row.get("viewed_detail") or 0) == 1
    )]
    if not positives or not negatives:
        return []
    negatives.sort(key=lambda row: (int(row.get("missing_required_count") or 0), float(row.get("rule_readiness") or 0)))
    return positives + negatives[: len(positives) * MAX_NEGATIVES_PER_POSITIVE]


def retrain() -> dict[str, Any]:
    global _model
    rows = _load_rows()
    real_positive_count = sum(int(row.get("bookmarked") or 0) for row in rows)
    source = "BOOKMARK_BEHAVIOR_V1" if real_positive_count >= MIN_REAL_POSITIVES else "RULE_BASED_WEAK_LABEL_V1"
    for row in rows:
        if source == "BOOKMARK_BEHAVIOR_V1":
            row["label"] = int(row.get("bookmarked") or 0)
        else:
            row["label"] = int(float(row.get("rule_readiness") or 0) >= 75 and int(row.get("missing_required_count") or 0) == 0)
    training_rows = _balanced_rows(rows, source)
    if len(training_rows) < 8 or len({row["label"] for row in training_rows}) < 2:
        _model = None
        return {
            "state": "warming_up",
            "source": source,
            "sample_count": len(training_rows),
            "positive_count": sum(int(row.get("label") or 0) for row in training_rows),
            "message": "Not enough balanced match data; rule-based readiness remains active.",
        }
    features, vectorizer = _feature_matrix(training_rows, fit=True)
    labels = np.array([int(row["label"]) for row in training_rows])
    classifier = RandomForestClassifier(
        n_estimators=180,
        max_depth=8,
        min_samples_leaf=2,
        class_weight="balanced",
        random_state=42,
        n_jobs=1,
    )
    classifier.fit(features, labels)
    _model = TrainedModel(classifier, vectorizer, source, len(training_rows), int(labels.sum()))
    joblib.dump(_model, MODEL_PATH)
    return {
        "state": "trained",
        "source": source,
        "sample_count": len(training_rows),
        "positive_count": int(labels.sum()),
        "features": [*NUMERIC_FIELDS, "role_tfidf_cosine"],
    }


def _current_model() -> TrainedModel | None:
    global _model
    if _model is None and MODEL_PATH.exists():
        _model = joblib.load(MODEL_PATH)
    return _model


def score(candidates: list[dict[str, Any]]) -> dict[str, Any]:
    model = _current_model()
    if model is None:
        return {"state": "warming_up", "source": "RULE_BASED_WARMUP", "scores": []}
    features, _ = _feature_matrix(candidates, vectorizer=model.vectorizer, fit=False)
    probabilities = model.classifier.predict_proba(features)[:, 1]
    return {
        "state": "ready",
        "source": model.source,
        "sample_count": model.sample_count,
        "positive_count": model.positive_count,
        "scores": [round(float(value) * 100, 2) for value in probabilities],
    }


def model_status() -> dict[str, Any]:
    model = _current_model()
    if model is None:
        return {"state": "warming_up", "source": "RULE_BASED_WARMUP"}
    return {
        "state": "ready",
        "source": model.source,
        "sample_count": model.sample_count,
        "positive_count": model.positive_count,
        "features": [*NUMERIC_FIELDS, "role_tfidf_cosine"],
    }
