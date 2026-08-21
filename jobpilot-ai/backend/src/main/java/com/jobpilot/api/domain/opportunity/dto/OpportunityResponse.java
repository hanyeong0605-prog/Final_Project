package com.jobpilot.api.domain.opportunity.dto;

import java.util.List;

public record OpportunityResponse(
        Long id,
        String type,
        String title,
        String organization,
        String period,
        String deadline,
        String reason,
        List<String> tags,
        String sourceUrl,
        String status, String address, String phone, String trainingTarget, Integer capacity, Integer enrolledCount, Integer courseFee, Integer selfPayFee, java.math.BigDecimal satisfactionScore, String detailUrl, String institutionUrl
) {}
