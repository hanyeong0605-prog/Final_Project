import { Navigate } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useEmployerAuth } from "../model/EmployerAuthContext";

export function RequireEmployer({ children }: PropsWithChildren) {
  const { employer, loading } = useEmployerAuth();
  if (loading) return <div className="auth-loading">기업회원 정보를 확인하고 있습니다.</div>;
  return employer ? children : <Navigate to="/employer/login" replace />;
}
