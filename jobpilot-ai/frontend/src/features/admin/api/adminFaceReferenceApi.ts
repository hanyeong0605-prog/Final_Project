import { getJson, postForm } from "../../../api/httpClient";

export type AdminFaceReference = { loginId: string; nickname: string; registered: boolean };
const BASE = "/api/v1/admin/face-references";

export const getAdminFaceReferences = () => getJson<AdminFaceReference[]>(BASE);
export const uploadAdminFaceReference = (loginId: string, photo: File) => {
  const form = new FormData();
  form.append("photo", photo);
  return postForm<AdminFaceReference>(`${BASE}/${encodeURIComponent(loginId)}`, form);
};
