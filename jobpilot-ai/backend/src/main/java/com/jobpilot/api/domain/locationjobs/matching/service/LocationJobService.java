package com.jobpilot.api.domain.locationjobs.matching.service;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.locationjobs.matching.dto.LocationJobResponseDto;
import com.jobpilot.api.domain.locationjobs.matching.entity.Location;
import com.jobpilot.api.domain.locationjobs.matching.repository.LocationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class LocationJobService {

    private final LocationJobRepository locationJobRepository;

    public LocationJobService(LocationJobRepository locationJobRepository) {
        this.locationJobRepository = locationJobRepository;
    }

    public List<LocationJobResponseDto> getJobsWithinRadius(Double centerLat, Double centerLng, Double radiusKm) {
        List<Location> locations = locationJobRepository.findJobsWithinRadius(centerLat, centerLng, radiusKm);

        return locations.stream()
                .map(loc -> {
                    double distance = calculateDistance(centerLat, centerLng, loc.getLatitude(), loc.getLongitude());
                    double roundedDistance = Math.round(distance * 10.0) / 10.0;

                    String shortAddress = formatRegionAddress(loc);

                    // 연관된 JobPosting 객체 가져오기
                    JobPosting jp = loc.getJobPosting();

                    // 원본 공고가 있으면 원본 공고 데이터를, 없으면 fallback 데이터 적용
                    String title = (jp != null && jp.getTitle() != null) ? jp.getTitle() : "우리 동네 채용 공고";
                    String companyName = (jp != null && jp.getCompanyName() != null)
                            ? jp.getCompanyName()
                            : (loc.getSourceProvider() != null ? loc.getSourceProvider() : "채용 기업");
                    String logoUrl = (jp != null) ? jp.getCompanyLogoUrl() : null;
                    String experienceType = (jp != null) ? jp.getExperienceType() : null;
                    String employmentType = (jp != null) ? jp.getEmploymentType() : null;
                    String deadlineAt = (jp != null && jp.getDeadlineAt() != null) ? jp.getDeadlineAt().toString() : null;
                    String keywords = (jp != null) ? jp.getKeywords() : null;
                    String salary = (jp != null) ? jp.getSalary() : null;

                    return LocationJobResponseDto.builder()
                            .id(loc.getId())
                            .jobPostingId(loc.getJobPostingId())
                            .title(title)
                            .companyName(companyName)
                            .companyLogoUrl(logoUrl)
                            .address(shortAddress)
                            .experienceType(experienceType)
                            .employmentType(employmentType)
                            .deadlineAt(deadlineAt)
                            .keywords(keywords)
                            .salary(salary)
                            .latitude(loc.getLatitude())
                            .longitude(loc.getLongitude())
                            .distanceKm(roundedDistance)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String formatRegionAddress(Location loc) {
        StringBuilder sb = new StringBuilder();
        if (loc.getSido() != null && !loc.getSido().isBlank()) {
            sb.append(loc.getSido());
        }
        if (loc.getSigungu() != null && !loc.getSigungu().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(loc.getSigungu());
        }
        return sb.toString();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return 6371.0 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}