import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { PropsWithChildren } from "react";
import { getInterestIds, toggleInterest as requestInterestToggle } from "../api/interestsApi";
import { useAuth } from "../../auth/model/AuthContext";

interface InterestContextValue {
  interestCount: number;
  interestIds: number[];
  isInterested: (targetId: number) => boolean;
  toggleInterest: (targetId: number) => Promise<void>;
}

const InterestContext = createContext<InterestContextValue | null>(null);

export function InterestProvider({ children }: PropsWithChildren) {
  const [interestIds, setInterestIds] = useState<number[]>([]);
  const { member } = useAuth();

  useEffect(() => {
    if (!member) { setInterestIds([]); return; }
    void getInterestIds().then(setInterestIds).catch(() => setInterestIds([]));
  }, [member]);

  const toggleInterest = useCallback(async (targetId: number) => {
    const alreadyInterested = interestIds.includes(targetId);
    const next = alreadyInterested
      ? interestIds.filter((id) => id !== targetId)
      : [...interestIds, targetId];
    setInterestIds(next);
    try { await requestInterestToggle(targetId, !alreadyInterested); }
    catch (error) { setInterestIds(interestIds); throw error; }
  }, [interestIds]);

  const value = useMemo<InterestContextValue>(() => ({
    interestCount: interestIds.length,
    interestIds,
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
