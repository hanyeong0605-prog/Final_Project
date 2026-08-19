export type EmployerAccountStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface EmployerAccount {
  id: number;
  loginId: string;
  email: string;
  managerName: string;
  managerPhone: string | null;
  companyName: string;
  businessRegistrationNumber: string;
  representativeName: string;
  openingDate: string;
  companyAddress: string | null;
  ntsVerified: boolean;
  status: EmployerAccountStatus;
  rejectionReason: string | null;
}

export interface EmployerAuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  employer: EmployerAccount;
}

export interface EmployerSignupInput {
  loginId: string;
  email: string;
  password: string;
  managerName: string;
  managerPhone?: string;
  companyName: string;
  businessRegistrationNumber: string;
  representativeName: string;
  openingDate: string;
  companyAddress?: string;
}

export interface EmployerLoginInput {
  loginId: string;
  password: string;
}
