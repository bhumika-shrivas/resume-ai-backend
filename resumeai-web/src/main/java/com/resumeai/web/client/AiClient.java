package com.resumeai.web.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "ai-service")
public interface AiClient {
    @PostMapping("/api/v1/ai/summary")
    String generateSummary(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> payload);
    
    @PostMapping("/api/v1/ai/ats-check")
    Map<String, Object> checkAts(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> payload);
}
