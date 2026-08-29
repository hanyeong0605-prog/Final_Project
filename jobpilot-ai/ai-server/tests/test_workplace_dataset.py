from dataclasses import replace

import pytest

from ml.sentiment.workplace_dataset import LABELS, build_candidates, validate


def test_candidate_set_is_balanced_and_unique():
    rows = build_candidates()
    summary = validate(rows)
    assert summary["count"] == 240
    assert summary["labels"] == {label: 60 for label in LABELS}
    assert summary["verified"] == 0


def test_unverified_candidates_cannot_be_final_evaluation_data():
    with pytest.raises(ValueError, match="unverified"):
        validate(build_candidates(), final_evaluation=True)

    verified = [replace(row, review_status="VERIFIED") for row in build_candidates()]
    assert validate(verified, final_evaluation=True)["verified"] == 240
