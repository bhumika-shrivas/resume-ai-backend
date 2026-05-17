package com.resumeai.auth.service;

import com.resumeai.auth.entity.AuditLog;
import com.resumeai.auth.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuditService {
    @Autowired
    private AuditLogRepository auditLogRepository;

    public void log(String userId, String action, String details) {
        AuditLog entry = AuditLog.of(userId, action, details);
        auditLogRepository.save(entry);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }
}