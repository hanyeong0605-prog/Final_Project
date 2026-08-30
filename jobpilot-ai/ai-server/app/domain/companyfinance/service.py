from functools import lru_cache
from pathlib import Path

from app.core.config import settings
from ml.company_growth_model import CompanyGrowthModel


class GrowthModelUnavailableError(RuntimeError):
    pass


class CompanyGrowthService:
    def __init__(self, artifact_dir: Path):
        self.artifact_dir = artifact_dir
        self.model = None
        self.failure = False
        try:
            if (artifact_dir / "model.joblib").is_file():
                self.model = CompanyGrowthModel(artifact_dir)
        except Exception:
            self.failure = True

    def status(self) -> dict:
        state = "FAILED" if self.failure else "READY" if self.model else "UNAVAILABLE"
        version = self.model.metadata["model_version"] if self.model else None
        return {"state": state, "modelVersion": version}

    def predict(self, features: dict) -> dict:
        if self.model is None:
            raise GrowthModelUnavailableError()
        return self.model.predict(features)


@lru_cache
def get_company_growth_service() -> CompanyGrowthService:
    return CompanyGrowthService(Path(settings.company_growth_model_dir))
