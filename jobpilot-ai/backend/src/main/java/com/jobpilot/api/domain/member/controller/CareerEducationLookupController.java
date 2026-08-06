package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.EducationMajorResponse;
import com.jobpilot.api.domain.member.dto.EducationSchoolResponse;
import com.jobpilot.api.domain.member.service.CareerEducationLookupService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/education")
public class CareerEducationLookupController {
    private final CareerEducationLookupService service;

    public CareerEducationLookupController(CareerEducationLookupService service) {
        this.service = service;
    }

    @GetMapping("/schools")
    public List<EducationSchoolResponse> schools(
            @RequestParam String query,
            @RequestParam(required = false) String educationLevel
    ) {
        return service.searchSchools(query, educationLevel);
    }

    @GetMapping("/majors")
    public List<EducationMajorResponse> majors(
            @RequestParam String query,
            @RequestParam(required = false) String educationLevel,
            @RequestParam String schoolName
    ) {
        return service.searchMajors(query, educationLevel, schoolName);
    }
}
