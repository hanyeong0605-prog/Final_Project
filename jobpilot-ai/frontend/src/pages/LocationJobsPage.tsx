import React, { useState, useEffect, useRef } from "react";
import { Search, MapPin, Navigation, List, Filter, Loader2 } from "lucide-react";
import { KakaoMapContainer } from "../features/location-jobs/components/KakaoMapContainer";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import { LocationJob } from "../features/location-jobs/types";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobPosting } from "../model/jobPosting.types";

export const LocationJobsPage: React.FC = () => {
  const [isPostcodeOpen, setIsPostcodeOpen] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<number | null>(null);
  
  const cardRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  // 기본 설정 주소 (서울시청)
  const [currentAddress, setCurrentAddress] = useState("서울특별시 중구 세종대로 110");
  const [center, setCenter] = useState<{ lat: number; lng: number }>({
    lat: 37.5665,
    lng: 126.9780,
  });

  const [radiusKm, setRadiusKm] = useState<number>(5);

  // 백엔드에서 받아올 실제 공고 목록 및 로딩 상태
  const [jobs, setJobs] = useState<LocationJob[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // 1. 백엔드 API 호출 함수
  const fetchLocationJobs = async (lat: number, lng: number, radius: number) => {
    setIsLoading(true);
    try {
      const response = await fetch(
  `${import.meta.env.VITE_API_BASE_URL}/api/location-jobs?latitude=${lat}&longitude=${lng}&radiusKm=${radius}`
);

      if (!response.ok) {
        throw new Error("채용 공고를 불러오는데 실패했습니다.");
      }

      const data: LocationJob[] = await response.json();
      setJobs(data);
    } catch (error) {
      console.error("API Fetch Error:", error);
    } finally {
      setIsLoading(false);
    }
  };

  // 2. 마커나 카드를 눌러 selectedJobId가 바뀔 때, 해당 카드를 목록 맨 위로 부드럽게 스크롤
  useEffect(() => {
    if (selectedJobId !== null) {
      const targetCard = cardRefs.current.get(selectedJobId);
      if (targetCard) {
        targetCard.scrollIntoView({
          behavior: "smooth",
          block: "start", // 선택된 카드를 리스트 최상단으로 올림
        });
      }
    }
  }, [selectedJobId]);

  // 3. 중심 좌표나 반경 변경 시 자동 조회
  useEffect(() => {
    fetchLocationJobs(center.lat, center.lng, radiusKm);
  }, [center, radiusKm]);

  // 주소 검색을 통해 주소 변경 시 좌표 변환
  const handleSelectAddress = (address: string) => {
    setCurrentAddress(address);
    setSelectedJobId(null);

    if (window.kakao && window.kakao.maps && window.kakao.maps.services) {
      const geocoder = new window.kakao.maps.services.Geocoder();
      geocoder.addressSearch(address, (result: any[], status: any) => {
        if (status === window.kakao.maps.services.Status.OK) {
          setCenter({
            lat: parseFloat(result[0].y),
            lng: parseFloat(result[0].x),
          });
        }
      });
    }
  };

  // 기본 설정 주소로 재설정
  const handleResetToUserAddress = () => {
    handleSelectAddress("서울특별시 중구 세종대로 110");
  };

  return (
    <div style={{ display: "flex", width: "100%", height: "calc(100vh - 80px)", backgroundColor: "#f9fafb" }}>
      
      {/* 좌측 검색 및 리스트 패널 */}
      <div style={{ width: "380px", minWidth: "380px", backgroundColor: "#ffffff", borderRight: "1px solid #e5e7eb", display: "flex", flexDirection: "column", zIndex: 10 }}>
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
              onClick={handleResetToUserAddress}
              style={{
                padding: "8px",
                backgroundColor: "#eff6ff",
                border: "1px solid #bfdbfe",
                borderRadius: "6px",
                color: "#2563eb",
                cursor: "pointer",
              }}
              title="내 기본 설정 주소로 이동"
            >
              <Navigation size={16} />
            </button>
          </div>

          {/* 반경 선택*/}
          <div style={{ display: "flex", alignItems: "center", backgroundColor: "#f0f9ff", padding: "8px 12px", borderRadius: "6px", border: "1px solid #e0f2fe" }}>
            <span style={{ fontSize: "12px", fontWeight: "600", color: "#0369a1", display: "flex", alignItems: "center", gap: "4px", flex: 1 }}>
              <Filter size={14} /> 탐색 반경 설정
            </span>
            <select
              value={radiusKm}
              onChange={(e) => {
                setRadiusKm(Number(e.target.value));
                setSelectedJobId(null);
              }}
              style={{ fontSize: "12px", fontWeight: "bold", color: "#0284c7", backgroundColor: "#ffffff", border: "1px solid #bae6fd", borderRadius: "4px", padding: "2px 6px", cursor: "pointer" }}
            >
              <option value={1}>1 km 이내</option>
              <option value={3}>3 km 이내</option>
              <option value={5}>5 km 이내</option>
              <option value={10}>10 km 이내</option>
              <option value={20}>20 km 이내</option>
            </select>
          </div>
        </div>

        {/* 채용 공고 카드 리스트 */}
        <div style={{ flex: 1, overflowY: "auto", padding: "16px" }}>
          <div style={{ fontSize: "12px", color: "#6b7280", marginBottom: "10px" }}>
            주변 공고 <strong style={{ color: "#2563eb" }}>{jobs.length}</strong>개
          </div>

          {isLoading ? (
            <div style={{ textAlign: "center", padding: "40px 0", color: "#6b7280", fontSize: "13px" }}>
              <Loader2 size={24} className="animate-spin" style={{ margin: "0 auto 8px" }} />
              주변 공고를 찾는 중입니다...
            </div>
          ) : jobs.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 0", color: "#9ca3af", fontSize: "12px" }}>
              <List size={24} style={{ margin: "0 auto 8px", opacity: 0.5 }} />
              선택한 반경 내에 등록된 공고가 없습니다.
            </div>
          ) : (
            jobs.map((job: any) => {
              const isSelected = job.id === selectedJobId;
              
              
              const postingData = {
                ...job,
                id: job.jobPostingId || job.id,
                companyName: job.companyName || job.company_name || job.company || "기업명 미지정",
                title: job.title || job.jobTitle || "채용 공고",
                location: job.address || job.locationText || job.location,
                companyLogoUrl: job.companyLogoUrl || job.logoUrl,
              } as unknown as JobPosting;

              return (
                <div
                  key={job.id}
                  // ref 등록: 지도의 마커 클릭 시 스크롤 위치를 찾아올 수 있게 설정
                  ref={(el) => {
                    if (el) {
                      cardRefs.current.set(job.id, el);
                    } else {
                      cardRefs.current.delete(job.id);
                    }
                  }}
                  onClick={() => setSelectedJobId(job.id)}
                  style={{
                    position: "relative",
                    borderRadius: "12px",
                    marginBottom: "12px",
                    border: isSelected ? "2px solid #2563eb" : "1px solid transparent",
                    boxShadow: isSelected ? "0 4px 12px rgba(37, 99, 235, 0.15)" : "none",
                    transition: "all 0.2s",
                  }}
                >
                  
                  {job.distanceKm !== undefined && (
                    <span
                      style={{
                        position: "absolute",
                        top: "14px",
                        right: "48px",
                        fontSize: "11px",
                        fontWeight: "bold",
                        color: "#2563eb",
                        backgroundColor: "#eff6ff",
                        padding: "2px 8px",
                        borderRadius: "12px",
                        zIndex: 2,
                        pointerEvents: "none",
                      }}
                    >
                      {job.distanceKm}km
                    </span>
                  )}

                  {/* JobPostingCard 재사용 */}
                  <JobPostingCard posting={postingData} />
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* 카카오 지도 */}
      <div style={{ flex: 1, height: "100%", position: "relative" }}>
        <KakaoMapContainer
          center={center}
          radiusKm={radiusKm}
          jobs={jobs}
          selectedJobId={selectedJobId}
          onSelectJob={(job) => setSelectedJobId(job.id)}
        />
      </div>

      <PostcodeSearchModal
        isOpen={isPostcodeOpen}
        onClose={() => setIsPostcodeOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
    </div>
  );
};