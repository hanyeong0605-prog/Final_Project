import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from "react";
import { clearEmployerAccessToken, getEmployerAccessToken, setEmployerAccessToken } from "../api/employerHttpClient";
import { getMe, login as requestLogin, signup as requestSignup } from "../api/employerAuthApi";
import type { EmployerAccount, EmployerLoginInput, EmployerSignupInput } from "./employer.types";

interface EmployerAuthContextValue {
  employer: EmployerAccount | null;
  loading: boolean;
  login: (input: EmployerLoginInput) => Promise<void>;
  signup: (input: EmployerSignupInput) => Promise<EmployerAccount>;
  logout: () => void;
  refresh: () => Promise<void>;
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
    login: async (input) => { const response = await requestLogin(input); setEmployerAccessToken(response.accessToken); setEmployer(response.employer); },
    signup: requestSignup,
    logout: () => { clearEmployerAccessToken(); setEmployer(null); },
    refresh: async () => { setEmployer(await getMe()); },
  }), [employer, loading]);

  return <EmployerAuthContext.Provider value={value}>{children}</EmployerAuthContext.Provider>;
}

export function useEmployerAuth() {
  const context = useContext(EmployerAuthContext);
  if (!context) throw new Error("useEmployerAuth must be used inside EmployerAuthProvider.");
  return context;
}
