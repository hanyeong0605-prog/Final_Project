"""question_corpus.get_pool() 단위 테스트.

실제 코퍼스 파일(ml/interview_qa_pairs_categorized_with_fields.jsonl) 대신 임시 JSONL
파일로 CORPUS_PATH를 바꿔치기해서 로직만 검증한다 - 전역 캐시(_pools)를 초기화해야
바뀐 경로가 반영된다(모듈 로드 시 1회만 읽어서 전역 캐시에 저장하는 구조라서).

2026-08-20: 캐시 초기화는 question_corpus.reload_pools() 대신
monkeypatch.setattr(question_corpus, "_pools", None)으로 한다 - reload_pools()는 전역
_pools를 직접(= monkeypatch 추적 밖에서) None으로 바꿔버리는데, 그 뒤 get_pool() 호출이
그 자리에서 실제 코퍼스를 다시 읽어 _pools를 채워 넣는 것도 마찬가지로 monkeypatch 밖의
일반 대입이라, 테스트가 끝나고 CORPUS_PATH는 monkeypatch가 원래 경로로 되돌려도 _pools는
이 테스트가 만든 가짜(임시 파일) 데이터로 채워진 채 다음 테스트 파일까지 그대로 남는다
(다른 모듈이 question_corpus.get_pool()을 목킹 없이 실제 코퍼스인 것처럼 호출하면 이
가짜 데이터를 돌려받게 됨 - test_question_similarity.py에서 실제로 이렇게 오염되는 걸
확인함). monkeypatch.setattr로 _pools를 바꾸면 그 대입 자체를 monkeypatch가 추적해서
테스트가 끝날 때 이전 값(None이었으면 None, 이미 실제 코퍼스가 로드돼 있었으면 그
실제 딕셔너리)으로 정확히 되돌려준다.
"""

import json

from app.domain.interview import question_corpus


def _write_corpus(tmp_path, rows: list[dict]):
    path = tmp_path / "corpus.jsonl"
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    return path


def test_field_sensitive_category_returns_matching_job_pool(tmp_path, monkeypatch):
    rows = [
        {"job": "백엔드", "question": "JPA N+1 문제를 설명해 주세요.", "category": "기술_직무역량"},
        {"job": "프론트엔드", "question": "가상 DOM의 동작 원리를 설명해 주세요.", "category": "기술_직무역량"},
    ]
    monkeypatch.setattr(question_corpus, "CORPUS_PATH", _write_corpus(tmp_path, rows))
    monkeypatch.setattr(question_corpus, "_pools", None)

    pool = question_corpus.get_pool("기술_직무역량", "백엔드")

    assert pool == ["JPA N+1 문제를 설명해 주세요."]


def test_field_sensitive_category_falls_back_to_default_job_when_no_match(tmp_path, monkeypatch):
    """분야별 전용 풀이 없는 job(예: 프로필 미입력으로 DEFAULT_JOB이 온 경우)이 오면,
    분야 구분 없이 두루 쓰이던 기존 공통 풀(job=DEFAULT_JOB)로 폴백해야 한다."""
    rows = [
        {"job": "ICT 개발자(신입)", "question": "가장 자신 있는 기술을 말씀해 주세요.", "category": "기술_직무역량"},
        {"job": "백엔드", "question": "JPA N+1 문제를 설명해 주세요.", "category": "기술_직무역량"},
    ]
    monkeypatch.setattr(question_corpus, "CORPUS_PATH", _write_corpus(tmp_path, rows))
    monkeypatch.setattr(question_corpus, "_pools", None)

    pool = question_corpus.get_pool("기술_직무역량", "존재하지-않는-분야")

    assert pool == ["가장 자신 있는 기술을 말씀해 주세요."]


def test_non_field_sensitive_category_ignores_job(tmp_path, monkeypatch):
    """인성/역량 계열 카테고리는 job이 달라도 같은 풀을 공유해야 한다."""
    rows = [
        {"job": "ICT 개발자(신입)", "question": "팀 갈등을 어떻게 해결했나요?", "category": "협업_리더십_커뮤니케이션"},
    ]
    monkeypatch.setattr(question_corpus, "CORPUS_PATH", _write_corpus(tmp_path, rows))
    monkeypatch.setattr(question_corpus, "_pools", None)

    pool = question_corpus.get_pool("협업_리더십_커뮤니케이션", "아무-분야나")

    assert pool == ["팀 갈등을 어떻게 해결했나요?"]


def test_missing_corpus_file_returns_empty_pool(tmp_path, monkeypatch):
    monkeypatch.setattr(question_corpus, "CORPUS_PATH", tmp_path / "없는파일.jsonl")
    monkeypatch.setattr(question_corpus, "_pools", None)

    assert question_corpus.get_pool("기술_직무역량", "백엔드") == []
