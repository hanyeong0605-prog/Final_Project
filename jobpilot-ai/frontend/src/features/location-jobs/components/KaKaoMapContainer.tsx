import React, { useEffect, useRef } from "react";
import { LocationJob } from "../types";

interface Props {
  center: { lat: number; lng: number };
  jobs: LocationJob[];
}

export const KakaoMapContainer: React.FC<Props> = ({ center, jobs }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const markersRef = useRef<any[]>([]);

  // 1. 지도
  useEffect(() => {
    if (!containerRef.current || !window.kakao) return;

    window.kakao.maps.load(() => {
      const options = {
        center: new window.kakao.maps.LatLng(center.lat, center.lng),
        level: 7, 
      };
      mapRef.current = new window.kakao.maps.Map(containerRef.current, options);
    });
  }, []);

  // 2. 중심 이동 및 반경 20km
  useEffect(() => {
    if (!mapRef.current) return;

    const moveLatLon = new window.kakao.maps.LatLng(center.lat, center.lng);
    mapRef.current.setCenter(moveLatLon);

    const circle = new window.kakao.maps.Circle({
      center: moveLatLon,
      radius: 20000, // 20km
      strokeWeight: 2,
      strokeColor: "#2563EB",
      strokeOpacity: 0.8,
      strokeStyle: "dashed",
      fillColor: "#3B82F6",
      fillOpacity: 0.1,
    });

    circle.setMap(mapRef.current);
    return () => circle.setMap(null);
  }, [center]);

  useEffect(() => {
    if (!mapRef.current) return;

    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];

    jobs.forEach((job) => {
      const markerPosition = new window.kakao.maps.LatLng(job.latitude, job.longitude);
      const marker = new window.kakao.maps.Marker({
        position: markerPosition,
        title: job.title,
      });

      marker.setMap(mapRef.current);
      markersRef.current.push(marker);
    });
  }, [jobs]);

  return <div ref={containerRef} className="w-full h-full rounded-r-xl" />;
};