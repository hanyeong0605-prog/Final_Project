package com.jobpilot.api.domain.Qnet;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/qnet")
@RequiredArgsConstructor
public class QnetController {

    private final QnetService qnetService;

    @GetMapping("/qualifications")
    public ResponseEntity<List<QnetDto>> getQualifications() {
        // serviceKey 파라미터 제거 후, DB에서 바로 조회해서 리턴
        List<QnetDto> list = qnetService.getQnetFromDB();
        return ResponseEntity.ok(list);
    }
}