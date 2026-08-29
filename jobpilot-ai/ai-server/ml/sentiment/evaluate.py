"""Evaluate a trusted local baseline without fitting or tuning on test labels.

The report distinguishes ID leakage (an error) from repeated source texts
(reported as a limitation). Never load joblib files supplied by end users.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import platform
from dataclasses import asdict, dataclass
from pathlib import Path
from time import perf_counter

import joblib
import numpy as np
from sklearn.metrics import accuracy_score, f1_score, hamming_loss, precision_recall_fscore_support

from app.domain.sentiment.labels import KOTE_LABELS
from ml.sentiment.dataset import load_split
from ml.sentiment.artifact import read_manifest, write_json


@dataclass(frozen=True)
class EvaluationReport:
    model_version: str
    split: str
    sample_count: int
    micro_f1: float
    macro_f1: float
    subset_accuracy: float
    hamming_loss: float
    per_label: list[dict]
    cpu_latency_p50_ms: float
    cpu_latency_p95_ms: float
    artifact_bytes: int
    source_commit: str
    text_overlap_with_train: int
    runtime: str


def evaluate_artifact(artifact_dir: Path, dataset_root: Path, split: str = "test") -> EvaluationReport:
    labels = json.loads((artifact_dir / "labels.json").read_text(encoding="utf-8"))
    if labels != list(KOTE_LABELS):
        raise ValueError("artifact label order differs from canonical KOTE labels")
    if split not in {"test", "validation"}:
        raise ValueError("split must be test or validation")
    metadata = json.loads((artifact_dir / "metadata.json").read_text(encoding="utf-8"))
    if metadata["model_type"] != "tfidf-logistic-ovr":
        raise ValueError("this evaluator supports baseline artifacts only")
    thresholds = np.asarray(json.loads((artifact_dir / "thresholds.json").read_text(encoding="utf-8")))
    if thresholds.shape != (44,) or not np.isfinite(thresholds).all() or ((thresholds < 0) | (thresholds > 1)).any():
        raise ValueError("invalid artifact thresholds")
    manifest = read_manifest(dataset_root)
    if manifest["resolved_commit"] != metadata["source_commit"]:
        raise ValueError("artifact source commit differs from dataset")
    # Check pinned bytes before evaluation; a manifest must not mask local edits.
    for name, record in manifest.get("files", {}).items():
        if name not in {"train.tsv", "val.tsv", "test.tsv"}:
            raise ValueError("unexpected dataset manifest filename")
        if hashlib.sha256((dataset_root / name).read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError("dataset checksum mismatch")
    train = load_split(dataset_root / "train.tsv")
    validation = load_split(dataset_root / "val.tsv")
    rows = load_split(dataset_root / "test.tsv") if split == "test" else validation
    if not rows:
        raise ValueError("evaluation split is empty")
    forbidden_ids = {row.id for row in train}
    if split == "test":
        forbidden_ids.update(row.id for row in validation)
    if forbidden_ids.intersection(row.id for row in rows):
        raise ValueError("evaluation split ID overlap")
    model = joblib.load(artifact_dir / "model.joblib")
    texts = [row.text for row in rows]
    probabilities = np.asarray(model.predict_proba(texts))
    if probabilities.shape != (len(rows), 44) or not np.isfinite(probabilities).all():
        raise ValueError("invalid prediction dimensions or nonfinite scores")
    expected = np.zeros((len(rows), 44), dtype=np.int8)
    for i, row in enumerate(rows):
        expected[i, list(row.labels)] = 1
    predicted = probabilities >= thresholds
    precision, recall, f1, support = precision_recall_fscore_support(expected, predicted, zero_division=0)
    per_label = [dict(label=label, precision=float(precision[i]), recall=float(recall[i]),
                      f1=float(f1[i]), support=int(support[i])) for i, label in enumerate(labels)]
    # Warmed single-request inference, not batch throughput or model loading time.
    model.predict_proba([texts[0]])
    latencies = []
    for i in range(50):
        start = perf_counter()
        model.predict_proba([texts[i % len(texts)]])
        latencies.append((perf_counter() - start) * 1000)
    train_texts = {row.text for row in train}
    return EvaluationReport(
        model_version=metadata["model_version"], split=split, sample_count=len(rows),
        micro_f1=float(f1_score(expected, predicted, average="micro", zero_division=0)),
        macro_f1=float(f1_score(expected, predicted, average="macro", zero_division=0)),
        subset_accuracy=float(accuracy_score(expected, predicted)),
        hamming_loss=float(hamming_loss(expected, predicted)), per_label=per_label,
        cpu_latency_p50_ms=float(np.percentile(latencies, 50)),
        cpu_latency_p95_ms=float(np.percentile(latencies, 95)),
        artifact_bytes=sum(p.stat().st_size for p in artifact_dir.iterdir() if p.is_file()),
        source_commit=manifest["resolved_commit"],
        text_overlap_with_train=sum(row.text in train_texts for row in rows),
        runtime=f"Python {platform.python_version()} / {platform.system()} {platform.machine()}",
    )


def write_report(report: EvaluationReport, directory: Path) -> tuple[Path, Path, Path]:
    directory.mkdir(parents=True, exist_ok=True)
    stem = f"{report.model_version}-{report.split}"
    json_path, csv_path, md_path = (directory / f"{stem}{suffix}" for suffix in (".json", ".csv", ".md"))
    write_json(json_path, asdict(report))
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["label", "precision", "recall", "f1", "support"])
        writer.writeheader()
        writer.writerows(report.per_label)
    lines = [f"# {report.model_version}: {report.split}", "", f"Samples: {report.sample_count}",
             f"Macro-F1: {report.macro_f1:.6f}", f"Micro-F1: {report.micro_f1:.6f}",
             f"Subset accuracy: {report.subset_accuracy:.6f}", f"Hamming loss: {report.hamming_loss:.6f}",
             f"CPU single-request p50/p95: {report.cpu_latency_p50_ms:.3f}/{report.cpu_latency_p95_ms:.3f} ms",
             f"Artifact bytes: {report.artifact_bytes}", f"Source: {report.source_commit}",
             f"Exact text overlap with train: {report.text_overlap_with_train}", f"Runtime: {report.runtime}",
             "", "## Lowest per-label F1", "", "| Label | F1 | Support |", "|---|---:|---:|"]
    lines.extend(f"| {r['label']} | {r['f1']:.4f} | {r['support']} |" for r in sorted(report.per_label, key=lambda r: r["f1"])[:10])
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return json_path, csv_path, md_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--split", choices=["test", "validation"], default="test")
    parser.add_argument("--reports", type=Path, required=True)
    args = parser.parse_args()
    report = evaluate_artifact(args.artifact, args.data, args.split)
    write_report(report, args.reports)
    print(json.dumps({k: v for k, v in asdict(report).items() if k != "per_label"}, indent=2))


if __name__ == "__main__":
    main()
