export interface AuthMember { id: number; loginId: string; email: string; nickname: string; onboardingCompleted: boolean; }
export interface AuthResponse { accessToken: string; tokenType: "Bearer"; expiresInSeconds: number; member: AuthMember; }
export interface SignupInput { loginId: string; email: string; password: string; nickname: string; }
export interface LoginInput { loginId: string; password: string; }
