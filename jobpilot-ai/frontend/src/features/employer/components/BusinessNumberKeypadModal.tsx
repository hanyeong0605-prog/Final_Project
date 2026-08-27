import { useEffect, useState } from "react";
import { Delete } from "lucide-react";

interface Props {
  isOpen: boolean;
  initialValue: string;
  onClose: () => void;
  onConfirm: (digits: string) => void;
}

const LENGTH = 10;

// 2026-08-26: "사업자등록번호 약간 키패드처럼, 은행처럼" 요청으로 추가 - 은행 앱 계좌번호
// 입력 화면처럼 작은 모달에 숫자 키패드만 띄워서 자리 수(10자리)를 맞춰 입력하게 한다.
// PostcodeSearchModal(overlay/card/header 패턴)과 톤을 맞췄다.
function formatBusinessNumber(digits: string): string {
  const first = digits.slice(0, 3);
  const second = digits.slice(3, 5);
  const third = digits.slice(5, 10);
  return [first, second, third].filter(Boolean).join("-");
}

export function BusinessNumberKeypadModal({ isOpen, initialValue, onClose, onConfirm }: Props) {
  const [digits, setDigits] = useState(initialValue);

  // 모달을 다시 열 때마다 폼에 이미 입력돼 있던 값(수정 중 다시 열기 등)으로 초기화한다.
  useEffect(() => { if (isOpen) setDigits(initialValue); }, [isOpen, initialValue]);

  if (!isOpen) return null;

  const press = (digit: string) => setDigits((current) => (current.length >= LENGTH ? current : current + digit));
  const backspace = () => setDigits((current) => current.slice(0, -1));
  const clearAll = () => setDigits("");
  const complete = digits.length === LENGTH;

  const confirm = () => {
    if (!complete) return;
    onConfirm(digits);
    onClose();
  };

  return (
    <div className="postcode-modal-overlay">
      <div className="keypad-modal-card">
        <div className="postcode-modal-header">
          <h3>사업자등록번호 입력</h3>
          <button type="button" onClick={onClose} className="postcode-modal-close" aria-label="닫기">✕</button>
        </div>

        <div className="keypad-display" aria-live="polite">
          <span className={digits ? "keypad-display-value" : "keypad-display-placeholder"}>
            {digits ? formatBusinessNumber(digits) : "000-00-00000"}
          </span>
          <span className="keypad-display-count">{digits.length}/{LENGTH}</span>
        </div>

        <div className="keypad-grid">
          {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((digit) => (
            <button key={digit} type="button" className="keypad-key" onClick={() => press(digit)}>{digit}</button>
          ))}
          <button type="button" className="keypad-key keypad-key-text" onClick={clearAll}>전체삭제</button>
          <button type="button" className="keypad-key" onClick={() => press("0")}>0</button>
          <button type="button" className="keypad-key keypad-key-text" onClick={backspace} aria-label="한 자리 지우기"><Delete size={18} /></button>
        </div>

        <button type="button" className="primary-button keypad-confirm" disabled={!complete} onClick={confirm}>
          {complete ? "입력 완료" : `${LENGTH - digits.length}자리 더 입력해 주세요`}
        </button>
      </div>
    </div>
  );
}
