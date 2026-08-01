import { getJson, postJson } from "../../../api/httpClient";
import type { AuthMember, AuthResponse, LoginInput, SignupInput } from "../model/auth.types";

export const signup = (input: SignupInput) => postJson<AuthResponse>("/api/v1/auth/signup", input);
export const login = (input: LoginInput) => postJson<AuthResponse>("/api/v1/auth/login", input);
export const getMe = () => getJson<AuthMember>("/api/v1/auth/me");
