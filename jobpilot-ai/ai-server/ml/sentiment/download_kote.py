"""Download a commit-pinned copy of the public KOTE dataset.

Raw comments are intentionally kept outside Git.  The generated manifest is
the audit trail that ties a local training run to exact upstream bytes.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.request import Request, urlopen


FILES = ("train.tsv", "val.tsv", "test.tsv")
REPOSITORY = "https://github.com/searle-j/KOTE"
_COMMITS_API = "https://api.github.com/repos/searle-j/KOTE/commits/{source_ref}"
_RAW_URL = "https://raw.githubusercontent.com/searle-j/KOTE/{commit}/{name}"
_COMMIT_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")


def _download_bytes(url: str) -> bytes:
    request = Request(url, headers={"User-Agent": "JobPilot-KOTE-Downloader/1.0"})
    with urlopen(request, timeout=30) as response:  # noqa: S310 - fixed official hosts only
        return response.read()


def _resolve_commit(source_ref: str) -> str:
    payload = json.loads(_download_bytes(_COMMITS_API.format(source_ref=source_ref)))
    commit = payload.get("sha")
    if not isinstance(commit, str):
        raise ValueError("GitHub commit response did not include a SHA")
    return commit


def _row_count(payload: bytes) -> int:
    decoded = payload.decode("utf-8-sig")
    return sum(1 for row in csv.reader(io.StringIO(decoded), delimiter="\t") if row)


def _write_atomic(path: Path, payload: bytes) -> None:
    temporary = path.with_suffix(path.suffix + ".part")
    temporary.write_bytes(payload)
    temporary.replace(path)


def download_dataset(destination: Path, source_ref: str = "main") -> dict[str, Any]:
    """Download official splits and return the persisted provenance manifest."""

    commit = _resolve_commit(source_ref)
    if not _COMMIT_PATTERN.fullmatch(commit):
        raise ValueError("resolved commit must be a 40-character hexadecimal SHA")

    destination.mkdir(parents=True, exist_ok=True)
    records: dict[str, dict[str, object]] = {}
    for name in FILES:
        payload = _download_bytes(_RAW_URL.format(commit=commit, name=name))
        _write_atomic(destination / name, payload)
        records[name] = {
            "sha256": hashlib.sha256(payload).hexdigest(),
            "bytes": len(payload),
            "rows": _row_count(payload),
        }

    manifest: dict[str, Any] = {
        "repository": REPOSITORY,
        "resolved_commit": commit.lower(),
        "downloaded_at": datetime.now(timezone.utc).isoformat(),
        "files": records,
    }
    encoded = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
    _write_atomic(destination / "manifest.json", encoded)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--source-ref", default="main")
    args = parser.parse_args()
    print(json.dumps(download_dataset(args.destination, args.source_ref), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
