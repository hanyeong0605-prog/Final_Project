export interface AuthMember { id: number; loginId: string; email: string; nickname: string; onboardingCompleted: boolean; role: "USER" | "ADMIN"; }
export interface AuthResponse { accessToken: string; tokenType: "Bearer"; expiresInSeconds: number; member: AuthMember; }
export interface SignupInput {
  loginId: string;
  email: string;
  emailVerificationToken: string;
  password: string;
  nickname: string;
  termsAgreed: boolean;
  privacyCollectionAgreed: boolean;
  marketingEmailAgreed: boolean;
}
export interface LoginInput { loginId: string; password: string; }
export interface EmailVerificationConfirmResponse { verificationToken: string; }
export interface LoginIdAvailabilityResponse { available: boolean; }
