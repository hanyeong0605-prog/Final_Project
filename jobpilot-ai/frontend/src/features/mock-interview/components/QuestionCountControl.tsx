import { useState } from "react";

import {
  normalizeQuestionCountInput,
  questionCountRange,
  stepQuestionCount,
  type InterviewKind,
} from "../model/interviewConfig";

// 2026-08-29: 무료(2~5개)와 실전(5~10개)이 같은 컨트롤을 쓰기 때문에 컴포넌트로 뺐다.
// 규칙(빈 값 보정, 소수 반올림, 범위 클램프)은 전부 interviewConfig의 순수 함수에 있고
// 여기서는 "언제 부를지"만 다룬다 - 그래야 UI 없이 테스트할 수 있다.
//
// 입력창이 숫자 상태(value={questionCount})만 들고 있으면 지우는 순간 Number("")가 0이 돼서
// 화면에 0이 찍히고 다시 타이핑하기도 불편했다. 그래서 편집 중에는 draft(문자열)를 그대로
// 보여주고, 포커스가 빠지거나 Enter를 누를 때만 확정값으로 정규화한다.
type Props = {
  kind: InterviewKind;
  value: number;
  onChange: (next: number) => void;
  /** "최대 5개까지 선택할 수 있어요" 같은 범위 안내. aria-describedby로 입력창에 묶인다. */
  hint: string;
  inputId?: string;
};

export function QuestionCountControl({ kind, value, onChange, hint, inputId = "interview-question-count" }: Props) {
  const [draft, setDraft] = useState<string | null>(null);
  const [min, max] = questionCountRange(kind);
  const hintId = `${inputId}-hint`;

  const commit = (raw: string) => {
    onChange(normalizeQuestionCountInput(kind, raw));
    setDraft(null);
  };

  const step = (delta: number) => {
    setDraft(null);
    onChange(stepQuestionCount(kind, value, delta));
  };

  return (
    <div className="interview-question-count">
      <div className="interview-question-count-control">
        <button type="button" aria-label="질문 수 줄이기" disabled={value <= min} onClick={() => step(-1)}>
          −
        </button>
        <input
          id={inputId}
          type="number"
          inputMode="numeric"
          min={min}
          max={max}
          aria-label="질문 수"
          aria-describedby={hintId}
          value={draft ?? String(value)}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={(event) => commit(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            // 시작 화면이 form 안에 있지 않아도 Enter로 확정하는 흐름은 자연스럽다.
            event.preventDefault();
            commit(event.currentTarget.value);
          }}
        />
        <button type="button" aria-label="질문 수 늘리기" disabled={value >= max} onClick={() => step(1)}>
          +
        </button>
      </div>
      <small id={hintId} className="interview-question-count-hint">
        {hint}
      </small>
    </div>
  );
}
