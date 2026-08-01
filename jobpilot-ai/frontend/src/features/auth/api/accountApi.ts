import { deleteJson, patchJson } from "../../../api/httpClient";
import type { AuthMember } from "../model/auth.types";

export const changeNickname = (nickname: string) =>
  patchJson<AuthMember>("/api/v1/members/me/nickname", { nickname });
export const changePassword = (currentPassword: string, newPassword: string) =>
  patchJson<void>("/api/v1/members/me/password", { currentPassword, newPassword });
export const withdraw = (password: string) => deleteJson("/api/v1/members/me", { password });
