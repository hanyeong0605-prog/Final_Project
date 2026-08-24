import React, { useEffect, useRef } from "react";
import type { LocationJob } from "../model/types";
import { useNavigate } from "react-router-dom";

const CURRENT_LOCATION_MARKER = "/map-markers/current-location-cat.png";

interface Props {
  center: { lat: number; lng: number };
  radiusKm: number;
  jobs: LocationJob[];
  selectedJobId: number | null;
  isCenterLocked: boolean;
  onSelectJob: (job: LocationJob) => void;
  onCenterChanged: (center: { lat: number; lng: number }) => void;
}

export const KakaoMapContainer: React.FC<Props> = ({
  center,
  radiusKm,
  jobs,
  selectedJobId,
  isCenterLocked,
  onSelectJob,
  onCenterChanged,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const circleRef = useRef<any>(null);
  const centerMarkerRef = useRef<any>(null); 
  const markersRef = useRef<any[]>([]);
  const clustererRef = useRef<any>(null);
  const overlayRef = useRef<any>(null);
  const centerLockedRef = useRef(isCenterLocked);
  const onCenterChangedRef = useRef(onCenterChanged);
  
  const navigate = useNavigate();

  useEffect(() => {
    centerLockedRef.current = isCenterLocked;
    mapRef.current?.setDraggable(true);
  }, [isCenterLocked]);

  useEffect(() => {
    onCenterChangedRef.current = onCenterChanged;
  }, [onCenterChanged]);

  // 말풍선 생성 및 이벤트 핸들러
  const createOverlayContent = (job: LocationJob) => {
    const container = document.createElement("div");
    container.style.cssText = `
      padding: 10px 12px;
      background-color: #ffffff;
      border: 1px solid #277fbb;
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
        <strong style="color: #277fbb; font-size: 13px;">${job.companyName || "기업명 미지정"}</strong>
        <span style="font-size: 10px; color: #277fbb; font-weight: bold; background: #edf9ff; padding: 2px 6px; border-radius: 4px;">상세보기 &gt;</span>
      </div>
      <div style="font-weight: 600; color: #111827; margin-top: 2px; max-width: 180px; overflow: hidden; text-overflow: ellipsis;">
        ${job.title || "채용 공고"}
      </div>
      <div style="color: #6b7280; font-size: 11px; margin-top: 2px;">
        ${job.distanceKm}km 거리에 위치
      </div>
    `;

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

  // 지도 초기화
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
        kakaoMap.setDraggable(true);
        window.kakao.maps.event.addListener(kakaoMap, "idle", () => {
          const nextCenter = kakaoMap.getCenter();
          const next = { lat: nextCenter.getLat(), lng: nextCenter.getLng() };
          if (!centerLockedRef.current) onCenterChangedRef.current(next);
        });

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
      strokeColor: "#277fbb",
      strokeOpacity: 0.8,
      strokeStyle: "dashed",
      fillColor: "#65b8e4",
      fillOpacity: 0.1,
    });

    circle.setMap(map);
    circleRef.current = circle;

    if (centerMarkerRef.current) {
      centerMarkerRef.current.setMap(null);
    }

    // 내 기준 위치는 한눈에 구분되는 고양이 핀을 조금 크게, 공고 위치는 더 작은
    // 좌표 핀을 쓴다. offset을 핀 끝으로 맞춰 실제 좌표가 이미지 하단에 놓인다.
    const imageSize = new window.kakao.maps.Size(50, 66);
    const markerImage = new window.kakao.maps.MarkerImage(CURRENT_LOCATION_MARKER, imageSize, {
      offset: new window.kakao.maps.Point(25, 64),
    });

    const centerMarker = new window.kakao.maps.Marker({
      position: moveLatLon,
      image: markerImage,
      title: "설정된 내 기준 위치",
      zIndex: 4
    });

    centerMarker.setMap(map);
    centerMarkerRef.current = centerMarker;
  };

  // 3. 채용 공고 마커 생성
  const updateMarkers = (map: any, currentJobs: LocationJob[]) => {
    if (!map || !window.kakao) return;

    clustererRef.current?.clear();
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];

    if (overlayRef.current) {
      overlayRef.current.setMap(null);
    }

    const singleMarkerSvg = encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="38" height="38" viewBox="0 0 38 38"><circle cx="19" cy="19" r="17" fill="#5B92F3" stroke="white" stroke-width="3"/><text x="19" y="24" text-anchor="middle" fill="white" font-family="Arial,sans-serif" font-size="15" font-weight="700">1</text></svg>`);
    const jobMarkerImage = new window.kakao.maps.MarkerImage(`data:image/svg+xml;charset=UTF-8,${singleMarkerSvg}`, new window.kakao.maps.Size(38, 38), { offset: new window.kakao.maps.Point(19, 19) });

    currentJobs.forEach((job) => {
      const markerPosition = new window.kakao.maps.LatLng(job.latitude, job.longitude);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
        image: jobMarkerImage,
        title: job.title,
      });

      window.kakao.maps.event.addListener(marker, "click", () => {
        const contentNode = createOverlayContent(job);
        overlayRef.current.setContent(contentNode);
        overlayRef.current.setPosition(markerPosition);
        overlayRef.current.setMap(map);

        onSelectJob(job);
      });

      markersRef.current.push(marker);
    });

    const clusterer = new window.kakao.maps.MarkerClusterer({
      map,
      markers: markersRef.current,
      averageCenter: true,
      minLevel: 1,
      minClusterSize: 2,
      disableClickZoom: false,
      styles: [{ width: "42px", height: "42px", background: "#5B92F3", border: "3px solid #fff", borderRadius: "50%", color: "#fff", textAlign: "center", fontWeight: "800", fontSize: "14px", lineHeight: "36px", boxShadow: "0 5px 14px rgba(43, 82, 153, .3)" }],
    });
    clustererRef.current = clusterer;
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
