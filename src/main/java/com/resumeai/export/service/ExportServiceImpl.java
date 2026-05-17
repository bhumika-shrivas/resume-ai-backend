package com.resumeai.export.service;

import com.resumeai.export.dto.ExportRequest;
import com.resumeai.export.entity.ExportJob;
import com.resumeai.export.repository.ExportRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExportServiceImpl implements ExportService {

    @Autowired
    private ExportRepository exportRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FileStorageService fileStorageService;

    @Value("${export.download.url}")
    private String downloadUrl;

    @Autowired
    private com.resumeai.export.client.AuthClient authClient;

    @Value("${export.exchange.name}")
    private String exchange;

    @Value("${export.routing.key}")
    private String routingKey;

    @Override
    @Transactional
    public ExportJob exportToPdf(Long resumeId, String userId, Map<String, Object> data) {
        return submitJob(resumeId, userId, "PDF", data);
    }

    @Override
    @Transactional
    public ExportJob exportToDocx(Long resumeId, String userId, Map<String, Object> data) {
        return submitJob(resumeId, userId, "DOCX", data);
    }

    @Override
    @Transactional
    public ExportJob exportToJson(Long resumeId, String userId, Map<String, Object> data) {
        return submitJob(resumeId, userId, "JSON", data);
    }

    private ExportJob submitJob(Long resumeId, String userId, String format, Map<String, Object> data) {
        checkExportLimit(userId, format);
        
        String jobId = UUID.randomUUID().toString();
        
        ExportJob job = new ExportJob();
        job.setJobId(jobId);
        job.setResumeId(resumeId);
        job.setUserId(userId);
        job.setFormat(format);
        job.setStatus("QUEUED");
        job.setRequestedAt(LocalDateTime.now());
        job.setTemplateId((String) data.getOrDefault("templateId", "default"));
        
        ExportJob savedJob = exportRepository.save(job);
        
        // Trigger async processing via RabbitMQ
        ExportRequest request = new ExportRequest(jobId, resumeId, userId, format, data);
        rabbitTemplate.convertAndSend(exchange, routingKey, request);
        
        return savedJob;
    }

    private void checkExportLimit(String userId, String format) {
        // userId here is likely an email based on previous context, but let's be safe
        Map<String, Object> user = authClient.getUserByEmail(userId);
        String plan = (String) user.getOrDefault("subscriptionPlan", "FREE");

        if ("FREE".equalsIgnoreCase(plan)) {
            if ("PDF".equalsIgnoreCase(format)) {
                int count = exportRepository.countByUserIdToday(userId, LocalDate.now().atStartOfDay());
                if (count >= 10) {
                    throw new RuntimeException("Daily PDF export limit (10) reached for Free tier. Upgrade to Premium for unlimited exports.");
                }
            } else {
                throw new RuntimeException(format + " export is a Premium feature. Please upgrade to use it.");
            }
        }
    }

    @Override
    @Transactional
    public void trackExport(Long resumeId, String userId, String format) {
        checkExportLimit(userId, format);

        // If limit is not exceeded, log the export for tracking
        ExportJob job = new ExportJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setResumeId(resumeId);
        job.setUserId(userId);
        job.setFormat(format);
        job.setStatus("COMPLETED"); // Frontend downloaded it directly
        job.setRequestedAt(LocalDateTime.now());
        job.setCompletedAt(LocalDateTime.now());
        exportRepository.save(job);
    }

    @Override
    public Optional<ExportJob> getJobStatus(String jobId) {
        return exportRepository.findByJobId(jobId).map(job -> {
            if ("COMPLETED".equals(job.getStatus()) && job.getFileUrl() != null) {
                job.setFileUrl(downloadUrl + job.getJobId());
            }
            return job;
        });
    }

    @Override
    public List<ExportJob> getExportsByUser(String userId) {
        List<ExportJob> jobs = exportRepository.findByUserId(userId);
        jobs.forEach(job -> {
            if ("COMPLETED".equals(job.getStatus()) && job.getFileUrl() != null) {
                job.setFileUrl(downloadUrl + job.getJobId());
            }
        });
        return jobs;
    }

    @Override
    public byte[] downloadFile(String jobId) {
        return exportRepository.findByJobId(jobId)
                .map(job -> fileStorageService.getFile(job.getFileUrl()))
                .orElseThrow(() -> new RuntimeException("Job not found or file not available"));
    }

    @Override
    @Transactional
    public void deleteExport(String jobId) {
        exportRepository.findByJobId(jobId).ifPresent(job -> {
            if (job.getFileUrl() != null) {
                fileStorageService.deleteFile(job.getFileUrl());
            }
            exportRepository.delete(job);
        });
    }

    @Override
    @Transactional
    public void cleanupExpiredExports() {
        // Disabled as requested (files stay permanently)
    }

    @Override
    public Map<String, Object> getExportStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExports", exportRepository.count());
        stats.put("pdfCount", exportRepository.findByFormat("PDF").size());
        stats.put("docxCount", exportRepository.findByFormat("DOCX").size());
        stats.put("jsonCount", exportRepository.findByFormat("JSON").size());
        return stats;
    }
}
