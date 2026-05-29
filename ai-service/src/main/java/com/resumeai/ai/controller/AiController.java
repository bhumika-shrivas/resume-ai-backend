package com.resumeai.ai.controller;

import com.resumeai.ai.entity.AiRequest;
import com.resumeai.ai.service.AiService;
import com.resumeai.ai.service.QuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private QuotaService quotaService;

    @PostMapping("/generateSummary")
    public ResponseEntity<String> generateSummary(
            @RequestHeader("X-Auth-User") String email,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        String role = (String) payload.get("role");
        String experience = (String) payload.get("experience");
        String currentSummary = (String) payload.get("currentSummary");
        String result = aiService.generateSummary(resumeId, email, role, experience, currentSummary);
        quotaService.incrementAiUsage(email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generateBullets")
    public ResponseEntity<List<String>> generateBullets(
            @RequestHeader("X-Auth-User") String email,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        String role = (String) payload.get("role");
        String description = (String) payload.get("description");
        List<String> result = aiService.generateBulletPoints(resumeId, email, role, description);
        quotaService.incrementAiUsage(email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generateCoverLetter")
    public ResponseEntity<String> generateCoverLetter(
            @RequestHeader("X-Auth-User") String email,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> jobDetails = (Map<String, Object>) payload.get("jobDetails");
        String result = aiService.generateCoverLetter(resumeId, email, jobDetails);
        quotaService.incrementAiUsage(email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/improveSection")
    public ResponseEntity<String> improveSection(
            @RequestHeader("X-Auth-User") String email,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        String sectionContent = (String) payload.get("sectionContent");
        String result = aiService.improveSection(resumeId, email, sectionContent);
        quotaService.incrementAiUsage(email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/checkAts")
    public ResponseEntity<Map<String, Object>> checkAts(
            @RequestHeader("X-Auth-User") String email,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Map<String, Object> result = aiService.checkAtsCompatibility(email, payload);
        quotaService.incrementAtsUsage(email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/suggestSkills")
    public ResponseEntity<List<String>> suggestSkills(
            @RequestHeader("X-Auth-User") String userId,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        String roleOrIndustry = (String) payload.get("roleOrIndustry");
        return ResponseEntity.ok(aiService.suggestSkills(resumeId, userId, roleOrIndustry));
    }

    @PostMapping("/tailorForJob")
    public ResponseEntity<Map<String, Object>> tailorForJob(
            @RequestHeader("X-Auth-User") String userId,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        String jobDescription = (String) payload.get("jobDescription");
        return ResponseEntity.ok(aiService.tailorResumeForJob(resumeId, userId, jobDescription));
    }

    @PostMapping("/translate")
    public ResponseEntity<Map<String, String>> translateResume(
            @RequestHeader("X-Auth-User") String userId,
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String userRole,
            @RequestBody Map<String, Object> payload) {
        Long resumeId = getResumeId(payload);
        String targetLanguage = (String) payload.get("targetLanguage");
        return ResponseEntity.ok(aiService.translateResume(resumeId, userId, targetLanguage));
    }

    @GetMapping("/history")
    public ResponseEntity<List<AiRequest>> getHistory(@RequestHeader("X-Auth-User") String userId) {
        return ResponseEntity.ok(aiService.getAiHistory(userId));
    }

    @GetMapping("/quota")
    public ResponseEntity<Integer> getRemainingQuota(@RequestHeader("X-Auth-User") String userId) {
        return ResponseEntity.ok(aiService.getRemainingQuota(userId));
    }

    @Autowired
    private com.resumeai.ai.repository.AiRequestRepository aiRequestRepository;

    @Autowired
    private com.resumeai.ai.repository.AiPricingConfigRepository aiPricingConfigRepository;

    private Long getResumeId(Map<String, Object> payload) {
        Object resumeIdObj = payload.get("resumeId");
        if (resumeIdObj instanceof Number) {
            return ((Number) resumeIdObj).longValue();
        } else if (resumeIdObj instanceof String) {
            return Long.parseLong((String) resumeIdObj);
        }
        return null;
    }

    @GetMapping("/admin/pricing")
    public ResponseEntity<List<com.resumeai.ai.entity.AiPricingConfig>> getPricingConfig() {
        return ResponseEntity.ok(aiPricingConfigRepository.findAll());
    }

    @PutMapping("/admin/pricing")
    public ResponseEntity<Void> updatePricingConfig(@RequestBody List<com.resumeai.ai.entity.AiPricingConfig> configs) {
        aiPricingConfigRepository.saveAll(configs);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<com.resumeai.ai.dto.AiStatsResponse> getAdminStats() {
        long totalTokens = aiRequestRepository.sumAllTokensUsed();
        long geminiProTokens = aiRequestRepository.sumTokensUsedByModel("gemini-1.5-pro");
        long geminiFlashTokens = aiRequestRepository.sumTokensUsedByModel("gemini-2.5-flash");
        
        // Fetch dynamic pricing
        double proPrice = aiPricingConfigRepository.findById("Gemini 1.5 Pro")
                .map(com.resumeai.ai.entity.AiPricingConfig::getCostPer1kTokens).orElse(0.005);
        double flashPrice = aiPricingConfigRepository.findById("Gemini 1.5 Flash")
                .map(com.resumeai.ai.entity.AiPricingConfig::getCostPer1kTokens).orElse(0.001);

        double cost = (geminiProTokens / 1000.0 * proPrice) + (geminiFlashTokens / 1000.0 * flashPrice);
        
        return ResponseEntity.ok(new com.resumeai.ai.dto.AiStatsResponse(totalTokens, geminiProTokens, geminiFlashTokens, cost));
    }

    @GetMapping("/admin/health/queue-time")
    public ResponseEntity<Double> getAverageQueueTime() {
        return ResponseEntity.ok(aiRequestRepository.getAverageQueueTime());
    }
}
