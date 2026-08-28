import json

import pytest

from app.domain.sentiment.labels import KOTE_LABELS


def test_default_policy_covers_every_kote_label_exactly_once():
    from app.domain.sentiment.labels import load_polarity_policy

    policy = load_polarity_policy()
    assigned = [
        *policy.positive,
        *policy.negative,
        *policy.contextual,
        *policy.neutral,
    ]

    assert len(assigned) == 44
    assert len(set(assigned)) == 44
    assert set(assigned) == set(KOTE_LABELS)
    assert policy.version == "kote-polarity-v1"


def test_policy_rejects_duplicate_and_missing_labels(tmp_path):
    from app.domain.sentiment.labels import load_polarity_policy

    broken = {
        "version": "broken",
        "positive": [KOTE_LABELS[0]],
        "negative": [KOTE_LABELS[0]],
        "contextual": [],
        "neutral": [],
    }
    path = tmp_path / "broken.json"
    path.write_text(json.dumps(broken, ensure_ascii=False), encoding="utf-8")

    with pytest.raises(ValueError, match="exactly once"):
        load_polarity_policy(path)


def test_mixed_when_positive_and_negative_are_both_strong():
    from app.domain.sentiment.labels import aggregate_polarity

    result = aggregate_polarity({"기쁨": 0.82, "불평/불만": 0.76})

    assert result.label == "MIXED"
    assert result.positive == pytest.approx(0.82)
    assert result.negative == pytest.approx(0.76)


def test_no_emotion_maps_to_neutral():
    from app.domain.sentiment.labels import aggregate_polarity

    result = aggregate_polarity({"없음": 0.91})

    assert result.label == "NEUTRAL"
    assert result.neutral == pytest.approx(0.91)


def test_contextual_emotion_does_not_become_positive_without_evidence():
    from app.domain.sentiment.labels import aggregate_polarity

    result = aggregate_polarity({"놀람": 0.88})

    assert result.label == "NEUTRAL"
    assert result.neutral == pytest.approx(0.88)


def test_unknown_score_label_is_rejected():
    from app.domain.sentiment.labels import aggregate_polarity

    with pytest.raises(ValueError, match="unknown KOTE labels"):
        aggregate_polarity({"만족": 0.9})
