"""이력서 작성 도우미(self_introduction.py, project.py) 공용 헬퍼.

interview/evaluation.py의 _parse_json_response/_clamp_score/_as_str_list와 완전히 같은
패턴을 재사용한다 - 이 도메인이 원래부터 그 모듈을 참고해서 설계됐다(Gemini에게
response_mime_type="application/json"으로 강제하고, 혹시 모를 코드펜스만 방어적으로 벗겨낸다).
"""

import json

_MAX_LIST_ITEMS = 5


def parse_json_response(raw: str) -> dict | None:
    text = raw.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:]
        text = text.strip()
    try:
        data = json.loads(text)
    except (json.JSONDecodeError, ValueError):
        return None
    return data if isinstance(data, dict) else None


def as_str_list(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(v).strip() for v in value if str(v).strip()][:_MAX_LIST_ITEMS]
