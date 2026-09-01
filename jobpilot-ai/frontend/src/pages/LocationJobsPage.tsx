import { useEffect, useRef, useState } from "react";
import { Filter, Lock, MapPin, Navigation, Search, Unlock } from "lucide-react";
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
  const [isCenterLocked, setIsCenterLocked] = useState(true);
  const [locating, setLocating] = useState(false);
  const [locationMessage, setLocationMessage] = useState<string | null>(null);

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
    setIsCenterLocked(true);
    setLocationMessage("선택한 주소를 탐색 기준으로 고정했습니다.");

    window.kakao?.maps?.services?.Geocoder().addressSearch(address, (result: any[], statusResult: any) => {
      if (statusResult === window.kakao.maps.services.Status.OK) {
        setCenter({ lat: parseFloat(result[0].y), lng: parseFloat(result[0].x) });
      }
    });
  };

  const handleMapCenterChanged = (nextCenter: { lat: number; lng: number }) => {
    if (isCenterLocked) return;
    setSelectedJobId(null);
    setCenter((current) => Math.abs(current.lat - nextCenter.lat) < 0.00001 && Math.abs(current.lng - nextCenter.lng) < 0.00001 ? current : nextCenter);
  };

  const getAddressFromCoordinates = (lat: number, lng: number) => new Promise<string | null>((resolve) => {
    const geocoder = window.kakao?.maps?.services?.Geocoder ? new window.kakao.maps.services.Geocoder() : null;
    if (!geocoder) {
      resolve(null);
      return;
    }
    geocoder.coord2Address(lng, lat, (result: any[], statusResult: any) => {
      if (statusResult !== window.kakao.maps.services.Status.OK || !result[0]) {
        resolve(null);
        return;
      }
      resolve(result[0].road_address?.address_name ?? result[0].address?.address_name ?? null);
    });
  });

  const moveToCurrentLocation = () => {
    if (!navigator.geolocation) {
      setLocationMessage("이 브라우저에서는 현재 위치 기능을 사용할 수 없습니다.");
      return;
    }
    setLocating(true);
    setLocationMessage(null);
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        const nextCenter = { lat: coords.latitude, lng: coords.longitude };
        const address = await getAddressFromCoordinates(nextCenter.lat, nextCenter.lng);
        setCenter(nextCenter);
        setCurrentAddress(address ?? "현재 위치");
        setSelectedJobId(null);
        setIsCenterLocked(true);
        setLocationMessage("현재 실제 위치를 기준으로 고정했습니다.");
        setLocating(false);
      },
      () => {
        setLocationMessage("현재 위치를 가져오지 못했습니다. 브라우저 위치 권한을 허용해 주세요.");
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
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
                  <MapPin size={15} color="#277fbb" />
                  <span style={styles.ellipsis}>{currentAddress}</span>
                </div>
                <Search size={14} color="#929aaa" />
              </button>

              <button
                type="button"
                onClick={moveToCurrentLocation}
                style={styles.navBtn}
                title="내 실제 위치로 이동"
                disabled={locating}
              >
                <Navigation size={15} />
              </button>
            </div>

            <div style={styles.locationControl}>
              <span style={styles.locationHint}>
                {isCenterLocked ? <Lock size={12} /> : <Unlock size={12} />}
                {isCenterLocked ? "선택한 기준 위치에 고정됨" : "지도를 움직여 탐색 중"}
              </span>
              <label style={styles.lockSwitch} title={isCenterLocked ? "탐색 중심과 반경 고정" : "지도 중심을 따라 탐색"}>
                <input type="checkbox" checked={isCenterLocked} onChange={(event) => { const locked = event.target.checked; setIsCenterLocked(locked); setLocationMessage(locked ? "탐색 중심과 반경을 고정했습니다. 지도 화면은 자유롭게 이동할 수 있습니다." : "지도 중심을 따라 탐색 위치가 이동합니다."); }} style={styles.lockSwitchInput} />
                <span style={{ ...styles.lockSwitchTrack, backgroundColor: isCenterLocked ? "#5B92F3" : "#cbd3df" }}><span style={{ ...styles.lockSwitchThumb, transform: isCenterLocked ? "translateX(16px)" : "translateX(0)" }} /></span>
                <span>{isCenterLocked ? "ON" : "OFF"}</span>
              </label>
            </div>
            {locationMessage && <p style={styles.locationMessage}>{locationMessage}</p>}

            <div style={styles.selectRow}>
              <span style={styles.selectLabel}>
                <Filter size={13} color="#277fbb" /> 탐색 반경
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
                  thumbnailUrl: job.thumbnailUrl,
                } as unknown as JobPosting;

                return (
                  <div
                    key={job.id}
                    ref={(el) => { el ? cardRefs.current.set(job.id, el) : cardRefs.current.delete(job.id); }}
                    onClick={() => setSelectedJobId(job.id)}
                    style={{
                      ...styles.cardWrapper,
                      border: isSelected ? "2px solid #277fbb" : "2px solid transparent",
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
              isCenterLocked={isCenterLocked}
              onSelectJob={(job) => setSelectedJobId(job.id)}
              onCenterChanged={handleMapCenterChanged}
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
  navBtn: { padding: "7px 9px", backgroundColor: "#edf9ff", border: "1px solid #b9dff1", borderRadius: "8px", color: "#277fbb" },
  locationControl: { display: "flex", alignItems: "center", justifyContent: "space-between", gap: "8px", padding: "2px 1px 0" },
  locationHint: { display: "inline-flex", alignItems: "center", gap: "4px", color: "#69778e", fontSize: "10px", fontWeight: "700" },
  lockSwitch: { display: "inline-flex", alignItems: "center", gap: "5px", color: "#5B92F3", fontSize: "10px", fontWeight: "800", cursor: "pointer", userSelect: "none" },
  lockSwitchInput: { position: "absolute", width: "1px", height: "1px", opacity: 0, pointerEvents: "none" },
  lockSwitchTrack: { display: "inline-flex", alignItems: "center", width: "34px", height: "18px", padding: "2px", borderRadius: "999px", transition: "background-color .18s ease" },
  lockSwitchThumb: { display: "block", width: "14px", height: "14px", borderRadius: "50%", backgroundColor: "#fff", boxShadow: "0 1px 4px rgba(30, 48, 82, .28)", transition: "transform .18s ease" },
  locationMessage: { margin: "-1px 1px 0", color: "#718099", fontSize: "10px", lineHeight: 1.4 },
  selectRow: { display: "flex", alignItems: "center", justifyContent: "space-between", backgroundColor: "#ffffff", border: "1px solid #dce1ea", padding: "6px 10px", borderRadius: "8px" },
  selectLabel: { fontSize: "12px", fontWeight: "600", color: "#424b60", display: "flex", alignItems: "center", gap: "6px" },
  select: { fontSize: "11px", fontWeight: "bold", color: "#277fbb", backgroundColor: "#ffffff", border: "1px solid #dce1ea", borderRadius: "6px", padding: "2px 6px" },
  countHeader: { padding: "0 16px 6px 4px", display: "flex", justifyContent: "space-between", fontSize: "12px", color: "#7c8596" },
  cardWrapper: { position: "relative", marginBottom: "8px", borderRadius: "10px", transition: "all 0.18s ease", cursor: "pointer", transform: "scale(0.96)", transformOrigin: "top left", width: "104%" },
  distanceBadge: { position: "absolute", top: "10px", right: "14px", fontSize: "10px", fontWeight: "800", color: "#277fbb", backgroundColor: "#edf9ff", padding: "2px 6px", borderRadius: "20px", zIndex: 2, pointerEvents: "none" },
  mapContainer: { width: "100%", height: "100%", borderRadius: "13px", border: "1px solid #e8ebf1", overflow: "hidden", boxShadow: "0 1px 3px rgba(39, 63, 133, 0.03)" },
  wordCloudSection: { padding: "60px 20px", backgroundColor: "#ffffff", marginTop: "24px", borderTop: "1px solid #ebedf2" },
  ellipsis: { overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" },
};
