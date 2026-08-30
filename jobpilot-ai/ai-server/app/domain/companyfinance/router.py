from secrets import compare_digest
from fastapi import APIRouter, Depends, Header, HTTPException

from app.core.config import settings
from app.domain.companyfinance.schemas import GrowthFeatures, GrowthPrediction
from app.domain.companyfinance.service import CompanyGrowthService, GrowthModelUnavailableError, get_company_growth_service

router = APIRouter()


def require_internal_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    expected = settings.internal_api_key
    if not expected or not compare_digest((x_internal_api_key or "").encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="Invalid internal API key")


@router.get("/health")
def health(service: CompanyGrowthService = Depends(get_company_growth_service)):
    return service.status()


@router.post("/predict", response_model=GrowthPrediction, dependencies=[Depends(require_internal_key)])
def predict(request: GrowthFeatures, service: CompanyGrowthService = Depends(get_company_growth_service)):
    try:
        return service.predict(request.model_dump())
    except GrowthModelUnavailableError:
        raise HTTPException(status_code=503, detail={"code": "COMPANY_GROWTH_MODEL_UNAVAILABLE"}) from None
