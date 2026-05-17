package com.resumeai.resume.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "template-service")
public interface TemplateClient {
    @GetMapping("/api/v1/templates/{id}")
    TemplateDto getTemplateById(@PathVariable("id") String id);
}
