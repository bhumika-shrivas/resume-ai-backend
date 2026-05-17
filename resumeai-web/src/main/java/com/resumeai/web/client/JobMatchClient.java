package com.resumeai.web.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "jobmatch-service")
public interface JobMatchClient {
    @GetMapping("/api/v1/job-matches/search/linkedin")
    List<Map<String, Object>> searchLinkedIn(@RequestParam("query") String query, @RequestParam("location") String location);
    
    @PostMapping("/api/v1/job-matches/analyze")
    Map<String, Object> analyzeFit(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> payload);
}
