package com.resumeai.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "resume-service")
public interface ResumeClient {

    @GetMapping("/api/v1/resumes/{id}")
    Map<String, Object> getResumeById(@PathVariable("id") Long id, @RequestHeader("X-Auth-User") String email);
}
