"""Canonical KOTE labels and versioned service-display polarity policy.

Artifact outputs are positional, so changing this order would silently attach
scores to the wrong Korean emotion. Artifacts must store and verify the same
sequence before they become READY. Positive/negative/neutral is a JobPilot
presentation policy and is never described as KOTE's original ground truth.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Mapping

KOTE_LABELS: tuple[str, ...] = (
    "불평/불만",
    "환영/호의",
    "감동/감탄",
    "지긋지긋",
    "고마움",
    "슬픔",
    "화남/분노",
    "존경",
    "기대감",
    "우쭐댐/무시함",
    "안타까움/실망",
    "비장함",
    "의심/불신",
    "뿌듯함",
    "편안/쾌적",
    "신기함/관심",
    "아껴주는",
    "부끄러움",
    "공포/무서움",
    "절망",
    "한심함",
    "역겨움/징그러움",
    "짜증",
    "어이없음",
    "없음",
    "패배/자기혐오",
    "귀찮음",
    "힘듦/지침",
    "즐거움/신남",
    "깨달음",
    "죄책감",
    "증오/혐오",
    "흐뭇함(귀여움/예쁨)",
    "당황/난처",
    "경악",
    "부담/안_내킴",
    "서러움",
    "재미없음",
    "불쌍함/연민",
    "놀람",
    "행복",
    "불안/걱정",
    "기쁨",
    "안심/신뢰",
)

_DEFAULT_POLICY_PATH = Path(__file__).with_name("polarity-map.v1.json")


@dataclass(frozen=True)
class PolarityPolicy:
    version: str
    positive: tuple[str, ...]
    negative: tuple[str, ...]
    contextual: tuple[str, ...]
    neutral: tuple[str, ...]


@dataclass(frozen=True)
class PolarityScores:
    label: Literal["POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"]
    positive: float
    neutral: float
    negative: float
    policy_version: str


def load_polarity_policy(path: Path = _DEFAULT_POLICY_PATH) -> PolarityPolicy:
    payload = json.loads(path.read_text(encoding="utf-8"))
    policy = PolarityPolicy(
        version=str(payload["version"]),
        positive=tuple(payload["positive"]),
        negative=tuple(payload["negative"]),
        contextual=tuple(payload["contextual"]),
        neutral=tuple(payload["neutral"]),
    )
    assigned = [*policy.positive, *policy.negative, *policy.contextual, *policy.neutral]
    if len(assigned) != len(KOTE_LABELS) or len(set(assigned)) != len(KOTE_LABELS):
        raise ValueError("polarity policy must assign every KOTE label exactly once")
    if set(assigned) != set(KOTE_LABELS):
        raise ValueError("polarity policy must assign every KOTE label exactly once")
    return policy


def aggregate_polarity(
    scores: Mapping[str, float],
    policy: PolarityPolicy | None = None,
) -> PolarityScores:
    unknown = set(scores) - set(KOTE_LABELS)
    if unknown:
        raise ValueError(f"unknown KOTE labels: {sorted(unknown)}")
    if any(not math.isfinite(value) or value < 0.0 or value > 1.0 for value in scores.values()):
        raise ValueError("emotion scores must be between 0 and 1")

    selected_policy = policy or load_polarity_policy()

    def highest(labels: tuple[str, ...]) -> float:
        return max((float(scores.get(label, 0.0)) for label in labels), default=0.0)

    positive = highest(selected_policy.positive)
    negative = highest(selected_policy.negative)
    neutral = max(highest(selected_policy.neutral), highest(selected_policy.contextual))
    # Independent one-vs-rest scores are commonly high at the same time. MIXED therefore
    # requires a small margin, instead of collapsing every text with two scores over .40.
    if positive >= 0.40 and negative >= 0.40 and abs(positive - negative) < 0.10:
        label: Literal["POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"] = "MIXED"
    elif positive > max(neutral, negative):
        label = "POSITIVE"
    elif negative > max(neutral, positive):
        label = "NEGATIVE"
    else:
        label = "NEUTRAL"
    return PolarityScores(
        label=label,
        positive=positive,
        neutral=neutral,
        negative=negative,
        policy_version=selected_policy.version,
    )
