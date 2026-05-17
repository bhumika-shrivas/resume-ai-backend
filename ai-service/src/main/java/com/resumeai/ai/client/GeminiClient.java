package com.resumeai.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiClient {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGemini(String prompt, String model) {
        return executeGeminiRequest(prompt, model, false);
    }
    
    public String callGeminiJsonMode(String prompt, String model) {
        return executeGeminiRequest(prompt, model, true);
    }

    private String executeGeminiRequest(String prompt, String model, boolean jsonMode) {
        if (apiKey == null || apiKey.isEmpty() || "mock_key".equals(apiKey)) {
            throw new RuntimeException("Gemini API Key is missing or invalid. Please check your application.properties.");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        
        // Build contents
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, String>> parts = new ArrayList<>();
        Map<String, String> partMap = new HashMap<>();
        partMap.put("text", prompt);
        parts.add(partMap);
        contentMap.put("parts", parts);
        contents.add(contentMap);
        
        requestBody.put("contents", contents);
        
        // Add generation config to ensure deterministic responses and JSON mode if requested
        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", 0.0);
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json");
        }
        requestBody.put("generationConfig", genConfig);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            
            // Extract the text from the response structure: candidates[0].content.parts[0].text
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            }
            throw new RuntimeException("Unexpected response structure from Gemini API");
            
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Gemini API Error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }
}
