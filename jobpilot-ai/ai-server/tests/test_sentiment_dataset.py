import hashlib
import json

import pytest


def _tsv(identifier: str, text: str, labels: str) -> bytes:
    return f"ID\ttext\tlabels\n{identifier}\t{text}\t{labels}\n".encode("utf-8")


def test_download_manifest_records_pinned_source_and_file_integrity(tmp_path, monkeypatch):
    from ml.sentiment import download_kote

    payloads = {
        "train.tsv": _tsv("train-1", "좋아요", "[42]"),
        "val.tsv": _tsv("val-1", "보통이에요", "[24]"),
        "test.tsv": _tsv("test-1", "싫어요", "[22]"),
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

    payload = b"ID\ttext\tlabels\n1\tfirst\t[1]\n2\tsecond\t[2]"
    monkeypatch.setattr(download_kote, "_resolve_commit", lambda source_ref: "b" * 40)
    monkeypatch.setattr(download_kote, "_download_bytes", lambda url: payload)

    manifest = download_kote.download_dataset(tmp_path)

    assert manifest["files"]["train.tsv"]["rows"] == 2
