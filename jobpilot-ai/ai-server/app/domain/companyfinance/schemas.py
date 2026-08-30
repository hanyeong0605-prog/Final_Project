from typing import Literal
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class Contract(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, protected_namespaces=())


class GrowthFeatures(Contract):
    revenue_growth_1y: float
    revenue_growth_3y: float
    operating_margin: float
    operating_margin_change: float
    debt_ratio: float
    debt_ratio_change: float
    operating_cashflow_ratio: float
    cashflow_ratio_change: float
    profitable: int = Field(ge=0, le=1)
    size_bucket: Literal["SMALL", "MEDIUM", "LARGE", "UNKNOWN"]


class GrowthPrediction(Contract):
    model_version: str
    validated: bool
    growth_probability: float = Field(ge=0, le=1)
    profitability_improvement_probability: float = Field(ge=0, le=1)
    stability_risk_probability: float = Field(ge=0, le=1)
    expected_revenue_growth: float
    outlook: Literal["POSITIVE", "CAUTION", "NEGATIVE"]
    confidence: Literal["HIGH", "MEDIUM", "LOW"]
