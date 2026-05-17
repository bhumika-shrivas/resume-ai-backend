package com.resumeai.section.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ResumeClient {

    private final String BASE_URL = "http://localhost:8082/resume";

    public String getResumeOwner(Long resumeId) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(BASE_URL + "/email/" + resumeId, String.class);
    }
}