from fastapi import FastAPI
from fastapi.testclient import TestClient


class StubService:
    def status(self):
        return {"state": "READY", "modelVersion": "growth-test-v1"}

    def predict(self, features):
        return {"model_version": "growth-test-v1", "validated": True, "growth_probability": .7,
                "profitability_improvement_probability": .6, "stability_risk_probability": .2,
                "expected_revenue_growth": .08, "outlook": "POSITIVE", "confidence": "HIGH"}


def client(monkeypatch):
    from app.domain.companyfinance import router as routes
    app = FastAPI()
    app.include_router(routes.router, prefix="/company-finance")
    app.dependency_overrides[routes.get_company_growth_service] = StubService
    monkeypatch.setattr(routes.settings, "internal_api_key", "test-key")
    return TestClient(app)


def valid_features():
    return {"revenueGrowth1Y": .1, "revenueGrowth3Y": .25, "operatingMargin": .12,
            "operatingMarginChange": .02, "debtRatio": .8, "debtRatioChange": -.1,
            "operatingCashflowRatio": .09, "cashflowRatioChange": .01,
            "profitable": 1, "sizeBucket": "MEDIUM"}


def test_predict_requires_complete_three_year_snapshot_and_internal_key(monkeypatch):
    api = client(monkeypatch)
    headers = {"X-Internal-API-Key": "test-key"}
    assert api.post("/company-finance/predict", json=valid_features()).status_code == 401
    incomplete = valid_features()
    incomplete.pop("revenueGrowth3Y")
    assert api.post("/company-finance/predict", json=incomplete, headers=headers).status_code == 422
    response = api.post("/company-finance/predict", json=valid_features(), headers=headers)
    assert response.status_code == 200
    assert response.json()["validated"] is True
    assert response.json()["modelVersion"] == "growth-test-v1"
