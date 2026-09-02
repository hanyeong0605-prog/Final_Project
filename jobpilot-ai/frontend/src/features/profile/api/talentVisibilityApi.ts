import { getJson, putJson } from "../../../api/httpClient";
export const getTalentVisibility = () => getJson<{ enabled: boolean }>("/api/v1/members/me/talent-visibility");
export const setTalentVisibility = (enabled: boolean) => putJson<{ enabled: boolean }>("/api/v1/members/me/talent-visibility", { enabled });
