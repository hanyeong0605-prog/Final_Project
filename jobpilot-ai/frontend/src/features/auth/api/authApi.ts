import { getJson, postJson } from "../../../api/httpClient";
import type { AuthMember, AuthResponse, EmailVerificationConfirmResponse, LoginIdAvailabilityResponse, LoginInput, SignupInput } from "../model/auth.types";

export const signup = (input: SignupInput) => postJson<AuthResponse>("/api/v1/auth/signup", input);
export const login = (input: LoginInput) => postJson<AuthResponse>("/api/v1/auth/login", input);
export const developmentLogin = () => postJson<AuthResponse>("/api/v1/dev/auth/token", {});
export const getMe = () => getJson<AuthMember>("/api/v1/auth/me");
export const checkLoginIdAvailability = (loginId: string) =>
  getJson<LoginIdAvailabilityResponse>(`/api/v1/auth/login-id-availability?loginId=${encodeURIComponent(loginId)}`);
export const sendEmailVerificationCode = (email: string) => postJson<void>("/api/v1/auth/email-verifications", { email });
export const confirmEmailVerificationCode = (email: string, code: string) =>
  postJson<EmailVerificationConfirmResponse>("/api/v1/auth/email-verifications/confirm", { email, code });
export const completeOAuthSignup = (input: {
  ticket: string; email: string; emailVerificationToken: string;
  termsAgreed: boolean; privacyCollectionAgreed: boolean; marketingEmailAgreed: boolean;
}) => postJson<AuthResponse>("/api/v1/auth/oauth/complete", input);
