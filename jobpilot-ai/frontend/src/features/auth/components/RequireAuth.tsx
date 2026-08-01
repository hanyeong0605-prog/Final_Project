import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useAuth } from "../model/AuthContext";

export function RequireAuth({ children }: PropsWithChildren) {
  const { member, loading } = useAuth();
  if (loading) return <div className="auth-loading">인증 정보를 확인하고 있습니다.</div>;
  return member ? children : <Navigate to="/login" replace />;
}
