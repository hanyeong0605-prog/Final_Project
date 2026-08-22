package com.jobpilot.api.domain.book.controller;

import com.jobpilot.api.domain.book.dto.AladinBookPageResponse;
import com.jobpilot.api.domain.book.service.AladinBookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
public class AladinBookController {
    private final AladinBookService service;

    public AladinBookController(AladinBookService service) { this.service = service; }

    @GetMapping
    public AladinBookPageResponse list(@RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long requirementId, @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") String category, @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return service.search(jobPostingId, requirementId, query, category, sort, page, size);
    }
}
