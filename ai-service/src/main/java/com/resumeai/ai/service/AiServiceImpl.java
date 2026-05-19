package com.resumeai.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.ai.client.AuthClient;
import com.resumeai.ai.client.NotificationClient;
import com.resumeai.ai.client.GeminiClient;
import com.resumeai.ai.client.ResumeClient;
import com.resumeai.ai.entity.AiRequest;
import com.resumeai.ai.repository.AiRequestRepository;
import com.resumeai.ai.util.InputSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Service
public class AiServiceImpl implements AiService {

    private static final int FREE_AI_MONTHLY_QUOTA = 5;
    private static final String GEMINI_MODEL = "gemini-1.5-flash";

    @Autowired
    private AuthClient authClient;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private ResumeClient resumeClient;

    @Autowired
    private GeminiClient geminiClient;

    @Autowired
    private AiRequestRepository aiRequestRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int getRemainingQuota(String userId) {
        String plan = getSubscriptionPlan(userId);
        if ("PREMIUM".equalsIgnoreCase(plan)) return 999;

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        
        int totalUsed = aiRequestRepository.countByUserIdAndTypeAndDate(userId, "SUMMARY", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "BULLETS", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "IMPROVE", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "SKILLS", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "ATS", startOfMonth);
        
        return Math.max(0, FREE_AI_MONTHLY_QUOTA - totalUsed);
    }

    private void checkPremiumOnly(String userId) {
        String plan = getSubscriptionPlan(userId);
        if (!"PREMIUM".equalsIgnoreCase(plan)) {
            throw new RuntimeException("PREMIUM_REQUIRED: This is a premium-only feature.");
        }
    }

    private void checkQuota(String userId, String type) {
        String plan = getSubscriptionPlan(userId);
        if ("PREMIUM".equalsIgnoreCase(plan)) return;

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        
        checkAndSendQuotaWarning(userId, plan, startOfMonth);

        int totalUsed = aiRequestRepository.countByUserIdAndTypeAndDate(userId, "SUMMARY", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "BULLETS", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "IMPROVE", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "SKILLS", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "ATS", startOfMonth);
        
        if (totalUsed >= FREE_AI_MONTHLY_QUOTA) {
            throw new RuntimeException("Monthly AI quota (5) exceeded. Upgrade to Premium for unlimited access.");
        }
    }

    private String getSubscriptionPlan(String userId) {
        try {
            com.resumeai.ai.client.UserDto user = authClient.getUserByEmail(userId);
            return user.getSubscriptionPlan() != null ? user.getSubscriptionPlan() : "FREE";
        } catch (Exception e) {
            return "FREE";
        }
    }

    private void checkAndSendQuotaWarning(String userId, String plan, LocalDateTime startOfMonth) {
        int totalUsed = aiRequestRepository.countByUserIdAndTypeAndDate(userId, "SUMMARY", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "BULLETS", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "IMPROVE", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "SKILLS", startOfMonth) +
                        aiRequestRepository.countByUserIdAndTypeAndDate(userId, "ATS", startOfMonth);
        
        if (totalUsed == (int)(FREE_AI_MONTHLY_QUOTA * 0.8)) {
            sendQuotaNotification(userId, "AI Quota Warning", "You have used 80% of your monthly AI quota.");
        }
    }

