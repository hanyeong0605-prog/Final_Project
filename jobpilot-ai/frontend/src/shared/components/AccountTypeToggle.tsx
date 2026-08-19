import { useNavigate } from "react-router-dom";

type AccountType = "member" | "employer";

interface AccountTypeToggleProps {
  value: AccountType;
  memberTo: string;
  employerTo: string;
}

// 로그인/가입 화면 상단에서 개인회원·기업회원 경로를 라디오 버튼으로 전환한다.
// 두 계정 체계는 완전히 분리된 라우트라 실제로는 선택 시 해당 경로로 이동한다.
export function AccountTypeToggle({ value, memberTo, employerTo }: AccountTypeToggleProps) {
  const navigate = useNavigate();

  return (
    <div className="account-type-toggle" role="radiogroup" aria-label="회원 유형 선택">
      <label className={value === "member" ? "active" : ""}>
        <input
          type="radio"
          name="accountType"
          checked={value === "member"}
          onChange={() => navigate(memberTo)}
        />
        개인회원
      </label>
      <label className={value === "employer" ? "active" : ""}>
        <input
          type="radio"
          name="accountType"
          checked={value === "employer"}
          onChange={() => navigate(employerTo)}
        />
        기업회원
      </label>
    </div>
  );
}
