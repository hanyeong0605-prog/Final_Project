import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useAuth } from "../model/AuthContext";
import { LoadingScreen } from "../../../shared/components/LoadingScreen";

export function RequireAdmin({ children }: PropsWithChildren) {
  const { member, loading } = useAuth();
  if (loading) return <div className="auth-loading"><LoadingScreen label="권한 정보를 확인하는 중입니다" /></div>;
  return member?.role === "ADMIN" ? children : <Navigate to="/" replace />;
}
