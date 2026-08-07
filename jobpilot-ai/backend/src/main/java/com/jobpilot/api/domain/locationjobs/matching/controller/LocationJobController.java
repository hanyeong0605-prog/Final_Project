package com.jobpilot.api.domain.locationjobs.matching.controller;

import com.jobpilot.api.domain.locationjobs.matching.dto.LocationJobResponseDto;
import com.jobpilot.api.domain.locationjobs.matching.service.LocationJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location-jobs")
public class LocationJobController {

    private final LocationJobService locationJobService;

    public LocationJobController(LocationJobService locationJobService) {
        this.locationJobService = locationJobService;
    }

    @GetMapping
    public ResponseEntity<List<LocationJobResponseDto>> getJobsWithinRadius(
            @RequestParam(name = "latitude") Double latitude,
            @RequestParam(name = "longitude") Double longitude,
            @RequestParam(name = "radiusKm", defaultValue = "5.0") Double radiusKm) {

        List<LocationJobResponseDto> result = locationJobService.getJobsWithinRadius(latitude, longitude, radiusKm);
        return ResponseEntity.ok(result);
    }
}