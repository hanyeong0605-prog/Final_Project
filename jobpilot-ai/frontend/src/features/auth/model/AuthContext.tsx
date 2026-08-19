import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from "react";
import { clearAccessToken, getAccessToken, setAccessToken } from "../../../api/httpClient";
import {
  developmentAdminLogin as requestDevelopmentAdminLogin,
  developmentLogin as requestDevelopmentLogin,
  getMe,
  login as requestLogin,
  signup as requestSignup,
} from "../api/authApi";
import type { AuthMember, LoginInput, SignupInput } from "./auth.types";

interface AuthContextValue {
  member: AuthMember | null;
  loading: boolean;
  login: (input: LoginInput) => Promise<void>;
  developmentLogin: () => Promise<void>;
  developmentAdminLogin: () => Promise<void>;
  signup: (input: SignupInput) => Promise<void>;
  completeExternalLogin: (accessToken: string) => Promise<void>;
  logout: () => void;
  updateMember: (member: AuthMember) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [member, setMember] = useState<AuthMember | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getAccessToken()) { setLoading(false); return; }
    void getMe().then(setMember).catch(() => { clearAccessToken(); setMember(null); }).finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    member,
    loading,
    login: async (input) => { const response = await requestLogin(input); setAccessToken(response.accessToken); setMember(response.member); },
    developmentLogin: async () => { const response = await requestDevelopmentLogin(); setAccessToken(response.accessToken); setMember(response.member); },
    developmentAdminLogin: async () => { const response = await requestDevelopmentAdminLogin(); setAccessToken(response.accessToken); setMember(response.member); },
    signup: async (input) => { const response = await requestSignup(input); setAccessToken(response.accessToken); setMember(response.member); },
    completeExternalLogin: async (accessToken) => { setAccessToken(accessToken); setMember(await getMe()); },
    logout: () => { clearAccessToken(); setMember(null); },
    updateMember: setMember,
  }), [member, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider.");
  return context;
}
