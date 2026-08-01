import { getJson, postJson, putJson } from "../../../api/httpClient";
import type { AuthMember } from "../../auth/model/auth.types";
import type { CareerProfile } from "../model/careerProfile.types";
export const getCareerProfile = () => getJson<CareerProfile | undefined>("/api/v1/members/me/career-profile");
export const saveCareerProfile = (input: CareerProfile) => putJson<CareerProfile>("/api/v1/members/me/career-profile", input);
export const skipCareerProfile = () => postJson<AuthMember>("/api/v1/members/me/career-profile/skip", {});
