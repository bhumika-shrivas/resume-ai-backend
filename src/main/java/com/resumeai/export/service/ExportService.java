package com.resumeai.export.service;

import com.resumeai.export.entity.ExportJob;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExportService {
    ExportJob exportToPdf(Long resumeId, String userId, Map<String, Object> data);
    ExportJob exportToDocx(Long resumeId, String userId, Map<String, Object> data);
    ExportJob exportToJson(Long resumeId, String userId, Map<String, Object> data);
    
    void trackExport(Long resumeId, String userId, String format);
    
    Optional<ExportJob> getJobStatus(String jobId);
    List<ExportJob> getExportsByUser(String userId);
    
    byte[] downloadFile(String jobId);
    void deleteExport(String jobId);
    void cleanupExpiredExports();
    Map<String, Object> getExportStats();
}
