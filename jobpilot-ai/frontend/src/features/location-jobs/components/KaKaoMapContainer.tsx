import React, { useEffect, useRef } from "react";
import { LocationJob } from "../types";
import { useNavigate } from "react-router-dom";

interface Props {
  center: { lat: number; lng: number };
  radiusKm: number;
  jobs: LocationJob[];
  selectedJobId: number | null;
  onSelectJob: (job: LocationJob) => void;
}

export const KakaoMapContainer: React.FC<Props> = ({
  center,
  radiusKm,
  jobs,
  selectedJobId,
  onSelectJob,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const circleRef = useRef<any>(null);
  const markersRef = useRef<any[]>([]);
  const overlayRef = useRef<any>(null);
  
  const navigate = useNavigate();

  // 말풍선 생성 및 이벤트 핸들러
  const createOverlayContent = (job: LocationJob) => {
    const container = document.createElement("div");
    container.style.cssText = `
      padding: 10px 12px;
      background-color: #ffffff;
      border: 1px solid #2563eb;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      font-size: 12px;
      line-height: 1.4;
      cursor: pointer;
      position: relative;
      bottom: 45px;
      white-space: nowrap;
    `;

    container.innerHTML = `
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px;">
        <strong style="color: #2563eb; font-size: 13px;">${job.companyName || "기업명 미지정"}</strong>
        <span style="font-size: 10px; color: #2563eb; font-weight: bold; background: #eff6ff; padding: 2px 6px; border-radius: 4px;">상세보기 &gt;</span>
      </div>
      <div style="font-weight: 600; color: #111827; margin-top: 2px; max-width: 180px; overflow: hidden; text-overflow: ellipsis;">
        ${job.title || "채용 공고"}
      </div>
      <div style="color: #6b7280; font-size: 11px; margin-top: 2px;">
        ${job.distanceKm}km 거리에 위치
      </div>
    `;

    // 말풍선 클릭 시 상세 페이지 이동
    container.onclick = (e) => {
      e.stopPropagation();
      onSelectJob(job);
      
      const targetId = job.jobPostingId || job.id;
      if (targetId) {
        navigate(`/job-postings/${targetId}`);
      }
    };

    return container;
  };

  // 1. 지도 초기화
  useEffect(() => {
    if (!containerRef.current) return;

    const initMap = () => {
      if (!window.kakao || !window.kakao.maps) return;

      window.kakao.maps.load(() => {
        const options = {
          center: new window.kakao.maps.LatLng(center.lat, center.lng),
          level: 6,
        };
        const kakaoMap = new window.kakao.maps.Map(containerRef.current, options);
        mapRef.current = kakaoMap;

        // 커스텀 오버레이 생성
        overlayRef.current = new window.kakao.maps.CustomOverlay({
          zIndex: 3,
        });

        updateCircleAndLevel(kakaoMap, center, radiusKm, true);
        updateMarkers(kakaoMap, jobs);
      });
    };

    if (window.kakao && window.kakao.maps) {
      initMap();
    } else {
      const interval = setInterval(() => {
        if (window.kakao && window.kakao.maps) {
          initMap();
          clearInterval(interval);
        }
      }, 100);
      return () => clearInterval(interval);
    }
  }, []);

  // 2. 반경 원 
  const updateCircleAndLevel = (
    map: any, 
    currentCenter: { lat: number; lng: number }, 
    currentRadius: number,
    shouldMoveMap: boolean = false
  ) => {
    if (!map || !window.kakao) return;

    const moveLatLon = new window.kakao.maps.LatLng(currentCenter.lat, currentCenter.lng);
    
    if (shouldMoveMap) {
      map.setCenter(moveLatLon);
    }

    let level = 6;
    if (currentRadius >= 20) level = 8;
    else if (currentRadius >= 10) level = 7;
    else if (currentRadius >= 5) level = 6;
    else level = 5;
    map.setLevel(level);

    if (circleRef.current) circleRef.current.setMap(null);

    const circle = new window.kakao.maps.Circle({
      center: moveLatLon,
      radius: currentRadius * 1000,
      strokeWeight: 2,
      strokeColor: "#2563EB",
      strokeOpacity: 0.8,
      strokeStyle: "dashed",
      fillColor: "#3B82F6",
      fillOpacity: 0.1,
    });

    circle.setMap(map);
    circleRef.current = circle;
  };

  // 3. 마커 생성
  const updateMarkers = (map: any, currentJobs: LocationJob[]) => {
    if (!map || !window.kakao) return;

    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];

    if (overlayRef.current) {
      overlayRef.current.setMap(null);
    }

    currentJobs.forEach((job) => {
      const markerPosition = new window.kakao.maps.LatLng(job.latitude, job.longitude);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
        title: job.title,
      });

      marker.setMap(map);

      window.kakao.maps.event.addListener(marker, "click", () => {
        const contentNode = createOverlayContent(job);
        overlayRef.current.setContent(contentNode);
        overlayRef.current.setPosition(markerPosition);
        overlayRef.current.setMap(map);

        onSelectJob(job);
      });

      markersRef.current.push(marker);
    });
  };

  useEffect(() => {
    if (!mapRef.current || !selectedJobId) return;

    const targetJob = jobs.find((j) => j.id === selectedJobId);
    if (targetJob && overlayRef.current) {
      const markerPosition = new window.kakao.maps.LatLng(targetJob.latitude, targetJob.longitude);


      const contentNode = createOverlayContent(targetJob);
      overlayRef.current.setContent(contentNode);
      overlayRef.current.setPosition(markerPosition);
      overlayRef.current.setMap(mapRef.current);
    }
  }, [selectedJobId]);

  useEffect(() => {
    if (mapRef.current) updateCircleAndLevel(mapRef.current, center, radiusKm, true);
  }, [center, radiusKm]);

  useEffect(() => {
    if (mapRef.current) updateMarkers(mapRef.current, jobs);
  }, [jobs]);

  return (
    <div
      ref={containerRef}
      style={{
        width: "100%",
        height: "100%",
        minHeight: "600px",
        backgroundColor: "#e5e7eb",
      }}
    />
  );
};