package com.resumeai.jobmatch.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "ai-service")
public interface AiClient {

    @PostMapping("/api/v1/ai/checkAts")
    Map<String, Object> checkAtsCompatibility(
            @RequestHeader("X-Auth-User") String userId,
            @RequestBody Map<String, Object> payload);

    @PostMapping("/api/v1/ai/tailorForJob")
    Map<String, Object> tailorResumeForJob(
            @RequestHeader("X-Auth-User") String userId,
            @RequestBody Map<String, Object> payload);
}
