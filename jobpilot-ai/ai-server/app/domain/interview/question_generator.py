"""모의면접 질문 생성 (polyglot-ko-1.3b + LoRA, Gemini 검수).

AI Hub "채용면접 인터뷰 데이터"(TL_05, ICT/신입, 5194건 - 남녀 합본)의 실제 면접 질문
5039개(중복 제거)로 EleutherAI/polyglot-ko-1.3b를 LoRA 파인튜닝한 결과물을 불러와서 질문을
생성한다. 학습 과정은 ai-server/ml/mock_interview_question_generator.ipynb 참고 (Colab, GPU
필요해서 로컬/서버에서는 학습을 다시 돌리지 않고 학습된 어댑터만 로드한다).

2026-08-04 설계 메모:
- 처음엔 skt/kogpt2-base-v2(125M)로 학습했는데 문장이 중간에 다른 질문이랑 이어붙거나 문형이
  안 맞는 문제가 있었다 - 베이스 모델을 polyglot-ko-1.3b(10배 큼)로 바꿔서 개선했다.
  LoRA로 우리 데이터를 학습시키는 방식 자체는 동일 - "직접 모은 데이터로 학습시켰다"는
  부분은 그대로 유지된다.
- 이 라벨 데이터에는 세션/턴 연결 정보가 없어서 "이전 답변에 대한 진짜 후속 질문"은 아직 못 만든다.
  지금은 ICT 신입 면접 질문 5039개의 문체/주제 분포를 학습한 수준 - "그럴듯한 질문 생성기"다.
- 문법은 멀쩡한데 존재하지 않는 단어를 섞어 만드는("프론트스파트" 같은) 경우가 있다 - 이건
  규칙 기반 필터로 못 잡는다(한국어 조사/어미 때문에 단순 단어 대조는 오탐이 너무 많음).
  그래서 최종적으로 Gemini에게 다듬어달라고 요청하는 가벼운 검수를 추가했다(_gemini_polish).
  질문을 생성하는 건 여전히 우리 모델이고, Gemini는 문장을 다듬기만 할 뿐 주제/내용은 못 바꾸게
  프롬프트로 제한해뒀다 - "직접 학습시킨 모델"이라는 본질은 안 바뀐다. 키가 없으면 그냥
  건너뛴다(fail-open, 원문 그대로 반환).
- whisper와 마찬가지로, transformers/torch를 파일 맨 위에서 import하면 서버 기동 시점에 무거운
  라이브러리가 통째로 로드된다 - 실제로 질문 생성을 호출할 때(_get_loaded_model 호출 시점)까지 미룬다.
"""

from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from app.core.config import settings

# 학습된 LoRA 어댑터 + 토크나이저가 저장된 경로 (노트북 9단계에서 다운로드한 zip을 풀어서 여기 둠).
MODEL_DIR = Path(__file__).parent / "model" / "question_generator_lora"

# LoRA 베이스 모델. 어댑터 자체는 MODEL_DIR에 있지만, 베이스 가중치는 최초 1회 HuggingFace Hub에서
# 받아서 로컬 캐시에 저장된다(그 이후는 오프라인으로도 동작). 어댑터 학습 때와 반드시 같은 베이스여야 한다.
BASE_MODEL_NAME = "EleutherAI/polyglot-ko-1.3b"

DEFAULT_JOB = "ICT 개발자(신입)"

