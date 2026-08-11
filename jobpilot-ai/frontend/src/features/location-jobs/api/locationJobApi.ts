import { getJson } from "../../../api/httpClient";
import type { LocationJob, LocationJobRequestParams } from "../model/types";

export const fetchLocationJobs = async (
  params: LocationJobRequestParams
): Promise<LocationJob[]> => {
  const query = new URLSearchParams({
    latitude: String(params.latitude),
    longitude: String(params.longitude),
    radiusKm: String(params.radiusKm ?? params.radius ?? 20),
  });
  return getJson<LocationJob[]>(`/api/location-jobs?${query}`);
};
