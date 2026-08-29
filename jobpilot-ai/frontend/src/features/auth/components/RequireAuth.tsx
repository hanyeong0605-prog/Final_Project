import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useAuth } from "../model/AuthContext";
import { LoadingScreen } from "../../../shared/components/LoadingScreen";

export function RequireAuth({ children }: PropsWithChildren) {
  const { member, loading } = useAuth();
  if (loading) return <div className="auth-loading"><LoadingScreen label="인증 정보를 확인하는 중입니다" /></div>;
  return member ? children : <Navigate to="/login" replace />;
}
