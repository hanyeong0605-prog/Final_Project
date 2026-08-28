# KOTE Sentiment ML Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible KOTE download, validation, training, evaluation, artifact, and FastAPI inference foundation that later review and community plans can consume.

**Architecture:** Keep raw datasets and trained weights outside Git and Docker images, while versioning provenance manifests, preprocessing code, label policy, tests, and model reports. Train a transparent TF-IDF/One-vs-Rest baseline first, expose a lazy-loaded fail-clear sentiment domain in the existing FastAPI service, and provide a separate KcELECTRA fine-tuning script for GPU execution and comparison.

**Tech Stack:** Python 3.11, FastAPI, Pydantic 2, scikit-learn 1.5, joblib 1.4, pandas 2.2, PyTorch 2.5 CPU runtime, Transformers 4.44+, pytest 8.3, Docker Compose

**Spec:** `jobpilot-ai/docs/superpowers/specs/2026-08-29-sentiment-company-review-community-design.md`

## Global Constraints

- Work only on branch `김한영브뤤취학원` and preserve unrelated local changes.
- KOTE is `PUBLIC_RESEARCH`; synthetic company/review data is never training ground truth without human validation.
- Preserve KOTE's official 40,000/5,000/5,000 train/validation/test split.
- Raw KOTE files, processed corpora, caches, and trained weights stay out of Git and Docker build context.
- Record source URL, resolved source commit, download time, SHA-256, byte size, and row count.
- Treat the 43 emotions plus `없음` as a 44-output multi-label problem.
- User-facing polarity is a versioned presentation mapping, not an original KOTE label.
- Training never runs from an HTTP endpoint or on the production EC2 host.
- Production inference must be CPU-compatible and fail clear when no artifact is mounted.
- Comments explain design decisions; full reproducibility belongs in data/model documentation.

---

## Planned File Structure

```text
jobpilot-ai/ai-server/
├─ app/
│  ├─ core/config.py                              # sentiment artifact settings
│  └─ domain/sentiment/
│     ├─ __init__.py
│     ├─ labels.py                               # canonical labels and polarity policy loader
│     ├─ schemas.py                              # HTTP request/response contracts
│     ├─ service.py                              # lazy artifact loading and inference
│     └─ router.py                               # internal sentiment endpoints
├─ ml/sentiment/
│  ├─ README.md                                  # reproducible data/train/evaluate commands
│  ├─ DATA_CARD.md                               # source, license, limitations
│  ├─ MODEL_CARD.md                              # generated/updated evaluation summary
│  ├─ polarity-map.v1.json                       # versioned presentation mapping
│  ├─ download_kote.py                           # official source downloader + manifest
│  ├─ dataset.py                                 # TSV parsing and validation
│  ├─ train_baseline.py                          # TF-IDF One-vs-Rest training
│  ├─ train_transformer.py                       # KcELECTRA GPU fine-tuning
│  ├─ evaluate.py                                # multi-label metrics and report output
│  ├─ fixtures/kote-mini/                        # tiny synthetic format fixture only
│  ├─ data/                                      # ignored raw/processed data
│  ├─ artifacts/                                 # ignored model artifacts
│  └─ reports/                                   # JSON/Markdown metrics, no weights
├─ tests/
│  ├─ test_sentiment_dataset.py
│  ├─ test_sentiment_training.py
│  ├─ test_sentiment_service.py
│  └─ test_router_sentiment.py
├─ .gitignore
└─ requirements.txt

jobpilot-ai/
├─ docker-compose.yml                            # local read-only model mount
├─ docker-compose.prod.yml                       # production read-only model mount
└─ deploy/jobpilot.env.example                   # SENTIMENT_MODEL_DIR example

.github/workflows/ci.yml                         # sentiment unit-test gate
```

---

### Task 1: KOTE provenance and deterministic downloader

