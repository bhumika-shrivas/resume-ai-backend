package com.resumeai.ai.dto;

public class AiStatsResponse {
    private long totalTokens;
    private long geminiProTokens;
    private long geminiFlashTokens;
    private double estimatedCostUsd;

    public AiStatsResponse(long totalTokens, long geminiProTokens, long geminiFlashTokens, double estimatedCostUsd) {
        this.totalTokens = totalTokens;
        this.geminiProTokens = geminiProTokens;
        this.geminiFlashTokens = geminiFlashTokens;
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

    public long getGeminiProTokens() { return geminiProTokens; }
    public void setGeminiProTokens(long geminiProTokens) { this.geminiProTokens = geminiProTokens; }

    public long getGeminiFlashTokens() { return geminiFlashTokens; }
    public void setGeminiFlashTokens(long geminiFlashTokens) { this.geminiFlashTokens = geminiFlashTokens; }

    public double getEstimatedCostUsd() { return estimatedCostUsd; }
    public void setEstimatedCostUsd(double estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; }
}
