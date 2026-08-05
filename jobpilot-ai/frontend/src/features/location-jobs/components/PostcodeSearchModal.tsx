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
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl p-4 w-full max-w-lg relative shadow-2xl">
        <div className="flex justify-between items-center mb-3 pb-2 border-b">
          <h3 className="font-bold text-gray-800">주소 검색</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 font-bold text-lg"
          >
            ✕
          </button>
        </div>
        <DaumPostcode onComplete={handleComplete} />
      </div>
    </div>
  );
};