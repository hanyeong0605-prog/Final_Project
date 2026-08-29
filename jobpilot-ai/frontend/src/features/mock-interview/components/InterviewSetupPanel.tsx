import type { Dispatch, SetStateAction } from "react";

import { QuestionCountControl } from "./QuestionCountControl";
import { questionCountRange, type InterviewKind, type InterviewQuestionSource } from "../model/interviewConfig";

// 2026-08-29: 시작 화면을 "모드를 먼저 고르고, 그 모드에 필요한 설정만 보여주는" 구조로
// 바꾸면서 모드 의존적인 부분만 이 컴포넌트로 뺐다(설계 문서 "첫 화면" 절). 카메라/채팅 선택,
// 공고 검색, 목소리 선택처럼 두 모드가 똑같이 쓰는 블록은 페이지에 그대로 뒀다 - 여기로
// 다 끌고 오면 컴포넌트가 그냥 페이지의 복사본이 된다.
//
// 세그먼트 UI지만 마크업은 fieldset/legend + 진짜 radio input이다 - 보기에만 탭처럼 생긴
// 버튼 묶음으로 만들면 키보드 화살표 이동과 스크린리더의 "2개 중 1번째" 안내가 사라진다.
const REAL_SOURCE_OPTIONS: [InterviewQuestionSource, string][] = [
  ["spec", "스펙만"],
  ["spec_company", "스펙 + 회사"],
  ["company", "회사"],
];

type Props = {
  kind: InterviewKind;
  onKindChange: (kind: InterviewKind) => void;
  /** 실전면접을 지금 쓸 수 있는지(이용권 잔여 > 0 또는 관리자). 확인이 끝나기 전
   *  (subscriptionChecked=false)에는 실전을 고를 수 없다. */
  subscribed: boolean;
  subscriptionChecked: boolean;
  /** 남은 이용권 횟수 - 실전 모드에서 안내로 보여준다. */
  remainingSessions?: number;
  /** 이용권이 없는 사용자가 실전을 눌렀을 때. 설정값은 그대로 두고 안내 모달만 연다. */
  onSubscriptionRequired: () => void;
  realSource: InterviewQuestionSource;
  onRealSourceChange: (source: InterviewQuestionSource) => void;
  /** 실전 `스펙만`/`스펙+회사`에 쓸 스펙이 저장돼 있는지. */
  hasSpec: boolean;
  onGoToSpec: () => void;
  hasSelectedJobPosting: boolean;
  questionCount: number;
  /** QuestionCountControl이 −/+ 를 함수형 업데이트로 넘기므로 setState를 그대로 받는다. */
  onQuestionCountChange: Dispatch<SetStateAction<number>>;
};

export function InterviewSetupPanel({
  kind,
  onKindChange,
  subscribed,
  subscriptionChecked,
  remainingSessions,
  onSubscriptionRequired,
  realSource,
  onRealSourceChange,
  hasSpec,
  onGoToSpec,
  hasSelectedJobPosting,
  questionCount,
  onQuestionCountChange,
}: Props) {
  const [min, max] = questionCountRange(kind);
  const needsSpec = kind === "real" && realSource !== "company" && !hasSpec;
  const needsCompany = kind === "real" && realSource !== "spec" && !hasSelectedJobPosting;

  return (
    <>
      <fieldset className="interview-kind-segment">
        <legend className="sr-only">면접 모드</legend>
        {(["practice", "real"] as const).map((option) => (
          <label key={option} className={kind === option ? "active" : ""}>
            <input
              type="radio"
              name="interview-kind"
              value={option}
              checked={kind === option}
              // 구독 확인 전에는 실전을 못 고르게 막는다 - 아직 모르는 상태에서 "구독하세요"
              // 모달을 띄우면 구독자에게 거짓말을 하게 된다.
              disabled={option === "real" && !subscriptionChecked}
              onChange={() => {
                if (option === "real" && !subscribed) {
                  onSubscriptionRequired();
                  return;
                }
                onKindChange(option);
              }}
            />
            {option === "practice" ? "모의면접" : "실전면접"}
          </label>
        ))}
      </fieldset>

      {/* legend는 flex/grid 컨테이너 안에서 브라우저마다 배치가 갈리므로, fieldset은 평범한
          block으로 두고 안쪽 row만 grid로 깐다. */}
      {kind === "real" && (
        <fieldset className="interview-fieldset">
          <legend className="interview-option-label">질문에 사용할 정보</legend>
          <div className="interview-option-row" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
            {REAL_SOURCE_OPTIONS.map(([value, label]) => (
              <label key={value} className={`interview-option-chip${realSource === value ? " active" : ""}`}>
                <input
                  type="radio"
                  name="real-source"
                  value={value}
                  checked={realSource === value}
                  onChange={() => onRealSourceChange(value)}
                />
                {label}
              </label>
            ))}
          </div>
          {needsSpec && (
            <p className="account-alert">
              실전면접에 사용할 스펙이 없습니다.{" "}
              <button type="button" className="text-button" onClick={onGoToSpec}>
                스펙 입력하기
              </button>
            </p>
          )}
          {needsCompany && (
            <p className="account-alert">아래 &quot;준비 중인 공고&quot;에서 회사를 하나 골라주세요.</p>
          )}
          {/* 2026-08-29: 이용권은 시작해서 질문이 만들어진 뒤에 1회 차감된다 - 시작 전에
              몇 회 남았는지 보여줘야 사용자가 예상할 수 있다. */}
          {remainingSessions !== undefined && (
            <p className="interview-question-count-hint">
              이용권 {remainingSessions}회 남음 · 시작하면 1회가 차감돼요
            </p>
          )}
        </fieldset>
      )}

      <div className="interview-option-group">
        <span className="interview-option-label">질문 개수</span>
        <QuestionCountControl
          kind={kind}
          value={questionCount}
          onChange={onQuestionCountChange}
          hint={
            kind === "practice"
              ? `자기소개를 포함해 최소 ${min}개, 최대 ${max}개까지 선택할 수 있어요`
              : `자기소개와 입사 후 포부를 포함해 최소 ${min}개, 최대 ${max}개까지 선택할 수 있어요`
          }
        />
      </div>
    </>
  );
}
