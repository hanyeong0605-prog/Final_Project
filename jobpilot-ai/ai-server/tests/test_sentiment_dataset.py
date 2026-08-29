import hashlib
import json
from pathlib import Path

import pytest


def _tsv(identifier: str, text: str, labels: str) -> bytes:
    return f"{identifier}\t{text}\t{labels}\n".encode("utf-8")


def test_download_manifest_records_pinned_source_and_file_integrity(tmp_path, monkeypatch):
    from ml.sentiment import download_kote

    payloads = {
        "train.tsv": _tsv("train-1", "좋아요", "42"),
        "val.tsv": _tsv("val-1", "보통이에요", "24"),
        "test.tsv": _tsv("test-1", "싫어요", "22"),
    }
    pinned_commit = "a" * 40

    monkeypatch.setattr(download_kote, "_resolve_commit", lambda source_ref: pinned_commit)
    monkeypatch.setattr(
        download_kote,
        "_download_bytes",
        lambda url: payloads[url.rsplit("/", 1)[-1]],
    )

    manifest = download_kote.download_dataset(tmp_path)

    assert manifest["repository"] == "https://github.com/searle-j/KOTE"
    assert manifest["resolved_commit"] == pinned_commit
    assert set(manifest["files"]) == {"train.tsv", "val.tsv", "test.tsv"}
    assert manifest["files"]["train.tsv"] == {
        "sha256": hashlib.sha256(payloads["train.tsv"]).hexdigest(),
        "bytes": len(payloads["train.tsv"]),
        "rows": 1,
    }
    assert (tmp_path / "train.tsv").read_bytes() == payloads["train.tsv"]
    assert json.loads((tmp_path / "manifest.json").read_text(encoding="utf-8")) == manifest


def test_download_rejects_unpinned_or_malformed_commit_before_fetch(tmp_path, monkeypatch):
    from ml.sentiment import download_kote

    monkeypatch.setattr(download_kote, "_resolve_commit", lambda source_ref: "main")

    def unexpected_download(url: str) -> bytes:
        pytest.fail(f"malformed commit must stop before download: {url}")

    monkeypatch.setattr(download_kote, "_download_bytes", unexpected_download)

    with pytest.raises(ValueError, match="40-character hexadecimal"):
        download_kote.download_dataset(tmp_path)


def test_download_counts_last_row_without_trailing_newline(tmp_path, monkeypatch):
    from ml.sentiment import download_kote

    payload = b"1\tfirst\t1\n2\tsecond\t2"
    monkeypatch.setattr(download_kote, "_resolve_commit", lambda source_ref: "b" * 40)
    monkeypatch.setattr(download_kote, "_download_bytes", lambda url: payload)

    manifest = download_kote.download_dataset(tmp_path)

    assert manifest["files"]["train.tsv"]["rows"] == 2


def test_load_split_preserves_multi_labels_and_utf8_text(tmp_path):
    from ml.sentiment.dataset import load_split

    fixture = tmp_path / "test.tsv"
    fixture.write_text("test-1\t환경이 불편해요\t0,22\n", encoding="utf-8")

    rows = load_split(fixture)

    assert rows[0].id == "test-1"
    assert rows[0].text == "환경이 불편해요"
    assert rows[0].labels == (0, 22)


def test_load_split_rejects_out_of_range_label(tmp_path):
    from ml.sentiment.dataset import load_split

    path = tmp_path / "train.tsv"
    path.write_text("1\t문장\t44\n", encoding="utf-8")

    with pytest.raises(ValueError, match="label index"):
        load_split(path)


def test_load_split_rejects_duplicate_ids(tmp_path):
    from ml.sentiment.dataset import load_split

    path = tmp_path / "train.tsv"
    path.write_text(
        "1\t첫 문장\t1\n1\t둘째 문장\t2\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="duplicate ID"):
        load_split(path)


def test_validate_dataset_rejects_ids_shared_across_splits(tmp_path):
    from ml.sentiment.dataset import validate_dataset

    for name in ("train.tsv", "val.tsv", "test.tsv"):
        (tmp_path / name).write_text(
            "shared\t문장\t1\n",
            encoding="utf-8",
        )

    with pytest.raises(ValueError, match="split ID overlap"):
        validate_dataset(tmp_path, expected_counts=None)


def test_validate_mini_dataset_reports_split_and_label_counts():
    from ml.sentiment.dataset import validate_dataset

    root = (
        Path(__file__).parents[1]
        / "ml"
        / "sentiment"
        / "fixtures"
        / "kote-mini"
    )

    summary = validate_dataset(root, expected_counts=None)

    assert summary.split_counts == {"train": 1, "validation": 1, "test": 1}
    assert summary.label_counts[42] == 1
    assert summary.label_counts[24] == 1
    assert summary.label_counts[0] == 1
    assert summary.label_counts[22] == 1
