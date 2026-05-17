package com.resumeai.web.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "template-service")
public interface TemplateClient {
    @GetMapping("/api/v1/templates")
    List<Map<String, Object>> getTemplates();
}
