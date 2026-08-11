import { useEffect, useRef, useState } from "react";
import { Filter, MapPin, Navigation, Search } from "lucide-react";
import { KakaoMapContainer } from "../features/location-jobs/components/KaKaoMapContainer";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import type { LocationJob } from "../features/location-jobs/model/types";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobPosting } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";
// import { WordCloudSection } from '../features/word-cloud/components/WordCloudSection';

const DEFAULT_ADDRESS = "서울특별시 마포구 백범로 23";
const DEFAULT_CENTER = { lat: 37.5528112, lng: 126.9379482 };
const PAGE_BG_COLOR = "#f5f7fb";

export function LocationJobsPage() {
  const [isPostcodeOpen, setIsPostcodeOpen] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<number | null>(null);
  const cardRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  const [currentAddress, setCurrentAddress] = useState(DEFAULT_ADDRESS);
  const [center, setCenter] = useState(DEFAULT_CENTER);
  const [radiusKm, setRadiusKm] = useState<number>(5);

  const [jobs, setJobs] = useState<LocationJob[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  const fetchLocationJobs = async (lat: number, lng: number, radius: number) => {
    setStatus("loading");
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || "";
      const response = await fetch(
        `${baseUrl}/api/location-jobs?latitude=${lat}&longitude=${lng}&radiusKm=${radius}`
      );
      if (!response.ok) throw new Error();
      setJobs(await response.json());
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

    window.kakao?.maps?.services?.Geocoder().addressSearch(address, (result: any[], statusResult: any) => {
      if (statusResult === window.kakao.maps.services.Status.OK) {
        setCenter({ lat: parseFloat(result[0].y), lng: parseFloat(result[0].x) });
      }
    });
  };

  return (
    <div style={{ backgroundColor: PAGE_BG_COLOR, minHeight: "100vh", width: "100%" }}>
      <PageHeading
        eyebrow="LOCATION BASED JOBS"
        title="우리 동네 채용공고"
        body="설정한 주소와 탐색 반경 내 위치한 개발 직무 공고를 한눈에 확인하세요."
      />

      <div style={{ display: "flex", width: "100%", height: "calc(100vh - 110px)", overflow: "hidden" }}>
        {/* 좌측 사이드바 */}
        <aside style={styles.aside}>
          <div style={styles.filterBox}>
            <div style={{ display: "flex", gap: "6px" }}>
              <button type="button" onClick={() => setIsPostcodeOpen(true)} style={styles.addressBtn}>
                <div style={{ display: "flex", alignItems: "center", gap: "6px", overflow: "hidden" }}>
                  <MapPin size={15} color="#526af3" />
                  <span style={styles.ellipsis}>{currentAddress}</span>
                </div>
                <Search size={14} color="#929aaa" />
              </button>

              <button
                type="button"
                onClick={() => handleSelectAddress(DEFAULT_ADDRESS)}
                style={styles.navBtn}
                title="기본 설정 주소로 이동"
              >
                <Navigation size={15} />
              </button>
            </div>

            <div style={styles.selectRow}>
              <span style={styles.selectLabel}>
                <Filter size={13} color="#526af3" /> 탐색 반경
              </span>
              <select
                value={radiusKm}
                onChange={(e) => {
                  setRadiusKm(Number(e.target.value));
                  setSelectedJobId(null);
                }}
                style={styles.select}
              >
                {[1, 3, 5, 10, 20].map((r) => (
                  <option key={r} value={r}>{r} km 이내</option>
                ))}
              </select>
            </div>
          </div>

          <div style={styles.countHeader}>
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
                    ref={(el) => { el ? cardRefs.current.set(job.id, el) : cardRefs.current.delete(job.id); }}
                    onClick={() => setSelectedJobId(job.id)}
                    style={{
                      ...styles.cardWrapper,
                      border: isSelected ? "2px solid #526af3" : "2px solid transparent",
                      boxShadow: isSelected ? "0 4px 12px rgba(39, 63, 133, 0.12)" : "none",
                    }}
                  >
                    {job.distanceKm !== undefined && (
                      <span style={styles.distanceBadge}>{job.distanceKm}km</span>
                    )}
                    <JobPostingCard posting={postingData} />
                  </div>
                );
              })}
          </div>
        </aside>

        {/* 우측 지도 */}
        <main style={{ flex: 1, height: "100%", padding: "0 16px 16px 16px" }}>
          <div style={styles.mapContainer}>
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

      {/* 예시용 워드클라우드 가져오기 */}
      {/* <section style={styles.wordCloudSection}>
        <WordCloudSection />
      </section> */}

      <PostcodeSearchModal
        isOpen={isPostcodeOpen}
        onClose={() => setIsPostcodeOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
    </div>
  );
}

//  스타일 정리
const styles: Record<string, React.CSSProperties> = {
  aside: { width: "380px", minWidth: "380px", height: "100%", display: "flex", flexDirection: "column", zIndex: 10, padding: "0 0 16px 16px" },
  filterBox: { padding: "12px", backgroundColor: "#ffffff", borderRadius: "12px", border: "1px solid #ebedf2", boxShadow: "0 1px 3px rgba(39, 63, 133, 0.03)", display: "flex", flexDirection: "column", gap: "8px", marginBottom: "10px", marginRight: "12px" },
  addressBtn: { flex: 1, display: "flex", alignItems: "center", justifyContent: "space-between", padding: "7px 10px", backgroundColor: "#ffffff", border: "1px solid #dce1ea", borderRadius: "8px", fontSize: "12px", color: "#30394d" },
  navBtn: { padding: "7px 9px", backgroundColor: "#eef2ff", border: "1px solid #cbd4ff", borderRadius: "8px", color: "#526af3" },
  selectRow: { display: "flex", alignItems: "center", justifyContent: "space-between", backgroundColor: "#ffffff", border: "1px solid #dce1ea", padding: "6px 10px", borderRadius: "8px" },
  selectLabel: { fontSize: "12px", fontWeight: "600", color: "#424b60", display: "flex", alignItems: "center", gap: "6px" },
  select: { fontSize: "11px", fontWeight: "bold", color: "#526af3", backgroundColor: "#ffffff", border: "1px solid #dce1ea", borderRadius: "6px", padding: "2px 6px" },
  countHeader: { padding: "0 16px 6px 4px", display: "flex", justifyContent: "space-between", fontSize: "12px", color: "#7c8596" },
  cardWrapper: { position: "relative", marginBottom: "8px", borderRadius: "10px", transition: "all 0.18s ease", cursor: "pointer", transform: "scale(0.96)", transformOrigin: "top left", width: "104%" },
  distanceBadge: { position: "absolute", top: "10px", right: "14px", fontSize: "10px", fontWeight: "800", color: "#526af3", backgroundColor: "#eef2ff", padding: "2px 6px", borderRadius: "20px", zIndex: 2, pointerEvents: "none" },
  mapContainer: { width: "100%", height: "100%", borderRadius: "13px", border: "1px solid #e8ebf1", overflow: "hidden", boxShadow: "0 1px 3px rgba(39, 63, 133, 0.03)" },
  wordCloudSection: { padding: "60px 20px", backgroundColor: "#ffffff", marginTop: "24px", borderTop: "1px solid #ebedf2" },
  ellipsis: { overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" },
};