    private void sendQuotaNotification(String userId, String title, String message) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("recipientId", userId);
        notif.put("title", title);
        notif.put("message", message);
        notif.put("type", "QUOTA_WARNING");
        notificationClient.sendNotification(notif);
    }

    private AiRequest logRequest(String userId, Long resumeId, String type, String prompt, String model) {
        AiRequest req = AiRequest.builder()
                .userId(userId)
                .resumeId(resumeId)
                .requestType(type)
                .inputPrompt(prompt)
                .model(model)
                .status("QUEUED")
                .build();
        return aiRequestRepository.save(req);
    }

    private AiRequest completeRequest(AiRequest req, String response, int tokens) {
        req.setStatus("COMPLETED");
        req.setAiResponse(response);
        req.setTokensUsed(tokens);
        req.setCompletedAt(LocalDateTime.now());
        AiRequest saved = aiRequestRepository.save(req);

        Map<String, Object> notif = new HashMap<>();
        notif.put("recipientId", req.getUserId());
        notif.put("title", "AI Content Ready");
        notif.put("message", "Your AI " + req.getRequestType().toLowerCase() + " generation is complete.");
        notif.put("type", "AI_DONE");
        notif.put("relatedId", req.getResumeId() != null ? req.getResumeId().toString() : "null");
        try {
            notificationClient.sendNotification(notif);
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }

        return saved;
    }

    private String fetchResumeContent(Long resumeId, String userId) {
        if (resumeId == null) return "No resume provided.";
        try {
            Map<String, Object> resume = resumeClient.getResumeById(resumeId, userId);
            return InputSanitizer.sanitize(objectMapper.writeValueAsString(resume));
        } catch (Exception e) {
            System.err.println("Could not fetch resume from resume-service: " + e.getMessage());
            return "Could not fetch resume content.";
        }
    }

    private void sendAiDoneNotification(String userId) {
        try {
            java.util.Map<String, Object> notif = new java.util.HashMap<>();
            notif.put("recipientId", userId);
            notif.put("title", "AI Content Generated");
            notif.put("message", "Your AI-powered content has been generated successfully.");
            notif.put("type", "AI_DONE");
            notif.put("actionUrl", "/app/resumes");
            notificationClient.sendNotification(notif);
        } catch (Exception e) {
            System.err.println("Failed to send AI notification: " + e.getMessage());
        }
    }

    @Override
    public String generateSummary(Long resumeId, String userId, String role, String experience, String currentSummary) {
        checkQuota(userId, "SUMMARY");
        String resumeContent = fetchResumeContent(resumeId, userId);
        role = InputSanitizer.sanitize(role);
        experience = InputSanitizer.sanitize(experience);
        currentSummary = currentSummary != null ? InputSanitizer.sanitize(currentSummary).trim() : "";
        
        String prompt;
        if (!currentSummary.isEmpty() && !currentSummary.equals("undefined")) {
            prompt = "You are an expert resume writer. The user has drafted the following text for their professional summary:\n\n" +
                     "\"" + currentSummary + "\"\n\n" +
                     "Your task is to take this raw draft and rewrite it into a highly impressive, job-oriented professional summary paragraph (3-4 sentences). " +
                     "Fix the grammar, improve the vocabulary, and make it sound extremely professional.\n" +
                     "Role context: " + role + ". Experience context: " + experience + ".\n\n" +
                     "STRICT RULES:\n" +
                     "1. Respond ONLY with the final professional summary text.\n" +
                     "2. Do NOT include any greetings, conversational filler, or explanations (e.g., do not say 'Here is your summary').\n" +
                     "3. Do NOT provide templates with brackets like [Insert Name].";
        } else {
            prompt = "You are an expert resume writer. Generate a single, concise professional summary paragraph (3-4 sentences) for a " + role + " with " + experience + " experience.\n\n" +
                     "User's resume data for context:\n" + resumeContent + "\n\n" +
                     "STRICT RULES:\n" +
                     "1. Respond ONLY with the final professional summary text.\n" +
                     "2. Do NOT include any greetings, conversational filler, or explanations (e.g., do not say 'Here is your summary').\n" +
                     "3. Do NOT provide templates with brackets like [Insert Name]. If the input is generic, invent a strong, generalized professional summary.";
        }
        AiRequest req = logRequest(userId, resumeId, "SUMMARY", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGemini(prompt, GEMINI_MODEL);
            completeRequest(req, response, 50);
            sendAiDoneNotification(userId);
            return response;
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            String fallback = "This is a premium AI-generated professional summary tailored for a " + role + " with " + experience + " experience. (Fallback Mode Active due to API Quota)";
            completeRequest(req, fallback, 0);
            return fallback;
        }
    }

    @Override
    public List<String> generateBulletPoints(Long resumeId, String userId, String role, String description) {
        checkQuota(userId, "BULLETS");
        role = InputSanitizer.sanitize(role);
        description = InputSanitizer.sanitize(description);
        
        String prompt = "Generate 3 professional resume bullet points for the role: " + role + " based on the following task/description:\n" + description + "\n\nFormat the output strictly as a JSON object with a single key 'bullets' containing an array of strings:\n{\n  \"bullets\": [\"point 1\", \"point 2\", \"point 3\"]\n}";
        AiRequest req = logRequest(userId, resumeId, "BULLETS", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGeminiJsonMode(prompt, GEMINI_MODEL);
            return parseBulletsResponse(response, req);
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            List<String> fallback = Arrays.asList(
                "Spearheaded development of core features resulting in 30% efficiency increase.", 
                "Collaborated with cross-functional teams to deliver projects on time.", 
                "Optimized application performance and improved user experience. (Fallback Mode)"
            );
            completeRequest(req, fallback.toString(), 0);
            return fallback;
        }
    }
    
    private List<String> parseBulletsResponse(String response, AiRequest req) {
        try {
            Map<String, List<String>> result = objectMapper.readValue(response, new TypeReference<Map<String, List<String>>>(){});
            List<String> bullets = result.getOrDefault("bullets", new ArrayList<>());
            if(bullets.isEmpty()) {
                bullets = objectMapper.readValue(response, new TypeReference<List<String>>(){});
            }
            completeRequest(req, response, 120);
            sendAiDoneNotification(req.getUserId());
            return bullets;
        } catch (Exception e) {
            List<String> fallback = new ArrayList<>();
            fallback.add(response);
            completeRequest(req, response, 120);
            return fallback;
        }
    }

    @Override
    public String generateCoverLetter(Long resumeId, String userId, Map<String, Object> jobDetails) {
        checkPremiumOnly(userId);
        String resumeContent = fetchResumeContent(resumeId, userId);
        String jobDetailsStr = InputSanitizer.sanitize(jobDetails.toString());
        
        String prompt = "You are an expert cover letter writer. Generate a professional cover letter (3-4 paragraphs) for the following job details:\n" + jobDetailsStr + "\n\n" +
                        "Based on the candidate's resume:\n" + resumeContent + "\n\n" +
                        "STRICT RULES:\n" +
                        "1. Respond ONLY with the final cover letter text.\n" +
                        "2. Do NOT include any conversational filler, explanations, or templates with brackets (like [Company Name]). Guess or use placeholders naturally if data is missing.";
        AiRequest req = logRequest(userId, resumeId, "COVER_LETTER", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGemini(prompt, GEMINI_MODEL);
            completeRequest(req, response, 200);
            sendAiDoneNotification(userId);
            return response;
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            String fallback = "Dear Hiring Manager,\n\nI am writing to express my interest in the position. Based on my experience and skills, I believe I would be a great fit for your team. (Fallback Mode Active)\n\nSincerely,\nCandidate";
            completeRequest(req, fallback, 0);
            return fallback;
        }
    }

    @Override
    public String improveSection(Long resumeId, String userId, String sectionContent) {
        checkPremiumOnly(userId);
        sectionContent = InputSanitizer.sanitize(sectionContent);
        
        String prompt = "You are an expert resume writer. Improve the professional tone, clarity, and impact of the following resume section:\n" + sectionContent + "\n\n" +
                        "STRICT RULES:\n" +
                        "1. Respond ONLY with the improved text.\n" +
                        "2. Do NOT include any conversational filler, greetings, or explanations.";
        AiRequest req = logRequest(userId, resumeId, "IMPROVE", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGemini(prompt, GEMINI_MODEL);
            completeRequest(req, response, 80);
            sendAiDoneNotification(userId);
            return response;
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            String fallback = sectionContent + "\n\n[Improved by AI Fallback Mode: Demonstrated strong capability in this area with measurable impact.]";
            completeRequest(req, fallback, 0);
            return fallback;
        }
    }

    @Override
    public Map<String, Object> checkAtsCompatibility(String userId, Map<String, Object> payload) {
        checkQuota(userId, "ATS");
        
        Long resumeId = null;
        Object rIdObj = payload.get("resumeId");
        if (rIdObj instanceof Number) resumeId = ((Number) rIdObj).longValue();
        else if (rIdObj instanceof String) resumeId = Long.parseLong((String) rIdObj);
        
        String jobDescription = (String) payload.get("jobDescription");
        jobDescription = jobDescription != null ? InputSanitizer.sanitize(jobDescription).trim() : "";
        
        String resumeContent;
        if (payload.containsKey("resumeData") && payload.get("resumeData") != null) {
            try {
                resumeContent = InputSanitizer.sanitize(objectMapper.writeValueAsString(payload.get("resumeData")));
            } catch (Exception e) {
                resumeContent = fetchResumeContent(resumeId, userId);
            }
        } else {
            resumeContent = fetchResumeContent(resumeId, userId);
        }
        
        String prompt;
        if (jobDescription.isEmpty()) {
            prompt = "You are an ATS (Applicant Tracking System) expert. Analyze the following resume for general ATS compatibility and best practices. " +
                     "Evaluate keyword optimization, action verbs, formatting, and overall structure.\n" +
                     "DO NOT hallucinate. Base your analysis PURELY on the provided Resume JSON.\n\n" +
                     "Resume JSON:\n" + resumeContent + "\n\n" +
                     "Respond ONLY with a valid JSON object matching the SCHEMA below. Replace the example values with your actual analysis of the resume:\n" +
                     "{\n" +
                     "  \"score\": <integer from 0 to 100 based on resume quality>,\n" +
                     "  \"missingKeywords\": [\"<keyword1>\", \"<keyword2>\"],\n" +
                     "  \"recommendations\": [\"<actionable recommendation 1>\", \"<actionable recommendation 2>\"]\n" +
                     "}";
        } else {
            prompt = "You are an ATS (Applicant Tracking System) expert. Compare the following resume against the provided Job Description.\n" +
                     "DO NOT hallucinate. Base your analysis PURELY on how well the Resume JSON matches the Job Description.\n\n" +
                     "Resume JSON:\n" + resumeContent + "\n\n" +
                     "Job Description:\n" + jobDescription + "\n\n" +
                     "Respond ONLY with a valid JSON object matching the SCHEMA below. Replace the example values with your actual analysis of the match:\n" +
                     "{\n" +
                     "  \"score\": <integer from 0 to 100 based on match quality>,\n" +
                     "  \"missingKeywords\": [\"<keyword1>\", \"<keyword2>\"],\n" +
                     "  \"recommendations\": [\"<actionable recommendation 1>\", \"<actionable recommendation 2>\"]\n" +
                     "}";
        }
        
        AiRequest req = logRequest(userId, resumeId, "ATS", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGeminiJsonMode(prompt, GEMINI_MODEL);
            return parseAtsResponse(response, req);
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("score", 75);
            fallback.put("missingKeywords", Arrays.asList("Fallback Keyword 1", "Fallback Keyword 2"));
            fallback.put("recommendations", Arrays.asList("Add more details about your experience.", "Include missing keywords. (Fallback Mode)"));
            completeRequest(req, fallback.toString(), 0);
            return fallback;
        }
    }
    
    private Map<String, Object> parseAtsResponse(String response, AiRequest req) throws Exception {
        try {
            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>(){});
            completeRequest(req, response, 150);
            return result;
        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse ATS JSON response: " + e.getMessage());
            throw e; 
        }
    }

    @Override
    public List<String> suggestSkills(Long resumeId, String userId, String roleOrIndustry) {
        checkQuota(userId, "SKILLS");
        roleOrIndustry = InputSanitizer.sanitize(roleOrIndustry);
        
        String prompt = "Suggest 5 to 10 key technical and soft skills for the role or industry: " + roleOrIndustry + ".\nReturn the response strictly as a JSON object with a single key 'skills' containing an array of strings:\n{\n  \"skills\": [\"skill1\", \"skill2\"]\n}";
        AiRequest req = logRequest(userId, resumeId, "SKILLS", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGeminiJsonMode(prompt, GEMINI_MODEL);
            return parseSkillsResponse(response, req);
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            List<String> fallback = Arrays.asList("Communication", "Teamwork", "Problem Solving", "Agile Methodology", "Project Management (Fallback)");
            completeRequest(req, fallback.toString(), 0);
            return fallback;
        }
    }
    
    private List<String> parseSkillsResponse(String response, AiRequest req) {
        try {
            Map<String, List<String>> result = objectMapper.readValue(response, new TypeReference<Map<String, List<String>>>(){});
            List<String> skills = result.getOrDefault("skills", new ArrayList<>());
            if(skills.isEmpty()) {
                 skills = objectMapper.readValue(response, new TypeReference<List<String>>(){});
            }
            completeRequest(req, response, 30);
            return skills;
        } catch (Exception e) {
            List<String> fallback = new ArrayList<>();
            fallback.add(response);
            completeRequest(req, response, 30);
            return fallback;
        }
    }

    @Override
    public Map<String, Object> tailorResumeForJob(Long resumeId, String userId, String jobDescription) {
        checkPremiumOnly(userId);
        String resumeContent = fetchResumeContent(resumeId, userId);
        jobDescription = InputSanitizer.sanitize(jobDescription);
        
        String prompt = "You are an expert resume writer. Tailor the given resume JSON for the provided Job Description.\n\n" +
                        "Resume JSON:\n" + resumeContent + "\n\n" +
                        "Job Description:\n" + jobDescription + "\n\n" +
                        "Respond ONLY with a JSON object matching this structure exactly:\n" +
                        "{\n" +
                        "  \"updatedSummary\": \"Optimized summary...\",\n" +
                        "  \"suggestedSkills\": [\"Keyword1\", \"Keyword2\"]\n" +
                        "}";
        
        AiRequest req = logRequest(userId, resumeId, "TAILOR", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGeminiJsonMode(prompt, GEMINI_MODEL);
            return parseTailorResponse(response, req);
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("status", "SUCCESS");
            fallback.put("updatedSummary", "Optimized summary based on Job Description (Fallback Mode Active due to API Quota).");
            fallback.put("suggestedSkills", Arrays.asList("Skill 1", "Skill 2"));
            completeRequest(req, fallback.toString(), 0);
            return fallback;
        }
    }
    
    private Map<String, Object> parseTailorResponse(String response, AiRequest req) throws Exception {
        try {
            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>(){});
            result.put("status", "SUCCESS");
            completeRequest(req, response, 600);
            return result;
        } catch (Exception e) {
            throw e; 
        }
    }

    @Override
    public List<AiRequest> getAiHistory(String userId) {
        return aiRequestRepository.findByUserId(userId);
    }

    @Override
    public Map<String, String> translateResume(Long resumeId, String userId, String targetLanguage) {
        checkPremiumOnly(userId);
        String resumeContent = fetchResumeContent(resumeId, userId);
        targetLanguage = InputSanitizer.sanitize(targetLanguage);
        
        String prompt = "Translate the following resume JSON to " + targetLanguage + ".\n\n" +
                        "Resume JSON:\n" + resumeContent + "\n\n" +
                        "Return ONLY the fully translated JSON object, keeping the exact same keys.";
        
        AiRequest req = logRequest(userId, resumeId, "TRANSLATE", prompt, GEMINI_MODEL);
        
        try {
            String response = geminiClient.callGeminiJsonMode(prompt, GEMINI_MODEL);
            return createTranslateResponse(response, req);
        } catch (Exception e) {
            System.err.println("Gemini failed, using fallback: " + e.getMessage());
            Map<String, String> fallback = new HashMap<>();
            fallback.put("status", "SUCCESS");
            fallback.put("translatedJson", "{\"message\": \"Translation Fallback Mode Active due to API Quota\"}");
            completeRequest(req, fallback.toString(), 0);
            return fallback;
        }
    }
    
    private Map<String, String> createTranslateResponse(String response, AiRequest req) {
        Map<String, String> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("translatedJson", response);
        completeRequest(req, response, 300);
        return result;
    }
}
