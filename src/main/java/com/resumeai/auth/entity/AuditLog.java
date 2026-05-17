package com.resumeai.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String action; // LOGIN, REGISTER, CREATE_RESUME, EXPORT, UPGRADE
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Static factory to avoid depending on Lombok-generated builder in the IDE
    public static AuditLog of(String userId, String action, String details) {
        AuditLog a = new AuditLog();
        a.userId = userId;
        a.action = action;
        a.details = details;
        return a;
    }
}