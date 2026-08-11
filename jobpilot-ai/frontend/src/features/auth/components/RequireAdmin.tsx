import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useAuth } from "../model/AuthContext";

export function RequireAdmin({ children }: PropsWithChildren) {
  const { member, loading } = useAuth();
  if (loading) return <div className="auth-loading">권한 정보를 확인하고 있습니다.</div>;
  return member?.role === "ADMIN" ? children : <Navigate to="/" replace />;
}
