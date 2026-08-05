// import React, { useEffect, useRef } from "react";
// import { LocationJob } from "../types";

// interface Props {
//   center: { lat: number; lng: number };
//   radiusKm: number;
//   jobs: LocationJob[];
// }

// export const KakaoMapContainer: React.FC<Props> = ({ center, radiusKm, jobs }) => {
//   const containerRef = useRef<HTMLDivElement>(null);
//   const mapRef = useRef<any>(null);
//   const circleRef = useRef<any>(null);
//   const markersRef = useRef<any[]>([]);

//   // 1. 지도 초기화 (SDK 로딩 대기 처리)
//   useEffect(() => {
//     if (!containerRef.current) return;

//     const initMap = () => {
//       if (!window.kakao || !window.kakao.maps) return;

//       window.kakao.maps.load(() => {
//         const options = {
//           center: new window.kakao.maps.LatLng(center.lat, center.lng),
//           level: 6,
//         };
//         const kakaoMap = new window.kakao.maps.Map(containerRef.current, options);
//         mapRef.current = kakaoMap;

//         // 초기 원 및 마커 그리기 실행
//         updateCircleAndLevel(kakaoMap, center, radiusKm);
//         updateMarkers(kakaoMap, jobs);
//       });
//     };

//     // 만약 kakao SDK가 아직 안 불러와졌다면 주기적으로 확인
//     if (window.kakao && window.kakao.maps) {
//       initMap();
//     } else {
//       const interval = setInterval(() => {
//         if (window.kakao && window.kakao.maps) {
//           initMap();
//           clearInterval(interval);
//         }
//       }, 100);

//       return () => clearInterval(interval);
//     }
//   }, []); // 최초 1회만 초기화

//   // 원 및 줌 레벨 업데이트 함수
//   const updateCircleAndLevel = (map: any, currentCenter: { lat: number; lng: number }, currentRadius: number) => {
//     if (!map || !window.kakao) return;

//     const moveLatLon = new window.kakao.maps.LatLng(currentCenter.lat, currentCenter.lng);
//     map.setCenter(moveLatLon);

//     let level = 6;
//     if (currentRadius >= 20) level = 8;
//     else if (currentRadius >= 10) level = 7;
//     else if (currentRadius >= 5) level = 6;
//     else level = 5;
//     map.setLevel(level);

//     // 기존 원 제거
//     if (circleRef.current) {
//       circleRef.current.setMap(null);
//     }

//     // 새 원 생성
//     const circle = new window.kakao.maps.Circle({
//       center: moveLatLon,
//       radius: currentRadius * 1000,
//       strokeWeight: 2,
//       strokeColor: "#2563EB",
//       strokeOpacity: 0.8,
//       strokeStyle: "dashed",
//       fillColor: "#3B82F6",
//       fillOpacity: 0.1,
//     });

//     circle.setMap(map);
//     circleRef.current = circle;
//   };

//   // 마커 업데이트 함수
//   const updateMarkers = (map: any, currentJobs: LocationJob[]) => {
//     if (!map || !window.kakao) return;

//     markersRef.current.forEach((m) => m.setMap(null));
//     markersRef.current = [];

//     currentJobs.forEach((job) => {
//       const markerPosition = new window.kakao.maps.LatLng(job.latitude, job.longitude);
//       const marker = new window.kakao.maps.Marker({
//         position: markerPosition,
//         title: job.title,
//       });

//       marker.setMap(map);
//       markersRef.current.push(marker);
//     });
//   };

//   // 2. center 또는 radiusKm 변경 시 업데이트
//   useEffect(() => {
//     if (mapRef.current) {
//       updateCircleAndLevel(mapRef.current, center, radiusKm);
//     }
//   }, [center, radiusKm]);

//   useEffect(() => {
//     if (mapRef.current) {
//       updateMarkers(mapRef.current, jobs);
//     }
//   }, [jobs]);

//   return (
//     <div
//       ref={containerRef}
//       style={{
//         width: "100%",
//         height: "100%",
//         minHeight: "600px",
//         backgroundColor: "#e5e7eb", 
//       }}
//     />
//   );
// };
import React, { useEffect, useRef } from "react";
import { LocationJob } from "../types";

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
  const infoWindowRef = useRef<any>(null);

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

        // 인포윈도우(말풍선) 객체 생성
        infoWindowRef.current = new window.kakao.maps.InfoWindow({ zIndex: 1 });

        updateCircleAndLevel(kakaoMap, center, radiusKm);
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

  // 2. 반경 원 및 Zoom 레벨 업데이트
  const updateCircleAndLevel = (map: any, currentCenter: { lat: number; lng: number }, currentRadius: number) => {
    if (!map || !window.kakao) return;

    const moveLatLon = new window.kakao.maps.LatLng(currentCenter.lat, currentCenter.lng);
    map.panTo(moveLatLon);

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

  // 3. 마커 및 클릭 시 말풍선(InfoWindow) 이벤트 연결
  const updateMarkers = (map: any, currentJobs: LocationJob[]) => {
    if (!map || !window.kakao) return;

    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];

    currentJobs.forEach((job) => {
      const markerPosition = new window.kakao.maps.LatLng(job.latitude, job.longitude);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
        title: job.title,
      });

      marker.setMap(map);

      // 마커 클릭 시 말풍선 표시 및 선택 상태 전달
      window.kakao.maps.event.addListener(marker, "click", () => {
        const content = `
          <div style="padding:10px;font-size:12px;width:180px;line-height:1.4;">
            <strong style="color:#2563eb;">${job.companyName}</strong><br/>
            <span style="font-weight:600;color:#111827;">${job.title}</span><br/>
            <span style="color:#6b7280;font-size:11px;">${job.distanceKm}km 거리에 위치</span>
          </div>
        `;
        infoWindowRef.current.setContent(content);
        infoWindowRef.current.open(map, marker);
        onSelectJob(job);
      });

      markersRef.current.push(marker);
    });
  };

  // 4. 선택된 카드(selectedJobId) 변경 시 지도 이동 & 말풍선 띄우기
  useEffect(() => {
    if (!mapRef.current || !selectedJobId) return;

    const targetJob = jobs.find((j) => j.id === selectedJobId);
    if (targetJob) {
      const moveLatLon = new window.kakao.maps.LatLng(targetJob.latitude, targetJob.longitude);
      mapRef.current.panTo(moveLatLon);

      // 해당 위치의 마커에 말풍선 띄우기
      const targetMarker = markersRef.current.find(
        (m, idx) => jobs[idx]?.id === selectedJobId
      );
      if (targetMarker && infoWindowRef.current) {
        const content = `
          <div style="padding:10px;font-size:12px;width:180px;line-height:1.4;">
            <strong style="color:#2563eb;">${targetJob.companyName}</strong><br/>
            <span style="font-weight:600;color:#111827;">${targetJob.title}</span><br/>
            <span style="color:#6b7280;font-size:11px;">${targetJob.distanceKm}km 거리에 위치</span>
          </div>
        `;
        infoWindowRef.current.setContent(content);
        infoWindowRef.current.open(mapRef.current, targetMarker);
      }
    }
  }, [selectedJobId]);

  useEffect(() => {
    if (mapRef.current) updateCircleAndLevel(mapRef.current, center, radiusKm);
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