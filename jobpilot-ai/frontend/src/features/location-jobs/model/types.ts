export interface LocationJob {
  id: number;
  jobPostingId?: number;     
  title: string;
  jobTitle?: string;
  companyName?: string;      
  company?: string;          
  company_name?: string;     
  address?: string;
  locationText?: string;     
  location?: string;
  latitude: number;
  longitude: number;
  distanceKm?: number;
  companyLogoUrl?: string;
  logoUrl?: string;
  thumbnailUrl?: string;
}

export interface LocationJobRequestParams {
  latitude: number;
  longitude: number;
  radiusKm?: number;        
  radius?: number; 
}
