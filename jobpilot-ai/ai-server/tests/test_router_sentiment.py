import json
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from tests.test_sentiment_training import _mini_training_dataset


def test_missing_model_returns_unavailable_without_breaking_service(tmp_path):
    from app.domain.sentiment.service import SentimentService, SentimentUnavailableError
    service = SentimentService(tmp_path / "absent")
    assert service.status().state == "UNAVAILABLE"
    with pytest.raises(SentimentUnavailableError):
        service.analyze("좋아요")


def test_real_artifact_analysis_and_internal_routes(tmp_path, monkeypatch):
    from ml.sentiment.train_baseline import train_baseline
    from app.domain.sentiment.service import SentimentService
    from app.domain.sentiment import router as routes

    root = _mini_training_dataset(tmp_path / "dataset")
    artifact = tmp_path / "artifact"
    train_baseline(root, artifact, min_df=1, max_features=2000)
    service = SentimentService(artifact)
    result = service.analyze("일하기 좋아요", top_k=3)
    assert result.model_version.startswith("kote-baseline-")
    assert len(result.emotions) <= 3
    assert result.policy_version == "kote-polarity-v1.1"
    assert len(result.content_hash) == 64
    assert service.status().state == "READY"

    app = FastAPI()
    app.include_router(routes.router, prefix="/sentiment")
    app.dependency_overrides[routes.get_sentiment_service] = lambda: service
    monkeypatch.setattr(routes.settings, "internal_api_key", "test-key")
    client = TestClient(app)
    headers = {"X-Internal-API-Key": "test-key"}
    assert client.post("/sentiment/analyze", json={"text": "좋아요"}).status_code == 401
    response = client.post("/sentiment/analyze", json={"text": "좋아요", "topK": 3}, headers=headers)
    assert response.status_code == 200
    assert response.json()["modelVersion"] == result.model_version
    assert client.post("/sentiment/analyze", json={"text": "  "}, headers=headers).status_code == 422
    assert client.post("/sentiment/analyze", json={"text": "x" * 5001}, headers=headers).status_code == 422
    assert client.post("/sentiment/analyze/batch", json={"texts": ["a"] * 33}, headers=headers).status_code == 422
    batch = client.post("/sentiment/analyze/batch", json={"texts": ["좋아요", "싫어요"]}, headers=headers)
    assert batch.status_code == 200
    assert [r["contentHash"] for r in batch.json()] == [service.analyze(t).content_hash for t in ["좋아요", "싫어요"]]


def test_missing_model_api_health_is_200_and_inference_is_503(tmp_path, monkeypatch):
    from app.domain.sentiment.service import SentimentService
    from app.domain.sentiment import router as routes
    app = FastAPI()
    app.include_router(routes.router, prefix="/sentiment")
    app.dependency_overrides[routes.get_sentiment_service] = lambda: SentimentService(tmp_path / "missing")
    monkeypatch.setattr(routes.settings, "internal_api_key", "test-key")
    client = TestClient(app)
    assert client.get("/sentiment/health").json()["state"] == "UNAVAILABLE"
    response = client.post("/sentiment/analyze", json={"text": "좋아요"}, headers={"X-Internal-API-Key": "test-key"})
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "SENTIMENT_MODEL_UNAVAILABLE"
    assert str(tmp_path) not in response.text


def test_corrupt_artifact_fails_clear(tmp_path):
    from app.domain.sentiment.service import SentimentService
    (tmp_path / "metadata.json").write_text("broken", encoding="utf-8")
    assert SentimentService(tmp_path).status().state == "FAILED"
