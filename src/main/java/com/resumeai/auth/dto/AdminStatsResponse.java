package com.resumeai.auth.dto;

public class AdminStatsResponse {
    private long totalUsers;
    private long premiumUsers;
    private long totalAiCalls;
    private long totalAtsChecks;

    public AdminStatsResponse(long totalUsers, long premiumUsers, long totalAiCalls, long totalAtsChecks) {
        this.totalUsers = totalUsers;
        this.premiumUsers = premiumUsers;
        this.totalAiCalls = totalAiCalls;
        this.totalAtsChecks = totalAtsChecks;
    }

    // Getters and Setters
    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getPremiumUsers() { return premiumUsers; }
    public void setPremiumUsers(long premiumUsers) { this.premiumUsers = premiumUsers; }

    public long getTotalAiCalls() { return totalAiCalls; }
    public void setTotalAiCalls(long totalAiCalls) { this.totalAiCalls = totalAiCalls; }

    public long getTotalAtsChecks() { return totalAtsChecks; }
    public void setTotalAtsChecks(long totalAtsChecks) { this.totalAtsChecks = totalAtsChecks; }
}
