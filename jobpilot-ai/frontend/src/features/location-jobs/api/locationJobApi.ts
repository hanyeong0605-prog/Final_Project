import { httpClient } from "../../../api/httpClient";
import { LocationJob, LocationJobRequestParams } from "..model/types";

export const fetchLocationJobs = async (
  params: LocationJobRequestParams
): Promise<LocationJob[]> => {
  const response = await httpClient.get<LocationJob[]>("/api/location-jobs", {
    params: {
      latitude: params.latitude,
      longitude: params.longitude,
      radius: params.radius ?? 20,
    },
  });
  return response.data;
};