**Files:**
- Modify: `jobpilot-ai/ai-server/.gitignore`
- Create: `jobpilot-ai/ai-server/ml/sentiment/download_kote.py`
- Create: `jobpilot-ai/ai-server/ml/sentiment/DATA_CARD.md`
- Create: `jobpilot-ai/ai-server/ml/sentiment/fixtures/kote-mini/train.tsv`
- Create: `jobpilot-ai/ai-server/ml/sentiment/fixtures/kote-mini/val.tsv`
- Create: `jobpilot-ai/ai-server/ml/sentiment/fixtures/kote-mini/test.tsv`
- Create: `jobpilot-ai/ai-server/tests/test_sentiment_dataset.py`

**Interfaces:**
- Produces: `download_dataset(destination: Path, source_ref: str = "main") -> dict[str, object]`
- Produces: `<destination>/manifest.json` with `repository`, `resolved_commit`, `downloaded_at`, and per-file hashes/counts.
- Consumes: official repository `https://github.com/searle-j/KOTE` only.

- [ ] **Step 1: Write the failing downloader manifest test**

```python
def test_manifest_records_hash_size_and_rows(tmp_path, monkeypatch):
    payloads = {
        "train.tsv": b"ID\ttext\tlabels\n1\t좋아요\t[42]\n",
        "val.tsv": b"ID\ttext\tlabels\n2\t보통\t[24]\n",
        "test.tsv": b"ID\ttext\tlabels\n3\t싫어요\t[22]\n",
    }
    monkeypatch.setattr(download_kote, "_resolve_commit", lambda _: "a" * 40)
    monkeypatch.setattr(download_kote, "_download_bytes", lambda url: payloads[url.rsplit("/", 1)[-1]])

    manifest = download_kote.download_dataset(tmp_path)

    assert manifest["resolved_commit"] == "a" * 40
    assert manifest["files"]["train.tsv"]["rows"] == 1
    assert len(manifest["files"]["train.tsv"]["sha256"]) == 64
```

- [ ] **Step 2: Run the test and confirm the module is missing**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_dataset.py::test_manifest_records_hash_size_and_rows -v`

Expected: FAIL with import/module error for `ml.sentiment.download_kote`.

- [ ] **Step 3: Implement downloader and atomic manifest write**

```python
FILES = ("train.tsv", "val.tsv", "test.tsv")
REPOSITORY = "https://github.com/searle-j/KOTE"

