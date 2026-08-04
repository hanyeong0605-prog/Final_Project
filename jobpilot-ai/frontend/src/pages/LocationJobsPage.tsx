import React, { useState } from "react";
import { Search, MapPin, Navigation, List, Filter } from "lucide-react";
import { KakaoMapContainer } from "../features/location-jobs/components/KakaoMapContainer";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import { LocationJob } from "../features/location-jobs/types";

export const LocationJobsPage: React.FC = () => {
  const [isPostcodeOpen, setIsPostcodeOpen] = useState(false);
  const [currentAddress, setCurrentAddress] = useState("서울특별시 중구 세종대로 110");
  const [center, setCenter] = useState<{ lat: number; lng: number }>({
    lat: 37.555390,
    lng: 126.936086,
  });

  const [radiusKm, setRadiusKm] = useState<number>(5);

  const [jobs] = useState<LocationJob[]>([
    {
      id: 1,
      title: "프론트엔드 React 개발자 채용",
      companyName: "ict 인재개발원 신촌",
      address: "서울특별시 마포구 백범로 23",
      latitude: 37.5508,
      longitude: 126.9405,
      distanceKm: 0.4,
    },
    {
      id: 2,
      title: "백엔드 Java/Spring 개발자 모집",
      companyName: "테크 파이오니어",
      address: "서울특별시 종로구 종로 15",
      latitude: 37.5700,
      longitude: 126.9770,
      distanceKm: 1.5,
    },
  ]);

  const filteredJobs = jobs.filter((job) => job.distanceKm <= radiusKm);

  const handleSelectAddress = (address: string) => {
    setCurrentAddress(address);

    const geocoder = new window.kakao.maps.services.Geocoder();
    geocoder.addressSearch(address, (result: any[], status: any) => {
      if (status === window.kakao.maps.services.Status.OK) {
        setCenter({
          lat: parseFloat(result[0].y),
          lng: parseFloat(result[0].x),
        });
      }
    });
  };

  const handleGetCurrentLocation = () => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition((pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        setCenter({ lat, lng });

        const geocoder = new window.kakao.maps.services.Geocoder();
        geocoder.coord2Address(lng, lat, (result: any[], status: any) => {
          if (status === window.kakao.maps.services.Status.OK) {
            setCurrentAddress(result[0].address.address_name);
          }
        });
      });
    }
  };

  return (
    <div className="flex w-full h-[calc(100vh-80px)] bg-gray-100 overflow-hidden border-t border-gray-200">
      
      <div className="w-[380px] shrink-0 bg-white shadow-md flex flex-col z-10 border-r border-gray-200 h-full">
        <div className="p-4 border-b border-gray-100 space-y-3">
          <h1 className="text-lg font-bold text-gray-800 flex items-center gap-2">
            <MapPin className="text-blue-600 w-5 h-5 shrink-0" /> 우리 동네 채용공고
          </h1>

          <div className="flex gap-1.5">
            <button
              onClick={() => setIsPostcodeOpen(true)}
              className="flex-1 flex items-center justify-between px-3 py-2 bg-gray-50 border border-gray-300 rounded-md text-xs text-gray-700 hover:border-blue-500 transition shadow-sm"
            >
              <span className="truncate">{currentAddress}</span>
              <Search className="w-4 h-4 text-gray-400 shrink-0 ml-1" />
            </button>
            <button
              onClick={handleGetCurrentLocation}
              className="p-2 bg-blue-50 text-blue-600 rounded-md hover:bg-blue-100 transition border border-blue-200"
              title="내 현재 위치"
            >
              <Navigation className="w-4 h-4" />
            </button>
          </div>

          <div className="flex items-center justify-between bg-blue-50/60 px-3 py-2 rounded-md border border-blue-100">
            <span className="text-xs font-medium text-gray-700 flex items-center gap-1">
              <Filter className="w-3.5 h-3.5 text-blue-600" /> 탐색 반경 설정
            </span>
            <select
              value={radiusKm}
              onChange={(e) => setRadiusKm(Number(e.target.value))}
              className="text-xs font-bold text-blue-600 bg-white border border-blue-200 rounded px-2 py-0.5 focus:outline-none cursor-pointer"
            >
              <option value={3}>3 km 이내</option>
              <option value={5}>5 km 이내</option>
              <option value={10}>10 km 이내</option>
              <option value={20}>20 km 이내</option>
            </select>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
          <div className="text-xs text-gray-500 mb-1">
            주변 공고 <span className="text-blue-600 font-bold">{filteredJobs.length}</span>개
          </div>

          {filteredJobs.length === 0 ? (
            <div className="text-center py-10 text-gray-400 text-xs">
              <List className="mx-auto mb-2 opacity-40 w-6 h-6" />
              반경 내에 등록된 공고가 없습니다.
            </div>
          ) : (
            filteredJobs.map((job) => (
              <div
                key={job.id}
                className="p-3.5 border border-gray-200 rounded-lg hover:border-blue-500 hover:shadow-sm cursor-pointer transition bg-white"
              >
                <h3 className="font-semibold text-gray-800 text-sm mb-1">{job.title}</h3>
                <p className="text-xs text-gray-500 mb-2">{job.companyName}</p>
                <div className="flex justify-between items-center text-xs text-gray-400">
                  <span className="truncate max-w-[180px]">{job.address}</span>
                  <span className="font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded text-[11px]">
                    {job.distanceKm}km
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="flex-1 h-full relative min-w-0">
        <KakaoMapContainer center={center} radiusKm={radiusKm} jobs={filteredJobs} />
      </div>

      <PostcodeSearchModal
        isOpen={isPostcodeOpen}
        onClose={() => setIsPostcodeOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
    </div>
  );
};