from typing import Any

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from app.core.config import settings
from app.domain.matching.service import model_status, retrain, score


router = APIRouter()


class MatchCandidate(BaseModel):
    skill_coverage: float = Field(ge=0, le=1)
    certificate_coverage: float = Field(ge=0, le=1)
    experience_match: float = Field(ge=0, le=1)
    education_match: float = Field(ge=0, le=1)
    rule_readiness: float = Field(ge=0, le=100)
    missing_required_count: int = Field(ge=0)
    target_text: str = ""
    job_text: str = ""


class ScoreRequest(BaseModel):
    candidates: list[MatchCandidate]


def _require_internal_key(value: str | None) -> None:
    if not settings.internal_api_key or value != settings.internal_api_key:
        raise HTTPException(status_code=401, detail="Invalid internal API key")


@router.get("/status")
def status() -> dict[str, Any]:
    return model_status()


@router.post("/retrain")
def train(x_internal_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    _require_internal_key(x_internal_api_key)
    return retrain()


@router.post("/score-batch")
def score_batch(request: ScoreRequest, x_internal_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    _require_internal_key(x_internal_api_key)
    return score([candidate.model_dump() for candidate in request.candidates])
