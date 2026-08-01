package com.jobpilot.api.domain.jobposting.provider.saramindata.controller;

import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataSyncResponse;
import com.jobpilot.api.domain.jobposting.provider.saramindata.service.SaraminDataSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers/saramin-data")
public class SaraminDataController {
    private final SaraminDataSyncService syncService;

    public SaraminDataController(SaraminDataSyncService syncService) { this.syncService = syncService; }

    @PostMapping("/sync")
    public ResponseEntity<SaraminDataSyncResponse> sync() {
        return ResponseEntity.ok(syncService.sync());
    }
}
