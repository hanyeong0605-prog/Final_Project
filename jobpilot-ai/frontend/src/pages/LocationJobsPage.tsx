import React, { useState } from "react";
import { Search, MapPin, Navigation, List, Filter } from "lucide-react";
import { KakaoMapContainer } from "../features/location-jobs/components/KakaoMapContainer";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import { LocationJob } from "../features/location-jobs/types";

export const LocationJobsPage: React.FC = () => {
  const [isPostcodeOpen, setIsPostcodeOpen] = useState(false);
  const [currentAddress, setCurrentAddress] = useState("서울특별시 마포구 신촌로 지하 90");
  const [center, setCenter] = useState<{ lat: number; lng: number }>({
    lat: 37.55528,
    lng: 126.93694,
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
    <div style={{ display: "flex", width: "100%", height: "calc(100vh - 80px)", backgroundColor: "#f9fafb" }}>
      
      {/* 1. 좌측 패널 (너비 380px 고정) */}
      <div style={{ width: "380px", minWidth: "380px", backgroundColor: "#ffffff", borderRight: "1px solid #e5e7eb", display: "flex", flexDirection: "column", zIndex: 10 }}>
        
        {/* 상단 검색 & 필터 영역 */}
        <div style={{ padding: "16px", borderBottom: "1px solid #f3f4f6" }}>
          <h1 style={{ fontSize: "18px", fontWeight: "bold", color: "#1f2937", display: "flex", alignItems: "center", gap: "8px", marginBottom: "12px" }}>
            <MapPin size={20} color="#2563eb" /> 우리 동네 채용공고
          </h1>

          {/* 주소 검색 바 */}
          <div style={{ display: "flex", gap: "6px", marginBottom: "10px" }}>
            <button
              onClick={() => setIsPostcodeOpen(true)}
              style={{
                flex: 1,
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                padding: "8px 12px",
                backgroundColor: "#f9fafb",
                border: "1px solid #d1d5db",
                borderRadius: "6px",
                fontSize: "12px",
                color: "#374151",
                cursor: "pointer",
              }}
            >
              <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{currentAddress}</span>
              <Search size={14} color="#9ca3af" />
            </button>
            <button
              onClick={handleGetCurrentLocation}
              style={{
                padding: "8px",
                backgroundColor: "#eff6ff",
                border: "1px solid #bfdbfe",
                borderRadius: "6px",
                color: "#2563eb",
                cursor: "pointer",
              }}
              title="내 현재 위치"
            >
              <Navigation size={16} />
            </button>
          </div>

          {/* 반경 선택 셀렉트 */}
          <div style={{ display: "flex", alignItems: "center", justifyBetween: "space-between", backgroundColor: "#f0f9ff", padding: "8px 12px", borderRadius: "6px", border: "1px solid #e0f2fe" }}>
            <span style={{ fontSize: "12px", fontWeight: "600", color: "#0369a1", display: "flex", alignItems: "center", gap: "4px", flex: 1 }}>
              <Filter size={14} /> 탐색 반경 설정
            </span>
            <select
              value={radiusKm}
              onChange={(e) => setRadiusKm(Number(e.target.value))}
              style={{ fontSize: "12px", fontWeight: "bold", color: "#0284c7", backgroundColor: "#ffffff", border: "1px solid #bae6fd", borderRadius: "4px", padding: "2px 6px", cursor: "pointer" }}
            >
              <option value={3}>3 km 이내</option>
              <option value={5}>5 km 이내</option>
              <option value={10}>10 km 이내</option>
              <option value={20}>20 km 이내</option>
            </select>
          </div>
        </div>

        {/* 채용공고 목록 리스트 */}
        <div style={{ flex: 1, overflowY: "auto", padding: "16px" }}>
          <div style={{ fontSize: "12px", color: "#6b7280", marginBottom: "10px" }}>
            주변 공고 <strong style={{ color: "#2563eb" }}>{filteredJobs.length}</strong>개
          </div>

          {filteredJobs.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 0", color: "#9ca3af", fontSize: "12px" }}>
              <List size={24} style={{ margin: "0 auto 8px", opacity: 0.5 }} />
              반경 내에 등록된 공고가 없습니다.
            </div>
          ) : (
            filteredJobs.map((job) => (
              <div
                key={job.id}
                style={{
                  padding: "12px",
                  border: "1px solid #e5e7eb",
                  borderRadius: "8px",
                  backgroundColor: "#ffffff",
                  marginBottom: "10px",
                  cursor: "pointer",
                }}
              >
                <h3 style={{ fontSize: "14px", fontWeight: "600", color: "#111827", margin: "0 0 4px 0" }}>{job.title}</h3>
                <p style={{ fontSize: "12px", color: "#4b5563", margin: "0 0 8px 0" }}>{job.companyName}</p>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "11px", color: "#9ca3af" }}>
                  <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "200px" }}>{job.address}</span>
                  <span style={{ fontWeight: "bold", color: "#2563eb", backgroundColor: "#eff6ff", padding: "2px 8px", borderRadius: "12px" }}>
                    {job.distanceKm}km
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 2. 우측 지도 영역 */}
      <div style={{ flex: 1, height: "100%", position: "relative" }}>
        <KakaoMapContainer center={center} radiusKm={radiusKm} jobs={filteredJobs} />
      </div>

      {/* 3. 우편번호 모달 */}
      <PostcodeSearchModal
        isOpen={isPostcodeOpen}
        onClose={() => setIsPostcodeOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
    </div>
  );
};