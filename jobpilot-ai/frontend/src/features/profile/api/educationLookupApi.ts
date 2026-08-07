import { getJson } from "../../../api/httpClient";

export interface EducationSchool {
  id: string;
  name: string;
  schoolType: string;
  region: string;
  campusName: string;
}

export interface EducationMajor {
  id: string;
  name: string;
  field: string;
  relatedNames: string;
}

export function searchEducationSchools(query: string, educationLevel: string | null): Promise<EducationSchool[]> {
  return getJson<EducationSchool[]>(`/api/v1/education/schools?query=${encodeURIComponent(query)}&educationLevel=${encodeURIComponent(educationLevel ?? "")}`);
}

export function searchEducationMajors(query: string, educationLevel: string | null, schoolName: string): Promise<EducationMajor[]> {
  return getJson<EducationMajor[]>(`/api/v1/education/majors?query=${encodeURIComponent(query)}&educationLevel=${encodeURIComponent(educationLevel ?? "")}&schoolName=${encodeURIComponent(schoolName)}`);
}
