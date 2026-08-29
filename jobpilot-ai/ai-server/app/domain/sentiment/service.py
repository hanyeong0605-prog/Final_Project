"""Load only operator-controlled local artifacts; no training or network calls.

joblib is executable serialization, so the model directory must never contain
user uploads. Missing/corrupt weights disable this component, not other APIs.
Deploy a new immutable directory and restart the worker to switch versions.
"""
from __future__ import annotations

import hashlib
import json
from functools import lru_cache
from pathlib import Path
from threading import Lock

import joblib
import numpy as np
import sklearn

from app.core.config import settings
from app.domain.sentiment.labels import KOTE_LABELS, aggregate_polarity, load_polarity_policy
from app.domain.sentiment.schemas import AnalyzeRequest, Emotion, ModelStatus, Polarity, SentimentAnalysis


class SentimentUnavailableError(RuntimeError):
    pass


class SentimentService:
    def __init__(self, artifact_dir: Path):
        self._directory = artifact_dir
        self._lock = Lock()
        self._loaded = False
        self._state = ModelStatus(state="UNAVAILABLE")
        self._model = None

    def _load(self) -> None:
        with self._lock:
            if self._loaded:
                return
            self._loaded = True
            if not self._directory.is_dir():
                return
            try:
                def read(name):
                    return json.loads((self._directory / name).read_text(encoding="utf-8"))
                metadata = read("metadata.json")
                if metadata["model_type"] != "tfidf-logistic-ovr" or read("labels.json") != list(KOTE_LABELS):
                    raise ValueError("invalid artifact type or label ordering")
                if metadata["sklearn_version"] != sklearn.__version__:
                    raise ValueError("artifact runtime version mismatch")
                thresholds = np.asarray(read("thresholds.json"), dtype=float)
                if thresholds.shape != (44,) or not np.isfinite(thresholds).all() or ((thresholds < 0) | (thresholds > 1)).any():
                    raise ValueError("invalid thresholds")
                model = joblib.load(self._directory / "model.joblib")
                self._check_scores(model.predict_proba(["모델 확인"]), 1)
                self._policy = load_polarity_policy()
                self._thresholds = thresholds
                self._model = model
                self._state = ModelStatus(state="READY", model_version=metadata["model_version"])
            except Exception:
                # Never expose artifact paths or serialized exception details to callers.
                self._state = ModelStatus(state="FAILED")

    @staticmethod
    def _check_scores(values, count):
        scores = np.asarray(values, dtype=float)
        if scores.shape != (count, 44) or not np.isfinite(scores).all() or ((scores < 0) | (scores > 1)).any():
            raise ValueError("invalid model scores")
        return scores

    def status(self) -> ModelStatus:
        self._load()
        return self._state.model_copy()

    def analyze(self, text: str, top_k: int = 5) -> SentimentAnalysis:
        request = AnalyzeRequest(text=text, top_k=top_k)
        self._load()
        if self._state.state != "READY":
            raise SentimentUnavailableError("sentiment model unavailable")
        # Keep inference text preprocessing identical to the trained TF-IDF pipeline.
        try:
            scores = self._check_scores(self._model.predict_proba([request.text]), 1)[0]
        except Exception as exc:
            raise SentimentUnavailableError("sentiment inference failed") from exc
        ordered = sorted(range(44), key=lambda i: (-scores[i], i))
        emotions = [Emotion(label=KOTE_LABELS[i], score=float(scores[i])) for i in ordered
                    if scores[i] >= self._thresholds[i]][:request.top_k]
        polarity = aggregate_polarity(dict(zip(KOTE_LABELS, scores)), self._policy)
        return SentimentAnalysis(
            model_version=self._state.model_version, policy_version=polarity.policy_version,
            content_hash=hashlib.sha256(request.text.encode("utf-8")).hexdigest(), emotions=emotions,
            polarity=Polarity(label=polarity.label, positive=polarity.positive,
                              neutral=polarity.neutral, negative=polarity.negative),
        )


@lru_cache(maxsize=1)
def get_sentiment_service() -> SentimentService:
    return SentimentService(Path(settings.sentiment_model_dir))