# 2026-08-04: 재학습 없이 품질을 살짝 개선하려고 넣은 얕은 필터. "미리 만들어둔 질문 목록에서
# 나쁜 걸 지우는" 방식이 아니라 - 생성기는 매번 새로 만들어내니까(무한 공급) - 이상하면
# 그냥 한 번 더 생성해보고, 그래도 안 되면 마지막 결과라도 반환한다(항상 뭔가는 나오게).
# 학습 데이터(AI Hub 실제 면접 질문)가 전부 이런 격식체 종결어미로 끝나서 이걸 기준으로 삼았다 -
# 완벽한 문법 체커는 아니고 "면접 질문처럼 안 끝나는" 명백히 이상한 케이스만 걸러내는 용도.
#
# 2026-08-04 수정: 원래 "습니다"/"봅니다"/"바랍니다"/"십니다"를 각각 나열했는데, "궁금합니다"
# 처럼 "하다" 계열 어간의 "-ㅂ니다"(합니다/됩니다/옵니다 등)는 그 목록에 없어서 _cut_at_first_ending이
# 진짜 첫 종결 지점을 못 찾고 훨씬 뒤에 있는 다음 "~니다"까지 그대로 이어붙여 내보냈다
# (예: "...직무에 잘 부합하시는지 궁금합니다 적합한 이유와 함께 제시해 주시기를 바랍니다"처럼
# 사실상 문장 두 개가 붙어서 나옴 - "궁금합니다"에서 안 잘렸기 때문). "-습니다/-ㅂ니다" 계열은
# 전부 "니다"로 끝나므로 어간별로 나열하는 대신 "니다" 하나로 통합해서 이 사각지대를 없앴다.
_QUESTION_ENDING_HINTS = (
    "니다", "까요", "나요", "니까", "주세요",
)
# 2026-08-05: "생성 1개 -> Gemini 검수 1개"를 최대 5번 반복하던 구조를 "한 번에 여러 개 생성 ->
# 그중 로컬 필터를 가장 잘 통과한 후보 하나를 Gemini에게 다듬어달라고 요청"으로 바꿨다.
# model.generate()를 num_return_sequences로 한 번에 묶어서 호출하면 매 시도마다 파이썬/토크나이저
# 오버헤드가 반복되던 것도 줄고, 느렸던 진짜 원인인 Gemini API 왕복 횟수도 최대 5번 -> 1번으로
# 줄어든다(버리고 재시도하는 대신 그 자리에서 고쳐서 쓰기 때문에 재시도 자체가 필요 없어짐).
_NUM_CANDIDATES = 3

# 2026-08-05: 학습 데이터 자체에 "지원자님" 말고 "면접자님"(호칭이 반대 - 실제로는 면접관이
# 면접자다) 같은 변형이 섞여 있어서 모델이 가끔 그대로 따라 만든다. Gemini 검수는 "내용은
# 바꾸지 마라"는 제약 때문에 이런 호칭까지 고쳐준다는 보장이 없어서(운에 맡기는 셈), 확실하게
# 통일하려면 검수 이전에 결정론적으로 치환해야 한다 - 재학습으로 고치는 것보다 훨씬 빠르고
# 100% 보장됨.
_ADDRESS_TERM_REPLACEMENTS = {
    "면접자님": "지원자님",
    "면접자분": "지원자분",
    "면접자께서": "지원자께서",
    "응시자님": "지원자님",
    "응시자분": "지원자분",
}
_MIN_QUESTION_LENGTH = 8
# 너무 길게 늘어지면 "니다"로 안 끝나는 어색한 이어붙임(위 사각지대류)이 남아있을 확률이 높아서
# 방어적으로 상한을 둔다 - 학습 데이터 질문들은 대부분 이보다 짧다.
_MAX_QUESTION_LENGTH = 70


def _cut_at_first_ending(text: str) -> str:
    """모델이 한 문장에서 안 끝내고 질문 두 개를 이어 붙이는 경우가 있어서(예: "~같습니까 혹시
    이 일을 하는 데 있어서...") 첫 번째로 종결어미가 나오는 어절까지만 남기고 뒷부분은 버린다.
    못 찾으면 원본 그대로 반환 - 그 경우는 아래 _looks_like_question에서 걸러진다."""
    words = text.split(" ")
    acc: list[str] = []
    for w in words:
        acc.append(w)
        stripped = w.rstrip("?!.,~ 。")
        if stripped.endswith(_QUESTION_ENDING_HINTS):
            return " ".join(acc)
    return text


def _normalize_addressing(text: str) -> str:
    """"면접자님" 같은 잘못된 호칭을 "지원자님"으로 무조건 통일한다 (위 _ADDRESS_TERM_REPLACEMENTS
    설명 참고) - Gemini 검수보다 먼저 적용해서, 검수를 거치든 안 거치든(키 없음/실패로 원문
    그대로 반환되는 경우 포함) 항상 통일된 호칭이 나가게 한다."""
    for wrong, right in _ADDRESS_TERM_REPLACEMENTS.items():
        text = text.replace(wrong, right)
    return text


def _looks_like_question(text: str) -> bool:
    if not text or not (_MIN_QUESTION_LENGTH <= len(text) <= _MAX_QUESTION_LENGTH):
        return False
    if "�" in text:  # 디코딩 깨짐(�) - 이론상 안 나와야 하지만 방어적으로 체크
        return False
    trimmed = text.rstrip("?!. 。")
    return trimmed.endswith(_QUESTION_ENDING_HINTS)


