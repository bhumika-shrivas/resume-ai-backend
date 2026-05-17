package com.resumeai.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_usage")
public class UserUsage {

    @Id
    private Long userId; // Maps 1:1 with User.id

    private int aiCallsThisMonth;
    private int atsChecksThisMonth;
    private int resumesCreated;
    
    private LocalDateTime lastResetDate;

    public UserUsage() {}

    public UserUsage(Long userId) {
        this.userId = userId;
        this.aiCallsThisMonth = 0;
        this.atsChecksThisMonth = 0;
        this.resumesCreated = 0;
        this.lastResetDate = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getAiCallsThisMonth() { return aiCallsThisMonth; }
    public void setAiCallsThisMonth(int aiCallsThisMonth) { this.aiCallsThisMonth = aiCallsThisMonth; }

    public int getAtsChecksThisMonth() { return atsChecksThisMonth; }
    public void setAtsChecksThisMonth(int atsChecksThisMonth) { this.atsChecksThisMonth = atsChecksThisMonth; }

    public int getResumesCreated() { return resumesCreated; }
    public void setResumesCreated(int resumesCreated) { this.resumesCreated = resumesCreated; }

    public LocalDateTime getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDateTime lastResetDate) { this.lastResetDate = lastResetDate; }
}
