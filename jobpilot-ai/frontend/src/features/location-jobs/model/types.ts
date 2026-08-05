export interface LocationJob {
  id: number;
  title: string;
  companyName: string;
  address: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
}

export interface LocationJobRequestParams {
  latitude: number;
  longitude: number;
  radius?: number; 
}