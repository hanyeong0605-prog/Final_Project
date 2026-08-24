import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from "react";
import { clearEmployerAccessToken, getEmployerAccessToken, setEmployerAccessToken } from "../api/employerHttpClient";
import { getMe, login as requestLogin, signup as requestSignup } from "../api/employerAuthApi";
import type { EmployerAccount, EmployerAuthResponse, EmployerLoginInput, EmployerSignupInput } from "./employer.types";

interface EmployerAuthContextValue {
  employer: EmployerAccount | null;
  loading: boolean;
  acceptPasswordlessAuth: (response: EmployerAuthResponse) => void;
  signup: (input: EmployerSignupInput) => Promise<EmployerAccount>;
  login: (input: EmployerLoginInput) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
  setCurrentEmployer: (employer: EmployerAccount) => void;
}

const EmployerAuthContext = createContext<EmployerAuthContextValue | null>(null);

export function EmployerAuthProvider({ children }: PropsWithChildren) {
  const [employer, setEmployer] = useState<EmployerAccount | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getEmployerAccessToken()) { setLoading(false); return; }
    void getMe().then(setEmployer).catch(() => { clearEmployerAccessToken(); setEmployer(null); }).finally(() => setLoading(false));
  }, []);

  const value = useMemo<EmployerAuthContextValue>(() => ({
    employer,
    loading,
    acceptPasswordlessAuth: (response) => { setEmployerAccessToken(response.accessToken); setEmployer(response.employer); },
    signup: requestSignup,
    login: async (input) => { const response = await requestLogin(input); setEmployerAccessToken(response.accessToken); setEmployer(response.employer); },
    logout: () => { clearEmployerAccessToken(); setEmployer(null); },
    refresh: async () => { setEmployer(await getMe()); },
    setCurrentEmployer: setEmployer,
  }), [employer, loading]);

  return <EmployerAuthContext.Provider value={value}>{children}</EmployerAuthContext.Provider>;
}

export function useEmployerAuth() {
  const context = useContext(EmployerAuthContext);
  if (!context) throw new Error("useEmployerAuth must be used inside EmployerAuthProvider.");
  return context;
}
