"""Shared, non-text artifact metadata helpers."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def read_manifest(dataset_root: Path) -> dict[str, Any]:
    path = dataset_root / "manifest.json"
    if not path.is_file():
        raise ValueError(f"dataset manifest is missing: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    commit = payload.get("resolved_commit")
    if not isinstance(commit, str) or len(commit) != 40:
        raise ValueError("dataset manifest has no valid resolved_commit")
    return payload
