package com.resumeai.ai.client;

import lombok.Data;

@Data
public class UserUsageDto {
    private int aiCallsThisMonth;
    private int atsChecksThisMonth;
    private int resumesCreated;

    // Explicit getters/setters to make IDE/compiler happy when Lombok isn't processed
    public int getAiCallsThisMonth() { return aiCallsThisMonth; }
    public void setAiCallsThisMonth(int aiCallsThisMonth) { this.aiCallsThisMonth = aiCallsThisMonth; }

    public int getAtsChecksThisMonth() { return atsChecksThisMonth; }
    public void setAtsChecksThisMonth(int atsChecksThisMonth) { this.atsChecksThisMonth = atsChecksThisMonth; }

    public int getResumesCreated() { return resumesCreated; }
    public void setResumesCreated(int resumesCreated) { this.resumesCreated = resumesCreated; }
}