import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useEmployerAuth } from "../model/EmployerAuthContext";
import { LoadingScreen } from "../../../shared/components/LoadingScreen";

export function RequireEmployer({ children }: PropsWithChildren) {
  const { employer, loading } = useEmployerAuth();
  if (loading) return <div className="auth-loading"><LoadingScreen label="기업회원 정보를 확인하는 중입니다" /></div>;
  return employer ? children : <Navigate to="/employer/login" replace />;
}
