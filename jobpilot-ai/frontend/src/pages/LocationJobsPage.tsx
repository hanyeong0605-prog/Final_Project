import { useEffect, useRef, useState } from "react";
import { Filter, MapPin, Navigation, Search } from "lucide-react";
import { KakaoMapContainer } from "../features/location-jobs/components/KakaoMapContainer";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import type { LocationJob } from "../features/location-jobs/types";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobPosting } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

const DEFAULT_ADDRESS = "서울특별시 마포구 백범로 23";
const DEFAULT_CENTER = { lat: 37.5528112 , lng: 126.9379482  };
const PAGE_BG_COLOR = "#f5f7fb";

export function LocationJobsPage() {
  const [isPostcodeOpen, setIsPostcodeOpen] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<number | null>(null);
  const cardRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  const [currentAddress, setCurrentAddress] = useState(DEFAULT_ADDRESS);
  const [center, setCenter] = useState<{ lat: number; lng: number }>(DEFAULT_CENTER);
  const [radiusKm, setRadiusKm] = useState<number>(5);

  const [jobs, setJobs] = useState<LocationJob[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  const fetchLocationJobs = async (lat: number, lng: number, radius: number) => {
    setStatus("loading");
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:9000";
      const response = await fetch(
        `${baseUrl}/api/location-jobs?latitude=${lat}&longitude=${lng}&radiusKm=${radius}`
      );

      if (!response.ok) throw new Error();

      const data: LocationJob[] = await response.json();
      setJobs(data);
      setStatus("ready");
    } catch {
      setStatus("error");
    }
  };

  useEffect(() => {
    if (selectedJobId !== null) {
      cardRefs.current.get(selectedJobId)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [selectedJobId]);

  useEffect(() => {
    void fetchLocationJobs(center.lat, center.lng, radiusKm);
  }, [center, radiusKm]);

  const handleSelectAddress = (address: string) => {
    setCurrentAddress(address);
    setSelectedJobId(null);

    if (window.kakao?.maps?.services) {
      const geocoder = new window.kakao.maps.services.Geocoder();
      geocoder.addressSearch(address, (result: any[], statusResult: any) => {
        if (statusResult === window.kakao.maps.services.Status.OK) {
          setCenter({
            lat: parseFloat(result[0].y),
            lng: parseFloat(result[0].x),
          });
        }
      });
    }
  };

  return (
    <div style={{ backgroundColor: PAGE_BG_COLOR, minHeight: "100vh", width: "100%" }}>
      <PageHeading
        eyebrow="LOCATION BASED JOBS"
        title="우리 동네 채용공고"
        body="설정한 주소와 탐색 반경 내 위치한 개발 직무 공고를 한눈에 확인하세요."
      />

      <div style={{ display: "flex", width: "100%", height: "calc(100vh - 110px)", overflow: "hidden" }}>
        <aside
          style={{
            width: "380px",
            minWidth: "380px",
            height: "100%",
            display: "flex",
            flexDirection: "column",
            zIndex: 10,
            padding: "0 0 16px 16px",
          }}
        >
          <div
            style={{
              padding: "12px",
              backgroundColor: "#ffffff",
              borderRadius: "12px",
              border: "1px solid #ebedf2",
              boxShadow: "0 1px 3px rgba(39, 63, 133, 0.03)",
              display: "flex",
              flexDirection: "column",
              gap: "8px",
              marginBottom: "10px",
              marginRight: "12px",
            }}
          >
            <div style={{ display: "flex", gap: "6px" }}>
              <button
                type="button"
                onClick={() => setIsPostcodeOpen(true)}
                style={{
                  flex: 1,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "7px 10px",
                  backgroundColor: "#ffffff",
                  border: "1px solid #dce1ea",
                  borderRadius: "8px",
                  fontSize: "12px",
                  color: "#30394d",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "6px", overflow: "hidden" }}>
                  <MapPin size={15} color="#526af3" />
                  <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{currentAddress}</span>
                </div>
                <Search size={14} color="#929aaa" />
              </button>

              <button
                type="button"
                onClick={() => handleSelectAddress(DEFAULT_ADDRESS)}
                style={{
                  padding: "7px 9px",
                  backgroundColor: "#eef2ff",
                  border: "1px solid #cbd4ff",
                  borderRadius: "8px",
                  color: "#526af3",
                }}
                title="기본 설정 주소로 이동"
              >
                <Navigation size={15} />
              </button>
            </div>

            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                backgroundColor: "#ffffff",
                border: "1px solid #dce1ea",
                padding: "6px 10px",
                borderRadius: "8px",
              }}
            >
              <span style={{ fontSize: "12px", fontWeight: "600", color: "#424b60", display: "flex", alignItems: "center", gap: "6px" }}>
                <Filter size={13} color="#526af3" /> 탐색 반경
              </span>
              <select
                value={radiusKm}
                onChange={(e) => {
                  setRadiusKm(Number(e.target.value));
                  setSelectedJobId(null);
                }}
                style={{
                  fontSize: "11px",
                  fontWeight: "bold",
                  color: "#526af3",
                  backgroundColor: "#ffffff",
                  border: "1px solid #dce1ea",
                  borderRadius: "6px",
                  padding: "2px 6px",
                }}
              >
                <option value={1}>1 km 이내</option>
                <option value={3}>3 km 이내</option>
                <option value={5}>5 km 이내</option>
                <option value={10}>10 km 이내</option>
                <option value={20}>20 km 이내</option>
              </select>
            </div>
          </div>

          <div style={{ padding: "0 16px 6px 4px", display: "flex", justifyContent: "space-between", fontSize: "12px", color: "#7c8596" }}>
            <strong>주변 공고 <span style={{ color: "#526af3" }}>{jobs.length.toLocaleString()}</span>개</strong>
            <span>반경 {radiusKm}km 이내</span>
          </div>

          <div style={{ flex: 1, overflowY: "auto", paddingRight: "10px" }}>
            {status === "loading" && <DataStatePanel state="loading" />}
            {status === "error" && <DataStatePanel state="error" />}
            {status === "ready" && jobs.length === 0 && (
              <DataStatePanel
                state="empty"
                emptyTitle="선택한 반경 내 공고가 없습니다"
                emptyBody="탐색 반경을 넓히거나 다른 지역으로 변경해 보세요."
              />
            )}

            {status === "ready" &&
              jobs.map((job) => {
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
                    ref={(el) => {
                      if (el) cardRefs.current.set(job.id, el);
                      else cardRefs.current.delete(job.id);
                    }}
                    onClick={() => setSelectedJobId(job.id)}
                    style={{
                      position: "relative",
                      marginBottom: "8px",
                      borderRadius: "10px",
                      border: isSelected ? "2px solid #526af3" : "2px solid transparent",
                      boxShadow: isSelected ? "0 4px 12px rgba(39, 63, 133, 0.12)" : "none",
                      transition: "all 0.18s ease",
                      cursor: "pointer",
                      transform: "scale(0.96)",
                      transformOrigin: "top left",
                      width: "104%",
                    }}
                  >
                    {job.distanceKm !== undefined && (
                      <span
                        style={{
                          position: "absolute",
                          top: "10px",
                          right: "14px",
                          fontSize: "10px",
                          fontWeight: "800",
                          color: "#526af3",
                          backgroundColor: "#eef2ff",
                          padding: "2px 6px",
                          borderRadius: "20px",
                          zIndex: 2,
                          pointerEvents: "none",
                        }}
                      >
                        {job.distanceKm}km
                      </span>
                    )}
                    <JobPostingCard posting={postingData} />
                  </div>
                );
              })}
          </div>
        </aside>

        <main style={{ flex: 1, height: "100%", padding: "0 16px 16px 16px" }}>
          <div
            style={{
              width: "100%",
              height: "100%",
              borderRadius: "13px",
              border: "1px solid #e8ebf1",
              overflow: "hidden",
              boxShadow: "0 1px 3px rgba(39, 63, 133, 0.03)",
            }}
          >
            <KakaoMapContainer
              center={center}
              radiusKm={radiusKm}
              jobs={jobs}
              selectedJobId={selectedJobId}
              onSelectJob={(job) => setSelectedJobId(job.id)}
            />
          </div>
        </main>
      </div>

      <PostcodeSearchModal
        isOpen={isPostcodeOpen}
        onClose={() => setIsPostcodeOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
    </div>
  );
}