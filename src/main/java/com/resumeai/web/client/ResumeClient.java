package com.resumeai.web.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "resume-service")
public interface ResumeClient {
    @GetMapping("/api/v1/resumes/user/{userId}")
    List<Map<String, Object>> getResumes(@PathVariable String userId);

    @GetMapping("/api/v1/resumes/{id}")
    Map<String, Object> getResume(@PathVariable Long id);

    @PostMapping("/api/v1/resumes")
    Map<String, Object> createResume(@RequestBody Map<String, Object> resume);
}
