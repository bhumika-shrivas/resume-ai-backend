package com.resumeai.export.controller;

import com.resumeai.export.entity.ExportJob;
import com.resumeai.export.service.ExportService;
import com.resumeai.export.service.PdfGenerationService;
import com.resumeai.export.service.DocxGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportResource {

    @Autowired
    private ExportService exportService;

    @Autowired
    private PdfGenerationService pdfGenerationService;

    @Autowired
    private DocxGenerationService docxGenerationService;

    // ── Synchronous PDF endpoints ─────────────────────────────────────────

    /**
     * Synchronously generate and return PDF bytes for download.
     * POST /api/v1/exports/pdf/direct
     * Body: { "resumeId": 1, "templateId": "modern-executive", "filename": "resume.pdf" }
     */
    @PostMapping("/pdf/direct")
    public ResponseEntity<byte[]> exportPdfDirect(@RequestBody Map<String, Object> body) {
        Long resumeId = body.get("resumeId") != null ? Long.valueOf(body.get("resumeId").toString()) : 0L;
        String templateId = body.getOrDefault("templateId", "modern-executive").toString();
        String filename = body.getOrDefault("filename", "resume.pdf").toString();
        String htmlContent = body.get("htmlContent") != null ? body.get("htmlContent").toString() : null;

        byte[] pdfBytes;
        if (htmlContent != null && !htmlContent.isEmpty()) {
            pdfBytes = pdfGenerationService.htmlToPdf(htmlContent);
        } else {
            // Fallback for legacy behavior, though this will likely fail if template-service doesn't have the endpoint
            pdfBytes = pdfGenerationService.generatePdf(resumeId, templateId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    /**
     * Synchronously generate and return DOCX bytes for download.
     * POST /api/v1/exports/docx/direct
     */
    @PostMapping("/docx/direct")
    public ResponseEntity<?> exportDocxDirect(
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String role,
            @RequestBody Map<String, Object> body) {
        
        if (!"PREMIUM".equalsIgnoreCase(plan) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "DOCX export is a Premium feature. Please upgrade to use this feature."));
        }
        
        String filename = body.getOrDefault("filename", "resume.docx").toString();
        
        try {
            byte[] docxBytes = docxGenerationService.generateDocx(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(docxBytes.length);
            return ResponseEntity.ok().headers(headers).body(docxBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Synchronously generate and return JSON bytes for download.
     * POST /api/v1/exports/json/direct
     */
    @PostMapping("/json/direct")
    public ResponseEntity<?> exportJsonDirect(
            @RequestHeader(value = "X-Auth-Plan", defaultValue = "FREE") String plan,
            @RequestHeader(value = "X-Auth-Role", defaultValue = "USER") String role,
            @RequestBody Map<String, Object> body) {
        
        if (!"PREMIUM".equalsIgnoreCase(plan) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "JSON export is a Premium feature. Please upgrade to use this feature."));
        }
        
        String filename = body.getOrDefault("filename", "resume.json").toString();
        try {
            // Simple serialization
            byte[] jsonBytes = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(jsonBytes.length);
            return ResponseEntity.ok().headers(headers).body(jsonBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Synchronously generate and return PDF bytes for inline browser preview.
     * POST /api/v1/exports/pdf/preview
     */
    @PostMapping("/pdf/preview")
    public ResponseEntity<byte[]> previewPdf(@RequestBody Map<String, Object> body) {
        Long resumeId = Long.valueOf(body.get("resumeId").toString());
        String templateId = body.getOrDefault("templateId", "modern-executive").toString();

        byte[] pdfBytes = pdfGenerationService.generatePdf(resumeId, templateId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add("Content-Disposition", "inline; filename=\"resume.pdf\"");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    // ── Existing async queue endpoints (preserved) ────────────────────────


    @PostMapping("/pdf/{resumeId}")
    public ResponseEntity<ExportJob> exportPdf(@PathVariable Long resumeId, @RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> data) {
        return ResponseEntity.ok(exportService.exportToPdf(resumeId, userId, data));
    }

    @PostMapping("/docx/{resumeId}")
    public ResponseEntity<ExportJob> exportDocx(@PathVariable Long resumeId, @RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> data) {
        return ResponseEntity.ok(exportService.exportToDocx(resumeId, userId, data));
    }

    @PostMapping("/json/{resumeId}")
    public ResponseEntity<ExportJob> exportJson(@PathVariable Long resumeId, @RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> data) {
        return ResponseEntity.ok(exportService.exportToJson(resumeId, userId, data));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<ExportJob> getJobStatus(@PathVariable String jobId) {
        return exportService.getJobStatus(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ExportJob>> getExportsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(exportService.getExportsByUser(userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getExportStats() {
        return ResponseEntity.ok(exportService.getExportStats());
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteExport(@PathVariable String jobId) {
        exportService.deleteExport(jobId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String jobId) {
        ExportJob job = exportService.getJobStatus(jobId)
                .orElseThrow(() -> new RuntimeException("Export not found"));
        
        byte[] content = exportService.downloadFile(jobId);
        String extension = job.getFormat().toLowerCase();
        String contentType = "application/octet-stream";
        
        if ("pdf".equals(extension)) contentType = "application/pdf";
        else if ("docx".equals(extension)) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if ("json".equals(extension)) contentType = "application/json";

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"resume-" + jobId + "." + extension + "\"")
                .body(content);
    }

    @PostMapping("/track")
    public ResponseEntity<Map<String, String>> trackExport(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> body) {
        try {
            Long resumeId = body.get("resumeId") != null ? Long.valueOf(body.get("resumeId").toString()) : 0L;
            String format = body.getOrDefault("format", "PDF").toString().toUpperCase();
            
            exportService.trackExport(resumeId, userId, format);
            
            Map<String, String> resp = new java.util.HashMap<>();
            resp.put("status", "success");
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            Map<String, String> resp = new java.util.HashMap<>();
            resp.put("error", e.getMessage());
            return ResponseEntity.status(403).body(resp);
        }
    }
}
