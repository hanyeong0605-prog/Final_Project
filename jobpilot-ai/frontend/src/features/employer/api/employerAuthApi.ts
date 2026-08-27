import { deleteEmployerJson, getEmployerJson, postEmployerJson, putEmployerJson } from "./employerHttpClient";
import type { EmployerAccount, EmployerAuthResponse, EmployerEnrollmentInput, EmployerLoginInput, EmployerPasswordlessStart, EmployerProfileInput, EmployerSignupInput } from "../model/employer.types";

export function signup(input: EmployerSignupInput) {
  return postEmployerJson<EmployerAccount>("/api/v1/employer/auth/signup", input);
}
export const login = (input: EmployerLoginInput) => postEmployerJson<EmployerAuthResponse>("/api/v1/employer/auth/login", input);
// 2026-08-26: 기업회원 가입 폼 아이디 중복확인용 - 개인회원 checkLoginIdAvailability(authApi.ts)와
// 같은 응답 형태({available: boolean})라 별도 타입 없이 그대로 재사용한다.
export const checkEmployerLoginIdAvailability = (loginId: string) =>
  getEmployerJson<{ available: boolean }>(`/api/v1/employer/auth/login-id-availability?loginId=${encodeURIComponent(loginId)}`);

export const requestEnrollment = (input: EmployerEnrollmentInput) => postEmployerJson<{ registered: boolean; status: string; data?: unknown }>("/api/v1/employer/passwordless/enrollment", input);
export const checkEnrollment = (input: EmployerEnrollmentInput) => postEmployerJson<{ registered: boolean; status: string }>("/api/v1/employer/passwordless/enrollment/status", input);
export const startPasswordless = (loginId: string) => postEmployerJson<EmployerPasswordlessStart>("/api/v1/employer/passwordless/start", { loginId });
export const passwordlessResult = (loginId: string, sessionId: string) => postEmployerJson<EmployerAuthResponse | { result: "WAIT"; data: unknown }>("/api/v1/employer/passwordless/result", { loginId, sessionId });
export const cancelPasswordless = (loginId: string, sessionId: string) => postEmployerJson<{ result: string }>("/api/v1/employer/passwordless/cancel", { loginId, sessionId });

export function getMe() {
  return getEmployerJson<EmployerAccount>("/api/v1/employer/auth/me");
}
export const updateMe = (input: EmployerProfileInput) => putEmployerJson<EmployerAccount>("/api/v1/employer/auth/me", input);
export const withdraw = () => deleteEmployerJson<void>("/api/v1/employer/auth/me");
