import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { PropsWithChildren } from "react";
import { toggleInterest as requestInterestToggle } from "../api/interestsApi";

interface InterestContextValue {
  interestCount: number;
  isInterested: (targetId: number) => boolean;
  toggleInterest: (targetId: number) => void;
}

const InterestContext = createContext<InterestContextValue | null>(null);

export function InterestProvider({ children }: PropsWithChildren) {
  const [interestIds, setInterestIds] = useState<number[]>([101, 202]);

  const toggleInterest = useCallback((targetId: number) => {
    setInterestIds((current) => {
      const alreadyInterested = current.includes(targetId);
      void requestInterestToggle(targetId, !alreadyInterested);
      return alreadyInterested ? current.filter((id) => id !== targetId) : [...current, targetId];
    });
  }, []);

  const value = useMemo<InterestContextValue>(() => ({
    interestCount: interestIds.length,
    isInterested: (targetId) => interestIds.includes(targetId),
    toggleInterest,
  }), [interestIds, toggleInterest]);

  return <InterestContext.Provider value={value}>{children}</InterestContext.Provider>;
}

export function useInterests() {
  const context = useContext(InterestContext);
  if (!context) throw new Error("useInterests must be used inside InterestProvider.");
  return context;
}
