package com.resumeai.ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_requests")
public class AiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id", updatable = false, nullable = false)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "request_type", nullable = false)
    private String requestType; // SUMMARY, BULLETS, COVER_LETTER, IMPROVE, ATS, SKILLS, TAILOR, TRANSLATE

    @Column(name = "input_prompt", columnDefinition = "TEXT")
    private String inputPrompt;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "model")
    private String model; // GPT-4o, CLAUDE

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "status")
    private String status; // QUEUED, COMPLETED, FAILED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "QUEUED";
        }
    }

    public AiRequest() {}

    public AiRequest(String requestId, String userId, Long resumeId, String requestType, String inputPrompt, String aiResponse, String model, Integer tokensUsed, String status, LocalDateTime createdAt, LocalDateTime completedAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.resumeId = resumeId;
        this.requestType = requestType;
        this.inputPrompt = inputPrompt;
        this.aiResponse = aiResponse;
        this.model = model;
        this.tokensUsed = tokensUsed;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getInputPrompt() { return inputPrompt; }
    public void setInputPrompt(String inputPrompt) { this.inputPrompt = inputPrompt; }

    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public static AiRequestBuilder builder() {
        return new AiRequestBuilder();
    }

    public static class AiRequestBuilder {
        private String userId;
        private Long resumeId;
        private String requestType;
        private String inputPrompt;
        private String model;
        private String status;

        public AiRequestBuilder userId(String userId) { this.userId = userId; return this; }
        public AiRequestBuilder resumeId(Long resumeId) { this.resumeId = resumeId; return this; }
        public AiRequestBuilder requestType(String requestType) { this.requestType = requestType; return this; }
        public AiRequestBuilder inputPrompt(String inputPrompt) { this.inputPrompt = inputPrompt; return this; }
        public AiRequestBuilder model(String model) { this.model = model; return this; }
        public AiRequestBuilder status(String status) { this.status = status; return this; }

        public AiRequest build() {
            AiRequest req = new AiRequest();
            req.setUserId(this.userId);
            req.setResumeId(this.resumeId);
            req.setRequestType(this.requestType);
            req.setInputPrompt(this.inputPrompt);
            req.setModel(this.model);
            req.setStatus(this.status);
            return req;
        }
    }
}