def _gemini_polish(question: str) -> str | None:
    """존재하지 않는 단어/어색한 조합, 문장 두 개가 억지로 이어붙은 것 등을 Gemini에게 고쳐
    달라고 한다 - 규칙 기반으로는 한국어 조사/어미 때문에 오탐이 너무 많아서 판단 자체를
    위임한다.

    반환값: 다듬어진 질문(str), 도저히 못 고칠 정도로 내용이 이상해서 버려야 하면 None,
    키가 없거나 호출 자체가 실패하면 원문 그대로(fail-open).

    2026-08-05: 원래는 OK/NG 판정만 하고 NG면 통째로 버린 뒤 재시도했는데, 그러면 로컬 필터를
    통과한 후보가 전부 NG일 때 결국 다듬어지지 않은 원본을 그대로 반환하게 되는 문제가 있었다
    (예: "...걱정되는 지원자가 많은 것으로 알고 계십니다"처럼 질문이 아니라 서술문으로 어색하게
    끝나는 케이스가 그대로 나감). 그래서 버리는 대신 "질문의 주제/내용은 바꾸지 말고 문장만
    자연스럽게 고쳐달라"로 바꿨다. 다만 다듬는 것만으로 해결 안 되는(내용 자체가 의미불명인)
    경우까지 억지로 문장을 만들어내면 오히려 이상한 질문을 만들어낼 수 있어서, 그런 경우엔
    'DISCARD'를 반환하게 하고 호출부(generate_question)에서 다음 로컬 후보로 넘어가게 했다."""
    if not settings.gemini_api_key:
        return question
    try:
        # 2026-08-05: google.generativeai 지원 종료(2025-11-30)로 google-genai SDK로 전환
        # (evaluation.py의 같은 변경 메모 참고).
        from google import genai

        client = genai.Client(api_key=settings.gemini_api_key)
        prompt = (
            "다음은 AI가 생성한 한국어 채용면접 질문 후보다. 문법이 어색하거나, 존재하지 않는 "
            "단어가 섞여 있거나, 서로 다른 문장 두 개가 억지로 이어붙어 있거나, 질문이 아니라 "
            "서술문처럼 어색하게 끝나는 경우가 있다. 아래 규칙을 지켜서 자연스러운 면접 질문 "
            "하나로 고쳐라.\n"
            "1) 다루는 주제/소재는 절대 바꾸지 마라 - 전혀 새로운 내용을 추가하거나 다른 "
            "주제의 질문으로 바꾸지 마라\n"
            "2) 만약 후보가 질문이 아니라 서술문(예: '~할 수도 있습니다', '~라고 합니다')이면, "
            "같은 주제를 실제 면접 질문 형태(의문형 어미나 '~말씀해 주시겠습니까/주세요' 같은 "
            "요청형)로 바꿔서 반드시 질문 하나로 만들어라\n"
            "3) 질문 두 개가 이어붙어 있으면 그중 핵심적인 것 하나만 남기고 나머지는 제거해라 - "
            "두 개를 억지로 다 유지하려 하지 마라\n"
            "4) 이미 자연스러운 질문 하나로 되어 있으면 손대지 말고 그대로 반환해라\n"
            "5) 위 규칙을 다 적용해도 내용 자체가 의미가 통하지 않아서(존재하지 않는 개념을 "
            "묻거나 문맥이 아예 성립하지 않아서) 도저히 자연스러운 질문으로 못 고치겠으면, "
            "억지로 만들어내지 말고 'DISCARD'라는 단어만 출력해라\n"
            "6) 결과는 다듬어진 질문 문장 하나 또는 'DISCARD' 중 하나만 출력해라 - 설명, "
            "따옴표, 다른 말은 절대 붙이지 마라\n\n"
            f"질문 후보: {question}"
        )
        response = client.models.generate_content(model=settings.gemini_model, contents=prompt)
        polished = (response.text or "").strip().strip('"').strip()
        if polished.upper() == "DISCARD":
            return None
        return polished or question
    except Exception:
        return question


@dataclass
class LoadedModel:
    tokenizer: object
    model: object


