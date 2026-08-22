package com.jobpilot.api.domain.book.dto;

import java.util.List;

public record AladinBookResponse(
        String isbn13,
        String title,
        String author,
        String publisher,
        String publishedAt,
        String coverUrl,
        String link,
        String description,
        String category,
        int price,
        double rating,
        List<String> tags
) {}
