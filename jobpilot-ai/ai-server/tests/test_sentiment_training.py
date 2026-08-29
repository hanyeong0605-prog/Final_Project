import json
import hashlib
from pathlib import Path

import pytest

from app.domain.sentiment.labels import KOTE_LABELS


def _write_split(path: Path, prefix: str) -> None:
    lines = []
    for index, label in enumerate(KOTE_LABELS):
        lines.append(f"{prefix}-{index}-a\t{label} 표현입니다\t{index}")
        lines.append(f"{prefix}-{index}-b\t정말 {label} 느낌이에요\t{index}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _mini_training_dataset(root: Path) -> Path:
    root.mkdir()
    _write_split(root / "train.tsv", "train")
    _write_split(root / "val.tsv", "val")
    _write_split(root / "test.tsv", "test")
    (root / "manifest.json").write_text(
        json.dumps(
            {
                "repository": "fixture://kote-mini",
                "resolved_commit": "f" * 40,
                "files": {},
            }
        ),
        encoding="utf-8",
    )
    return root


def test_baseline_writes_reloadable_versioned_artifact(tmp_path):
    from ml.sentiment.train_baseline import train_baseline

    dataset_root = _mini_training_dataset(tmp_path / "dataset")
    output_dir = tmp_path / "artifact"

    result = train_baseline(dataset_root, output_dir, min_df=1, max_features=2_000)

    metadata = json.loads((output_dir / "metadata.json").read_text(encoding="utf-8"))
    thresholds = json.loads((output_dir / "thresholds.json").read_text(encoding="utf-8"))
    labels = json.loads((output_dir / "labels.json").read_text(encoding="utf-8"))
    assert result.output_dir == output_dir
    assert metadata["model_type"] == "tfidf-logistic-ovr"
    assert metadata["label_count"] == 44
    assert metadata["source_commit"] == "f" * 40
    assert metadata["split_counts"] == {"train": 88, "validation": 88}
    assert labels == list(KOTE_LABELS)
    assert len(thresholds) == 44
    assert all(0.20 <= value <= 0.70 for value in thresholds)
    assert (output_dir / "model.joblib").is_file()


def test_training_does_not_read_test_split_for_model_selection(tmp_path, monkeypatch):
    from ml.sentiment import train_baseline as module

    dataset_root = _mini_training_dataset(tmp_path / "dataset")
    accessed = []
    real_load_split = module.load_split

    def tracked_load_split(path: Path):
        accessed.append(path.name)
        if path.name == "test.tsv":
            raise AssertionError("test labels must not influence training or thresholds")
        return real_load_split(path)

    monkeypatch.setattr(module, "load_split", tracked_load_split)

    module.train_baseline(dataset_root, tmp_path / "artifact", min_df=1, max_features=2_000)

    assert accessed == ["train.tsv", "val.tsv"]


def test_evaluation_reports_metrics_without_changing_artifact(tmp_path):
    from ml.sentiment.train_baseline import train_baseline
    from ml.sentiment.evaluate import evaluate_artifact, write_report

    root = _mini_training_dataset(tmp_path / "dataset")
    artifact = tmp_path / "artifact"
    train_baseline(root, artifact, min_df=1, max_features=2_000)
    before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in artifact.iterdir()}
    report = evaluate_artifact(artifact, root, split="test")
    assert report.sample_count == 88
    assert 0 <= report.micro_f1 <= 1
    assert 0 <= report.macro_f1 <= 1
    assert len(report.per_label) == 44
    assert report.cpu_latency_p95_ms >= 0
    assert report.text_overlap_with_train == 88
    assert before == {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in artifact.iterdir()}
    paths = write_report(report, tmp_path / "reports")
    assert all(p.is_file() for p in paths)
    assert json.loads(paths[0].read_text(encoding="utf-8"))["sample_count"] == 88


def test_evaluation_rejects_misaligned_labels_before_loading_weights(tmp_path):
    from ml.sentiment.evaluate import evaluate_artifact

    artifact = tmp_path / "artifact"
    artifact.mkdir()
    (artifact / "labels.json").write_text('["wrong"]', encoding="utf-8")
    with pytest.raises(ValueError, match="label order"):
        evaluate_artifact(artifact, tmp_path / "dataset", split="test")
