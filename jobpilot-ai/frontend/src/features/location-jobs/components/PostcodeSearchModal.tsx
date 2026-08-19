import React from "react";
import DaumPostcode, { Address } from "react-daum-postcode";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSelectAddress: (address: string) => void;
}

export const PostcodeSearchModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onSelectAddress,
}) => {
  if (!isOpen) return null;

  const handleComplete = (data: Address) => {
    let fullAddress = data.address;
    let extraAddress = "";

    if (data.addressType === "R") {
      if (data.bname !== "") extraAddress += data.bname;
      if (data.buildingName !== "") {
        extraAddress += extraAddress !== "" ? `, ${data.buildingName}` : data.buildingName;
      }
      fullAddress += extraAddress !== "" ? ` (${extraAddress})` : "";
    }

    onSelectAddress(fullAddress);
    onClose();
  };

  return (
    <div className="postcode-modal-overlay">
      <div className="postcode-modal-card">
        <div className="postcode-modal-header">
          <h3>주소 검색</h3>
          <button type="button" onClick={onClose} className="postcode-modal-close" aria-label="닫기">
            ✕
          </button>
        </div>
        <DaumPostcode onComplete={handleComplete} />
      </div>
    </div>
  );
};