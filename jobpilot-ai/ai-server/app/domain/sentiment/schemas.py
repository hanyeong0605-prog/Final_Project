"""Internal sentiment contract; scores are independent, not percentages."""
from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from pydantic.alias_generators import to_camel

Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=5000)]


class Contract(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, protected_namespaces=())


class AnalyzeRequest(Contract):
    text: Text
    top_k: int = Field(default=5, ge=1, le=44)


class BatchRequest(Contract):
    texts: list[Text] = Field(min_length=1, max_length=32)
    top_k: int = Field(default=5, ge=1, le=44)


class Emotion(Contract):
    label: str
    score: float = Field(ge=0, le=1)


class Polarity(Contract):
    label: Literal["POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"]
    positive: float
    neutral: float
    negative: float


class SentimentAnalysis(Contract):
    model_version: str
    policy_version: str
    content_hash: str
    emotions: list[Emotion]
    polarity: Polarity


class ModelStatus(Contract):
    state: Literal["READY", "UNAVAILABLE", "FAILED"]
    model_version: str | None = None
