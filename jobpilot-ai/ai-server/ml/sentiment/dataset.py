"""Strict parser and integrity checks for commit-pinned KOTE TSV splits."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path

from app.domain.sentiment.labels import KOTE_LABELS


OFFICIAL_SPLIT_COUNTS = {"train": 40_000, "validation": 5_000, "test": 5_000}
_SPLIT_FILES = {"train": "train.tsv", "validation": "val.tsv", "test": "test.tsv"}


@dataclass(frozen=True)
class KoteExample:
    id: str
    text: str
    labels: tuple[int, ...]


@dataclass(frozen=True)
class DatasetSummary:
    split_counts: dict[str, int]
    label_counts: dict[int, int]


def _parse_labels(raw: str) -> tuple[int, ...]:
    parts = [part.strip() for part in raw.split(",") if part.strip()]
    if not parts or any(not part.isdecimal() for part in parts):
        raise ValueError("labels must be comma-separated integer indexes")
    labels = tuple(sorted({int(part) for part in parts}))
    if any(label < 0 or label >= len(KOTE_LABELS) for label in labels):
        raise ValueError("label index must be between 0 and 43")
    return labels


def load_split(path: Path) -> list[KoteExample]:
    """Load one split and reject malformed or ambiguous training examples."""

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.reader(handle, delimiter="\t")
        rows: list[KoteExample] = []
        seen_ids: set[str] = set()
        for line_number, raw in enumerate(reader, start=1):
            if len(raw) != 3:
                raise ValueError(
                    f"expected 3 tab-separated fields at line {line_number} in {path.name}"
                )
            identifier, text, raw_labels = (value.strip() for value in raw)
            if not identifier:
                raise ValueError(f"blank ID at line {line_number} in {path.name}")
            if identifier in seen_ids:
                raise ValueError(f"duplicate ID {identifier!r} in {path.name}")
            if not text:
                raise ValueError(f"blank text for ID {identifier!r} in {path.name}")
            seen_ids.add(identifier)
            rows.append(KoteExample(identifier, text, _parse_labels(raw_labels)))
    return rows


def validate_dataset(
    root: Path,
    expected_counts: dict[str, int] | None = OFFICIAL_SPLIT_COUNTS,
) -> DatasetSummary:
    split_rows = {name: load_split(root / filename) for name, filename in _SPLIT_FILES.items()}
    split_counts = {name: len(rows) for name, rows in split_rows.items()}
    if expected_counts is not None and split_counts != expected_counts:
        raise ValueError(f"unexpected split counts: expected {expected_counts}, got {split_counts}")

    owner_by_id: dict[str, str] = {}
    label_counts: Counter[int] = Counter()
    for split_name, rows in split_rows.items():
        for row in rows:
            previous = owner_by_id.setdefault(row.id, split_name)
            if previous != split_name:
                raise ValueError(
                    f"split ID overlap: {row.id!r} appears in {previous} and {split_name}"
                )
            label_counts.update(row.labels)
    return DatasetSummary(
        split_counts=split_counts,
        label_counts={index: label_counts[index] for index in range(len(KOTE_LABELS))},
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(asdict(validate_dataset(args.root)), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
