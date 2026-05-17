package com.resumeai.ai.service;

import com.resumeai.ai.entity.AiRequest;

import java.util.List;
import java.util.Map;

public interface AiService {

    String generateSummary(Long resumeId, String userId, String role, String experience, String currentSummary);

    List<String> generateBulletPoints(Long resumeId, String userId, String role, String description);

    String generateCoverLetter(Long resumeId, String userId, Map<String, Object> jobDetails);

    String improveSection(Long resumeId, String userId, String sectionContent);

    Map<String, Object> checkAtsCompatibility(String userId, Map<String, Object> payload);

    List<String> suggestSkills(Long resumeId, String userId, String roleOrIndustry);

    Map<String, Object> tailorResumeForJob(Long resumeId, String userId, String jobDescription);

    List<AiRequest> getAiHistory(String userId);

    int getRemainingQuota(String userId);

    Map<String, String> translateResume(Long resumeId, String userId, String targetLanguage);
}
