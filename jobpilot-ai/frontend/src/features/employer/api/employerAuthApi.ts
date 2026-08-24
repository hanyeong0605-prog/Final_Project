import { getEmployerJson, postEmployerJson } from "./employerHttpClient";
import type { EmployerAccount, EmployerAuthResponse, EmployerLoginInput, EmployerSignupInput } from "../model/employer.types";

export function signup(input: EmployerSignupInput) {
  return postEmployerJson<EmployerAccount>("/api/v1/employer/auth/signup", input);
}

export function login(input: EmployerLoginInput) {
  return postEmployerJson<EmployerAuthResponse>("/api/v1/employer/auth/login", input);
}

export function getMe() {
  return getEmployerJson<EmployerAccount>("/api/v1/employer/auth/me");
}
