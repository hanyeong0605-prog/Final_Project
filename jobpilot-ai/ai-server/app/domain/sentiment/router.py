"""Backend-only sentiment endpoints. No user content is logged or persisted here."""
from secrets import compare_digest
from fastapi import APIRouter, Depends, Header, HTTPException

from app.core.config import settings
from app.domain.sentiment.schemas import AnalyzeRequest, BatchRequest, ModelStatus, SentimentAnalysis
from app.domain.sentiment.service import SentimentService, SentimentUnavailableError, get_sentiment_service

router = APIRouter()


def require_internal_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    expected = settings.internal_api_key
    if not expected or not compare_digest((x_internal_api_key or "").encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="Invalid internal API key")


@router.get("/health", response_model=ModelStatus)
def health(service: SentimentService = Depends(get_sentiment_service)):
    return service.status()


@router.get("/model", response_model=ModelStatus, dependencies=[Depends(require_internal_key)])
def model(service: SentimentService = Depends(get_sentiment_service)):
    return service.status()


def _analyze(service, text, top_k):
    try:
        return service.analyze(text, top_k)
    except SentimentUnavailableError:
        raise HTTPException(status_code=503, detail={"code": "SENTIMENT_MODEL_UNAVAILABLE"}) from None


@router.post("/analyze", response_model=SentimentAnalysis, dependencies=[Depends(require_internal_key)])
def analyze(request: AnalyzeRequest, service: SentimentService = Depends(get_sentiment_service)):
    return _analyze(service, request.text, request.top_k)


@router.post("/analyze/batch", response_model=list[SentimentAnalysis], dependencies=[Depends(require_internal_key)])
def batch(request: BatchRequest, service: SentimentService = Depends(get_sentiment_service)):
    return [_analyze(service, text, request.top_k) for text in request.texts]