@lru_cache(maxsize=1)
def _get_loaded_model() -> LoadedModel:
    """프로세스당 한 번만 로드해서 재사용한다 (whisper 모델 캐싱과 같은 이유)."""
    if not MODEL_DIR.exists():
        raise RuntimeError(
            f"질문 생성 모델을 찾을 수 없습니다: {MODEL_DIR}. "
            "ai-server/ml/mock_interview_question_generator.ipynb로 학습한 결과물(zip)을 "
            "풀어서 이 경로에 둬야 합니다."
        )

    import torch
    from peft import PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer

    # 2026-08-04: tokenizer.json 파싱이 "ModelWrapper" 에러처럼 보이는 증상으로 계속 실패했다 -
    # 실제 원인은 transformers가 import 시점에 내부 deps 테이블로 tokenizers 버전을 강제 검사하는
    # 것이었다(transformers/dependency_versions_check.py). tokenizers==0.23.1이 설치된 상태에서는
    # transformers가 요구하는 상한(<=0.23.0)을 넘어서 "import transformers" 자체가 ImportError로
    # 죽었는데, 에러 로그만 보고는 tokenizer.json 파싱 실패로 오인하기 쉬웠다(네트워크 요청/다운로드
    # 까지도 못 간 상태였음). requirements.txt에 tokenizers>=0.22.0,<=0.23.0 핀을 추가해서 해결 -
    # 이 모델은 vocab.json/merges.txt가 없어 use_fast=False(slow tokenizer)는 애초에 쓸 수 없다
    # (HuggingFace 저장소에 tokenizer.json만 있음).
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL_NAME)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    base_model = AutoModelForCausalLM.from_pretrained(BASE_MODEL_NAME)
    model = PeftModel.from_pretrained(base_model, str(MODEL_DIR))
    model.eval()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    model.to(device)

    return LoadedModel(tokenizer=tokenizer, model=model)


def generate_question(job: str = DEFAULT_JOB, context: str = "") -> str:
    """면접 질문 하나를 생성한다.

    job: 직무 (지금 학습 데이터가 전부 "ICT/신입"이라, 다른 값을 넣어도 그 스타일 질문이 나올 확률이
         높다 - 다른 직무 데이터 추가 전까진 실질적인 분기가 안 된다는 점 인지하고 쓴다).
    context: 이전 답변 텍스트 (선택). 지금 학습 데이터엔 진짜 문맥 연결이 없어서 효과가 제한적이다.
    """
    loaded = _get_loaded_model()
    tokenizer, model = loaded.tokenizer, loaded.model

    prompt = f"직무: {job}\n이전 답변: {context}\n다음 질문:"
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

    import torch

    with torch.no_grad():
        # 2026-08-04: temperature/top_p를 살짝 낮추고 repetition_penalty를 올려서 "본인이
        # 가지고 있으신 이 직무에"처럼 두 표현이 어색하게 뭉개져 나오는 빈도를 줄였다.
        # 그래도 완전히 없어지진 않아서 _looks_like_question 길이 상한 + 강화된 Gemini
        # 프롬프트(어색한 이어붙임 체크)로 이중 방어한다.
        # 2026-08-05: num_return_sequences로 한 번의 generate() 호출에서 후보 여러 개를 뽑는다
        # (예전엔 이 블록 자체를 루프 안에서 최대 5번 새로 호출했음).
        output = model.generate(
            **inputs,
            max_new_tokens=40,
            do_sample=True,
            top_p=0.85,
            temperature=0.7,
            repetition_penalty=1.3,
            no_repeat_ngram_size=3,
            pad_token_id=tokenizer.pad_token_id,
            num_return_sequences=_NUM_CANDIDATES,
        )

    candidates: list[str] = []
    for sequence in output:
        text = tokenizer.decode(sequence, skip_special_tokens=True)
        candidate = text.split("다음 질문:")[-1].strip()
        candidate = _cut_at_first_ending(candidate)
        candidate = _normalize_addressing(candidate)
        if candidate:
            candidates.append(candidate)

    # 로컬 필터(끝맺음/길이)를 통과한 후보들을 순서대로 최선으로 삼고, 하나도 없으면 아무거나라도
    # 폴백으로 쓴다("아예 안 나오는" 것보단 낫다).
    any_fallback = candidates[0] if candidates else ""
    locally_valid = [c for c in candidates if _looks_like_question(c)]

    if not locally_valid and not any_fallback:
        return "질문 생성에 실패했습니다. 다시 시도해 주세요."

    # 2026-08-05: 버리고 재생성하는 대신, 그 자리에서 Gemini에게 "내용은 그대로, 문장만
    # 자연스럽게" 다듬어달라고 한다. 다만 도저히 못 고칠 정도로 이상한 후보는 Gemini가
    # None(DISCARD)을 돌려주는데, 이럴 땐 재시도 대신 이미 뽑아둔 다음 로컬 후보로 넘어간다
    # (한 번의 generate() 호출에서 여러 개를 뽑아뒀으니 그냥 버리지 않고 활용).
    for candidate in locally_valid:
        polished = _gemini_polish(candidate)
        if polished is None:  # DISCARD - 다음 후보로
            continue
        if _looks_like_question(polished):
            return polished
        return candidate  # 다듬은 결과가 형식을 깨면 다듬기 전 원본으로

    # 로컬 필터 통과한 후보가 전부 DISCARD당했거나 애초에 하나도 없었던 경우의 최후 폴백.
    return any_fallback or "질문 생성에 실패했습니다. 다시 시도해 주세요."
