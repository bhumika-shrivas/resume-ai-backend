package com.resumeai.web.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "export-service")
public interface ExportClient {
    @PostMapping("/api/v1/exports/pdf")
    Map<String, Object> exportPdf(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> payload);
}
