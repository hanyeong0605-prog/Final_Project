export type MemberCertificate = {
  id?: number;
  name: string;
  issuer: string | null;
  acquiredAt: string | null;
  expiresAt: string | null;
  officialUrl: string | null;
};

export type MemberCertificateInput = Omit<MemberCertificate, "id">;

export const emptyMemberCertificate = (): MemberCertificate => ({
  name: "",
  issuer: null,
  acquiredAt: null,
  expiresAt: null,
  officialUrl: null,
});
