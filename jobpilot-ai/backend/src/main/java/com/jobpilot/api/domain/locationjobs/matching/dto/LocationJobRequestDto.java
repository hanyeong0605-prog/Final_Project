package com.jobpilot.api.domain.locationjobs.matching.dto;

public class LocationJobRequestDto {
    private Double latitude;   // 위도
    private Double longitude;  // 경도
    private Double radiusKm;   // 검색 반경

    public LocationJobRequestDto() {}

    public LocationJobRequestDto(Double latitude, Double longitude, Double radiusKm) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
    }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
}