def download_dataset(destination: Path, source_ref: str = "main") -> dict[str, object]:
    commit = _resolve_commit(source_ref)
    destination.mkdir(parents=True, exist_ok=True)
    records = {}
    for name in FILES:
        payload = _download_bytes(f"https://raw.githubusercontent.com/searle-j/KOTE/{commit}/{name}")
        target = destination / name
        temporary = target.with_suffix(target.suffix + ".part")
        temporary.write_bytes(payload)
        temporary.replace(target)
        decoded = payload.decode("utf-8-sig")
        parsed_rows = list(csv.DictReader(io.StringIO(decoded), delimiter="\t"))
        records[name] = {
            "sha256": hashlib.sha256(payload).hexdigest(),
            "bytes": len(payload),
            "rows": len(parsed_rows),
        }
    manifest = {
        "repository": REPOSITORY,
        "resolved_commit": commit,
        "downloaded_at": datetime.now(timezone.utc).isoformat(),
        "files": records,
    }
    (destination / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest
```

Use the GitHub commits API in `_resolve_commit` and `urllib.request.urlopen` with a 30-second timeout in `_download_bytes`. Reject non-40-character hexadecimal commit values before downloading files.

- [ ] **Step 4: Exclude generated data and artifacts**

Append exactly these entries to `jobpilot-ai/ai-server/.gitignore`:

```gitignore
ml/sentiment/data/
ml/sentiment/artifacts/
ml/sentiment/reports/*.json
ml/sentiment/reports/*.csv
```

- [ ] **Step 5: Document provenance and run tests**

`DATA_CARD.md` must identify the three authors and affiliations from the paper, the official repository and paper URL, 50,000 comments, 44 labels, crowdsourced five-rater annotation, official split sizes, repository MIT license, domain limitations, and the exact download command.

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_dataset.py -v`

Expected: PASS without network access.

- [ ] **Step 6: Commit**

```powershell
git add jobpilot-ai/ai-server/.gitignore jobpilot-ai/ai-server/ml/sentiment jobpilot-ai/ai-server/tests/test_sentiment_dataset.py
git commit -m "feat: add reproducible KOTE dataset download"
```

---

### Task 2: Canonical labels, TSV parsing, and dataset validation

**Files:**
- Create: `jobpilot-ai/ai-server/ml/__init__.py`
- Create: `jobpilot-ai/ai-server/ml/sentiment/__init__.py`
- Create: `jobpilot-ai/ai-server/ml/sentiment/dataset.py`
- Create: `jobpilot-ai/ai-server/app/domain/sentiment/__init__.py`
- Create: `jobpilot-ai/ai-server/app/domain/sentiment/labels.py`
- Modify: `jobpilot-ai/ai-server/tests/test_sentiment_dataset.py`

**Interfaces:**
- Produces: `KOTE_LABELS: tuple[str, ...]` with exactly 44 labels in official order.
- Produces: `KoteExample(id: str, text: str, labels: tuple[int, ...])`.
- Produces: `load_split(path: Path) -> list[KoteExample]`.
- Produces: `validate_dataset(root: Path) -> DatasetSummary`.

- [ ] **Step 1: Add failing parser and validation tests**

```python
def test_load_split_preserves_multi_labels(mini_kote_dir):
    rows = load_split(mini_kote_dir / "train.tsv")
    assert rows[0].text == "좋아요"
    assert rows[0].labels == (42,)

def test_rejects_out_of_range_label(tmp_path):
    path = tmp_path / "train.tsv"
    path.write_text("ID\ttext\tlabels\n1\t문장\t[44]\n", encoding="utf-8")
    with pytest.raises(ValueError, match="label index"):
        load_split(path)
```

- [ ] **Step 2: Run tests to observe missing parser failures**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_dataset.py -v`

Expected: FAIL because `KoteExample`, `load_split`, and `validate_dataset` do not exist.

- [ ] **Step 3: Implement strict parser**

```python
@dataclass(frozen=True)
class KoteExample:
    id: str
    text: str
    labels: tuple[int, ...]

def _parse_labels(raw: str) -> tuple[int, ...]:
    value = ast.literal_eval(raw)
    if not isinstance(value, list) or not all(isinstance(item, int) for item in value):
        raise ValueError("labels must be a list of integers")
    labels = tuple(sorted(set(value)))
    if any(label < 0 or label >= len(KOTE_LABELS) for label in labels):
        raise ValueError("label index must be between 0 and 43")
    return labels
```

`load_split` must require the exact `ID`, `text`, and `labels` columns, reject blank IDs/text, and reject duplicate IDs within a split. `validate_dataset` must require 40,000/5,000/5,000 rows for real data, verify IDs do not cross split boundaries, and return per-label counts.

- [ ] **Step 4: Run dataset unit tests**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_dataset.py -v`

Expected: PASS.

- [ ] **Step 5: Validate downloaded real data**

Run:

```powershell
cd jobpilot-ai/ai-server
python -m ml.sentiment.download_kote --destination ml/sentiment/data/raw/kote
python -m ml.sentiment.dataset --root ml/sentiment/data/raw/kote
```

Expected: `train=40000 validation=5000 test=5000 labels=44`, with no overlap error. If the official TSV encoding differs from the fixture, update the parser and fixture together, record the discovered format in `DATA_CARD.md`, and rerun all tests before continuing.

- [ ] **Step 6: Commit**

```powershell
git add jobpilot-ai/ai-server/ml jobpilot-ai/ai-server/app/domain/sentiment jobpilot-ai/ai-server/tests/test_sentiment_dataset.py
git commit -m "feat: validate KOTE multi-label corpus"
```

---

### Task 3: Versioned emotion-to-polarity presentation policy

**Files:**
- Create: `jobpilot-ai/ai-server/ml/sentiment/polarity-map.v1.json`
- Modify: `jobpilot-ai/ai-server/app/domain/sentiment/labels.py`
- Create: `jobpilot-ai/ai-server/tests/test_sentiment_labels.py`

**Interfaces:**
- Produces: `PolarityScores(label: Literal["POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"], positive: float, neutral: float, negative: float)`.
- Produces: `aggregate_polarity(scores: Mapping[str, float]) -> PolarityScores`.

- [ ] **Step 1: Write failing aggregation tests**

```python
def test_mixed_when_positive_and_negative_are_both_strong():
    result = aggregate_polarity({"기쁨": 0.82, "불평/불만": 0.76})
    assert result.label == "MIXED"

def test_no_emotion_maps_to_neutral():
    result = aggregate_polarity({"없음": 0.91})
    assert result.label == "NEUTRAL"
    assert result.neutral == pytest.approx(0.91)
```

- [ ] **Step 2: Run tests and confirm missing policy failure**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_labels.py -v`

Expected: FAIL because `aggregate_polarity` is undefined.

- [ ] **Step 3: Add explicit version-one mapping**

The JSON must contain every canonical KOTE label exactly once. Use three sets: positive, negative, and contextual. Map `없음` to neutral. Contextual labels such as `놀람`, `깨달음`, and `신기함/관심` must not contribute to positive or negative without a future human-validated policy; their unmatched mass contributes to neutral. Validate coverage and reject duplicate or unknown labels at startup.

Aggregation rules:

```python
positive = max((scores.get(label, 0.0) for label in policy.positive), default=0.0)
negative = max((scores.get(label, 0.0) for label in policy.negative), default=0.0)
neutral = max(scores.get("없음", 0.0), max_contextual)
label = (
    "MIXED" if positive >= 0.40 and negative >= 0.40
    else "POSITIVE" if positive >= max(neutral, negative)
    else "NEGATIVE" if negative >= max(neutral, positive)
    else "NEUTRAL"
)
```

- [ ] **Step 4: Test complete label coverage and aggregation**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_labels.py -v`

Expected: PASS and policy coverage equals all 44 labels.

- [ ] **Step 5: Commit**

```powershell
git add jobpilot-ai/ai-server/ml/sentiment/polarity-map.v1.json jobpilot-ai/ai-server/app/domain/sentiment/labels.py jobpilot-ai/ai-server/tests/test_sentiment_labels.py
git commit -m "feat: define sentiment polarity presentation policy"
```

---

### Task 4: Explainable baseline training artifact

**Files:**
- Create: `jobpilot-ai/ai-server/ml/sentiment/train_baseline.py`
- Create: `jobpilot-ai/ai-server/ml/sentiment/artifact.py`
- Create: `jobpilot-ai/ai-server/tests/test_sentiment_training.py`

**Interfaces:**
- Produces: `train_baseline(dataset_root: Path, output_dir: Path) -> TrainingResult`.
- Produces artifact files: `model.joblib`, `metadata.json`, `thresholds.json`, `labels.json`.
- Metadata schema includes `model_version`, `model_type`, `source_commit`, `trained_at`, `label_count`, `sklearn_version`, and split counts.

- [ ] **Step 1: Write a failing miniature training test**

```python
def test_baseline_writes_reloadable_versioned_artifact(mini_training_dataset, tmp_path):
    result = train_baseline(mini_training_dataset, tmp_path / "artifact", min_df=1)
    metadata = json.loads((result.output_dir / "metadata.json").read_text(encoding="utf-8"))
    assert metadata["model_type"] == "tfidf-logistic-ovr"
    assert metadata["label_count"] == 44
    assert (result.output_dir / "model.joblib").is_file()
```

`mini_training_dataset` must be generated by a pytest fixture with at least three examples per label: two examples containing that label and one example not containing it. This guarantees that every one of the fixed 44 binary classifiers sees both classes while keeping the fixture text project-owned and small. The fixture must write separate train, validation, and test TSV files with disjoint IDs.

- [ ] **Step 2: Run test and confirm missing trainer**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_training.py::test_baseline_writes_reloadable_versioned_artifact -v`

Expected: FAIL because the trainer does not exist.

- [ ] **Step 3: Implement baseline pipeline**

Use a `Pipeline` containing:

```python
TfidfVectorizer(
    analyzer="char_wb",
    ngram_range=(2, 5),
    min_df=3,
    max_features=120_000,
    sublinear_tf=True,
)
OneVsRestClassifier(
    LogisticRegression(
        solver="liblinear",
        class_weight="balanced",
        max_iter=500,
        random_state=42,
    ),
    n_jobs=1,
)
```

Convert label tuples to a fixed 44-column binary matrix. Tune one threshold per label on validation data by choosing the threshold from `0.20` through `0.70` in `0.05` steps that maximizes label F1; break ties toward `0.40`. Never inspect test labels during threshold selection.

- [ ] **Step 4: Run miniature training tests**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_training.py -v`

Expected: PASS in under 30 seconds on the tiny fixture.

- [ ] **Step 5: Train the real baseline outside CI**

Run:

```powershell
cd jobpilot-ai/ai-server
python -m ml.sentiment.train_baseline --data ml/sentiment/data/raw/kote --output ml/sentiment/artifacts/kote-baseline-v1
```

Expected: artifact directory contains the four declared files and no raw text.

- [ ] **Step 6: Commit code and tests, not artifacts**

```powershell
git add jobpilot-ai/ai-server/ml/sentiment/train_baseline.py jobpilot-ai/ai-server/ml/sentiment/artifact.py jobpilot-ai/ai-server/tests/test_sentiment_training.py
git commit -m "feat: train explainable KOTE baseline"
```

---

### Task 5: Evaluation reports and model acceptance gate

**Files:**
- Create: `jobpilot-ai/ai-server/ml/sentiment/evaluate.py`
- Create: `jobpilot-ai/ai-server/ml/sentiment/MODEL_CARD.md`
- Modify: `jobpilot-ai/ai-server/tests/test_sentiment_training.py`

**Interfaces:**
- Produces: `evaluate_artifact(artifact_dir: Path, dataset_root: Path, split: str) -> EvaluationReport`.
- Produces: `reports/<model-version>-test.json`, `reports/<model-version>-per-label.csv`, and rendered Markdown summary.
- Acceptance gate: valid artifact, all 44 labels, finite metrics, measured CPU latency, and no train/test ID overlap.

- [ ] **Step 1: Add failing metric and report tests**

```python
def test_report_contains_multilabel_metrics_and_latency(trained_fixture_artifact, mini_training_dataset):
    report = evaluate_artifact(trained_fixture_artifact, mini_training_dataset, split="test")
    assert 0.0 <= report.micro_f1 <= 1.0
    assert 0.0 <= report.macro_f1 <= 1.0
    assert len(report.per_label) == 44
    assert report.cpu_latency_p95_ms >= 0.0
```

- [ ] **Step 2: Run and observe missing evaluator**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_training.py -v`

Expected: FAIL because `evaluate_artifact` is undefined.

- [ ] **Step 3: Implement evaluation without changing thresholds**

Calculate micro/macro F1, per-label precision/recall/F1/support, subset accuracy, Hamming loss, and 50 warmed CPU predictions for p50/p95 latency. The evaluator must load thresholds from the artifact and must never retune them on test data.

- [ ] **Step 4: Generate and inspect the real report**

Run:

```powershell
cd jobpilot-ai/ai-server
python -m ml.sentiment.evaluate --artifact ml/sentiment/artifacts/kote-baseline-v1 --data ml/sentiment/data/raw/kote --split test --reports ml/sentiment/reports
```

Expected: JSON, CSV, and Markdown include measured values and the ten weakest labels; no example text is copied into the committed model card unless it is a project-owned fixture.

- [ ] **Step 5: Run tests and commit**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_training.py -v`

```powershell
git add jobpilot-ai/ai-server/ml/sentiment/evaluate.py jobpilot-ai/ai-server/ml/sentiment/MODEL_CARD.md jobpilot-ai/ai-server/tests/test_sentiment_training.py
git commit -m "feat: evaluate KOTE sentiment artifacts"
```

---

### Task 6: KcELECTRA fine-tuning and comparison path

**Files:**
- Create: `jobpilot-ai/ai-server/ml/sentiment/train_transformer.py`
- Create: `jobpilot-ai/ai-server/tests/test_sentiment_transformer_config.py`
- Modify: `jobpilot-ai/ai-server/ml/sentiment/README.md`

**Interfaces:**
- Produces: `build_training_arguments(output_dir: Path, epochs: int = 3) -> TrainingArguments`.
- Produces Transformer artifact with the same `metadata.json`, `thresholds.json`, and `labels.json` contract as Task 4 plus Hugging Face model/tokenizer files.
- Consumes: `beomi/KcELECTRA-base-v2022` unless model availability verification requires the documented immutable revision.

- [ ] **Step 1: Write failing configuration tests**

```python
def test_transformer_uses_multilabel_problem_and_no_test_selection(tmp_path):
    config = build_model_config()
    args = build_training_arguments(tmp_path)
    assert config.problem_type == "multi_label_classification"
    assert config.num_labels == 44
    assert args.load_best_model_at_end is True
    assert args.metric_for_best_model == "eval_macro_f1"
```

- [ ] **Step 2: Run and confirm missing trainer configuration**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_transformer_config.py -v`

Expected: FAIL because configuration builders do not exist.

- [ ] **Step 3: Implement GPU-oriented fine-tuning command**

Use `AutoTokenizer`, `AutoModelForSequenceClassification`, `Trainer`, `BCEWithLogitsLoss` through the model's multi-label problem type, max length 256, batch size 16, gradient accumulation 2, learning rate `2e-5`, three epochs, weight decay `0.01`, fixed seed 42, and validation Macro-F1 for checkpoint selection. Test data is evaluated once after selecting the best validation checkpoint.

- [ ] **Step 4: Run configuration tests without downloading a model**

Structure builders so tests inject a local `PretrainedConfig` and never access the network.

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_transformer_config.py -v`

Expected: PASS without GPU or network.

- [ ] **Step 5: Run real fine-tuning on GPU environment**

Run:

```bash
python -m ml.sentiment.train_transformer \
  --data ml/sentiment/data/raw/kote \
  --output ml/sentiment/artifacts/kote-kcelectra-v1 \
  --seed 42
```

Then evaluate with Task 5 and record baseline-versus-Transformer Macro-F1, Micro-F1, artifact size, and CPU p95 latency in `MODEL_CARD.md`. Select the Transformer only if it improves Macro-F1 and remains within measured production memory/latency limits; otherwise deploy the baseline and document the decision.

- [ ] **Step 6: Commit**

```powershell
git add jobpilot-ai/ai-server/ml/sentiment/train_transformer.py jobpilot-ai/ai-server/tests/test_sentiment_transformer_config.py jobpilot-ai/ai-server/ml/sentiment/README.md
git commit -m "feat: add KOTE transformer training path"
```

---

### Task 7: Lazy-loaded fail-clear inference service

**Files:**
- Modify: `jobpilot-ai/ai-server/app/core/config.py`
- Create: `jobpilot-ai/ai-server/app/domain/sentiment/schemas.py`
- Create: `jobpilot-ai/ai-server/app/domain/sentiment/service.py`
- Create: `jobpilot-ai/ai-server/tests/test_sentiment_service.py`

**Interfaces:**
- Produces: `SentimentService(artifact_dir: Path)`.
- Produces: `analyze(text: str, top_k: int = 5) -> SentimentAnalysis`.
- Produces: `status() -> ModelStatus` with `READY`, `UNAVAILABLE`, or `FAILED`.
- Configuration: `SENTIMENT_MODEL_DIR`, default `/models/sentiment`.

- [ ] **Step 1: Write fail-clear and inference tests**

```python
def test_missing_artifact_reports_unavailable(tmp_path):
    service = SentimentService(tmp_path / "missing")
    assert service.status().state == "UNAVAILABLE"
    with pytest.raises(SentimentUnavailableError):
        service.analyze("좋은 회사입니다")

def test_analysis_returns_versioned_emotions(fake_artifact_dir):
    result = SentimentService(fake_artifact_dir).analyze("좋은 회사입니다", top_k=3)
    assert result.model_version == "fixture-v1"
    assert len(result.emotions) <= 3
    assert result.polarity.label in {"POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"}
```

- [ ] **Step 2: Run and confirm missing service failure**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_service.py -v`

Expected: FAIL because service and schemas do not exist.

- [ ] **Step 3: Implement lazy, locked artifact loading**

The constructor stores the path but does not load weights. The first `status` or `analyze` call loads once under `threading.Lock`. Validate metadata, label count/order, thresholds, and model file before setting state `READY`. Preserve a sanitized failure reason without stack traces or filesystem secrets.

Normalize input with Unicode NFC and whitespace collapse only; do not remove Korean punctuation or slang. Reject blank text and text over 5,000 characters before model invocation.

- [ ] **Step 4: Run service tests**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_sentiment_service.py -v`

Expected: PASS, including concurrent first-load test and deterministic top-score ordering.

- [ ] **Step 5: Commit**

```powershell
git add jobpilot-ai/ai-server/app/core/config.py jobpilot-ai/ai-server/app/domain/sentiment jobpilot-ai/ai-server/tests/test_sentiment_service.py
git commit -m "feat: serve versioned sentiment inference"
```

---

### Task 8: Internal FastAPI sentiment endpoints

**Files:**
- Create: `jobpilot-ai/ai-server/app/domain/sentiment/router.py`
- Modify: `jobpilot-ai/ai-server/app/main.py`
- Create: `jobpilot-ai/ai-server/tests/test_router_sentiment.py`

**Interfaces:**
- `POST /sentiment/analyze` consumes `{text: str, topK: int = 5}` and requires `X-Internal-API-Key`.
- `POST /sentiment/analyze/batch` consumes up to 32 texts and requires `X-Internal-API-Key`.
- `GET /sentiment/model` returns readiness and model metadata; no raw path.
- `GET /sentiment/health` returns HTTP 200 with component state so the main container remains healthy when an optional artifact is absent.

- [ ] **Step 1: Write endpoint contract and authorization tests**

```python
def test_analyze_rejects_missing_internal_key(client):
    response = client.post("/sentiment/analyze", json={"text": "좋아요"})
    assert response.status_code == 401

def test_unavailable_model_is_503(client, internal_headers):
    response = client.post(
        "/sentiment/analyze", json={"text": "좋아요"}, headers=internal_headers
    )
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "SENTIMENT_MODEL_UNAVAILABLE"
```

- [ ] **Step 2: Run and observe missing route failure**

Run: `cd jobpilot-ai/ai-server; pytest tests/test_router_sentiment.py -v`

Expected: FAIL with 404.

- [ ] **Step 3: Implement router using existing internal-key policy**

Follow `app/domain/matching/router.py` for header validation. Inject or monkeypatch the service in tests; do not download or train a model during API tests. Batch requests must reject more than 32 entries and preserve input order.

- [ ] **Step 4: Register router and run tests**

Add:

```python
from app.domain.sentiment.router import router as sentiment_router
app.include_router(sentiment_router, prefix="/sentiment", tags=["sentiment"])
```

Run: `cd jobpilot-ai/ai-server; pytest tests/test_router_sentiment.py tests/test_sentiment_service.py -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add jobpilot-ai/ai-server/app/main.py jobpilot-ai/ai-server/app/domain/sentiment/router.py jobpilot-ai/ai-server/tests/test_router_sentiment.py
git commit -m "feat: expose internal sentiment analysis API"
```

---

### Task 9: Model mounts, CI gate, and reproducibility documentation

**Files:**
- Modify: `jobpilot-ai/docker-compose.yml`
- Modify: `jobpilot-ai/docker-compose.prod.yml`
- Modify: `jobpilot-ai/deploy/jobpilot.env.example`
- Modify: `.github/workflows/ci.yml`
- Modify: `jobpilot-ai/ai-server/ml/sentiment/README.md`
- Modify: `jobpilot-ai/ai-server/ml/sentiment/DATA_CARD.md`
- Modify: `jobpilot-ai/ai-server/ml/sentiment/MODEL_CARD.md`

**Interfaces:**
- Host variable: `SENTIMENT_MODEL_DIR=/srv/jobpilot/sentiment-model`.
- Container variable: `SENTIMENT_MODEL_DIR=/models/sentiment`.
- Read-only mount: `${SENTIMENT_MODEL_DIR:-/srv/jobpilot/sentiment-model}:/models/sentiment:ro`.

- [ ] **Step 1: Add the read-only model mount and environment setting**

For the `ai-server` service in both Compose files add:

```yaml
environment:
  SENTIMENT_MODEL_DIR: /models/sentiment
volumes:
  - ${SENTIMENT_MODEL_DIR:-/srv/jobpilot/sentiment-model}:/models/sentiment:ro
```

Merge with existing `environment` keys rather than replacing them. Add the host variable and deployment explanation to `jobpilot.env.example`.

- [ ] **Step 2: Upgrade the AI CI job to run focused tests**

After compileall, install only the dependencies needed by sentiment unit tests and run the four sentiment test modules:

```yaml
- run: python -m pip install --disable-pip-version-check pytest==8.3.3 fastapi==0.115.0 pydantic==2.9.2 scikit-learn==1.5.2 joblib==1.4.2 httpx
- run: pytest -q tests/test_sentiment_dataset.py tests/test_sentiment_labels.py tests/test_sentiment_training.py tests/test_sentiment_service.py tests/test_router_sentiment.py tests/test_sentiment_transformer_config.py
```

If importing Transformer configuration requires `transformers`, add the repository-pinned compatible version to this focused install. CI must not download KOTE or KcELECTRA.

- [ ] **Step 3: Complete reproducibility documentation**

`README.md` must contain Windows PowerShell and Linux commands for download, validate, baseline train, evaluate, Transformer train, artifact placement, API smoke test, and rollback to the previous model directory. It must clearly state which outputs are measured and which commands require network/GPU.

- [ ] **Step 4: Run complete local verification**

Run:

```powershell
cd jobpilot-ai/ai-server
python -m compileall -q app ml
pytest -q tests/test_sentiment_dataset.py tests/test_sentiment_labels.py tests/test_sentiment_training.py tests/test_sentiment_service.py tests/test_router_sentiment.py tests/test_sentiment_transformer_config.py
cd ..
docker compose -f docker-compose.yml config
docker compose -f docker-compose.prod.yml config
```

Expected: compilation and tests pass; both Compose configurations render with a read-only `/models/sentiment` mount.

- [ ] **Step 5: Build the AI image and verify fail-clear startup**

Run:

```powershell
cd jobpilot-ai
docker build -t jobpilot-ai-sentiment-test ./ai-server
docker run --rm jobpilot-ai-sentiment-test python -c "from app.domain.sentiment.service import get_sentiment_service; print(get_sentiment_service().status().state)"
```

Expected: image builds and prints `UNAVAILABLE` without crashing when no model is mounted.

- [ ] **Step 6: Commit**

```powershell
git add .github/workflows/ci.yml jobpilot-ai/docker-compose.yml jobpilot-ai/docker-compose.prod.yml jobpilot-ai/deploy/jobpilot.env.example jobpilot-ai/ai-server/ml/sentiment
git commit -m "ci: verify sentiment ML foundation"
```

---

## Final Phase Verification

- [ ] Confirm `git status --short` contains only pre-existing user changes and no raw data or model artifacts.
- [ ] Confirm the committed manifest/documentation names the exact KOTE source and license without calling KOTE a company-review or three-class dataset.
- [ ] Confirm real baseline training and test evaluation reports were produced locally from the official split.
- [ ] Confirm the chosen deployed artifact decision is supported by measured Macro-F1, CPU p95 latency, artifact size, and memory behavior.
- [ ] Confirm `/sentiment/health` does not affect the root `/health` response when the optional model is absent.
- [ ] Confirm internal inference endpoints reject missing or invalid `X-Internal-API-Key`.
- [ ] Confirm no HTTP endpoint can start model training.
- [ ] Confirm Docker images contain neither KOTE raw text nor training output unless an artifact is deliberately mounted at runtime.

## Follow-on Plans

After this plan is verified, create and approve these implementation plans in order:

1. `fictional-company-review-platform`: 100 fictional companies, 100 postings, 500 reviews, member review CRUD, sentiment persistence, company/posting summaries, employer ownership dashboard, and TOP 10.
2. `community-sentiment-admin`: free/Q&A boards, posts/comments/views/likes/reports, separate community analysis context, service-feedback category, and admin preview/statistics dashboard.
3. `human-validated-domain-adaptation`: labeling guide, reviewer workflow, employment-domain evaluation set, retraining comparison, and optional aspect sentiment.
